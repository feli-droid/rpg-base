package com.rpg.core;

/** Acciones que el jugador puede elegir en su turno. */
public enum Accion {
    ATACAR,
    ESPECIAL,
    POCION,
    ESTADO,
    HUIR,
    /** TALLER CONCURRENCIA: el temporizador se agoto antes de que el jugador eligiera, el turno se pierde. */
    TIEMPO_AGOTADO
}