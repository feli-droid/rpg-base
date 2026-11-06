package com.rpg.view;

import com.rpg.controller.HeroeController;
import com.rpg.controller.Resultado;
import com.rpg.core.Consola;
import com.rpg.core.MotorJuego;
import com.rpg.entities.Personaje;
import com.rpg.model.Heroe;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class HeroeUI {

    private static final int OPCION_SALIR = 0;

    private final Consola consola;
    private final HeroeController controlador;

    public HeroeUI(Scanner scanner) {
        this.consola = new Consola(scanner);
        this.controlador = new HeroeController();
    }

    /** Arranca la aplicacion: hilo de auto-guardado, menu principal y cierre ordenado al salir. */
    public void iniciar() {
        MotorJuego motor = new MotorJuego(consola);
        motor.iniciarAutoGuardado();
        try {
            menuPrincipal(motor);
        } catch (NoSuchElementException e) {
            System.out.println("\nEntrada finalizada. Hasta luego.");
        } finally {
            motor.cerrar();
        }
    }

    private void menuPrincipal(MotorJuego motor) {
        int opcion;
        do {
            System.out.println("\n=== SIMULADOR DE BATALLA RPG ===");
            System.out.println("1. Combate rapido");
            System.out.println("2. Torneo");
            System.out.println("3. Ver bestiario");
            System.out.println("4. Guardar partida");
            System.out.println("5. Cargar partida");
            System.out.println("6. Ver historial de combates");
            System.out.println("7. Informacion de archivos");
            System.out.println("8. Borrar archivos");
            System.out.println("9. Registro de heroes (base de datos)");
            System.out.println("10. Cargar heroe desde BD");
            System.out.println("0. Salir");
            opcion = consola.leerEntero("Opcion: ", 0, 10);
            switch (opcion) {
                case 1 -> motor.combateRapido();
                case 2 -> motor.jugarTorneo();
                case 3 -> motor.mostrarBestiario();
                case 4 -> motor.guardarPartida();
                case 5 -> motor.cargarPartida();
                case 6 -> motor.verHistorial();
                case 7 -> motor.mostrarInfoArchivos();
                case 8 -> motor.borrarArchivos();
                case 9 -> menuRegistro();
                case 10 -> cargarHeroeYCombatir(motor);
                default -> System.out.println("Hasta luego.");
            }
        } while (opcion != OPCION_SALIR);
    }

    private void menuRegistro() {
        int opcion;
        do {
            System.out.println("\n=== REGISTRO DE HEROES (MySQL) ===");
            System.out.println("1. Insertar heroe");
            System.out.println("2. Listar heroes");
            System.out.println("3. Buscar heroes por tipo");
            System.out.println("4. Actualizar nivel de un heroe");
            System.out.println("5. Eliminar heroe");
            System.out.println("0. Volver");
            opcion = consola.leerEntero("Opcion: ", 0, 5);
            switch (opcion) {
                case 1 -> insertarHeroe();
                case 2 -> listarHeroes();
                case 3 -> buscarPorTipo();
                case 4 -> actualizarNivel();
                case 5 -> eliminarHeroe();
                default -> System.out.println("Volviendo al menu principal.");
            }
        } while (opcion != OPCION_SALIR);
    }

    private void insertarHeroe() {
        String nombre = consola.leerLinea("Nombre: ");
        String tipo = pedirTipo();
        int vidaMaxima = leerNumero("Vida maxima: ");
        int nivel = leerNumero("Nivel: ");
        mostrar(controlador.insertar(nombre, tipo, vidaMaxima, nivel));
    }

    private void listarHeroes() {
        Resultado<List<Heroe>> resultado = controlador.listar();
        mostrar(resultado);
        if (resultado.exito()) {
            imprimirTabla(resultado.datos());
        }
    }

    private void buscarPorTipo() {
        Resultado<List<Heroe>> resultado = controlador.buscarPorTipo(pedirTipo());
        mostrar(resultado);
        if (resultado.exito()) {
            imprimirTabla(resultado.datos());
        }
    }

    private void actualizarNivel() {
        int id = leerNumero("Id del heroe: ");
        int nivel = leerNumero("Nuevo nivel: ");
        mostrar(controlador.actualizarNivel(id, nivel));
    }

    private void eliminarHeroe() {
        int id = leerNumero("Id del heroe a eliminar: ");
        String confirmacion = consola.leerLinea("Seguro que quieres eliminarlo? (s/n): ");
        if (!confirmacion.equalsIgnoreCase("s")) {
            System.out.println("No se elimino nada.");
            return;
        }
        mostrar(controlador.eliminar(id));
    }

    private void cargarHeroeYCombatir(MotorJuego motor) {
        Resultado<List<Heroe>> lista = controlador.listar();
        mostrar(lista);
        if (!lista.exito() || lista.datos().isEmpty()) {
            return;
        }
        imprimirTabla(lista.datos());
        int id = leerNumero("Id del heroe a cargar (0 = cancelar): ");
        if (id == 0) {
            return;
        }
        Resultado<Personaje> cargado = controlador.cargarComoPersonaje(id);
        mostrar(cargado);
        if (cargado.exito()) {
            motor.combateRapido(cargado.datos());
        }
    }

    private void mostrar(Resultado<?> resultado) {
        System.out.println(resultado.exito() ? resultado.mensaje() : "Error: " + resultado.mensaje());
    }

    private void imprimirTabla(List<Heroe> heroes) {
        if (heroes.isEmpty()) {
            return;
        }
        System.out.printf("%-4s %-15s %-9s %-6s %-5s%n", "ID", "NOMBRE", "TIPO", "VIDA", "NIVEL");
        for (Heroe heroe : heroes) {
            System.out.printf("%-4d %-15s %-9s %-6d %-5d%n",
                    heroe.getId(), heroe.getNombre(), heroe.getTipo(), heroe.getVidaMaxima(), heroe.getNivel());
        }
    }

    private String pedirTipo() {
        int eleccion = consola.leerEntero("Tipo (1. Guerrero, 2. Mago): ", 1, 2);
        return eleccion == 1 ? Heroe.TIPO_GUERRERO : Heroe.TIPO_MAGO;
    }

    private int leerNumero(String mensaje) {
        return consola.leerEntero(mensaje, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
}