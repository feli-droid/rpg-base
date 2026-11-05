package com.rpg.entities;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public class PerfilJugador implements Serializable {

    private static final long serialVersionUID = 1L;

    private int victorias;
    private int derrotas;
    private final Set<String> logros = new TreeSet<>();

    public void registrarVictoria() {
        victorias++;
    }

    public void registrarDerrota() {
        derrotas++;
    }

    public boolean desbloquearLogro(String logro) {
        return logros.add(logro);
    }

    public int getVictorias() {
        return victorias;
    }

    public int getDerrotas() {
        return derrotas;
    }

    public Set<String> getLogros() {
        return Collections.unmodifiableSet(logros);
    }
}