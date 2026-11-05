package com.rpg.core;

import com.rpg.entities.Enemigo;
import com.rpg.entities.EstadoEfecto;
import com.rpg.entities.enums.TipoEfecto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Catalogo de monstruos (Map) y contador de derrotas por monstruo. */
public class Bestiario {

    private final Map<String, Enemigo> catalogo = new LinkedHashMap<>();
    private final Map<String, Integer> derrotados = new LinkedHashMap<>();

    public Bestiario() {
        registrar(new Enemigo("Goblin", 50, 8, 2));
        registrar(new Enemigo("Arana Gigante", 60, 10, 3,
                new EstadoEfecto("Veneno", TipoEfecto.DANIO_CONTINUO, 4, 3), false));
        registrar(new Enemigo("Ogro", 100, 14, 6, null, true));
        registrar(new Enemigo("Dragon Joven", 160, 20, 10,
                new EstadoEfecto("Quemadura", TipoEfecto.DANIO_CONTINUO, 6, 3), false));
    }

    private void registrar(Enemigo enemigo) {
        catalogo.put(enemigo.getNombre(), enemigo);
    }

    public List<String> nombres() {
        return new ArrayList<>(catalogo.keySet());
    }

    public Enemigo crearEnemigo(String nombre) {
        for (Map.Entry<String, Enemigo> entrada : catalogo.entrySet()) {
            if (entrada.getKey().equalsIgnoreCase(nombre)) {
                return entrada.getValue().copia();
            }
        }
        return null;
    }

    public void registrarEnemigoDerrotado(String nombre) {
        derrotados.put(nombre, derrotados.getOrDefault(nombre, 0) + 1);
    }

    public int vecesDerrotado(String nombre) {
        return derrotados.getOrDefault(nombre, 0);
    }

    public Map<String, Integer> exportarDerrotados() {
        return new LinkedHashMap<>(derrotados);
    }

    public void importarDerrotados(Map<String, Integer> datos) {
        derrotados.clear();
        derrotados.putAll(datos);
    }

    public String describir() {
        StringBuilder texto = new StringBuilder();
        int numero = 1;
        for (Map.Entry<String, Enemigo> entrada : catalogo.entrySet()) {
            Enemigo enemigo = entrada.getValue();
            texto.append(String.format("%d. %-14s HP %3d  ATK %2d  DEF %2d  Habilidad: %-9s Derrotado: %d%n",
                    numero++, entrada.getKey(), enemigo.getVidaMaxima(), enemigo.getAtaque(),
                    enemigo.getDefensa(), enemigo.getHabilidad(), vecesDerrotado(entrada.getKey())));
        }
        return texto.toString();
    }

    public String resumenDerrotados() {
        if (derrotados.isEmpty()) {
            return "ninguno";
        }
        return derrotados.toString();
    }
}