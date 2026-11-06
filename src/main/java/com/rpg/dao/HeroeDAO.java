package com.rpg.dao;

import com.rpg.model.Heroe;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class HeroeDAO {

    private static final String COLUMNAS = "id, nombre, tipo, vida_maxima, nivel";
    private static final String SQL_INSERTAR =
            "INSERT INTO heroes (nombre, tipo, vida_maxima, nivel) VALUES (?, ?, ?, ?)";
    private static final String SQL_LISTAR = "SELECT " + COLUMNAS + " FROM heroes ORDER BY id";
    private static final String SQL_POR_TIPO = "SELECT " + COLUMNAS + " FROM heroes WHERE tipo = ? ORDER BY id";
    private static final String SQL_POR_ID = "SELECT " + COLUMNAS + " FROM heroes WHERE id = ?";
    private static final String SQL_ACTUALIZAR_NIVEL = "UPDATE heroes SET nivel = ? WHERE id = ?";
    private static final String SQL_ELIMINAR = "DELETE FROM heroes WHERE id = ?";

    /** Codigo de error de MySQL para "entrada duplicada" (violacion de una clave UNIQUE). */
    private static final int ERROR_DUPLICADO = 1062;

    public int insertar(Heroe heroe) throws DaoException {
        try (Connection conexion = ConexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_INSERTAR, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setString(1, heroe.getNombre());      // los ? se numeran desde 1
            sentencia.setString(2, heroe.getTipo());
            sentencia.setInt(3, heroe.getVidaMaxima());
            sentencia.setInt(4, heroe.getNivel());
            int filas = sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                if (claves.next()) {
                    heroe.setId(claves.getInt(1));
                }
            }
            return filas;
        } catch (SQLException e) {
            if (e.getErrorCode() == ERROR_DUPLICADO) {
                throw new NombreDuplicadoException(heroe.getNombre(), e);
            }
            throw new DaoException("No se pudo guardar el heroe: " + e.getMessage(), e);
        }
    }

    /** Devuelve todos los heroes (executeQuery + while (rs.next())). */
    public List<Heroe> listar() throws DaoException {
        List<Heroe> heroes = new ArrayList<>();
        try (Connection conexion = ConexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_LISTAR);
             ResultSet filas = sentencia.executeQuery()) {
            while (filas.next()) {
                heroes.add(mapear(filas));
            }
        } catch (SQLException e) {
            throw new DaoException("No se pudo leer la lista de heroes: " + e.getMessage(), e);
        }
        return heroes;
    }

    /** Devuelve los heroes de un tipo (executeQuery con un ?). El tipo debe venir ya validado y en mayusculas. */
    public List<Heroe> buscarPorTipo(String tipo) throws DaoException {
        List<Heroe> heroes = new ArrayList<>();
        try (Connection conexion = ConexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_TIPO)) {
            sentencia.setString(1, tipo);
            // El ResultSet se abre DESPUES de dar valor a los ?, por eso va en su propio try-with-resources
            try (ResultSet filas = sentencia.executeQuery()) {
                while (filas.next()) {
                    heroes.add(mapear(filas));
                }
            }
        } catch (SQLException e) {
            throw new DaoException("No se pudo buscar heroes por tipo: " + e.getMessage(), e);
        }
        return heroes;
    }

    /** Busca un heroe por su id (la usa la integracion con el juego). Optional vacio si no existe. */
    public Optional<Heroe> buscarPorId(int id) throws DaoException {
        try (Connection conexion = ConexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_ID)) {
            sentencia.setInt(1, id);
            try (ResultSet filas = sentencia.executeQuery()) {
                return filas.next() ? Optional.of(mapear(filas)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DaoException("No se pudo buscar el heroe: " + e.getMessage(), e);
        }
    }

    /** @return filas afectadas: 1 si el heroe existia, 0 si no hay ningun heroe con ese id */
    public int actualizarNivel(int id, int nivel) throws DaoException {
        try (Connection conexion = ConexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ACTUALIZAR_NIVEL)) {
            sentencia.setInt(1, nivel);
            sentencia.setInt(2, id);
            return sentencia.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("No se pudo actualizar el nivel: " + e.getMessage(), e);
        }
    }

    /** @return filas afectadas: 1 si se elimino, 0 si no hay ningun heroe con ese id */
    public int eliminar(int id) throws DaoException {
        try (Connection conexion = ConexionDB.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ELIMINAR)) {
            sentencia.setInt(1, id);
            return sentencia.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("No se pudo eliminar el heroe: " + e.getMessage(), e);
        }
    }

    /** Convierte la fila actual del ResultSet en un objeto Heroe (leyendo cada columna por su nombre). */
    private Heroe mapear(ResultSet fila) throws SQLException {
        return new Heroe(
                fila.getInt("id"),
                fila.getString("nombre"),
                fila.getString("tipo"),
                fila.getInt("vida_maxima"),
                fila.getInt("nivel"));
    }
}