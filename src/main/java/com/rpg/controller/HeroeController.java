package com.rpg.controller;

import com.rpg.dao.DaoException;
import com.rpg.dao.HeroeDAO;
import com.rpg.dao.NombreDuplicadoException;
import com.rpg.entities.Guerrero;
import com.rpg.entities.Mago;
import com.rpg.entities.Personaje;
import com.rpg.model.Heroe;

import java.util.List;
import java.util.Optional;

/**
 * CONTROLLER (MVC): solo coordina. Recibe lo que escribio el usuario (a traves de la View), VALIDA las reglas,
 * llama al DAO y decide que mensaje mostrar. No imprime, no lee del teclado y no escribe una sola linea de SQL.
 *
 * Nota sobre la validacion doble: el Controller valida primero para dar mensajes amigables; el POJO Heroe vuelve a
 * validar en sus setters para que sea imposible construir un Heroe invalido aunque alguien se salte este paso.
 */
public class HeroeController {

    private static final int LARGO_MAXIMO_NOMBRE = 15;                 // igual que la columna nombre VARCHAR(15)
    private static final String PATRON_NOMBRE = "[\\p{L}0-9 ]+";       // letras, numeros y espacios (como en el juego)
    private static final String MENSAJE_NO_EXISTE = "No existe un heroe con ese id.";

    private final HeroeDAO dao;

    /** Constructor que usa la View: el Controller arma su propio DAO, asi la View nunca importa nada de dao. */
    public HeroeController() {
        this(new HeroeDAO());
    }

    /** Constructor con DAO inyectado (permite probar el Controller con un DAO falso). */
    public HeroeController(HeroeDAO dao) {
        this.dao = dao;
    }

    public Resultado<Heroe> insertar(String nombre, String tipo, int vidaMaxima, int nivel) {
        String problema = validarNombre(nombre);
        if (problema == null && !Heroe.esTipoValido(tipo)) {
            problema = "El tipo debe ser GUERRERO o MAGO.";
        }
        if (problema == null && vidaMaxima <= 0) {
            problema = "La vida maxima debe ser mayor que 0.";
        }
        if (problema == null && nivel < 1) {
            problema = "El nivel debe ser 1 o mayor.";
        }
        if (problema != null) {
            return Resultado.error(problema);
        }
        Heroe heroe = new Heroe(nombre.trim(), tipo.trim(), vidaMaxima, nivel);
        try {
            dao.insertar(heroe);
            return Resultado.ok("Heroe " + heroe.getNombre() + " guardado con el id " + heroe.getId() + ".", heroe);
        } catch (NombreDuplicadoException e) {
            return Resultado.error("Ya existe un heroe llamado " + heroe.getNombre() + ". Elige otro nombre.");
        } catch (DaoException e) {
            return Resultado.error(e.getMessage());
        }
    }

    public Resultado<List<Heroe>> listar() {
        try {
            List<Heroe> heroes = dao.listar();
            return Resultado.ok(heroes.isEmpty() ? "Todavia no hay heroes guardados." : heroes.size() + " heroe(s).", heroes);
        } catch (DaoException e) {
            return Resultado.error(e.getMessage());
        }
    }

    public Resultado<List<Heroe>> buscarPorTipo(String tipo) {
        if (!Heroe.esTipoValido(tipo)) {
            return Resultado.error("El tipo debe ser GUERRERO o MAGO.");
        }
        try {
            String tipoNormalizado = tipo.trim().toUpperCase();
            List<Heroe> heroes = dao.buscarPorTipo(tipoNormalizado);
            return Resultado.ok(heroes.isEmpty() ? "No hay heroes de tipo " + tipoNormalizado + "."
                    : heroes.size() + " heroe(s) de tipo " + tipoNormalizado + ".", heroes);
        } catch (DaoException e) {
            return Resultado.error(e.getMessage());
        }
    }

    public Resultado<Void> actualizarNivel(int id, int nivel) {
        if (id < 1) {
            return Resultado.error("El id debe ser 1 o mayor.");
        }
        if (nivel < 1) {
            return Resultado.error("El nivel debe ser 1 o mayor.");
        }
        try {
            int filas = dao.actualizarNivel(id, nivel);
            // executeUpdate devuelve 0 cuando el WHERE no encontro ninguna fila: el id no existe
            return filas == 0 ? Resultado.error(MENSAJE_NO_EXISTE)
                    : Resultado.ok("Nivel del heroe " + id + " actualizado a " + nivel + ".", null);
        } catch (DaoException e) {
            return Resultado.error(e.getMessage());
        }
    }

    public Resultado<Void> eliminar(int id) {
        if (id < 1) {
            return Resultado.error("El id debe ser 1 o mayor.");
        }
        try {
            int filas = dao.eliminar(id);
            return filas == 0 ? Resultado.error(MENSAJE_NO_EXISTE)
                    : Resultado.ok("Heroe " + id + " eliminado.", null);
        } catch (DaoException e) {
            return Resultado.error(e.getMessage());
        }
    }

    /**
     * INTEGRACION CON EL JUEGO: convierte una fila de la BD en un Guerrero o un Mago listo para pelear.
     * (Heroe es el POJO de la base de datos; Personaje es la entidad del juego: este metodo las conecta.)
     */
    public Resultado<Personaje> cargarComoPersonaje(int id) {
        if (id < 1) {
            return Resultado.error("El id debe ser 1 o mayor.");
        }
        try {
            Optional<Heroe> encontrado = dao.buscarPorId(id);
            if (encontrado.isEmpty()) {
                return Resultado.error(MENSAJE_NO_EXISTE);
            }
            Heroe heroe = encontrado.get();
            Personaje personaje = Heroe.TIPO_GUERRERO.equals(heroe.getTipo())
                    ? new Guerrero(heroe.getNombre())
                    : new Mago(heroe.getNombre());
            personaje.setVidaMaxima(heroe.getVidaMaxima());     // la vida del juego es la guardada en la BD
            personaje.setVida(heroe.getVidaMaxima());           // y empieza el combate con la vida completa
            return Resultado.ok(String.format("Heroe %s (%s, nivel %d, vida %d) cargado desde la base de datos.",
                    heroe.getNombre(), heroe.getTipo(), heroe.getNivel(), heroe.getVidaMaxima()), personaje);
        } catch (DaoException e) {
            return Resultado.error(e.getMessage());
        }
    }

    private String validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "El nombre no puede estar vacio.";
        }
        String limpio = nombre.trim();
        if (limpio.length() > LARGO_MAXIMO_NOMBRE) {
            return "El nombre admite como maximo " + LARGO_MAXIMO_NOMBRE + " caracteres.";
        }
        if (!limpio.matches(PATRON_NOMBRE)) {
            return "El nombre solo puede tener letras, numeros y espacios.";
        }
        return null;
    }
}