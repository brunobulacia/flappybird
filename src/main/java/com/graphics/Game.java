package com.graphics;

import org.lwjgl.opengl.GL;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.lwjgl.glfw.GLFW.*;

public class Game {

    // dimensiones de ventana
    private static final int WIDTH = 900;
    private static final int HEIGHT = 700;

    // dificultad progresiva
    private static final float BASE_SPEED = 0.62f; // 0.62f;
    private static final float MAX_SPEED = 1.50f;
    private static final float BASE_SPAWN_INTERVAL = 1.5f;
    private static final float MIN_SPAWN_INTERVAL = 0.7f;

    // rango de altura del centro del hueco
    private static final float GAP_MIN_Y = -0.42f;
    private static final float GAP_MAX_Y = 0.42f;

    // objetos principales
    private long window;
    private Renderer renderer;
    private InputManager input;
    private SoundManager sound;

    // estado del juego
    private Bird[] birds = new Bird[3];
    private List<Pipe> pipes;
    private float spawnTimer;
    private Random random;
    private GameState state;
    private boolean gameOverSoundPlayed;

    // =========================================================
    // API pública
    // =========================================================

    public void run() {
        init();
        resetGame();
        loop();
        cleanup();
    }

    // =========================================================
    // Inicialización
    // =========================================================

    private void init() {
        if (!glfwInit())
            throw new RuntimeException("GLFW no pudo inicializarse");

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE); // requerido en macOS
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

        window = glfwCreateWindow(WIDTH, HEIGHT, "Flappy Bird OpenGL", 0, 0);
        if (window == 0)
            throw new RuntimeException("No se pudo crear la ventana");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // vsync
        glfwShowWindow(window);

        GL.createCapabilities();

        renderer = new Renderer();
        renderer.init();

