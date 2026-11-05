package com.rpg.concurrency;

/**
 * TALLER CONCURRENCIA (Investigacion obligatoria: hilos daemon).
 * Se ejecuta con:  java -cp out com.rpg.concurrency.DemoHiloDaemon
 *
 * Que demuestra:
 *  1) La JVM se cierra aunque el hilo daemon siga vivo (su bucle es infinito y el programa termina igual).
 *  2) setDaemon(true) DESPUES de start() lanza IllegalThreadStateException.
 *  3) Un hilo de usuario SI mantiene viva la JVM: el programa espera a que termine.
 *
 * DIFERENCIA ENTRE HILO DE USUARIO Y HILO DAEMON
 *   - Hilo de usuario (por defecto): la JVM no termina mientras quede al menos uno vivo, aunque el
 *     main ya haya acabado. Sirve para trabajo que DEBE completarse (un combate, una escritura).
 *   - Hilo daemon: es un servicio de fondo. Cuando solo quedan hilos daemon, la JVM se cierra y los
 *     mata sin avisar (no hay finally ni limpieza garantizada). Sirve para tareas prescindibles como el
 *     auto-guardado periodico o un monitor; no debe hacer nada que no pueda interrumpirse en cualquier
 *     momento.
 */
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