# Etapa 1 · Concurrencia

## Cómo aplicarla
Copia el contenido de `src/` sobre el `src/` de tu proyecto (respeta las carpetas: nada cambia de lugar).
Los archivos de abajo **reemplazan** a los tuyos; los demás (`Arma`, `Gremio`, `Pocion`, etc.) no se tocan en esta etapa.

> Tus `.class` no traen comentarios ni Javadoc, así que los archivos reemplazados los reconstruí desde el código
> compilado y los comenté de nuevo. Guarda una copia (o haz commit) antes de pisar tus archivos.

| Nuevos | Modificados |
|---|---|
| `concurrency/TemporizadorTurnoTask`, `GestorTurno`, `EfectoContinuoTask`, `AutoGuardadoDaemon` | `core/MotorJuego`, `TorneoManager`, `Arena`, `RegistroCombate`, `Consola`, `Accion` |
| `core/TurnoCallback` | `entities/Personaje`, `EstadoEfecto` |
| `concurrency/DemoCondicionCarrera`, `DemoHiloDaemon` (investigación) | `Main` |

## Dónde está cada punto del checklist
| Sección | Dónde |
|---|---|
| 1. Temporizador de turno | `TurnoCallback`, `TemporizadorTurnoTask` (cuenta atrás + `sleep(1000)` + callback), `GestorTurno` (crea el `Thread`, `start()`, `cancelar()` con `interrupt()`), integrado en `MotorJuego.pedirAccion()` |
| 2. Efectos DoT/HoT | `EfectoContinuoTask` (`while(!isInterrupted())`, catch → limpiar + `interrupt()` + `break`) y `Personaje.aplicarEfecto/quitarEfecto/cancelarEfectos`. `procesarEfectos` ya no existe |
| 3. Torneo en paralelo | `TorneoManager.jugarTorneo()`: dos `Thread`, `start()`, `join()` A y B protegidos, `getState()` antes / durante / después |
| 4. Auto-guardado | `AutoGuardadoDaemon` (`while(true)` + `sleep`, `setDaemon(true)` antes de `start()`), guarda en memoria con `MotorJuego.autoGuardar()` |
| 6. Carrera / `synchronized` | `Personaje` (`recibirDanio`, `curar`, `setVida`, estado y efectos), `RegistroCombate`, `EstadoEfecto`; `getEfectos()` devuelve copia |
| Investigación | `DemoHiloDaemon` y `DemoCondicionCarrera` (ejecutables, con las explicaciones en el Javadoc) |

## Decisiones de diseño que conviene conocer
- **`nextLine()` no se puede interrumpir.** Si se agota el tiempo, el aviso sale al instante, pero el turno se pierde en cuanto el jugador pulsa ENTER (`Consola.leerEntero` con un `BooleanSupplier` para no pedir un número válido otra vez).
- **Los efectos ahora corren en tiempo real** (un tick por segundo), así que en los duelos automáticos añadí 500 ms de pausa por ronda (`Arena.duelo`). Sin pausa, el duelo termina en microsegundos y el veneno nunca llegaría a hacer efecto; además esa pausa es lo que permite ver `TIMED_WAITING`.
- Los hilos de efecto y del temporizador son **daemon**: si el juego se cierra a mitad de combate, la JVM no se queda esperando.
- Si un efecto del mismo nombre se vuelve a aplicar, se interrumpe el hilo anterior y arranca uno nuevo (nunca dos hilos para el mismo efecto, nunca `start()` dos veces).

## Explicaciones que pide el README
**¿Por qué `synchronized`?** `recibirDanio` no es una sola operación: lee la vida, resta, acota a `0..vidaMaxima` y, si llega a 0, cambia `estado` a `MUERTO`. Son varias variables que deben cambiar juntas; `synchronized` convierte el bloque en una unidad indivisible y se libera solo, incluso con excepciones.

**¿Por qué `AtomicInteger.addAndGet(-danio)` no basta?** Es atómico solo para *un* entero: no acota la vida (la demo la deja en **-100**) y no puede cambiar `estado` en el mismo paso. `updateAndGet(v -> Math.max(0, v - d))` arregla el acotado, pero `estado` sigue siendo una segunda variable fuera de la operación.

**`ReentrantLock` vs `synchronized`.** Lo prefiero cuando necesito `tryLock()`, `lockInterruptibly()`, un lock justo o varias `Condition`. A cambio, el `unlock()` debe ir siempre en un `finally`; si no, una excepción deja el lock tomado para siempre. (Versión reescrita en `DemoCondicionCarrera.VidaConReentrantLock`.)

