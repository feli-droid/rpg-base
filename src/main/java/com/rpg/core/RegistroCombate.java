package com.rpg.core;

/**
 * Imprime y acumula el registro del combate.
 *
 * TALLER CONCURRENCIA: lo usan varios hilos a la vez (las dos semifinales, los efectos DoT/HoT, el
 * auto-guardado que lee el texto), por eso sus metodos son synchronized: cada linea se imprime y
 * se acumula completa, sin mezclarse con la de otro hilo.
 *
 * TALLER ARCHIVOS: aqui el texto solo se acumula y se muestra; llevarlo a un .txt es parte del taller.
 */
public class RegistroCombate {

    private final StringBuilder texto = new StringBuilder();

    public synchronized void agregar(String mensaje) {
        System.out.println(mensaje);
        texto.append(mensaje).append(System.lineSeparator());
    }

    public void agregar(String formato, Object... argumentos) {
        agregar(String.format(formato, argumentos));
    }

    public synchronized void limpiar() {
        texto.setLength(0);
    }

    public synchronized String getTexto() {
        return texto.toString();
    }
}