package com.rpg.concurrency;

public class DemoHiloDaemon {

    public static void main(String[] args) {
        long inicio = System.currentTimeMillis();

        // 1) Hilo daemon con un bucle que nunca termina por si solo
        Thread daemon = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                System.out.printf("[daemon]  sigo vivo (t=%d ms)%n", System.currentTimeMillis() - inicio);
            }
        }, "demo-daemon");
        daemon.setDaemon(true);               // ANTES de start()
        daemon.start();
        System.out.println("daemon.isDaemon() = " + daemon.isDaemon());

        // 2) setDaemon despues de start(): error
        try {
            daemon.setDaemon(false);
            System.out.println("ERROR: no deberia llegar aqui");
        } catch (IllegalThreadStateException e) {
            System.out.println("setDaemon(false) despues de start() -> IllegalThreadStateException (esperado)");
        }

        // 3) Hilo de usuario: mantiene viva la JVM aunque main termine antes
        Thread usuario = new Thread(() -> {
            for (int i = 1; i <= 3; i++) {
                try {
                    Thread.sleep(700);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                System.out.printf("[usuario] trabajando %d/3 (t=%d ms)%n", i, System.currentTimeMillis() - inicio);
            }
            System.out.println("[usuario] termino: queda SOLO el daemon, asi que la JVM se cierra ahora mismo.");
        }, "demo-usuario");
        usuario.start();

        System.out.println("[main]    termina aqui; la JVM sigue porque queda un hilo de usuario.");
    }
}