        input = new InputManager(window);
        sound = new SoundManager();
        random = new Random();
        pipes = new ArrayList<>();
    }

    // =========================================================
    // Reset
    // =========================================================

    public void resetGame() {
        birds[0] = new Bird(0);
        birds[1] = new Bird(1);
        birds[2] = new Bird(2); // pájaro gris para mostrar vidas restantes
        pipes.clear();
        spawnTimer = 0f;
        state = GameState.START;
        gameOverSoundPlayed = false;
        updateWindowTitle();
    }

    // =========================================================
    // Bucle principal
    // =========================================================

    private void loop() {
        long lastTime = System.nanoTime();

        while (!glfwWindowShouldClose(window)) {
            long now = System.nanoTime();
            float dt = (now - lastTime) / 1_000_000_000f;
            lastTime = now;
            if (dt > 0.033f)
                dt = 0.033f;

            input.update();
            processInput();

            if (state == GameState.PLAYING) {
                update(dt);
            }

            renderer.render(birds, pipes, state, getSpeed());

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    // =========================================================
    // Input
    // =========================================================

    private void processInput() {
        if (input.escapePressed) {
            glfwSetWindowShouldClose(window, true);
            return;
        }

        // reiniciar desde GAME_OVER
        if (state == GameState.GAME_OVER && input.restartPressed) {
            resetGame();
            return; // esperar el siguiente frame para recibir saltos
        }

        // salto jugador 1 (SPACE)
        if (input.player1Jumped) {
            if (state == GameState.START)
                state = GameState.PLAYING;
            birds[0].jump();
            if (birds[0].alive)
                sound.playJump();
        }

        // salto jugador 2 (W o flecha arriba)
        if (input.player2Jumped) {
            if (state == GameState.START)
                state = GameState.PLAYING;
            birds[1].jump();
            if (birds[1].alive)
                sound.playJump();
        }

        if (input.player3Jumped) {
            if (state == GameState.START)
                state = GameState.PLAYING;
            birds[2].jump();
            if (birds[2].alive)
                sound.playJump();
        }
    }

    // =========================================================
    // Actualización de lógica
    // =========================================================

    private void update(float dt) {
        // física de pájaros
        for (Bird bird : birds) {
            bird.update(dt);
        }

        // spawn de tuberías
        spawnTimer += dt;
        if (spawnTimer >= getSpawnInterval()) {
            spawnTimer = 0f;
            float gapY = GAP_MIN_Y + random.nextFloat() * (GAP_MAX_Y - GAP_MIN_Y);
            pipes.add(new Pipe(Pipe.SPAWN_X, gapY));
        }

        // mover tuberías, colisiones y puntaje
        List<Pipe> toRemove = new ArrayList<>();
        for (Pipe pipe : pipes) {
            pipe.x -= getSpeed() * dt;

            for (int i = 0; i < 3; i++) {
                Bird bird = birds[i];

                // colisión AABB
                if (bird.alive && bird.collidesWith(pipe)) {
                    bird.hit();
                }

                // puntaje: la tubería pasó la posición X del pájaro
                if (!pipe.scored[i] && pipe.x + Pipe.W * 0.5f < Bird.X) {
                    pipe.scored[i] = true;
                    if (bird.alive) {
                        bird.score++;
                        sound.playScore();
                    }
                }
            }

            if (pipe.x + Pipe.W * 0.5f < Pipe.REMOVE_X) {
                toRemove.add(pipe);
            }
        }
        pipes.removeAll(toRemove);

        boolean alguienGano = false;
        for (Bird bird : birds) {
            if (bird.score >= 5) {
                alguienGano = true;
                break;
            }
        }
        if (alguienGano || (!birds[0].alive && !birds[1].alive && !birds[2].alive)) {
            if (state != GameState.GAME_OVER && !gameOverSoundPlayed) {
                sound.playGameOver();
                gameOverSoundPlayed = true;
            }
            state = GameState.GAME_OVER;
        }

        updateWindowTitle();
    }

    // =========================================================
    // Dificultad progresiva (req 2.3)
    // =========================================================

    public float getSpeed() {
        int maxScore = Math.max(birds[0].score, Math.max(birds[1].score, birds[2].score));
        // crece 0.022 unidades por punto hasta el límite
        return Math.min(BASE_SPEED + maxScore * 0.022f, MAX_SPEED);
    }

    // =========================================================
    // Intervalo de spawn progresivo (req 2.3)
    // =========================================================

    private float getSpawnInterval() {
        int maxScore = Math.max(birds[0].score, Math.max(birds[1].score, birds[2].score));
        // se acorta 0.016s por punto hasta el mínimo
        return Math.max(BASE_SPAWN_INTERVAL - maxScore * 0.016f, MIN_SPAWN_INTERVAL);
    }

    // =========================================================
    // Título de ventana como HUD de respaldo (req 2.3)
    // =========================================================

    private void updateWindowTitle() {
        String p1 = birds[0].alive ? "vivo" : "muerto";
        String p2 = birds[1].alive ? "vivo" : "muerto";
        String p3 = birds[2].alive ? "vivo" : "muerto";
        String vel = String.format("%.1fx", getSpeed() / BASE_SPEED);

        String title = switch (state) {
            case START -> "Flappy Bird | P1: SPACE  P2: W/↑ | para empezar";
            case PLAYING -> String.format(
                    "Flappy Bird | P1: %d pts (%s)  P2: %d pts (%s)  P3: %d pts (%s) | Vel: %s | Vidas: P1=%d P2=%d P3=%d",
                    birds[0].score, p1, birds[1].score, p2, birds[2].score, p3, vel, birds[0].lives, birds[1].lives,
                    birds[2].lives);
            case GAME_OVER -> String.format(
                    "Flappy Bird | P1: %d  P2: %d  P3: %d | GAME OVER — R/SPACE/W para reiniciar",
                    birds[0].score, birds[1].score, birds[2].score);
        };
        glfwSetWindowTitle(window, title);
    }

    // =========================================================
    // Cleanup
    // =========================================================

    private void cleanup() {
        sound.cleanup();
        renderer.cleanup();
        glfwDestroyWindow(window);
        glfwTerminate();
    }
}
