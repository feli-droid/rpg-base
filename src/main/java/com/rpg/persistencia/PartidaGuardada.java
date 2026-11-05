package com.rpg.persistencia;

import com.rpg.entities.Gremio;
import com.rpg.entities.PerfilJugador;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;


public class PartidaGuardada implements Serializable {

    private static final long serialVersionUID = 1L;

    private final PerfilJugador perfil;
    private final Gremio gremio;
    private final LinkedHashMap<String, Integer> derrotados;
    private final String fecha;

    public PartidaGuardada(PerfilJugador perfil, Gremio gremio, Map<String, Integer> derrotados) {
        this.perfil = perfil;
        this.gremio = gremio;
        this.derrotados = new LinkedHashMap<>(derrotados);
        this.fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public PerfilJugador getPerfil() {
        return perfil;
    }

    public Gremio getGremio() {
        return gremio;
    }

    public Map<String, Integer> getDerrotados() {
        return derrotados;
    }

    public String getFecha() {
        return fecha;
    }
}