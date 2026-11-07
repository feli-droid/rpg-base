package com.rpg.concurrency;

import com.rpg.entities.Guerrero;
import com.rpg.entities.Personaje;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class DemoCondicionCarrera {

    private static final int VIDA_INICIAL = 1_000_000;
    private static final int GOLPES_POR_HILO = 200_000;
    private static final int INTENTOS = 5;

    public static void main(String[] args) throws InterruptedException {
        int esperado = VIDA_INICIAL - 2 * GOLPES_POR_HILO;
        System.out.println("Dos hilos aplican " + GOLPES_POR_HILO + " golpes de 1 de dano cada uno.");
        System.out.println("Vida inicial: " + VIDA_INICIAL + " -> vida esperada: " + esperado + "\n");

        // 1) Sin proteccion: se repite hasta ver perdidas (puede no fallar en una sola pasada)
        int obtenido = esperado;
        for (int intento = 1; intento <= INTENTOS && obtenido == esperado; intento++) {
            VidaInsegura insegura = new VidaInsegura(VIDA_INICIAL);
            golpesUnitarios(insegura::recibirDanio);
            obtenido = insegura.getVida();
            System.out.printf("[SIN proteccion]   intento %d: vida = %d (%s)%n", intento, obtenido,
                    obtenido == esperado ? "correcto" : "PERDIO " + (obtenido - esperado) + " actualizaciones");
        }

        // 2) El Personaje real, con recibirDanio synchronized
        Personaje guerrero = new Guerrero("Prueba");
        guerrero.setVidaMaxima(VIDA_INICIAL);
        guerrero.setVida(VIDA_INICIAL);
        golpesUnitarios(guerrero::recibirDanio);
        System.out.printf("[synchronized]     Personaje.recibirDanio: vida = %d (esperado %d)%n", guerrero.getVida(), esperado);

        // 3) ReentrantLock
        VidaConReentrantLock conLock = new VidaConReentrantLock(VIDA_INICIAL);
        golpesUnitarios(conLock::recibirDanio);
        System.out.printf("[ReentrantLock]    lock()/unlock() en finally: vida = %d (esperado %d)%n", conLock.getVida(), esperado);

        // 4) AtomicInteger: exacto con un solo entero, pero no acota ni cambia el estado
        AtomicInteger atomica = new AtomicInteger(100);
        golpeGrande(n -> atomica.addAndGet(-n), 100);   // cada hilo pega UNA vez con 100 de dano
        System.out.printf("[AtomicInteger]    addAndGet(-danio) sobre vida 100 y 200 de dano: vida = %d%n", atomica.get());
        System.out.println("                   -> queda en NEGATIVO: no respeta el minimo 0 de setVida y tampoco marca MUERTO.");
        AtomicInteger acotada = new AtomicInteger(100);
        golpeGrande(n -> acotada.updateAndGet(v -> Math.max(0, v - n)), 100);
        System.out.printf("[updateAndGet]     con Math.max(0, ...): vida = %d (acotada, pero 'estado' sigue sin cambiar atomicamente)%n",
                acotada.get());
    }

    // ------------------------------------------------------------------------------------

    /** Dos hilos, cada uno con GOLPES_POR_HILO golpes de 1 de dano (muchas operaciones: aflora la carrera). */
    private static void golpesUnitarios(DanioAplicable objetivo) throws InterruptedException {
        Runnable tarea = () -> {
            for (int i = 0; i < GOLPES_POR_HILO; i++) {
                objetivo.aplicar(1);
            }
        };
        correr(tarea);
    }

    /** Dos hilos, cada uno con UN golpe de {@code danio}. */
    private static void golpeGrande(DanioAplicable objetivo, int danio) throws InterruptedException {
        correr(() -> objetivo.aplicar(danio));
    }

    private static void correr(Runnable tarea) throws InterruptedException {
        Thread a = new Thread(tarea, "dot-A");
        Thread b = new Thread(tarea, "dot-B");
        a.start();
        b.start();
        a.join();
        b.join();
    }

    @FunctionalInterface
    private interface DanioAplicable {
        void aplicar(int danio);
    }

    /** Copia del codigo ORIGINAL de Personaje.recibirDanio / setVida: nada lo protege. */
    private static class VidaInsegura {
        private final int vidaMaxima;
        private int vida;

        VidaInsegura(int vidaMaxima) {
            this.vidaMaxima = vidaMaxima;
            this.vida = vidaMaxima;
        }

        void recibirDanio(int danio) {
            setVida(vida - danio);          // leer, calcular y escribir: tres pasos separados
        }

        private void setVida(int nueva) {
            vida = Math.max(0, Math.min(nueva, vidaMaxima));
        }

        int getVida() {
            return vida;
        }
    }

    /** Personaje.recibirDanio reescrito con ReentrantLock: el unlock() va SIEMPRE en el finally. */
    private static class VidaConReentrantLock {
        private final ReentrantLock candado = new ReentrantLock();
        private final int vidaMaxima;
        private int vida;

        VidaConReentrantLock(int vidaMaxima) {
            this.vidaMaxima = vidaMaxima;
            this.vida = vidaMaxima;
        }

        void recibirDanio(int danio) {
            if (danio < 0) {
                throw new IllegalArgumentException("El danio no puede ser negativo");
            }
            candado.lock();
            try {
                vida = Math.max(0, Math.min(vida - danio, vidaMaxima));
            } finally {
                candado.unlock();           // sin finally, una excepcion dejaria el lock tomado para siempre
            }
        }

        int getVida() {
            candado.lock();
            try {
                return vida;
            } finally {
                candado.unlock();
            }
        }
    }
}