**Hilo de usuario vs daemon.** La JVM no termina mientras quede un hilo de usuario vivo; cuando solo quedan daemons, se cierra y los mata sin limpieza. Por eso el auto-guardado (prescindible) es daemon y un combate no lo sería.

**Ciclo de vida que se ve en el torneo.** `NEW` (creado, sin `start()`) → `RUNNABLE` (ejecutando) → `BLOCKED` (puntual: esperando el monitor de `RegistroCombate` para imprimir) → `TIMED_WAITING` (dentro de `Thread.sleep` de la pausa entre rondas) → `TERMINATED` (el `run()` acabó, tras el `join()`).

## Evidencias (cómo lo probé)
- `java -cp out com.rpg.concurrency.DemoCondicionCarrera` → sin protección se perdieron **25 591** actualizaciones de 400 000; con `synchronized` y con `ReentrantLock`, resultado exacto.
- `java -cp out com.rpg.concurrency.DemoHiloDaemon` → la JVM termina a los ~2,1 s aunque el daemon sigue en su bucle infinito; `setDaemon(false)` tras `start()` lanza `IllegalThreadStateException`.
- Temporizador: esperando 17 s → “Se acabo el tiempo… Thor tardo demasiado y pierde el turno”; respondiendo a tiempo, no hay aviso.
- Efectos: antídoto interrumpe el hilo del veneno (0 hilos después y la vida deja de bajar); muerte por veneno y fin de combate dejan 0 hilos; dos DoT + 50 000 golpes simultáneos → vida exacta.
- Salir del juego con el daemon activo cierra en ~0,1 s.
- # Etapa 2 · Persistencia (archivos)

## Cómo aplicarla
**Aplica primero la etapa 1** y después copia el contenido de `src/` de esta etapa encima (varios archivos de la etapa 1
se vuelven a reemplazar: `Main`, `MotorJuego`, `Personaje`, `EstadoEfecto`, `AutoGuardadoDaemon`).

| Nuevos (`com.rpg.persistencia`) | Modificados |
|---|---|
| `GestorArchivos`, `LogCombate`, `PartidaGuardada`, `GestorPartida` | `entities/`: `Personaje`, `Guerrero`, `Mago`, `Enemigo`, `Arma`, `Gremio`, `EstadoEfecto`, `PerfilJugador` |
| `DemoArchivos`, `DemoSerializacion` (investigación) | `items/`: `Inventario`, `Pocion` |
| | `core/`: `MotorJuego`, `Bestiario` · `concurrency/AutoGuardadoDaemon` · `Main` |

## Archivos que crea el juego (carpeta `partidas/`, relativa al directorio de trabajo)
- `combate.log`: historial de texto; cada combate/torneo se **agrega** al final.
- `partida.dat`: partida guardada a propósito (opción 4).
- `autoguardado.dat`: lo escribe el hilo daemon. Es un archivo **aparte** para que el auto-guardado nunca pise la partida que guardó el jugador. Solo se escribe si ya hay progreso.

Menú nuevo: `4` Guardar · `5` Cargar (partida o autoguardado) · `6` Ver historial · `7` Información de archivos · `8` Borrar (con confirmación).
Tras cargar, `Combate rápido` ofrece **continuar con un héroe del gremio guardado** (con su vida, pociones y efectos tal como estaban).

## Dónde está cada punto del checklist
| Sección | Dónde |
|---|---|
| 1. `File` | `GestorArchivos` (`mkdirs`, `createNewFile` con su `IOException`, `describir` → `exists/length/getName/getAbsolutePath`); opción 7 del menú; `DemoArchivos` (relativa vs absoluta) |
| 2. Escritura | `LogCombate.guardar` (`BufferedWriter` + `FileWriter(archivo, agregar)`, `newLine()`, `flush()`); `DemoArchivos` muestra append vs sobrescribir |
| 3. Lectura | `LogCombate.mostrar` (`while ((linea = lector.readLine()) != null)`, `FileNotFoundException` con mensaje claro); opción 6 |
| 4. Borrado | `GestorArchivos.borrarConFile` (`File.delete()`) y `borrarConNio` (`Files.deleteIfExists`); opción 8 con confirmación |
| 5. Serialización | `Serializable` + `serialVersionUID` en todas las clases del dominio; `transient` en `Personaje.hilosEfectos` y `observador`; `GestorPartida.guardar/cargar`; `DemoSerializacion` (NotSerializableException, transient, ciclo) |
| 6. try-with-resources | Cada flujo es un recurso (`FileOutputStream` + `BufferedOutputStream` + `ObjectOutputStream`, y análogo al leer); `catch` específicos en `GestorPartida`; ningún `close()` manual ni `catch` vacío |
| 7. Concurrencia | `AutoGuardadoDaemon` → `MotorJuego.autoGuardarEnDisco()`; un único candado (`GestorArchivos.CANDADO`) para todo acceso a archivos; `MotorJuego.cerrar()` al salir |

