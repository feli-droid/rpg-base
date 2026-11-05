package com.rpg.concurrency;

import com.rpg.entities.EstadoEfecto;
import com.rpg.entities.Personaje;

/**
 * TALLER CONCURRENCIA (Seccion 2): un efecto continuo (DoT o HoT) corriendo en su propio hilo.
 *
 * Cada cierto intervalo (en milisegundos) aplica un tick de dano o de curacion sobre el Personaje.
 * Termina por tres caminos: se le acaban los ticks, el personaje muere, o alguien llama a
 * interrupt() sobre su hilo (antidoto, purificacion, fin del combate o efecto reemplazado).
 * No se usa Thread.stop() ni banderas booleanas: la cancelacion es SIEMPRE por interrupcion.
 */
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