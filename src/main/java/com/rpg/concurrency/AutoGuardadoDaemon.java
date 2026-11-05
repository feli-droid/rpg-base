package com.rpg.concurrency;

import com.rpg.core.MotorJuego;

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
                motor.autoGuardarEnDisco();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}