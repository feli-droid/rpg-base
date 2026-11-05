package com.rpg.concurrency;

import com.rpg.core.MotorJuego;

/**
 * TALLER CONCURRENCIA (Seccion 4): auto-guardado periodico en segundo plano.
 *
 * Corre en un hilo DAEMON: no impide que la JVM termine. Cuando el jugador sale del juego y el
 * hilo principal acaba, la JVM se cierra aunque este bucle siga "vivo" (while (true)).
 * Por eso setDaemon(true) se llama ANTES de start(); despues de start() lanza
 * IllegalThreadStateException.
 *
 * En esta etapa el guardado es en MEMORIA (MotorJuego.autoGuardar()). El taller de archivos
 * lo lleva a disco.
 */
public class AutoGuardadoDaemon implements Runnable {

    private final MotorJuego motor;
    private final long intervaloMs;

    public AutoGuardadoDaemon(MotorJuego motor, long intervaloMs) {
        if (motor == null) {
            throw new IllegalArgumentException("El motor es obligatorio");
        }
        if (intervaloMs <= 0) {
            throw new IllegalArgumentException("El intervalo debe ser positivo");
        }
        this.motor = motor;
        this.intervaloMs = intervaloMs;
    }

    /** Crea el hilo, lo marca como daemon (antes de start) y lo arranca. */
    public Thread iniciar() {
        Thread hilo = new Thread(this, "auto-guardado");
        hilo.setDaemon(true);
        hilo.start();
        return hilo;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(intervaloMs);
                motor.autoGuardar();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;                                   // alguien pidio detener el guardado
            }
        }
    }
}