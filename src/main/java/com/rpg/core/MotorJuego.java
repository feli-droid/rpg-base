package com.rpg.core;

import com.rpg.concurrency.AutoGuardadoDaemon;
import com.rpg.concurrency.GestorTurno;
import com.rpg.entities.Enemigo;
import com.rpg.entities.EstadoEfecto;
import com.rpg.entities.Gremio;
import com.rpg.entities.Guerrero;
import com.rpg.entities.Mago;
import com.rpg.entities.PerfilJugador;
import com.rpg.entities.Personaje;
import com.rpg.entities.enums.RaridadItem;
import com.rpg.entities.enums.TipoEfecto;
import com.rpg.exceptions.CapacidadExcedidaException;
import com.rpg.items.Inventario;
import com.rpg.items.Pocion;

import java.util.List;
import java.util.Random;

/**
 * Flujo del juego: crear heroe, bucle de combate, resultado y guardado del progreso.
 *
 * TALLER CONCURRENCIA:
 *  - pedirAccion() usa un temporizador de turno en su propio hilo (GestorTurno).
 *  - Los efectos DoT / HoT corren en hilos; el combate los cancela al terminar.
 *  - Un hilo daemon (AutoGuardadoDaemon) guarda el progreso en memoria cada cierto tiempo, por eso
 *    el estado compartido (perfil, gremio, bestiario) se toca siempre con el candado "candadoEstado".
 */
public class MotorJuego {

    private static final String NOMBRE_GREMIO = "Guardianes del Alba";
    private static final int CURACION_POCION_VIDA = 30;
    /** Segundos que tiene el jugador para elegir su accion. */
    private static final int SEGUNDOS_TURNO = 15;
    /** Cada cuantos milisegundos guarda el hilo daemon. */
    private static final long INTERVALO_AUTOGUARDADO_MS = 30_000;

    private final Consola consola;
    private final RegistroCombate registro = new RegistroCombate();
    private final Arena arena = new Arena(registro, new Random());
    private final Bestiario bestiario = new Bestiario();
    private final PerfilJugador perfil = new PerfilJugador();
    private final Gremio gremio = new Gremio(NOMBRE_GREMIO);
    private final TorneoManager torneo = new TorneoManager(arena, registro);

    /** Candado del estado de la partida (perfil, gremio, bestiario): lo comparten el hilo principal y el daemon. */
    private final Object candadoEstado = new Object();
    private volatile String ultimoGuardado = "";

    public MotorJuego(Consola consola) {
        this.consola = consola;
    }

    // ------------------------------------------------------------------------------------
    // Opciones del menu
    // ------------------------------------------------------------------------------------

    public void combateRapido() {
        registro.limpiar();
        Personaje jugador = crearJugador();
        Enemigo enemigo = elegirEnemigo();
        jugador.setObservador(registro::agregar);
        enemigo.setObservador(registro::agregar);
        registro.agregar("%n=== %s vs %s ===", jugador.getNombre(), enemigo.getNombre());

        boolean huyo;
        try {
            huyo = combatir(jugador, enemigo);
        } finally {
            // Termine como termine el combate (victoria, derrota, huida o error), los hilos de los
            // efectos se interrumpen y terminan limpios.
            jugador.cancelarEfectos();
            enemigo.cancelarEfectos();
        }
        cerrarCombate(jugador, enemigo, huyo);
        guardarProgreso();
    }

    public void jugarTorneo() {
        registro.limpiar();
        Personaje campeon = torneo.jugarTorneo();
        if (campeon == null) {
            registro.agregar("%nEl torneo se cancelo antes de terminar.");
            return;
        }
        registro.agregar("%nCAMPEON DEL TORNEO: %s", campeon.getNombre());
        synchronized (candadoEstado) {
            perfil.desbloquearLogro("Campeon del Torneo");
        }
        guardarProgreso();
    }

    public void mostrarBestiario() {
        System.out.println("\n=== BESTIARIO ===");
        synchronized (candadoEstado) {
            System.out.print(bestiario.describir());
            System.out.printf("Victorias: %d | Derrotas: %d | Logros: %s%n",
                    perfil.getVictorias(), perfil.getDerrotas(), perfil.getLogros());
        }
    }

    // ------------------------------------------------------------------------------------
    // Creacion del heroe y del rival
    // ------------------------------------------------------------------------------------

    private Personaje crearJugador() {
        int clase = consola.leerEntero("Clase (1. Guerrero, 2. Mago): ", 1, 2);
        String nombre = consola.leerNombre("Nombre del heroe: ");
        Personaje heroe = switch (clase) {
            case 1 -> new Guerrero(nombre);
            default -> new Mago(nombre);
        };
        entregarKitInicial(heroe);
        synchronized (candadoEstado) {
            heroe.unirseAGremio(gremio);
            System.out.printf("%s se une al gremio %s (%d miembros).%n",
                    heroe.getNombre(), gremio.getNombre(), gremio.getMiembros().size());
        }
        return heroe;
    }

