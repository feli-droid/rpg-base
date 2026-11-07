package com.rpg.concurrency;

import com.rpg.core.TurnoCallback;

public class TemporizadorTurnoTask implements Runnable {

    private static final long UN_SEGUNDO_MS = 1000;
    private static final int SEGUNDOS_DE_AVISO = 5;

    private final int duracionSegundos;
    private final TurnoCallback callback;

    public TemporizadorTurnoTask(int duracionSegundos, TurnoCallback callback) {
        if (duracionSegundos <= 0) {
            throw new IllegalArgumentException("La duracion del turno debe ser positiva");
        }
        if (callback == null) {
            throw new IllegalArgumentException("El callback no puede ser null");
        }
        this.duracionSegundos = duracionSegundos;
        this.callback = callback;
    }

    @Override
    public void run() {
        for (int restante = duracionSegundos; restante > 0; restante--) {
            if (restante == SEGUNDOS_DE_AVISO && duracionSegundos > SEGUNDOS_DE_AVISO) {
                System.out.println("   (quedan " + SEGUNDOS_DE_AVISO + " segundos para elegir)");
            }
            try {
                Thread.sleep(UN_SEGUNDO_MS);
            } catch (InterruptedException e) {
                // El jugador respondio a tiempo: es una cancelacion pedida, no un error.
                // Se restaura la bandera de interrupcion y se sale SIN llamar al callback.
                Thread.currentThread().interrupt();
                return;
            }
        }
        callback.onTiempoAgotado();
    }
}