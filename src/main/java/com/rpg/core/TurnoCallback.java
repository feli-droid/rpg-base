package com.rpg.core;

/**
 * TALLER CONCURRENCIA (Seccion 1).
 * Contrato para quien quiera enterarse de que el temporizador de un turno llego a cero.
 * Lo implementa GestorTurno y lo invoca TemporizadorTurnoTask desde su propio hilo.
 */
@FunctionalInterface
public interface TurnoCallback {

    /** Se llama (desde el hilo del temporizador) cuando se acaba el tiempo del turno. */
    void onTiempoAgotado();
}