    private void entregarKitInicial(Personaje personaje) {
        List<Pocion> kit = List.of(
                new Pocion("Pocion de Vida", CURACION_POCION_VIDA, RaridadItem.COMUN),
                new Pocion("Elixir de Regeneracion", 0, RaridadItem.RARO,
                        new EstadoEfecto("Regeneracion", TipoEfecto.CURACION_CONTINUA, 6, 3), false),
                new Pocion("Antidoto", 0, RaridadItem.COMUN, null, true));
        try {
            for (Pocion pocion : kit) {
                personaje.getInventario().agregar(pocion);
            }
        } catch (CapacidadExcedidaException e) {
            System.out.println("No se pudo entregar todo el kit: " + e.getMessage());
        }
    }

    private Enemigo elegirEnemigo() {
        List<String> nombres = bestiario.nombres();
        System.out.println("Rivales disponibles:");
        for (int i = 0; i < nombres.size(); i++) {
            System.out.printf("  %d. %s%n", i + 1, nombres.get(i));
        }
        Enemigo enemigo;
        while ((enemigo = resolverRival(consola.leerLinea("Rival (numero o nombre): "), nombres)) == null) {
            System.out.println("No existe ese rival.");
        }
        return enemigo;
    }

    private Enemigo resolverRival(String texto, List<String> nombres) {
        try {
            int numero = Integer.parseInt(texto);
            boolean valido = numero >= 1 && numero <= nombres.size();
            return valido ? bestiario.crearEnemigo(nombres.get(numero - 1)) : null;
        } catch (NumberFormatException e) {
            return bestiario.crearEnemigo(texto);
        }
    }

    // ------------------------------------------------------------------------------------
    // Bucle de combate
    // ------------------------------------------------------------------------------------

    /** @return true si el jugador huyo. */
    private boolean combatir(Personaje jugador, Enemigo enemigo) {
        int ronda = 1;
        while (jugador.estaVivo() && enemigo.estaVivo()) {
            registro.agregar("%n-- Ronda %d --", ronda);
            if (arena.iniciarTurno(jugador) && turnoJugador(jugador, enemigo)) {
                return true;
            }
            arena.turnoAutomatico(enemigo, jugador);
            ronda++;
        }
        return false;
    }

    /** @return true si el jugador huyo. */
    private boolean turnoJugador(Personaje jugador, Enemigo enemigo) {
        boolean turnoConsumido;
        do {
            registro.agregar(FormatoCombate.resumen(jugador));
            registro.agregar(FormatoCombate.resumen(enemigo));
            Accion accion = pedirAccion();
            if (!jugador.estaVivo()) {
                return false;                       // murio por un efecto mientras elegia
            }
            if (accion == Accion.HUIR) {
                return true;
            }
            turnoConsumido = switch (accion) {
                case ATACAR -> {
                    arena.atacar(jugador, enemigo);
                    yield true;
                }
                case ESPECIAL -> arena.intentarEspecial(jugador, enemigo);
                case POCION -> usarPocion(jugador);
                case TIEMPO_AGOTADO -> {
                    registro.agregar("%s tardo demasiado y pierde el turno.", jugador.getNombre());
                    yield true;
                }
                default -> {
                    mostrarEstado(jugador);
                    yield false;
                }
            };
        } while (!turnoConsumido && enemigo.estaVivo() && jugador.estaVivo());
        return false;
    }

    /**
     * TALLER CONCURRENCIA (Seccion 1): pide la accion con un temporizador de SEGUNDOS_TURNO segundos.
     *
     * El temporizador corre en otro hilo mientras este hilo espera al teclado (nextLine() no se puede
     * interrumpir). Si el jugador responde a tiempo, se cancela el temporizador con interrupt(). Si el
     * tiempo se agota primero, el turno se pierde en cuanto el jugador pulse ENTER.
     */
    private Accion pedirAccion() {
        System.out.printf("1. Atacar  2. Habilidad especial  3. Usar pocion  4. Ver estado  0. Huir"
                + "  (tienes %d s)%n", SEGUNDOS_TURNO);
        GestorTurno temporizador = new GestorTurno();
        temporizador.iniciar(SEGUNDOS_TURNO);
        int opcion;
        try {
            opcion = consola.leerEntero("Accion: ", 0, 4, temporizador::isTiempoAgotado);
        } finally {
            temporizador.cancelar();                // respondio (o fallo la lectura): se interrumpe el hilo
        }
        if (temporizador.isTiempoAgotado()) {
            return Accion.TIEMPO_AGOTADO;
        }
        return switch (opcion) {
            case 1 -> Accion.ATACAR;
            case 2 -> Accion.ESPECIAL;
            case 3 -> Accion.POCION;
            case 4 -> Accion.ESTADO;
            default -> Accion.HUIR;
        };
    }

