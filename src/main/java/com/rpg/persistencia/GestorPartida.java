package com.rpg.persistencia;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.NotSerializableException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public final class GestorPartida {

    /** Lista blanca y limites para deserializar (JEP 290). "!*" rechaza todo lo demas. */
    static final String PATRON_FILTRO =
            "maxdepth=100;maxrefs=100000;maxbytes=5000000;com.rpg.**;java.lang.*;java.util.*;!*";

    /** Se pone a true al cerrar el juego; protegido por GestorArchivos.CANDADO. */
    private static boolean cerrado = false;

    private GestorPartida() {
    }

    /** Guarda la partida en el archivo indicado. @return true si se escribio completa. */
    public static boolean guardar(PartidaGuardada partida, File destino) {
        synchronized (GestorArchivos.CANDADO) {
            return escribir(partida, destino);
        }
    }

    /** Guardado del hilo daemon: si el juego ya se esta cerrando, no empieza una escritura nueva. */
    public static boolean guardarAutomatico(PartidaGuardada partida) {
        synchronized (GestorArchivos.CANDADO) {
            if (cerrado) {
                return false;
            }
            return escribir(partida, GestorArchivos.ARCHIVO_AUTOGUARDADO);
        }
    }

    /**
     * Se llama al salir del juego. Tomar el candado obliga a ESPERAR a que termine una escritura en curso
     * del daemon, y la marca "cerrado" impide que empiece otra antes de que la JVM se apague.
     */
    public static void cerrar() {
        synchronized (GestorArchivos.CANDADO) {
            cerrado = true;
        }
    }


    private static boolean escribir(PartidaGuardada partida, File destino) {
        if (!GestorArchivos.asegurarDirectorio()) {
            return false;
        }
        File temporal = new File(destino.getPath() + ".tmp");
        // Cada flujo es un recurso: se cierran los tres, en orden inverso, aunque haya excepcion.
        try (FileOutputStream archivo = new FileOutputStream(temporal);
             BufferedOutputStream buffer = new BufferedOutputStream(archivo);
             ObjectOutputStream salida = new ObjectOutputStream(buffer)) {
            salida.writeObject(partida);
        } catch (NotSerializableException e) {
            System.out.println("No se pudo guardar: hay un objeto que no es Serializable (" + e.getMessage() + ").");
            borrarTemporal(temporal);
            return false;
        } catch (FileNotFoundException e) {
            System.out.println("No se pudo abrir " + temporal.getPath() + " para escribir (ruta invalida o sin permisos): "
                    + e.getMessage());
            return false;
        } catch (IOException e) {
            System.out.println("Error de entrada/salida al guardar la partida: " + e.getMessage());
            borrarTemporal(temporal);
            return false;
        }
        return reemplazar(temporal, destino);
    }

    /** El temporal ya esta completo y cerrado: se renombra sobre el definitivo (operacion atomica si el sistema puede). */
    private static boolean reemplazar(File temporal, File destino) {
        try {
            try {
                Files.move(temporal.toPath(), destino.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            System.out.println("No se pudo terminar de guardar " + destino.getPath() + ": " + e.getMessage());
            borrarTemporal(temporal);
            return false;
        }
    }

    private static void borrarTemporal(File temporal) {
        try {
            Files.deleteIfExists(temporal.toPath());
        } catch (IOException e) {
            System.out.println("Aviso: no se pudo borrar el temporal " + temporal.getPath() + ": " + e.getMessage());
        }
    }


    /** Carga la partida de un archivo. Si algo falla, avisa y devuelve Optional.empty(): el juego sigue. */
    public static Optional<PartidaGuardada> cargar(File origen) {
        synchronized (GestorArchivos.CANDADO) {
            try (FileInputStream archivo = new FileInputStream(origen);
                 BufferedInputStream buffer = new BufferedInputStream(archivo);
                 ObjectInputStream entrada = new ObjectInputStream(buffer)) {
                entrada.setObjectInputFilter(ObjectInputFilter.Config.createFilter(PATRON_FILTRO));
                Object leido = entrada.readObject();
                if (leido instanceof PartidaGuardada partida) {
                    return Optional.of(partida);
                }
                System.out.println("El archivo " + origen.getName() + " no contiene una partida de este juego.");
            } catch (FileNotFoundException e) {
                System.out.println("No existe el archivo " + origen.getPath() + ": todavia no hay partida guardada.");
            } catch (InvalidClassException e) {
                System.out.println("La partida es de otra version del juego (o fue rechazada por seguridad) y no se puede cargar: "
                        + e.getMessage());
            } catch (StreamCorruptedException | EOFException e) {
                System.out.println("El archivo " + origen.getName() + " esta danado o incompleto ("
                        + (e.getMessage() != null ? e.getMessage() : "termina antes de tiempo") + ").");
            } catch (ClassNotFoundException e) {
                System.out.println("La partida usa una clase que este juego no conoce: " + e.getMessage());
            } catch (IOException e) {
                System.out.println("No se pudo leer " + origen.getPath() + ": " + e.getMessage());
            }
            return Optional.empty();
        }
    }

    /** Filtro de seguridad: lo usa tambien DemoSerializacion para mostrar un objeto hostil rechazado. */
    static ObjectInputFilter filtro() {
        return ObjectInputFilter.Config.createFilter(PATRON_FILTRO);
    }
}