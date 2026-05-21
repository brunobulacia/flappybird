package com.graphics;

public class Bird {

    // posición X fija en NDC (ambos jugadores comparten la misma columna)
    public static final float X = -0.45f; // -0.45f;
    public static final float W = 0.10f;
    public static final float H = 0.10f;

    // física
    static float GRAVITY = -1.9f;
    static float JUMP_IMPULSE = 0.85f;
    static final float MAX_FALL_SPEED = -1.8f;

    // límite inferior de pantalla (encima del suelo visual)
    static final float FLOOR_Y = -0.78f;

    // colores por jugador: [0]=amarillo (J1), [1]=azul (J2)
    public static final float[][] COLORS = {
            { 0.98f, 0.85f, 0.20f }, // amarillo
            { 0.20f, 0.60f, 0.98f }, // azul
            { 0.20f, 0.98f, 0.20f }, // verde
            { 0.6f, 0.6f, 0.6f } // gris
    };

    public float y;
    public float velY;
    public boolean alive;
    public int score;
    public float wingAnim; // acumulador de tiempo para animación de ala
    public float deathTimer; // segundos que el pájaro sigue visible tras morir
    public float invincibleTimer; // segundos de invencibilidad tras recibir un golpe
    public int playerIndex;
    public int lives;

    public Bird(int playerIndex) {
        this.playerIndex = playerIndex;
        reset();
    }

    public void reset() {
        // posiciones Y iniciales separadas para que no se superpongan
        y = (playerIndex == 0) ? 0.12f : -0.12f;
        if (playerIndex == 2)
            y = 0f;
        velY = 0f;
        alive = true;
        score = 0;
        wingAnim = 0f;
        deathTimer = 0f;
        invincibleTimer = 0f;
        lives = 1;
        GRAVITY = -1.9f;
        JUMP_IMPULSE = 0.85f;
    }

    /** Mata al pájaro e inicia el contador de visibilidad post-muerte. */
    public void kill() {
        if (!alive)
            return;
        alive = false;
        deathTimer = 3.0f; // visible 3 segundos después de morir
    }

    /**
     * Recibe un golpe: consume una vida con invencibilidad temporal,
     * o muere definitivamente si ya no quedan vidas.
     */
    public void hit() {
        if (!alive || invincibleTimer > 0f)
            return;
        if (lives > 1) {
            lives--;
            invincibleTimer = 1.5f; // 1.5s sin poder recibir daño
        } else {
            kill();
        }
    }

    /** Devuelve true mientras el pájaro deba dibujarse. */
    public boolean isVisible() {
        return alive || deathTimer > 0f;
    }

    public void jump() {
        if (!alive)
            return;
        // si la gravedad está invertida, el impulso va hacia abajo
        velY = (GRAVITY < 0) ? JUMP_IMPULSE : -JUMP_IMPULSE;
        wingAnim = 0f; // reinicia animación de ala en cada salto
    }

    public void update(float dt) {
        if (!alive) {
            // cuenta regresiva de visibilidad post-muerte
            if (deathTimer > 0f)
                deathTimer -= dt;
            return;
        }

        // PARA AÑADIR LA MECANICA DE QUE SE INVIERTA LA GRAVEDAD EN CIERTOS PUNTOS
        // if (score == 5) {
        // GRAVITY = GRAVITY * -1;
        // } else if (score == 8) {
        // GRAVITY = Math.abs(GRAVITY) * -1;
        // }
        velY += GRAVITY * dt;
        // cuando la gravedad se invierte, los límites de velocidad también se invierten
        if (GRAVITY < 0)
            velY = clamp(velY, MAX_FALL_SPEED, JUMP_IMPULSE);
        else
            velY = clamp(velY, -JUMP_IMPULSE, -MAX_FALL_SPEED);
        y += velY * dt;

        wingAnim += dt;
        if (invincibleTimer > 0f)
            invincibleTimer -= dt;

        // colisión con techo y suelo
        if (y + H * 0.5f >= 1.0f || y - H * 0.5f <= FLOOR_Y) {
            y = clamp(y, FLOOR_Y + H * 0.5f, 1.0f - H * 0.5f);
            velY = -velY * 0.5f;
            hit();
        }
    }

    public boolean collidesWith(Pipe pipe) {
        if (!alive)
            return false;

        float bLeft = X - W * 0.5f;
        float bRight = X + W * 0.5f;
        float bBottom = y - H * 0.5f;
        float bTop = y + H * 0.5f;

        float pLeft = pipe.x - Pipe.W * 0.5f;
        float pRight = pipe.x + Pipe.W * 0.5f;

        // sin solapamiento horizontal → no hay colisión
        if (!(bRight > pLeft && bLeft < pRight))
            return false;

        float gapTop = pipe.gapCenterY + Pipe.GAP_H * 0.5f;
        float gapBottom = pipe.gapCenterY - Pipe.GAP_H * 0.5f;

        // colisiona si está fuera del hueco
        return bTop > gapTop || bBottom < gapBottom;
    }

    // inclinación en radianes según velocidad vertical
    public float getTilt() {
        return clamp(velY * 0.40f, -(float) Math.PI / 2.2f, (float) Math.PI / 6f);
    }

    public float[] getColor() {
        return COLORS[playerIndex];
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
