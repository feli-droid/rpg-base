package com.rpg.persistencia;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DemoArchivos {

    public static void main(String[] args) throws IOException {
        File carpeta = new File("partidas/demo");

        System.out.println("--- 1) Ruta relativa vs absoluta ---");
        File relativa = new File("partidas/demo/nota.txt");
        File absoluta = relativa.getAbsoluteFile();
        System.out.println("getPath()          = " + relativa.getPath() + "   (isAbsolute: " + relativa.isAbsolute() + ")");
        System.out.println("getAbsolutePath()  = " + absoluta.getPath() + "   (isAbsolute: " + absoluta.isAbsolute() + ")");
        System.out.println("user.dir           = " + System.getProperty("user.dir"));
        System.out.println("-> La relativa se resuelve contra user.dir: cambia segun desde donde se lance el programa.");
        System.out.println("   La absoluta apunta siempre al mismo sitio, pero rompe la portabilidad entre equipos.\n");

        System.out.println("--- 2) mkdirs() y createNewFile() ---");
        System.out.println("existe la carpeta antes?  " + carpeta.exists());
        System.out.println("mkdirs() devolvio         " + carpeta.mkdirs() + "  (true = la creo)");
        System.out.println("mkdirs() otra vez         " + carpeta.mkdirs() + "  (false = ya existia, no es un error)");
        System.out.println("createNewFile() devolvio  " + relativa.createNewFile() + "  (true = lo creo)");
        System.out.println("createNewFile() otra vez  " + relativa.createNewFile() + "  (false = ya existia)");
        System.out.println("getName()=" + relativa.getName() + " exists()=" + relativa.exists() + " length()=" + relativa.length() + "\n");

        System.out.println("--- 3) Escribir con y sin append ---");
        escribir(relativa, "linea 1", false);
        escribir(relativa, "linea 2", true);
        System.out.println("tras 'linea 1' (sin append) y 'linea 2' (append):  " + leer(relativa));
        escribir(relativa, "linea 3", false);
        System.out.println("tras 'linea 3' SIN append (sobrescribe):          " + leer(relativa));
        System.out.println("length() ahora = " + relativa.length() + " bytes\n");

        System.out.println("--- 4) File.delete() vs Files.deleteIfExists() ---");
        System.out.println("File.delete()        sobre el archivo existente  -> " + relativa.delete());
        System.out.println("File.delete()        otra vez (ya no existe)     -> " + relativa.delete() + "  (false: no dice si fallo o no existia)");
        Path ruta = relativa.toPath();
        System.out.println("deleteIfExists()     sobre archivo que no existe -> " + Files.deleteIfExists(ruta) + "  (false = no existia)");

        File carpetaConContenido = new File(carpeta, "llena");
        carpetaConContenido.mkdirs();
        new File(carpetaConContenido, "dentro.txt").createNewFile();
        System.out.println("File.delete()        sobre carpeta NO vacia      -> " + carpetaConContenido.delete() + "  (false mudo)");
        try {
            Files.deleteIfExists(carpetaConContenido.toPath());
        } catch (DirectoryNotEmptyException e) {
            System.out.println("deleteIfExists()     sobre carpeta NO vacia      -> DirectoryNotEmptyException: " + e.getMessage()
                    + "  (explica el motivo)");
        }
        new File(carpetaConContenido, "dentro.txt").delete();
        carpetaConContenido.delete();
        System.out.println();

        System.out.println("--- 5) FileWriter vs Files.writeString ---");
        Path nio = new File(carpeta, "nio.txt").toPath();
        Files.writeString(nio, "escrito con Files.writeString\n");
        System.out.println("Files.writeString (1 llamada) -> " + Files.readString(nio).trim());
        Files.writeString(nio, "otra linea\n", java.nio.file.StandardOpenOption.APPEND);
        System.out.println("con StandardOpenOption.APPEND  -> " + Files.readAllLines(nio));
        Files.deleteIfExists(nio);

        carpeta.delete();
        System.out.println("\n(Limpieza hecha: partidas/demo borrada.)");
    }

    private static void escribir(File archivo, String linea, boolean agregar) throws IOException {
        try (BufferedWriter escritor = new BufferedWriter(new FileWriter(archivo, agregar))) {
            escritor.write(linea);
            escritor.newLine();
        }
    }

    private static String leer(File archivo) throws IOException {
        StringBuilder texto = new StringBuilder();
        try (BufferedReader lector = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = lector.readLine()) != null) {
                texto.append("[").append(linea).append("] ");
            }
        }
        return texto.toString().trim();
    }
}