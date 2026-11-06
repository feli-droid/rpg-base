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
import com.rpg.persistencia.GestorArchivos;
import com.rpg.persistencia.GestorPartida;
import com.rpg.persistencia.LogCombate;
import com.rpg.persistencia.PartidaGuardada;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Flujo del juego: crear heroe, bucle de combate, resultado y guardado del progreso.
 *
 * TALLER CONCURRENCIA:
 *  - pedirAccion() usa un temporizador de turno en su propio hilo (GestorTurno).
 *  - Los efectos DoT / HoT corren en hilos; el combate los cancela al terminar.
 *  - Un hilo daemon (AutoGuardadoDaemon) guarda el progreso en memoria cada cierto tiempo, por eso
 *    el estado compartido (perfil, gremio, bestiario) se toca siempre con el candado "candadoEstado".
 *
 * TALLER ARCHIVOS:
 *  - Al terminar un combate o torneo, el registro se agrega a partidas/combate.log (LogCombate).
 *  - Guardar / cargar partida: serializacion de PartidaGuardada a partidas/partida.dat (GestorPartida).
 *  - El daemon tambien escribe a disco (partidas/autoguardado.dat), en un archivo APARTE para no pisar
 *    nunca la partida que el jugador guardo a proposito.
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
    // No son final: al cargar una partida se reemplazan por los objetos deserializados.
    // Se leen y escriben siempre con candadoEstado tomado.
    private PerfilJugador perfil = new PerfilJugador();
    private Gremio gremio = new Gremio(NOMBRE_GREMIO);
    /** true si el estado actual viene de una partida cargada: entonces se ofrece continuar con sus heroes. */
    private boolean partidaCargada = false;
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
        jugarCombate(elegirOCrearHeroe());
    }

    /**
     * UNIDAD 5.2 (integracion con la BD): combate rapido con un heroe que ya viene creado, por ejemplo uno cargado
     * desde MySQL. Recibe un Personaje (entidad del juego), asi el motor no sabe nada de bases de datos.
     */
    public void combateRapido(Personaje heroe) {
        registro.limpiar();
        entregarKitInicial(heroe);
        synchronized (candadoEstado) {
            heroe.unirseAGremio(gremio);
            System.out.printf("%s se une al gremio %s (%d miembros).%n",
                    heroe.getNombre(), gremio.getNombre(), gremio.getMiembros().size());
        }
        jugarCombate(heroe);
    }

    /** Elige rival y juega el combate completo con el heroe indicado. */
    private void jugarCombate(Personaje jugador) {
        Enemigo enemigo = elegirEnemigo();
        jugador.reanudarEfectos();      // si el heroe se guardo con un efecto activo, vuelve a correr
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
        guardarHistorial();
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
        guardarHistorial();
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

    /**
     * TALLER ARCHIVOS: tras cargar una partida se puede continuar con un heroe del gremio guardado
     * (con su vida, pociones, arma y efectos tal como se guardaron) o crear uno nuevo.
     */
    private Personaje elegirOCrearHeroe() {
        List<Personaje> disponibles = new ArrayList<>();
        synchronized (candadoEstado) {
            if (partidaCargada) {
                for (Personaje miembro : gremio.getMiembros()) {
                    if (miembro.estaVivo()) {
                        disponibles.add(miembro);
                    }
                }
            }
        }
        if (disponibles.isEmpty()) {
            return crearJugador();
        }
        System.out.println("Heroes del gremio guardado:");
        for (int i = 0; i < disponibles.size(); i++) {
            Personaje h = disponibles.get(i);
            System.out.printf("  %d. %s (%s) vida %d/%d, %d pociones%n", i + 1, h.getNombre(), h.getTipo(),
                    h.getVida(), h.getVidaMaxima(), h.getInventario().tamanio());
        }
        int eleccion = consola.leerEntero("Heroe del gremio (0 = crear uno nuevo): ", 0, disponibles.size());
        return eleccion == 0 ? crearJugador() : disponibles.get(eleccion - 1);
    }

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

    /** Guardado automatico en MEMORIA: lo invoca el hilo daemon cada cierto intervalo. */
    public void autoGuardar() {
        guardarEnMemoria("[Autoguardado en memoria]");
    }

    /**
     * TALLER ARCHIVOS (Seccion 7): guardado automatico en DISCO, tambien desde el hilo daemon.
     * No escribe nada si todavia no hay progreso (para no dejar un autoguardado vacio).
     */
    public void autoGuardarEnDisco() {
        boolean escrito;
        synchronized (candadoEstado) {
            if (gremio.getMiembros().isEmpty() && perfil.getVictorias() == 0 && perfil.getDerrotas() == 0) {
                return;
            }
            escrito = GestorPartida.guardarAutomatico(crearInstantanea());
        }
        if (escrito) {
            System.out.println("[Autoguardado en disco: " + GestorArchivos.ARCHIVO_AUTOGUARDADO.getPath() + "]");
        }
    }

    private void guardarEnMemoria(String aviso) {
        String resumen;
        synchronized (candadoEstado) {
            resumen = construirResumen();
        }
        ultimoGuardado = resumen;
        System.out.println(aviso);
    }

    // Resumen legible del estado (en memoria). El estado REAL a disco se guarda con PartidaGuardada (serializacion).
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

    // ------------------------------------------------------------------------------------
    // TALLER ARCHIVOS: historial, guardar, cargar, informacion y borrado
    // ------------------------------------------------------------------------------------

    /** Agrega el registro del combate que acaba de terminar al historial en disco. */
    private void guardarHistorial() {
        if (LogCombate.agregar(registro.getTexto())) {
            System.out.println("[Historial agregado a " + GestorArchivos.ARCHIVO_LOG.getPath() + "]");
        }
    }

    public void verHistorial() {
        LogCombate.mostrar();
    }

    /** "Foto" del estado actual. Hay que llamarla con candadoEstado tomado (el grafo se lee mientras se serializa). */
    private PartidaGuardada crearInstantanea() {
        return new PartidaGuardada(perfil, gremio, bestiario.exportarDerrotados());
    }

    /** Guardado manual en partidas/partida.dat (pide confirmacion si ya existe una partida). */
    public void guardarPartida() {
        File destino = GestorArchivos.ARCHIVO_PARTIDA;
        if (destino.exists() && !confirmar("Ya existe una partida guardada. Sobrescribirla?")) {
            System.out.println("Guardado cancelado.");
            return;
        }
        boolean guardada;
        synchronized (candadoEstado) {
            guardada = GestorPartida.guardar(crearInstantanea(), destino);
        }
        if (guardada) {
            System.out.printf("Partida guardada en %s (%d bytes).%n", destino.getPath(), destino.length());
        } else {
            System.out.println("No se pudo guardar la partida. Tu partida anterior (si habia) sigue intacta.");
        }
    }

    /** Carga la partida manual o el autoguardado y reemplaza el estado actual por el cargado. */
    public void cargarPartida() {
        System.out.println("1. Partida guardada (partida.dat)  2. Autoguardado (autoguardado.dat)  0. Cancelar");
        int opcion = consola.leerEntero("Cargar: ", 0, 2);
        if (opcion == 0) {
            return;
        }
        File origen = opcion == 1 ? GestorArchivos.ARCHIVO_PARTIDA : GestorArchivos.ARCHIVO_AUTOGUARDADO;
        Optional<PartidaGuardada> leida = GestorPartida.cargar(origen);
        if (leida.isEmpty()) {
            return;                                   // GestorPartida ya explico el motivo; el juego sigue
        }
        PartidaGuardada partida = leida.get();
        synchronized (candadoEstado) {
            perfil = partida.getPerfil();
            gremio = partida.getGremio();
            bestiario.importarDerrotados(partida.getDerrotados());
            partidaCargada = true;
            System.out.printf("Partida cargada (guardada el %s).%n", partida.getFecha());
            System.out.printf("Victorias: %d | Derrotas: %d | Logros: %s%n",
                    perfil.getVictorias(), perfil.getDerrotas(), perfil.getLogros());
            System.out.printf("Gremio %s: %d miembros%n", gremio.getNombre(), gremio.getMiembros().size());
            for (Personaje miembro : gremio.getMiembros()) {
                System.out.printf("  - %s (%s) vida %d/%d, estado %s, %d pociones%n", miembro.getNombre(),
                        miembro.getTipo(), miembro.getVida(), miembro.getVidaMaxima(), miembro.getEstado(),
                        miembro.getInventario().tamanio());
            }
            System.out.println("Enemigos derrotados: " + bestiario.resumenDerrotados());
        }
    }

    public void mostrarInfoArchivos() {
        System.out.println("\n=== ARCHIVOS DEL JUEGO ===");
        System.out.println("Directorio de trabajo: " + System.getProperty("user.dir"));
        System.out.println(GestorArchivos.describir(GestorArchivos.ARCHIVO_PARTIDA));
        System.out.println(GestorArchivos.describir(GestorArchivos.ARCHIVO_AUTOGUARDADO));
        System.out.println(GestorArchivos.describir(GestorArchivos.ARCHIVO_LOG));
    }

    /** Borra la partida guardada o el historial, siempre confirmando antes. */
    public void borrarArchivos() {
        System.out.println("1. Partida guardada (manual y autoguardado)  2. Historial de combates  0. Cancelar");
        int opcion = consola.leerEntero("Borrar: ", 0, 2);
        if (opcion == 0 || !confirmar("Seguro que quieres borrarlo? No se puede deshacer.")) {
            System.out.println("No se borro nada.");
            return;
        }
        if (opcion == 1) {
            File manual = GestorArchivos.ARCHIVO_PARTIDA;
            if (!manual.exists()) {
                System.out.println("No hay partida guardada manualmente.");
            } else if (GestorArchivos.borrarConFile(manual)) {         // File.delete(): se revisa el boolean
                System.out.println("Partida guardada borrada.");
            } else {
                System.out.println("No se pudo borrar " + manual.getPath() + " (permisos o archivo en uso).");
            }
            if (GestorArchivos.borrarConNio(GestorArchivos.ARCHIVO_AUTOGUARDADO)) {   // deleteIfExists
                System.out.println("Autoguardado borrado.");
            }
        } else if (GestorArchivos.borrarConNio(GestorArchivos.ARCHIVO_LOG)) {
            System.out.println("Historial borrado.");
        } else {
            System.out.println("No habia historial que borrar.");
        }
    }

    private boolean confirmar(String pregunta) {
        return consola.leerLinea(pregunta + " (s/n): ").equalsIgnoreCase("s");
    }

    /** Al salir: espera a que el daemon termine una escritura en curso y evita que empiece otra. */
    public void cerrar() {
        GestorPartida.cerrar();
    }

    public String getUltimoGuardado() {
        return ultimoGuardado;
    }
}