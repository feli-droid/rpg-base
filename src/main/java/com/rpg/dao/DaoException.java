package com.rpg.dao;

public class DaoException extends Exception {

    private static final long serialVersionUID = 1L;

    public DaoException(String mensaje) {
        super(mensaje);
    }

    public DaoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
