package com.rpg.entities;

import com.rpg.entities.enums.TipoEfecto;

/**
 * Efecto continuo (DoT / HoT) aplicado a un personaje.
 *
 * TALLER CONCURRENCIA: ahora lo consume un hilo (EfectoContinuoTask) mientras otro hilo puede
 * estar mostrandolo por pantalla, por eso los metodos que tocan "turnosRestantes" son synchronized.
 * "turnosRestantes" cuenta los ticks que le quedan al efecto.
 */
public class EstadoEfecto {

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