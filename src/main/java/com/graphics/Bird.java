package com.graphics;

public class Bird {

    // posición X fija en NDC (ambos jugadores comparten la misma columna)
    public static final float X = -0.45f;
    public static final float W = 0.10f;
    public static final float H = 0.10f;

    // física
    static final float GRAVITY        = -1.9f;
    static final float JUMP_IMPULSE   = 0.85f;
    static final float MAX_FALL_SPEED = -1.8f;

    // límite inferior de pantalla (encima del suelo visual)
    static final float FLOOR_Y = -0.78f;

    // colores por jugador: [0]=amarillo (J1), [1]=azul (J2)
    public static final float[][] COLORS = {
        {0.98f, 0.85f, 0.20f},
        {0.20f, 0.60f, 0.98f}
    };

    public float  y;
    public float  velY;
    public boolean alive;
    public int    score;
    public float  wingAnim;   // acumulador de tiempo para animación de ala
    public int    playerIndex;

    public Bird(int playerIndex) {
        this.playerIndex = playerIndex;
        reset();
    }

    public void reset() {
        // posiciones Y iniciales separadas para que no se superpongan
        y      = (playerIndex == 0) ? 0.12f : -0.12f;
        velY   = 0f;
        alive  = true;
        score  = 0;
        wingAnim = 0f;
    }

    public void jump() {
        if (!alive) return;
        velY     = JUMP_IMPULSE;
        wingAnim = 0f; // reinicia animación de ala en cada salto
    }

    public void update(float dt) {
        if (!alive) return;

        velY += GRAVITY * dt;
        velY  = clamp(velY, MAX_FALL_SPEED, JUMP_IMPULSE);
        y    += velY * dt;

        wingAnim += dt;

        // colisión con techo y suelo
        if (y + H * 0.5f >= 1.0f || y - H * 0.5f <= FLOOR_Y) {
            alive = false;
        }
    }

    public boolean collidesWith(Pipe pipe) {
        if (!alive) return false;

        float bLeft   = X - W * 0.5f;
        float bRight  = X + W * 0.5f;
        float bBottom = y - H * 0.5f;
        float bTop    = y + H * 0.5f;

        float pLeft  = pipe.x - Pipe.W * 0.5f;
        float pRight = pipe.x + Pipe.W * 0.5f;

        // sin solapamiento horizontal → no hay colisión
        if (!(bRight > pLeft && bLeft < pRight)) return false;

        float gapTop    = pipe.gapCenterY + Pipe.GAP_H * 0.5f;
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
