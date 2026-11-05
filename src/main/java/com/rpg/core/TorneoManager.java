package com.rpg.core;

import com.rpg.entities.Escuadron;
import com.rpg.entities.Guerrero;
import com.rpg.entities.Mago;
import com.rpg.entities.Personaje;

import java.util.List;

/**
 * Torneo de 4 gladiadores: dos semifinales y una final.
 *
 * TALLER CONCURRENCIA (Seccion 3): las dos semifinales corren AL MISMO TIEMPO, cada una en su
 * hilo, y el hilo principal espera a ambas con join() antes de leer los ganadores y lanzar la final.
 */
public class TorneoManager {

    /** Tiempo que el hilo principal espera antes de "mirar" como van las semifinales. */
    private static final long PAUSA_OBSERVACION_MS = 700;

    private final Arena arena;
    private final RegistroCombate registro;

    public TorneoManager(Arena arena, RegistroCombate registro) {
        this.arena = arena;
        this.registro = registro;
    }

    /** @return el campeon, o null si el torneo se interrumpio antes de terminar. */
    public Personaje jugarTorneo() {
        List<Personaje> gladiadores = crearGladiadores().getMiembros();

        // Memoria compartida: cada semifinal escribe SOLO en su casilla (A -> 0, B -> 1).
        // El hilo principal la lee unicamente despues de los join(), que garantizan que los
        // resultados ya estan escritos y son visibles.
        Personaje[] ganadores = new Personaje[2];

        Thread semifinalA = new Thread(
                () -> ganadores[0] = simularSemifinal(gladiadores.get(0), gladiadores.get(1)), "semifinal-A");
        Thread semifinalB = new Thread(
                () -> ganadores[1] = simularSemifinal(gladiadores.get(2), gladiadores.get(3)), "semifinal-B");

        mostrarEstados("antes de start()", semifinalA, semifinalB);        // NEW
        semifinalA.start();
        semifinalB.start();
        mostrarEstados("justo despues de start()", semifinalA, semifinalB);

        try {
            Thread.sleep(PAUSA_OBSERVACION_MS);
            mostrarEstados("durante la ejecucion", semifinalA, semifinalB); // RUNNABLE / TIMED_WAITING
            semifinalA.join();
            semifinalB.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            semifinalA.interrupt();
            semifinalB.interrupt();
            registro.agregar("Torneo interrumpido antes de terminar las semifinales.");
            return null;
        }
        mostrarEstados("despues de join()", semifinalA, semifinalB);       // TERMINATED

        // Solo aqui, con ambos join() ya retornados, es seguro leer los ganadores.
        if (ganadores[0] == null || ganadores[1] == null) {
            registro.agregar("Una semifinal termino sin ganador: se cancela el torneo.");
            return null;
        }
        return simularFinal(ganadores[0], ganadores[1]);
    }

    public Personaje simularSemifinal(Personaje a, Personaje b) {
        registro.agregar("=== SEMIFINAL: %s vs %s ===", a.getNombre(), b.getNombre());
        Personaje ganador = arena.duelo(a, b);
        registro.agregar("Clasifica a la final: %s", ganador.getNombre());
        return ganador;
    }

    private Personaje simularFinal(Personaje finalista1, Personaje finalista2) {
        finalista1.restaurarVida();
        finalista2.restaurarVida();
        registro.agregar("=== FINAL: %s vs %s ===", finalista1.getNombre(), finalista2.getNombre());
        return arena.duelo(finalista1, finalista2);
    }

    private void mostrarEstados(String momento, Thread semifinalA, Thread semifinalB) {
        registro.agregar("[Hilos %s] %s: %s | %s: %s", momento,
                semifinalA.getName(), semifinalA.getState(), semifinalB.getName(), semifinalB.getState());
    }

    private Escuadron crearGladiadores() {
        Escuadron escuadron = new Escuadron("Gladiadores");
        escuadron.agregar(new Guerrero("Maximo"));
        escuadron.agregar(new Mago("Circe"));
        escuadron.agregar(new Guerrero("Aquiles"));
        escuadron.agregar(new Mago("Merlina"));
        return escuadron;
    }
}