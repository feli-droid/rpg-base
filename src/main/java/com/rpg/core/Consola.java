package com.rpg.core;

import java.util.Scanner;
import java.util.function.BooleanSupplier;

/** Lectura por consola con validacion y reintento. */
public class Consola {

    private static final int LARGO_MAXIMO_NOMBRE = 15;
    private static final String PATRON_NOMBRE = "[\\p{L}0-9 ]+";

    private final Scanner scanner;

    public Consola(Scanner scanner) {
        this.scanner = scanner;
    }

    public String leerLinea(String mensaje) {
        System.out.print(mensaje);
        return scanner.nextLine().trim();
    }

    public int leerEntero(String mensaje, int minimo, int maximo) {
        return leerEntero(mensaje, minimo, maximo, () -> false);
    }

    /**
     * TALLER CONCURRENCIA: igual que leerEntero, pero con un "cancelado". nextLine() no se puede
     * interrumpir desde otro hilo, asi que cuando el temporizador del turno ya vencio, en cuanto
     * el jugador pulsa ENTER se devuelve de inmediato (sin pedir otra vez un numero valido) y el
     * llamador descarta el valor.
     */
    public int leerEntero(String mensaje, int minimo, int maximo, BooleanSupplier cancelado) {
        while (true) {
            String texto = leerLinea(mensaje);
            if (cancelado.getAsBoolean()) {
                return minimo;
            }
            if (texto.isEmpty()) {
                System.out.println("No escribiste nada.");
                continue;
            }
            try {
                int valor = Integer.parseInt(texto);
                if (valor >= minimo && valor <= maximo) {
                    return valor;
                }
                System.out.printf("Numero fuera de rango (%d a %d).%n", minimo, maximo);
            } catch (NumberFormatException e) {
                System.out.println("\"" + texto + "\" no es un numero.");
            }
        }
    }

    public String leerNombre(String mensaje) {
        while (true) {
            String nombre = leerLinea(mensaje);
            if (nombre.isEmpty()) {
                System.out.println("El nombre no puede estar vacio.");
                continue;
            }
            if (nombre.length() > LARGO_MAXIMO_NOMBRE) {
                System.out.println("Maximo " + LARGO_MAXIMO_NOMBRE + " caracteres.");
                continue;
            }
            if (!nombre.matches(PATRON_NOMBRE)) {
                System.out.println("Usa solo letras, numeros y espacios.");
                continue;
            }
            return nombre;
        }
    }
}