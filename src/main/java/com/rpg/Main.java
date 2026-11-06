package com.rpg;

import com.rpg.view.HeroeUI;

import java.util.Scanner;

/** Punto de entrada: unicamente arranca la aplicacion llamando a la View. */
public class Main {

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            new HeroeUI(scanner).iniciar();
        }
    }
}