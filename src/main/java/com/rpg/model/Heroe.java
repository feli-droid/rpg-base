package com.rpg.model;

/**
 * MODELO (MVC): un heroe guardado en la base de datos. Es un POJO: atributos privados, constructores y
 * getters/setters. No sabe nada de JDBC, de consola ni de menus; solo guarda datos y se protege de valores invalidos.
 *
 * Cada atributo corresponde a una columna de la tabla "heroes":
 * id -> id | nombre -> nombre | tipo -> tipo | vidaMaxima -> vida_maxima | nivel -> nivel
 */
public final class Heroe {

    public static final String TIPO_GUERRERO = "GUERRERO";
    public static final String TIPO_MAGO = "MAGO";

    private int id;
    private String nombre;
    private String tipo;
    private int vidaMaxima;
    private int nivel;

    /** Heroe nuevo, todavia sin id (lo asigna MySQL al insertarlo). */
    public Heroe(String nombre, String tipo, int vidaMaxima, int nivel) {
        this(0, nombre, tipo, vidaMaxima, nivel);
    }

    /** Heroe ya existente en la BD (el DAO lo construye con cada fila del ResultSet). */
    public Heroe(int id, String nombre, String tipo, int vidaMaxima, int nivel) {
        setId(id);
        setNombre(nombre);
        setTipo(tipo);
        setVidaMaxima(vidaMaxima);
        setNivel(nivel);
    }

    /** @return true si el texto es uno de los tipos validos (sin importar mayusculas). */
    public static boolean esTipoValido(String tipo) {
        return tipo != null
                && (tipo.trim().equalsIgnoreCase(TIPO_GUERRERO) || tipo.trim().equalsIgnoreCase(TIPO_MAGO));
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        if (id < 0) {
            throw new IllegalArgumentException("El id no puede ser negativo");
        }
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre no puede estar vacio");
        }
        this.nombre = nombre.trim();
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        if (!esTipoValido(tipo)) {
            throw new IllegalArgumentException("El tipo debe ser GUERRERO o MAGO");
        }
        this.tipo = tipo.trim().toUpperCase();
    }

    public int getVidaMaxima() {
        return vidaMaxima;
    }

    public void setVidaMaxima(int vidaMaxima) {
        if (vidaMaxima <= 0) {
            throw new IllegalArgumentException("La vida maxima debe ser mayor que 0");
        }
        this.vidaMaxima = vidaMaxima;
    }

    public int getNivel() {
        return nivel;
    }

    public void setNivel(int nivel) {
        if (nivel < 1) {
            throw new IllegalArgumentException("El nivel debe ser 1 o mayor");
        }
        this.nivel = nivel;
    }

    @Override
    public String toString() {
        return String.format("Heroe[id=%d, nombre=%s, tipo=%s, vida=%d, nivel=%d]", id, nombre, tipo, vidaMaxima, nivel);
    }
}