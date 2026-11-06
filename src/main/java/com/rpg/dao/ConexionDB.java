package com.rpg.dao;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConexionDB {

    private static final String ARCHIVO_CONFIG = "config.txt";
    private static final String CLAVE_URL = "url";
    private static final String CLAVE_USUARIO = "usuario";
    private static final String CLAVE_CLAVE = "clave";
    private static final List<String> CLAVES_OBLIGATORIAS = List.of(CLAVE_URL, CLAVE_USUARIO, CLAVE_CLAVE);

    // Codigos de error de MySQL que se traducen a un mensaje entendible
    private static final int ERROR_ACCESO_DENEGADO = 1045;
    private static final int ERROR_BD_DESCONOCIDA = 1049;

    private ConexionDB() {
    }

    public static Connection obtenerConexion() throws DaoException {
        Map<String, String> config = leerConfiguracion();
        String url = config.get(CLAVE_URL);
        try {
            // JDBC 4+ registra el controlador solo (no hace falta Class.forName). Solo mira la URL y lo elige.
            return DriverManager.getConnection(url, config.get(CLAVE_USUARIO), config.get(CLAVE_CLAVE));
        } catch (SQLException e) {
            throw new DaoException(describirFalloDeConexion(e, url), e);
        }
    }

    private static Map<String, String> leerConfiguracion() throws DaoException {
        File archivo = new File(ARCHIVO_CONFIG);
        if (!archivo.exists()) {
            throw new DaoException("No se encontro el archivo " + archivo.getAbsolutePath()
                    + ". Copia config.example.txt como config.txt en la raiz del proyecto y completa tus datos.");
        }
        Map<String, String> config = new HashMap<>();
        try (BufferedReader lector = new BufferedReader(new FileReader(archivo))) {
            String linea;
            int numero = 0;
            while ((linea = lector.readLine()) != null) {
                numero++;
                linea = linea.trim();
                if (linea.isEmpty() || linea.startsWith("#")) {
                    continue;                                   // lineas vacias y comentarios se ignoran
                }
                String[] partes = linea.split("=", 2);          // 2: la clave puede contener "=" sin romperse
                if (partes.length < 2) {
                    throw new DaoException("Linea " + numero + " invalida en " + ARCHIVO_CONFIG
                            + ": se esperaba clave=valor");
                }
                config.put(partes[0].trim(), partes[1].trim());
            }
        } catch (FileNotFoundException e) {
            throw new DaoException("No se pudo abrir " + archivo.getAbsolutePath()
                    + " (revisa que exista y que tengas permiso de lectura).", e);
        } catch (IOException e) {
            throw new DaoException("Error al leer " + ARCHIVO_CONFIG + ": " + e.getMessage(), e);
        }
        validarClaves(config);
        return config;
    }

    private static void validarClaves(Map<String, String> config) throws DaoException {
        for (String clave : CLAVES_OBLIGATORIAS) {
            if (!config.containsKey(clave)) {
                throw new DaoException("Falta la clave \"" + clave + "\" en " + ARCHIVO_CONFIG
                        + ". Mira config.example.txt para ver el formato.");
            }
        }
        // La clave (password) SI puede estar vacia (MySQL local sin contrasena); la url y el usuario no.
        if (config.get(CLAVE_URL).isEmpty() || config.get(CLAVE_USUARIO).isEmpty()) {
            throw new DaoException("En " + ARCHIVO_CONFIG + " los valores de \"" + CLAVE_URL + "\" y \""
                    + CLAVE_USUARIO + "\" no pueden estar vacios.");
        }
    }

    private static String describirFalloDeConexion(SQLException e, String url) {
        return switch (e.getErrorCode()) {
            case ERROR_ACCESO_DENEGADO -> "MySQL rechazo el usuario o la clave de config.txt.";
            case ERROR_BD_DESCONOCIDA -> "La base de datos de la URL no existe. Ejecuta schema.sql primero.";
            default -> "No se pudo conectar a " + url + ". Revisa que MySQL este encendido y que la URL sea correcta ("
                    + e.getMessage() + ")";
        };
    }
}