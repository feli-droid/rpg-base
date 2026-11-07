CREATE DATABASE IF NOT EXISTS rpg_grupal_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE rpg_grupal_db;

CREATE TABLE IF NOT EXISTS heroes (
    -- Identificador: lo genera MySQL (AUTO_INCREMENT); el programa lo recupera con getGeneratedKeys()
    id           INT         NOT NULL AUTO_INCREMENT,

    -- Maximo 15 caracteres, igual que el limite de nombre que ya usa el juego (Consola.leerNombre)
    nombre       VARCHAR(15) NOT NULL,

    -- Solo dos tipos validos, guardados en mayusculas
    tipo         VARCHAR(10) NOT NULL,

    vida_maxima  INT         NOT NULL,
    nivel        INT         NOT NULL DEFAULT 1,

    CONSTRAINT pk_heroes        PRIMARY KEY (id),
    -- Nombre unico (con esta collation tampoco se repite cambiando mayusculas: "Thor" = "thor")
    CONSTRAINT uq_heroes_nombre UNIQUE (nombre),
    CONSTRAINT ck_heroes_tipo   CHECK (tipo IN ('GUERRERO', 'MAGO')),
    CONSTRAINT ck_heroes_vida   CHECK (vida_maxima > 0),
    CONSTRAINT ck_heroes_nivel  CHECK (nivel >= 1)
) ENGINE = InnoDB;

-- Datos de ejemplo (opcionales, quitar el comentario si se quiere tener heroes de entrada):
-- INSERT INTO heroes (nombre, tipo, vida_maxima, nivel) VALUES
--     ('Aquiles', 'GUERRERO', 150, 3),
--     ('Merlina', 'MAGO',       90, 2);