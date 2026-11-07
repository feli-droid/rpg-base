package com.rpg.concurrency;

import com.rpg.entities.EstadoEfecto;
import com.rpg.entities.Personaje;

public class EfectoContinuoTask implements Runnable {

    private final Personaje objetivo;
    private final EstadoEfecto efecto;
    private final long intervaloMs;

    public EfectoContinuoTask(Personaje objetivo, EstadoEfecto efecto, long intervaloMs) {
        if (objetivo == null || efecto == null) {
            throw new IllegalArgumentException("Objetivo y efecto son obligatorios");
        }
        if (intervaloMs <= 0) {
            throw new IllegalArgumentException("El intervalo debe ser positivo");
        }
        this.objetivo = objetivo;
        this.efecto = efecto;
        this.intervaloMs = intervaloMs;
    }

    public String getNombreEfecto() {
        return efecto.getNombre();
    }

    @Override
    public void run() {
        try {
            // Se consulta isInterrupted() tambien aqui porque entre un sleep y otro hay trabajo
            // (el tick) donde no hay ninguna InterruptedException que nos avise.
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(intervaloMs);
                    if (!objetivo.aplicarTickEfecto(efecto)) {
                        break;                              // se acabo, lo cancelaron o el objetivo murio
                    }
                } catch (InterruptedException e) {
                    objetivo.limpiarEfecto(efecto);         // limpiar el estado del personaje
                    Thread.currentThread().interrupt();     // conservar la bandera de interrupcion
                    break;                                  // y salir del bucle
                }
            }
        } finally {
            objetivo.liberarHilo(efecto);                   // pase lo que pase, el hilo deja de estar registrado
        }
    }
}