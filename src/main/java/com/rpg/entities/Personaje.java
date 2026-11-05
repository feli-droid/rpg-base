package com.rpg.entities;

import com.rpg.concurrency.EfectoContinuoTask;
import com.rpg.entities.enums.EstadoPersonaje;
import com.rpg.entities.enums.TipoPersonaje;
import com.rpg.exceptions.SinManaException;
import com.rpg.items.Inventario;
import com.rpg.items.Pocion;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public abstract class Personaje implements ICombatiente, Serializable {

    /** TALLER ARCHIVOS: version de la clase serializada; si cambian los campos, se sube este numero. */
    private static final long serialVersionUID = 1L;

    private static final int VIDA_BASE = 100;
    private static final int ATAQUE_BASE = 10;
    private static final int DEFENSA_BASE = 5;
    private static final int CAPACIDAD_INVENTARIO = 3;
    private static final int DANIO_MINIMO = 1;
    private static final double PORCENTAJE_TOTAL = 100.0;
    /** Milisegundos entre dos ticks de un efecto continuo. */
    private static final long INTERVALO_EFECTO_MS = 1000;

    private volatile String nombre;
    private final TipoPersonaje tipo;
    private int vidaMaxima;
    private int vida;
    private final int ataqueBase;
    private final int defensaBase;
    private EstadoPersonaje estado = EstadoPersonaje.VIVO;
    private Arma arma;
    private Gremio gremio;
    private final Inventario<Pocion> inventario = new Inventario<>(CAPACIDAD_INVENTARIO);
    private final Set<String> habilidades = new HashSet<>();
    // Estado compartido entre hilos: solo se toca dentro de metodos synchronized.
    private final Map<String, EstadoEfecto> efectos = new LinkedHashMap<>();
    // transient: no se guarda. Al cargar vale null y se vuelve a crear vacio al usarse (ver hilos()).
    private transient Map<String, Thread> hilosEfectos = new HashMap<>();
    /** Quien recibe los mensajes de los efectos (el registro de combate). null = consola. */
    private transient volatile Consumer<String> observador;

    protected Personaje(String nombre, TipoPersonaje tipo) {
        this(nombre, tipo, VIDA_BASE, ATAQUE_BASE, DEFENSA_BASE);
    }

    protected Personaje(String nombre, TipoPersonaje tipo, int vidaMaxima, int ataqueBase, int defensaBase) {
        validarNombre(nombre);
        if (vidaMaxima <= 0 || ataqueBase < 0 || defensaBase < 0) {
            throw new IllegalArgumentException("Estadisticas invalidas para " + nombre);
        }
        this.nombre = nombre;
        this.tipo = tipo;
        this.vidaMaxima = vidaMaxima;
        this.vida = vidaMaxima;
        this.ataqueBase = ataqueBase;
        this.defensaBase = defensaBase;
    }

    private static void validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre no puede estar vacio");
        }
    }

    public abstract String realizarAtaqueEspecial(Personaje objetivo) throws SinManaException;

    /** Gancho que se ejecuta al iniciar el turno (el Mago regenera mana). */
    public void alIniciarTurno() {
    }

    public int calcularDanio() {
        double danio = ataqueBase + tipo.getBonificadorAtaque();
        if (arma != null) {
            danio += arma.getDanioEfectivo();
        }
        return (int) Math.round(danio);
    }

    @Override
    public void defender(int danioEntrante) {
        double reduccion = PORCENTAJE_TOTAL / (PORCENTAJE_TOTAL + getDefensa());
        int danioFinal = (int) Math.round(danioEntrante * reduccion);
        recibirDanio(Math.max(DANIO_MINIMO, danioFinal));
    }

    /**
     * Resta vida. synchronized: "leer vida, restar, escribir vida (y quiza cambiar estado)" son
     * varios pasos que juntos deben ser UNA sola operacion indivisible. Sin esto, un veneno y un
     * ataque simultaneos leen la misma vida y uno de los dos danios se pierde.
     *
     * Alternativa con ReentrantLock (lock()/unlock() dentro de try-finally): ver DemoCondicionCarrera.
     */
    public synchronized void recibirDanio(int danio) {
        if (danio < 0) {
            throw new IllegalArgumentException("El danio no puede ser negativo");
        }
        setVida(vida - danio);
    }

    /** Cura y devuelve cuanta vida se recupero de verdad. Tambien synchronized (lee estado y vida). */
    public synchronized int curar(int cantidad) {
        if (cantidad < 0) {
            throw new IllegalArgumentException("La curacion no puede ser negativa");
        }
        if (!estaVivo()) {
            return 0;
        }
        int vidaAntes = vida;
        setVida(vida + cantidad);
        return vida - vidaAntes;
    }

    /** Vuelve a vida completa (se usa antes de la final del torneo). Cancela los efectos activos. */
    public void restaurarVida() {
        cancelarEfectos();
        synchronized (this) {
            estado = EstadoPersonaje.VIVO;
            vida = vidaMaxima;
        }
    }

    @Override
    public synchronized boolean estaVivo() {
        return estado != EstadoPersonaje.MUERTO;
    }

    /** Quien recibe los avisos de los efectos (ticks, fin, muerte). Normalmente el RegistroCombate. */
    public void setObservador(Consumer<String> observador) {
        this.observador = observador;
    }

    private void notificar(String mensaje) {
        Consumer<String> destino = observador;
        if (destino != null) {
            destino.accept(mensaje);
        } else {
            System.out.println(mensaje);
        }
    }

    /**
     * Aplica un efecto y arranca el hilo que lo ejecuta. Si ya habia un efecto con el mismo nombre,
     * se interrumpe su hilo y se reemplaza (igual que antes: el efecto se "renueva").
     */
    public synchronized void aplicarEfecto(EstadoEfecto efecto) {
        if (!estaVivo()) {
            return;
        }
        interrumpirHilo(efecto.getNombre());
        EstadoEfecto propio = efecto.copia();
        efectos.put(propio.getNombre(), propio);
        if (propio.esDanio() && estado == EstadoPersonaje.VIVO) {
            estado = EstadoPersonaje.ENVENENADO;
        }
        Thread hilo = new Thread(new EfectoContinuoTask(this, propio, INTERVALO_EFECTO_MS),
                "efecto-" + propio.getNombre() + "-" + nombre);
        hilo.setDaemon(true);          // un efecto olvidado no debe impedir que la JVM termine
        hilos().put(propio.getNombre(), hilo);
        hilo.start();                  // siempre un Thread nuevo: nunca se llama start() dos veces
    }

    /** Quita un efecto (antidoto, purificacion) e interrumpe su hilo. */
    public synchronized boolean quitarEfecto(String nombreEfecto) {
        if (efectos.remove(nombreEfecto) == null) {
            return false;
        }
        interrumpirHilo(nombreEfecto);
        normalizarEstado();
        return true;
    }

    /** Quita todos los efectos de dano. Devuelve los nombres quitados. */
    public synchronized List<String> quitarEfectosDanio() {
        List<String> quitados = new ArrayList<>();
        for (String nombreEfecto : new ArrayList<>(efectos.keySet())) {
            if (efectos.get(nombreEfecto).esDanio()) {
                quitarEfecto(nombreEfecto);
                quitados.add(nombreEfecto);
            }
        }
        return quitados;
    }

    /**
     * Interrumpe TODOS los hilos de efectos y espera a que terminen limpios.
     * Se usa al acabar un combate, al restaurar la vida y cuando el personaje muere.
     */
    public void cancelarEfectos() {
        List<Thread> pendientes;
        synchronized (this) {
            pendientes = new ArrayList<>(hilos().values());
            hilos().clear();
            efectos.clear();
            normalizarEstado();
            for (Thread hilo : pendientes) {
                if (hilo != Thread.currentThread()) {
                    hilo.interrupt();
                }
            }
        }
        // El join va FUERA del synchronized: los hilos de efecto necesitan este monitor para terminar.
        for (Thread hilo : pendientes) {
            if (hilo == Thread.currentThread()) {
                continue;
            }
            try {
                hilo.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Lo llama EfectoContinuoTask en cada intervalo. Aplica un tick de dano o curacion.
     *
     * @return true si el efecto sigue activo; false si termino, fue cancelado o el personaje murio.
     */
    public boolean aplicarTickEfecto(EstadoEfecto efecto) {
        List<String> avisos = new ArrayList<>();
        boolean continua;
        boolean murio = false;
        synchronized (this) {
            String clave = efecto.getNombre();
            if (efectos.get(clave) != efecto) {
                return false;          // lo cancelaron o reemplazaron mientras dormia
            }
            if (!estaVivo()) {
                efectos.remove(clave); // el personaje murio por otra causa: el efecto ya no tiene sentido
                return false;
            }
            if (efecto.esDanio()) {
                recibirDanio(efecto.getValorPorTick());
                avisos.add(String.format("%s sufre %d de dano por %s", nombre, efecto.getValorPorTick(), clave));
            } else {
                int curado = curar(efecto.getValorPorTick());
                avisos.add(String.format("%s recupera %d de vida por %s", nombre, curado, clave));
            }
            efecto.consumirTurno();
            continua = !efecto.haTerminado();
            if (!estaVivo()) {
                avisos.add(String.format("%s cae derrotado por %s", nombre, clave));
                murio = true;
                continua = false;
            } else if (!continua) {
                efectos.remove(clave);
                normalizarEstado();
                avisos.add(nombre + ": termina " + clave);
            }
        }
        for (String aviso : avisos) {
            notificar(aviso);          // fuera del synchronized: no se imprime con el monitor tomado
        }
        if (murio) {
            cancelarEfectos();         // un muerto no sigue sufriendo (ni curandose) por otros efectos
        }
        return continua;
    }

    /** Lo llama el hilo del efecto al ser interrumpido: deja limpio el estado del personaje. */
    public synchronized void limpiarEfecto(EstadoEfecto efecto) {
        if (efectos.get(efecto.getNombre()) == efecto) {      // solo si sigue siendo ESTE efecto
            efectos.remove(efecto.getNombre());
            normalizarEstado();
        }
    }

    /** Lo llama el hilo del efecto al terminar (finally): se desregistra a si mismo. */
    public synchronized void liberarHilo(EstadoEfecto efecto) {
        if (hilos().get(efecto.getNombre()) == Thread.currentThread()) {
            hilos().remove(efecto.getNombre());
        }
    }

    /** Mapa de hilos de efecto; tras deserializar el campo transient es null y se recrea vacio. */
    private Map<String, Thread> hilos() {
        if (hilosEfectos == null) {
            hilosEfectos = new HashMap<>();
        }
        return hilosEfectos;
    }

    public synchronized void reanudarEfectos() {
        if (!estaVivo()) {
            return;
        }
        for (EstadoEfecto efecto : new ArrayList<>(efectos.values())) {
            if (!hilos().containsKey(efecto.getNombre()) && !efecto.haTerminado()) {
                Thread hilo = new Thread(new EfectoContinuoTask(this, efecto, INTERVALO_EFECTO_MS),
                        "efecto-" + efecto.getNombre() + "-" + nombre);
                hilo.setDaemon(true);
                hilos().put(efecto.getNombre(), hilo);
                hilo.start();
            }
        }
    }

    private synchronized void writeObject(ObjectOutputStream salida) throws IOException {
        salida.defaultWriteObject();
    }

    private void interrumpirHilo(String nombreEfecto) {
        Thread hilo = hilos().remove(nombreEfecto);
        if (hilo != null) {
            hilo.interrupt();
        }
    }

    /** Cantidad de hilos de efecto registrados (sirve para comprobar que terminan limpios). */
    public synchronized int cantidadHilosEfecto() {
        return hilos().size();
    }

    private boolean tieneEfectoDanio() {
        for (EstadoEfecto efecto : efectos.values()) {
            if (efecto.esDanio()) {
                return true;
            }
        }
        return false;
    }

    private void normalizarEstado() {
        if (estado == EstadoPersonaje.ENVENENADO && !tieneEfectoDanio()) {
            estado = EstadoPersonaje.VIVO;
        }
    }

    public synchronized void aturdir() {
        if (estaVivo()) {
            estado = EstadoPersonaje.ATURDIDO;
        }
    }

    public synchronized boolean consumirAturdimiento() {
        boolean estabaAturdido = estado == EstadoPersonaje.ATURDIDO;
        if (estabaAturdido) {
            estado = tieneEfectoDanio() ? EstadoPersonaje.ENVENENADO : EstadoPersonaje.VIVO;
        }
        return estabaAturdido;
    }

    public String usarPocion(Pocion pocion) {
        int curado = curar(pocion.getCuracionReal());
        if (pocion.getEfecto() != null) {
            aplicarEfecto(pocion.getEfecto());
        }
        String texto = String.format("%s usa %s (+%d de vida)", nombre, pocion.getNombre(), curado);
        if (pocion.isPurifica()) {
            List<String> quitados = quitarEfectosDanio();
            if (!quitados.isEmpty()) {
                texto += " y se libra de " + String.join(", ", quitados);
            }
        }
        return texto;
    }

    public void equiparArma(Arma arma) {
        this.arma = arma;
    }

    public void desequiparArma() {
        this.arma = null;
    }

    public void unirseAGremio(Gremio nuevoGremio) {
        if (nuevoGremio == null || nuevoGremio == this.gremio) {
            return;
        }
        if (this.gremio != null) {
            this.gremio.removerMiembro(this);
        }
        this.gremio = nuevoGremio;
        nuevoGremio.agregarMiembro(this);
    }

    public void abandonarGremio() {
        Gremio anterior = this.gremio;
        if (anterior == null) {
            return;
        }
        this.gremio = null;
        anterior.removerMiembro(this);
    }

    public boolean aprenderHabilidad(String habilidad) {
        return habilidades.add(habilidad);
    }

    /** Nunca baja de 0 ni supera la vida maxima; al llegar a 0 el personaje muere. */
    public synchronized void setVida(int vida) {
        this.vida = Math.max(0, Math.min(vida, vidaMaxima));
        if (this.vida == 0) {
            estado = EstadoPersonaje.MUERTO;
        }
    }

    public void setNombre(String nombre) {
        validarNombre(nombre);
        this.nombre = nombre;
    }

    public synchronized void setVidaMaxima(int vidaMaxima) {
        if (vidaMaxima <= 0) {
            throw new IllegalArgumentException("La vida maxima debe ser positiva");
        }
        this.vidaMaxima = vidaMaxima;
        this.vida = Math.min(this.vida, vidaMaxima);
    }

    public int getDefensa() {
        return defensaBase + tipo.getBonificadorDefensa();
    }

    public String getNombre() {
        return nombre;
    }

    public TipoPersonaje getTipo() {
        return tipo;
    }

    public synchronized int getVida() {
        return vida;
    }

    public synchronized int getVidaMaxima() {
        return vidaMaxima;
    }

    public synchronized EstadoPersonaje getEstado() {
        return estado;
    }

    public Arma getArma() {
        return arma;
    }

    public Gremio getGremio() {
        return gremio;
    }

    public Inventario<Pocion> getInventario() {
        return inventario;
    }

    public Set<String> getHabilidades() {
        return Collections.unmodifiableSet(habilidades);
    }

    /** Copia (instantanea) de los efectos activos: segura de recorrer aunque los hilos los modifiquen. */
    public synchronized Collection<EstadoEfecto> getEfectos() {
        return List.copyOf(efectos.values());
    }
}