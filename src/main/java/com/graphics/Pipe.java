package com.graphics;

public class Pipe {

    public static final float W = 0.18f; // Ancho de la tubería
    public static final float GAP_H = 0.48f; // Altura del espacio entre tuberías (gap)
    public static final float SPAWN_X = 1.2f; // Posición X donde aparecen las tuberías (fuera de pantalla derecha)
    public static final float REMOVE_X = -1.4f; // Posición X donde se eliminan las tuberías (fuera de pantalla
                                                // izquierda)

    public float x;
    public float gapCenterY;

    // registro independiente de puntaje para cada jugador
    public boolean[] scored = new boolean[3]; // scored[0] para jugador 1, scored[1] para jugador 2, scored[2] para
                                              // jugador 3

    public Pipe(float x, float gapCenterY) {
        this.x = x;
        this.gapCenterY = gapCenterY;
    }
}
