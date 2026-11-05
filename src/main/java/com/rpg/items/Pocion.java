package com.rpg.items;

import com.rpg.entities.EstadoEfecto;
import com.rpg.entities.enums.RaridadItem;

import java.io.Serializable;

public class Pocion implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String nombre;
    private final int curacion;
    private final RaridadItem raridad;
    private final EstadoEfecto efecto;
    private final boolean purifica;

    public Pocion(String nombre, int curacion, RaridadItem raridad) {
        this(nombre, curacion, raridad, null, false);
    }

    public Pocion(String nombre, int curacion, RaridadItem raridad, EstadoEfecto efecto, boolean purifica) {
        this.nombre = nombre;
        this.curacion = curacion;
        this.raridad = raridad;
        this.efecto = efecto;
        this.purifica = purifica;
    }

    public String getNombre() {
        return nombre;
    }

    public int getCuracionReal() {
        return (int) (curacion * raridad.getMultiplicadorStat());
    }

    public EstadoEfecto getEfecto() {
        return efecto;
    }

    public boolean isPurifica() {
        return purifica;
    }
}