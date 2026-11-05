package com.rpg.persistencia;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public final class LogCombate {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private LogCombate() {
    }

    /** Agrega el registro al final del historial: NO borra los combates anteriores. */
    public static boolean agregar(String texto) {
        return guardar(texto, true);
    }

    /**
     * @param agregar true = new FileWriter(archivo, true): escribe al final del archivo;
     *                false = new FileWriter(archivo): modo por defecto, que BORRA el contenido anterior.
     */
    public static boolean guardar(String texto, boolean agregar) {
        synchronized (GestorArchivos.CANDADO) {
            File archivo = GestorArchivos.ARCHIVO_LOG;
            if (!GestorArchivos.crearSiNoExiste(archivo)) {
                System.out.println("No se pudo preparar el historial de combates.");
                return false;
            }
            try (BufferedWriter escritor = new BufferedWriter(new FileWriter(archivo, agregar))) {
                escritor.write("=== Combate registrado el " + LocalDateTime.now().format(FORMATO_FECHA) + " ===");
                escritor.newLine();                       // salto de linea correcto en cada sistema operativo
                for (String linea : texto.split("\\R")) {
                    escritor.write(linea);
                    escritor.newLine();
                }
                escritor.newLine();
                escritor.flush();                         // vacia el buffer al disco (close() tambien lo hace)
                return true;
            } catch (IOException e) {
                System.out.println("No se pudo escribir el historial en " + archivo.getPath() + ": " + e.getMessage());
                return false;
            }
        }
    }

    /** Muestra todo el historial linea a linea. @return true si se pudo leer. */
    public static boolean mostrar() {
        synchronized (GestorArchivos.CANDADO) {
            File archivo = GestorArchivos.ARCHIVO_LOG;
            try (BufferedReader lector = new BufferedReader(new FileReader(archivo))) {
                System.out.println("\n=== HISTORIAL DE COMBATES (" + archivo.getName() + ") ===");
                int lineas = 0;
                String linea;
                while ((linea = lector.readLine()) != null) {     // readLine devuelve null al llegar al final
                    System.out.println(linea);
                    lineas++;
                }
                System.out.println("--- fin del historial (" + lineas + " lineas) ---");
                return true;
            } catch (FileNotFoundException e) {
                System.out.println("Todavia no hay historial: el archivo " + archivo.getPath()
                        + " no existe. Juega un combate o un torneo primero.");
                return false;
            } catch (IOException e) {
                System.out.println("No se pudo leer el historial: " + e.getMessage());
                return false;
            }
        }
    }
}