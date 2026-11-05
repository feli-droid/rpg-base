package com.rpg.entities;

import com.rpg.entities.enums.TipoEfecto;

import java.io.Serializable;

public class EstadoEfecto implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String nombre;
    private final TipoEfecto tipo;
    private final int valorPorTick;
    private int turnosRestantes;

    public EstadoEfecto(String nombre, TipoEfecto tipo, int valorPorTick, int turnosRestantes) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.valorPorTick = valorPorTick;
        this.turnosRestantes = turnosRestantes;
    }

    public synchronized EstadoEfecto copia() {
        return new EstadoEfecto(nombre, tipo, valorPorTick, turnosRestantes);
    }

    public synchronized void consumirTurno() {
        turnosRestantes--;
    }

    public synchronized boolean haTerminado() {
        return turnosRestantes <= 0;
    }

    public boolean esDanio() {
        return tipo == TipoEfecto.DANIO_CONTINUO;
    }

    public String getNombre() {
        return nombre;
    }

    public int getValorPorTick() {
        return valorPorTick;
    }

    public synchronized int getTurnosRestantes() {
        return turnosRestantes;
    }
}