## Cómo se evita el archivo “a medio escribir”
1. Se escribe en `autoguardado.dat.tmp` y, cuando está completo y cerrado, se **renombra** sobre el definitivo (`ATOMIC_MOVE`). Si algo corta la escritura, el archivo real anterior queda intacto.
2. Al salir, `Main` llama a `MotorJuego.cerrar()` → `GestorPartida.cerrar()`: toma el mismo candado (así **espera** a que termine una escritura en curso) y marca “cerrado” para que el daemon no empiece otra.
3. Orden de locks fijo (`candadoEstado` → `CANDADO`), nunca al revés: sin riesgo de interbloqueo.
4. Mientras se serializa, `Personaje.writeObject` es `synchronized`: un hilo de efecto no puede modificar al personaje a mitad de la escritura.

## Explicaciones que pide el README
**`serialVersionUID`.** Es el “número de versión” de la clase. Se escribe junto a los datos y, al cargar, Java lo compara con el de la clase actual; si difieren lanza `InvalidClassException`. Si no se declara, Java lo calcula desde la forma de la clase y cualquier cambio (un método nuevo) invalida las partidas viejas sin avisar. Declarándolo yo decido cuándo una partida antigua deja de ser compatible.

**`transient`.** `Personaje.hilosEfectos` guarda `Thread`s, que no son `Serializable`; sin `transient`, guardar un héroe lanzaría `NotSerializableException: java.lang.Thread`. Tras cargar vale `null` (se recrea vacío) y `reanudarEfectos()` relanza un hilo por cada efecto que sí se guardó. `DemoSerializacion` lo comprueba: efectos 1 → 1, hilos 1 → **0** → 1.

**`File.delete()` vs `Files.deleteIfExists`.** `delete()` devuelve `false` tanto si no existía como si falló, sin decir por qué. `deleteIfExists` devuelve `true` si borró, `false` solo si no existía, y ante un fallo real lanza `IOException` con el motivo (la demo lo enseña con una carpeta no vacía).

**`FileWriter` vs `Files.writeString`.** `FileWriter`/`BufferedWriter` es un flujo con buffer que abres, vas llenando y cierras tú: ideal para un log que crece línea a línea. `Files.writeString` (NIO) abre, escribe todo el texto y cierra en una sola llamada: ideal para texto pequeño que ya tienes completo; sobrescribe salvo que pases `APPEND`.

**Seguridad de `Serializable`.** `readObject` reconstruye objetos y ejecuta código de las clases que el archivo indique. Un `.dat` manipulado puede instanciar clases peligrosas del classpath (cadenas de *gadgets*) o agotar memoria con un grafo enorme. Nunca se deben cargar datos no confiables. Aquí además hay un `ObjectInputFilter` con lista blanca (`com.rpg`, `java.lang`, `java.util`) y límites de tamaño; `DemoSerializacion` muestra un `AtomicInteger` rechazado. Alternativas: JSON o un formato de texto propio, que solo transportan datos y no objetos con comportamiento.

## Evidencias (cómo lo probé)
- Guardar en una ejecución y **cargar en otra JVM**: bestiario `Derrotado: 0` → `1`, victorias/logros restaurados, héroe Thor 111/120 con 3 pociones listo para seguir.
- Archivo con basura, truncado, vacío, inexistente y carpeta `partidas` imposible de crear: el juego siempre avisa y continúa.
- Estrés: el daemon reescribe cada 1 ms mientras el hilo principal lee sin parar → **10 329 lecturas, 0 fallos**; tras `cerrar()` el archivo ya no cambia.
- `kill -9` en plena escritura, 20 veces: **20/20 archivos válidos**.
- `java -cp out com.rpg.persistencia.DemoArchivos` y `DemoSerializacion` muestran todos los experimentos (usan `partidas/demo/` o memoria, y se limpian solos).