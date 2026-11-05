package com.rpg.persistencia;

import com.rpg.entities.EstadoEfecto;
import com.rpg.entities.Gremio;
import com.rpg.entities.Guerrero;
import com.rpg.entities.Mago;
import com.rpg.entities.Personaje;
import com.rpg.entities.enums.TipoEfecto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DemoSerializacion {

    public static void main(String[] args) throws Exception {
        provocarNotSerializable();
        comprobarTransient();
        comprobarCiclo();
        rechazarObjetoHostil();
    }

    /** Un objeto Serializable que guarda dentro otro que NO lo es. */
    private static class MochilaRota implements Serializable {
        private static final long serialVersionUID = 1L;
        private final List<Object> items = new ArrayList<>();
    }

    private static class Gema {                       // NO implementa Serializable
        final String nombre = "Rubi";
    }

    private static class GemaSerializable implements Serializable {   // la correccion
        private static final long serialVersionUID = 1L;
        final String nombre = "Rubi";
    }

    private static void provocarNotSerializable() throws Exception {
        System.out.println("--- 1) NotSerializableException por un objeto anidado ---");
        MochilaRota mochila = new MochilaRota();
        mochila.items.add(new Gema());
        try {
            serializar(mochila);
            System.out.println("ERROR: no deberia llegar aqui");
        } catch (NotSerializableException e) {
            System.out.println("Provocada: NotSerializableException: " + e.getMessage());
            System.out.println("-> La mochila SI es Serializable, pero la Gema que lleva dentro no: se guarda TODO el grafo.");
        }
        mochila.items.clear();
        mochila.items.add(new GemaSerializable());
        System.out.println("Resuelta: con Gema implements Serializable se guardan " + serializar(mochila).length + " bytes.\n");
    }


    private static void comprobarTransient() throws Exception {
        System.out.println("--- 2) Campo transient: vuelve a su valor por defecto ---");
        Guerrero heroe = new Guerrero("Thor");
        heroe.aplicarEfecto(new EstadoEfecto("Veneno", TipoEfecto.DANIO_CONTINUO, 4, 5));
        System.out.printf("Antes de guardar:  efectos activos = %d | hilos de efecto = %d%n",
                heroe.getEfectos().size(), heroe.cantidadHilosEfecto());

        // Los hilos (Thread) NO son Serializable: por eso Personaje.hilosEfectos es transient. Sin 'transient'
        // aqui saltaria NotSerializableException: java.lang.Thread.
        Guerrero cargado = (Guerrero) deserializar(serializar(heroe));
        System.out.printf("Despues de cargar: efectos activos = %d (se guardaron) | hilos de efecto = %d (transient: vuelve a 0)%n",
                cargado.getEfectos().size(), cargado.cantidadHilosEfecto());
        cargado.reanudarEfectos();
        System.out.printf("Tras reanudarEfectos(): hilos de efecto = %d (se recrean a partir de los efectos guardados)%n%n",
                cargado.cantidadHilosEfecto());
        heroe.cancelarEfectos();
        cargado.cancelarEfectos();
    }

    private static void comprobarCiclo() throws Exception {
        System.out.println("--- 3) Ciclo Gremio <-> Personaje tras cargar ---");
        Gremio gremio = new Gremio("Guardianes del Alba");
        Personaje aquiles = new Guerrero("Aquiles");
        Personaje merlin = new Mago("Merlin");
        aquiles.unirseAGremio(gremio);
        merlin.unirseAGremio(gremio);

        Gremio copia = (Gremio) deserializar(serializar(gremio));
        boolean tamanio = copia.getMiembros().size() == 2;
        boolean refVuelta = copia.getMiembros().stream().allMatch(m -> m.getGremio() == copia);
        System.out.println("Miembros tras cargar: " + copia.getMiembros().size() + " (esperado 2) -> " + (tamanio ? "OK" : "FALLO"));
        System.out.println("Cada personaje apunta al gremio CARGADO (no al original): " + (refVuelta ? "OK" : "FALLO"));
        System.out.println("La copia es otro objeto distinto del original: " + (copia != gremio) + "\n");
    }

    private static void rechazarObjetoHostil() throws Exception {
        System.out.println("--- 4) Filtro de seguridad (ObjectInputFilter) ---");
        byte[] hostil = serializar(new AtomicInteger(1));      // clase ajena al juego
        try (ObjectInputStream entrada = new ObjectInputStream(new ByteArrayInputStream(hostil))) {
            entrada.setObjectInputFilter(GestorPartida.filtro());
            entrada.readObject();
            System.out.println("ERROR: el filtro dejo pasar una clase ajena");
        } catch (InvalidClassException e) {
            System.out.println("Rechazado por el filtro: " + e.getMessage());
        }
    }

    private static byte[] serializar(Object objeto) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream salida = new ObjectOutputStream(bytes)) {
            salida.writeObject(objeto);
        }
        return bytes.toByteArray();
    }

    private static Object deserializar(byte[] datos) throws IOException, ClassNotFoundException {
        try (ObjectInputStream entrada = new ObjectInputStream(new ByteArrayInputStream(datos))) {
            return entrada.readObject();
        }
    }
}