package com.rpg.controller;

public record Resultado<T>(boolean exito, String mensaje, T datos) {

    public static <T> Resultado<T> ok(String mensaje, T datos) {
        return new Resultado<>(true, mensaje, datos);
    }

    public static <T> Resultado<T> error(String mensaje) {
        return new Resultado<>(false, mensaje, null);
    }
}