    private boolean usarPocion(Personaje personaje) {
        Inventario<Pocion> inventario = personaje.getInventario();
        if (inventario.estaVacio()) {
            System.out.println("No tienes pociones.");
            return false;
        }
        int numero = 1;
        for (Pocion pocion : inventario) {
            System.out.printf("  %d. %s%n", numero++, pocion.getNombre());
        }
        int eleccion = consola.leerEntero("Pocion (0 = cancelar): ", 0, inventario.tamanio());
        if (eleccion == 0) {
            return false;
        }
        registro.agregar(personaje.usarPocion(inventario.quitar(eleccion - 1)));
        return true;
    }

    private void mostrarEstado(Personaje personaje) {
        System.out.printf("Habilidades: %s | Arma: %s | Defensa: %d | Pociones: %d%n",
                personaje.getHabilidades(),
                personaje.getArma() != null ? personaje.getArma().getNombre() : "ninguna",
                personaje.getDefensa(),
                personaje.getInventario().tamanio());
        for (EstadoEfecto efecto : personaje.getEfectos()) {
            System.out.printf("Efecto activo: %s (%d ticks restantes)%n", efecto.getNombre(), efecto.getTurnosRestantes());
        }
    }

    // ------------------------------------------------------------------------------------
    // Resultado del combate
    // ------------------------------------------------------------------------------------

    private void cerrarCombate(Personaje jugador, Enemigo enemigo, boolean huyo) {
        synchronized (candadoEstado) {
            if (huyo) {
                registro.agregar("%s huye del combate.", jugador.getNombre());
            } else if (jugador.estaVivo()) {
                registro.agregar("%nVictoria de %s!", jugador.getNombre());
                perfil.registrarVictoria();
                bestiario.registrarEnemigoDerrotado(enemigo.getNombre());
                desbloquearLogros(jugador, enemigo);
                repartirBotin(jugador);
            } else {
                registro.agregar("%n%s ha caido ante %s.", jugador.getNombre(), enemigo.getNombre());
                perfil.registrarDerrota();
            }
        }
    }

    private void desbloquearLogros(Personaje jugador, Enemigo enemigo) {
        boolean intocable = jugador.getVida() == jugador.getVidaMaxima();
        List<String> logros = List.of("Primera Victoria", "Cazador de " + enemigo.getNombre(),
                intocable ? "Intocable" : "");
        for (String logro : logros) {
            if (!logro.isEmpty() && perfil.desbloquearLogro(logro)) {
                registro.agregar("Logro desbloqueado: %s", logro);
            }
        }
    }

    private void repartirBotin(Personaje jugador) {
        try {
            jugador.getInventario().agregar(new Pocion("Pocion de Vida", CURACION_POCION_VIDA, RaridadItem.COMUN));
            registro.agregar("Botin: Pocion de Vida.");
        } catch (CapacidadExcedidaException e) {
            registro.agregar("Botin perdido: %s", e.getMessage());
        }
    }

    // ------------------------------------------------------------------------------------
    // Guardado (en memoria)
    // ------------------------------------------------------------------------------------

    /**
     * TALLER CONCURRENCIA (Seccion 4): arranca el hilo daemon de auto-guardado.
     * Lo llama Main una sola vez al empezar.
     */
    public Thread iniciarAutoGuardado() {
        return new AutoGuardadoDaemon(this, INTERVALO_AUTOGUARDADO_MS).iniciar();
    }

    /** Guardado manual: se invoca al terminar una pelea o un torneo. */
    public void guardarProgreso() {
        guardarEnMemoria("[Progreso guardado en memoria]");
    }

    /** Guardado automatico: lo invoca el hilo daemon cada cierto intervalo. */
    public void autoGuardar() {
        guardarEnMemoria("[Autoguardado en memoria]");
    }

    private void guardarEnMemoria(String aviso) {
        String resumen;
        synchronized (candadoEstado) {
            resumen = construirResumen();
        }
        ultimoGuardado = resumen;
        System.out.println(aviso);
    }

    // TALLER ARCHIVOS: este String en memoria es el "estado de la partida"; el taller de archivos lo lleva a disco.
    private String construirResumen() {
        String salto = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("=== PARTIDA RPG ===").append(salto);
        sb.append("Victorias: ").append(perfil.getVictorias()).append(salto);
        sb.append("Derrotas: ").append(perfil.getDerrotas()).append(salto);
        sb.append("Logros: ").append(String.join(", ", perfil.getLogros())).append(salto);
        sb.append("Gremio ").append(gremio.getNombre()).append(": ")
                .append(gremio.getMiembros().size()).append(" miembros").append(salto);
        for (Personaje miembro : gremio.getMiembros()) {
            sb.append(String.format("  - %s (%s) vida %d/%d, estado %s",
                    miembro.getNombre(), miembro.getTipo(), miembro.getVida(),
                    miembro.getVidaMaxima(), miembro.getEstado())).append(salto);
        }
        sb.append("Enemigos derrotados: ").append(bestiario.resumenDerrotados()).append(salto);
        sb.append(salto).append("--- Ultimo combate ---").append(salto);
        sb.append(registro.getTexto());
        return sb.toString();
    }

    public String getUltimoGuardado() {
        return ultimoGuardado;
    }
}