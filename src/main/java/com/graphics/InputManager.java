package com.graphics;

import static org.lwjgl.glfw.GLFW.*;

public class InputManager {

    private final long window;

    // estados previos para detección de flanco (evita repetición)
    private boolean prevSpace, prevW, prevUp, prevR, prevZ;

    // acciones detectadas en el frame actual
    public boolean player1Jumped;
    public boolean player2Jumped;
    public boolean player3Jumped;
    public boolean restartPressed;
    public boolean escapePressed;

    public InputManager(long window) {
        this.window = window;
    }

    public void update() {
        boolean spaceNow = key(GLFW_KEY_SPACE);
        boolean wNow = key(GLFW_KEY_W);
        boolean upNow = key(GLFW_KEY_UP);
        boolean rNow = key(GLFW_KEY_R);
        boolean zNow = key(GLFW_KEY_Z);

        // flanco de subida: estaba suelto y ahora está presionado
        player1Jumped = spaceNow && !prevSpace;
        player2Jumped = (wNow && !prevW) || (upNow && !prevUp);
        player3Jumped = zNow && !prevZ; // para el tercer jugador
        restartPressed = (rNow && !prevR) || player1Jumped || player2Jumped || player3Jumped;
        escapePressed = key(GLFW_KEY_ESCAPE);

        prevSpace = spaceNow;
        prevW = wNow;
        prevUp = upNow;
        prevR = rNow;
    }

    private boolean key(int code) {
        return glfwGetKey(window, code) == GLFW_PRESS;
    }
}
