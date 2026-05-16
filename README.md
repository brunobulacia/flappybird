# Flappy Bird — Primer Parcial Programación Gráfica

## Integrante

| Nombre | Usuario Git |
|--------|-------------|
| Bulacia | Bulacia |

---

## Controles

| Acción | Jugador 1 | Jugador 2 |
|--------|-----------|-----------|
| Saltar / iniciar | `SPACE` | `W` o `↑` |
| Salir | `ESC` | `ESC` |
| Reiniciar (game over) | `SPACE` o `R` | `W` o `↑` |

---

## Compilación y ejecución

**Requisitos:** Java 17+, Maven 3.6+

```bash
# Clonar e ingresar al directorio
cd code

# Compilar
mvn compile

# Ejecutar
mvn exec:exec
```

> En macOS el flag `-XstartOnFirstThread` ya está configurado en `pom.xml`.

---

## Cambios respecto a la versión base

### 1. Pájaro geométrico (`Bird.java`, `Renderer.java`)
Reemplazó el rectángulo único por un personaje compuesto de 6 partes:
- **Cuerpo** — rectángulo principal con color por jugador (amarillo J1, azul J2)
- **Cola** — triángulo rotado 180° en el extremo izquierdo
- **Ala** — rectángulo inferior animado con `sin(wingAnim × 9)`, se reinicia en cada salto
- **Pico** — triángulo naranja en el extremo derecho
- **Ojo** — rectángulo blanco + pupila oscura (se convierte en X al morir)
- **Inclinación** — ángulo proporcional a `velY` mediante `getTilt()`

### 2. Dos jugadores simultáneos (`Game.java`, `InputManager.java`)
- Array `birds[2]` con posición, velocidad, estado y puntaje independientes
- Las tuberías son compartidas; cada una registra si ya sumó punto a cada jugador
- La partida continúa mientras al menos un jugador esté vivo
- Puntajes diferenciados por color en el HUD

### 3. Dificultad progresiva (`Game.java`)
- Velocidad: `BASE_SPEED(0.62) + score × 0.022`, tope en `MAX_SPEED(1.50)`
- Intervalo de spawn: `1.5 s − score × 0.016`, mínimo `0.7 s`
- Nivel actual visible en el título de la ventana (`Vel: 1.3x`)

### 4. Mejoras de interfaz (`Renderer.java`, `SoundManager.java`)
- **Fondo** con degradado cielo usando shader GLSL (uniforms `uColor` / `uColor2`)
- **Nubes** en 6 capas con gradientes (sombra, base, picos, highlight)
- **Suelo** con franjas degradadas
- **Tuberías** con gradiente verde + franja de reflejo
- **HUD** con display de 7 segmentos para el puntaje de cada jugador
- **Pantalla de inicio** y **pantalla de game over** con texto en fuente de píxeles propia
- **Sonidos** sintetizados por PCM (`javax.sound`): salto (sweep ascendente), punto (dos notas), game over (sweep descendente), sin archivos de audio externos
