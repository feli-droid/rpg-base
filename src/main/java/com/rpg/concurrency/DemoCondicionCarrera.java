package com.rpg.concurrency;

import com.rpg.entities.Guerrero;
import com.rpg.entities.Personaje;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * TALLER CONCURRENCIA (Seccion 6 e "Investigacion obligatoria"): reproduce y resuelve la condicion
 * de carrera en la vida de un personaje. Se ejecuta con:  java -cp out com.rpg.concurrency.DemoCondicionCarrera
 *
 * 1) VidaInsegura: copia del codigo ORIGINAL de Personaje.recibirDanio (leer, calcular, escribir sin
 *    proteccion). Dos hilos hacen la misma cantidad de danio y se pierden actualizaciones.
 * 2) Personaje real: recibirDanio es synchronized, el resultado es exacto.
 * 3) VidaConReentrantLock: recibirDanio reescrito con lock()/unlock() dentro de try-finally.
 * 4) AtomicInteger: por que addAndGet(-danio) NO basta aqui.
 *
 * -----------------------------------------------------------------------------------------------
 * POR QUE synchronized ES EL MECANISMO PRINCIPAL
 *   recibirDanio no es una sola operacion: lee vida, resta, acota a 0..vidaMaxima y, si llega a 0,
 *   cambia "estado" a MUERTO. Son VARIAS variables dependientes entre si. synchronized convierte todo
 *   ese bloque en una unidad indivisible y ademas se libera solo, incluso si hay una excepcion.
 *
 * POR QUE AtomicInteger.addAndGet(-danio) NO BASTA
 *   Es atomico solo para UN entero. No acota la vida (puede quedar en negativo) y no puede cambiar
 *   "estado" en el mismo paso: entre el addAndGet y el cambio de estado otro hilo ve una vida 0 con
 *   estado VIVO. (Reto extra: updateAndGet(v -> Math.max(0, v - danio)) o un bucle con compareAndSet
 *   si arreglan el acotado, pero "estado" sigue siendo una segunda variable fuera de la operacion.)
 *
 * CUANDO PREFERIRIA ReentrantLock A synchronized
 *   Cuando necesito algo que synchronized no ofrece: tryLock() (no quedarme bloqueado), lockInterruptibly()
 *   (poder cancelar la espera con interrupt()), un lock "justo" (fair) o varias Condition en un mismo
 *   candado. A cambio, la responsabilidad es mia: si el unlock() no esta en el finally, una excepcion
 *   deja el lock tomado para siempre y el programa se bloquea.
 * -----------------------------------------------------------------------------------------------
 */
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