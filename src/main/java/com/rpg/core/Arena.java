package com.rpg.core;

import com.rpg.entities.Personaje;
import com.rpg.exceptions.SinManaException;
import com.rpg.exceptions.VidaInsuficienteException;

import java.util.Random;

/**
 * Reglas del combate: ataque, habilidad especial, inicio de turno y duelo automatico.
 *
 * TALLER CONCURRENCIA: los efectos continuos ya no se procesan aqui turno a turno; cada uno corre
 * en su propio hilo (EfectoContinuoTask). La Arena solo los prepara (observador) y los cancela
 * cuando el duelo termina. Random es seguro para varios hilos, asi que dos semifinales pueden
 * compartir la misma Arena.
 */
public class Arena {

    private static final int PROBABILIDAD_CRITICO = 15;
    private static final double FACTOR_CRITICO = 1.5;
    private static final int VARIACION_DANIO = 3;
    private static final int PROBABILIDAD_ESPECIAL_IA = 35;
    private static final int MAXIMO_RONDAS = 40;
    /** Pausa entre rondas de un duelo automatico: deja que los efectos (en tiempo real) hagan efecto. */
    private static final long PAUSA_RONDA_MS = 500;

    private final RegistroCombate registro;
    private final Random azar;

    public Arena(RegistroCombate registro, Random azar) {
        this.registro = registro;
        this.azar = azar;
    }

    /** Devuelve true si el personaje puede actuar en este turno. */
    public boolean iniciarTurno(Personaje personaje) {
        personaje.alIniciarTurno();
        // Los DoT / HoT corren en sus propios hilos y ya avisaron en el registro si hubo muerte.
        if (!personaje.estaVivo()) {
            return false;
        }
        if (personaje.consumirAturdimiento()) {
            registro.agregar("%s esta aturdido y pierde el turno", personaje.getNombre());
            return false;
        }
        return true;
    }

    public void atacar(Personaje atacante, Personaje defensor) {
        boolean critico = azar.nextInt(100) < PROBABILIDAD_CRITICO;
        double factor = critico ? FACTOR_CRITICO : 1.0;
        int danio = (int) Math.round(atacante.calcularDanio() * factor) + azar.nextInt(VARIACION_DANIO + 1);
        int vidaAntes = defensor.getVida();
        defensor.defender(danio);
        registro.agregar("%s ataca a %s%s y causa %d de dano", atacante.getNombre(), defensor.getNombre(),
                critico ? " (CRITICO)" : "", vidaAntes - defensor.getVida());
        registro.agregar("   %s", FormatoCombate.resumen(defensor));
    }

    public boolean intentarEspecial(Personaje atacante, Personaje defensor) {
        try {
            registro.agregar(atacante.realizarAtaqueEspecial(defensor));
            registro.agregar("   %s", FormatoCombate.resumen(defensor));
            return true;
        } catch (SinManaException | VidaInsuficienteException e) {
            registro.agregar("%s no puede usar su habilidad: %s", atacante.getNombre(), e.getMessage());
            return false;
        }
    }

    public void turnoAutomatico(Personaje actual, Personaje rival) {
        if (!(actual.estaVivo() && rival.estaVivo() && iniciarTurno(actual))) {
            return;
        }
        boolean usaEspecial = azar.nextInt(100) < PROBABILIDAD_ESPECIAL_IA;
        if (usaEspecial && intentarEspecial(actual, rival)) {
            return;
        }
        atacar(actual, rival);
    }

    /**
     * Duelo automatico. Se puede ejecutar en un hilo propio (las semifinales corren en paralelo).
     * Al terminar, cancela los efectos que sigan activos para que sus hilos acaben limpios.
     */
    public Personaje duelo(Personaje a, Personaje b) {
        a.setObservador(registro::agregar);
        b.setObservador(registro::agregar);
        try {
            for (int ronda = 1; ronda <= MAXIMO_RONDAS && a.estaVivo() && b.estaVivo(); ronda++) {
                registro.agregar("-- Ronda %d (%s vs %s) --", ronda, a.getNombre(), b.getNombre());
                turnoAutomatico(a, b);
                turnoAutomatico(b, a);
                if (!pausarRonda()) {
                    break;
                }
            }
        } finally {
            a.cancelarEfectos();
            b.cancelarEfectos();
        }
        return a.getVida() >= b.getVida() ? a : b;
    }

    /** @return false si el hilo fue interrumpido (el duelo se detiene). */
    private boolean pausarRonda() {
        try {
            Thread.sleep(PAUSA_RONDA_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            registro.agregar("Duelo interrumpido.");
            return false;
        }
    }
}