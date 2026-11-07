package com.rpg.concurrency;

import com.rpg.core.TurnoCallback;

public class GestorTurno implements TurnoCallback {

    private Thread hilo;
    private volatile boolean tiempoAgotado;

    /** Arranca un temporizador nuevo de {@code segundos} segundos. */
    public void iniciar(int segundos) {
        cancelar();                       // por si quedaba uno anterior
        tiempoAgotado = false;
        hilo = new Thread(new TemporizadorTurnoTask(segundos, this), "temporizador-turno");
        // Daemon: si el juego se cierra con el temporizador vivo, la JVM no se queda esperando.
        hilo.setDaemon(true);
        hilo.start();
    }

    /** El jugador respondio: se cancela el temporizador con interrupt() y se espera a que termine. */
    public void cancelar() {
        if (hilo == null) {
            return;
        }
        hilo.interrupt();
        try {
            hilo.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // no tragarse la interrupcion
        }
        hilo = null;
    }

    @Override
    public void onTiempoAgotado() {
        tiempoAgotado = true;
        System.out.println("\n*** Se acabo el tiempo! Pierdes el turno. Pulsa ENTER para continuar. ***");
    }

    public boolean isTiempoAgotado() {
        return tiempoAgotado;
    }
}