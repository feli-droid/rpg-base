package com.rpg.persistencia;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;


public final class GestorArchivos {

    /** Candado compartido por GestorArchivos, LogCombate y GestorPartida. */
    public static final Object CANDADO = new Object();

    public static final File DIRECTORIO = new File("partidas");
    public static final File ARCHIVO_PARTIDA = new File(DIRECTORIO, "partida.dat");
    public static final File ARCHIVO_AUTOGUARDADO = new File(DIRECTORIO, "autoguardado.dat");
    public static final File ARCHIVO_LOG = new File(DIRECTORIO, "combate.log");

    private GestorArchivos() {
    }

    /** Crea la carpeta partidas/ si no existe (mkdirs crea tambien los padres que falten). */
    public static boolean asegurarDirectorio() {
        synchronized (CANDADO) {
            if (DIRECTORIO.exists()) {
                if (!DIRECTORIO.isDirectory()) {            // existe pero es un archivo: no sirve
                    System.out.println("No se puede usar " + DIRECTORIO.getAbsolutePath()
                            + ": existe y no es una carpeta.");
                    return false;
                }
                return true;
            }
            boolean creada = DIRECTORIO.mkdirs();           // false si no pudo crearla
            if (!creada) {
                System.out.println("No se pudo crear la carpeta " + DIRECTORIO.getAbsolutePath());
            }
            return creada;
        }
    }

    /**
     * Crea el archivo vacio si no existe (createNewFile devuelve false si ya existia: no es un error).
     *
     * @return true si el archivo existe al terminar.
     */
    public static boolean crearSiNoExiste(File archivo) {
        synchronized (CANDADO) {
            if (!asegurarDirectorio()) {
                return false;
            }
            try {
                archivo.createNewFile();
                return archivo.exists();
            } catch (IOException e) {
                System.out.println("No se pudo crear el archivo " + archivo.getPath() + ": " + e.getMessage());
                return false;
            }
        }
    }

    /** Informacion de un archivo: nombre, existencia, tamano y rutas relativa y absoluta. */
    public static String describir(File archivo) {
        synchronized (CANDADO) {
            StringBuilder texto = new StringBuilder();
            texto.append(String.format("%-18s", archivo.getName()));
            if (archivo.exists()) {
                texto.append(String.format("existe | %d bytes", archivo.length()));
            } else {
                texto.append("no existe");
            }
            texto.append(System.lineSeparator()).append("    ruta relativa: ").append(archivo.getPath());
            texto.append(System.lineSeparator()).append("    ruta absoluta: ").append(archivo.getAbsolutePath());
            return texto.toString();
        }
    }

    /**
     * Borra con File.delete(). Devuelve false tanto si el archivo NO existe como si existe y no se pudo
     * borrar (permisos, en uso...): no distingue ni explica el motivo. Por eso siempre se revisa el boolean.
     */
    public static boolean borrarConFile(File archivo) {
        synchronized (CANDADO) {
            return archivo.delete();
        }
    }

    /**
     * Borra con Files.deleteIfExists(Path). DIFERENCIA con File.delete(): devuelve true si lo borro y false
     * SOLO si no existia; cualquier otro fallo real (sin permisos, carpeta no vacia...) lanza una
     * IOException con el motivo, en vez de un false mudo.
     */
    public static boolean borrarConNio(File archivo) {
        synchronized (CANDADO) {
            try {
                return Files.deleteIfExists(archivo.toPath());
            } catch (IOException e) {
                System.out.println("No se pudo borrar " + archivo.getPath() + ": " + e.getMessage());
                return false;
            }
        }
    }
}