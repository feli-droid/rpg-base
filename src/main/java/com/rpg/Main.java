package com.rpg;

import com.rpg.core.Consola;
import com.rpg.core.MotorJuego;

import java.util.NoSuchElementException;
import java.util.Scanner;

/** Menu principal por consola. */
public class Main {

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            Consola consola = new Consola(scanner);
            MotorJuego motor = new MotorJuego(consola);
            // TALLER CONCURRENCIA: hilo daemon de auto-guardado (memoria y disco). Al salir del juego la
            // JVM se cierra aunque este hilo siga vivo, porque es daemon.
            motor.iniciarAutoGuardado();
            try {
                ejecutarMenu(consola, motor);
            } finally {
                motor.cerrar();
            }
        } catch (NoSuchElementException e) {
            System.out.println("\nEntrada finalizada. Hasta luego.");
        }
    }

    private static void ejecutarMenu(Consola consola, MotorJuego motor) {
        int opcion;
        do {
            mostrarMenu();
            opcion = consola.leerEntero("Opcion: ", 0, 8);
            switch (opcion) {
                case 1 -> motor.combateRapido();
                case 2 -> motor.jugarTorneo();
                case 3 -> motor.mostrarBestiario();
                case 4 -> motor.guardarPartida();
                case 5 -> motor.cargarPartida();
                case 6 -> motor.verHistorial();
                case 7 -> motor.mostrarInfoArchivos();
                case 8 -> motor.borrarArchivos();
                default -> System.out.println("Hasta luego.");
            }
        } while (opcion != 0);
    }

    private static void mostrarMenu() {
        System.out.println("\n=== SIMULADOR DE BATALLA RPG ===");
        System.out.println("1. Combate rapido");
        System.out.println("2. Torneo");
        System.out.println("3. Ver bestiario");
        System.out.println("4. Guardar partida");
        System.out.println("5. Cargar partida");
        System.out.println("6. Ver historial de combates");
        System.out.println("7. Informacion de archivos");
        System.out.println("8. Borrar archivos");
        System.out.println("0. Salir");
    }
}