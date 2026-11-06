package com.rpg.dao;

public class NombreDuplicadoException extends DaoException {

    private static final long serialVersionUID = 1L;

    public NombreDuplicadoException(String nombre, Throwable causa) {
        super("Ya existe un heroe con el nombre " + nombre, causa);
    }
}