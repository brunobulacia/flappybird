package com.graphics;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Maneja todo el dibujo con OpenGL 3.3 Core Profile.
 *
 * Shaders:
 *  - Vertex: scale → rotate(uAngle) → translate(uOffset); emite vUV (0..1)
 *  - Fragment: mezcla uColor (arriba) con uColor2 (abajo) vía vUV.y, permite gradiente
 *
 * Primitivas: rect() = sólido, rectGrad() = gradiente arriba→abajo, tri() = triángulo sólido
 */
public class Renderer {

    // =========================================================
    // VAO / VBO
    // =========================================================
    private int quadVao, quadVbo;
    private int triVao,  triVbo;

    // =========================================================
    // Shader
    // =========================================================
    private int programa;
    private int locOffset, locScale, locAngle, locColor, locColor2, locAlpha;

    private static final String VERT_SRC = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            uniform vec2  uOffset;
            uniform vec2  uScale;
            uniform float uAngle;
            out vec2 vUV;
            void main() {
                vUV           = aPos.xy + vec2(0.5, 0.5);
                vec2  scaled  = aPos.xy * uScale;
                float c       = cos(uAngle);
                float s       = sin(uAngle);
                vec2  rotated = vec2(c * scaled.x - s * scaled.y,
                                     s * scaled.x + c * scaled.y);
                gl_Position   = vec4(rotated + uOffset, aPos.z, 1.0);
            }
            """;

    // uColor = color del borde superior  |  uColor2 = color del borde inferior
    // rect() pone uColor2 = uColor → sólido. rectGrad() los diferencia → gradiente.
    private static final String FRAG_SRC = """
            #version 330 core
            in vec2 vUV;
            uniform vec3  uColor;
            uniform vec3  uColor2;
            uniform float uAlpha;
            out vec4 fragColor;
            void main() {
                vec3 col  = mix(uColor, uColor2, 1.0 - vUV.y);
                fragColor = vec4(col, uAlpha);
            }
            """;

    // =========================================================
    // Nubes (posiciones fijas)
    // =========================================================
    private static final float[][] CLOUDS = {
        {-0.62f, 0.76f}, { 0.08f, 0.88f}, { 0.68f, 0.72f},
        {-0.20f, 0.60f}, { 0.42f, 0.55f}, {-0.85f, 0.50f}
    };

    // =========================================================
    // Inicialización
    // =========================================================

    public void init() {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        compileShaders();
        buildQuadVao();
        buildTriVao();
    }

    private void compileShaders() {
        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, VERT_SRC);
        glCompileShader(vs);
        if (glGetShaderi(vs, GL_COMPILE_STATUS) == GL_FALSE)
            throw new RuntimeException("Vertex shader: " + glGetShaderInfoLog(vs));

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, FRAG_SRC);
        glCompileShader(fs);
        if (glGetShaderi(fs, GL_COMPILE_STATUS) == GL_FALSE)
            throw new RuntimeException("Fragment shader: " + glGetShaderInfoLog(fs));

        programa = glCreateProgram();
        glAttachShader(programa, vs);
        glAttachShader(programa, fs);
        glLinkProgram(programa);
        if (glGetProgrami(programa, GL_LINK_STATUS) == GL_FALSE)
            throw new RuntimeException("Shader link: " + glGetProgramInfoLog(programa));

        glDeleteShader(vs);
        glDeleteShader(fs);

        locOffset = glGetUniformLocation(programa, "uOffset");
        locScale  = glGetUniformLocation(programa, "uScale");
        locAngle  = glGetUniformLocation(programa, "uAngle");
        locColor  = glGetUniformLocation(programa, "uColor");
        locColor2 = glGetUniformLocation(programa, "uColor2");
        locAlpha  = glGetUniformLocation(programa, "uAlpha");

        if (locOffset < 0 || locScale < 0 || locAngle < 0
                || locColor < 0 || locColor2 < 0 || locAlpha < 0)
            throw new RuntimeException("Uniform no encontrado en el shader");
    }

    private void buildQuadVao() {
        float[] verts = {
            -0.5f, -0.5f, 0f,  0.5f, -0.5f, 0f,  0.5f,  0.5f, 0f,
            -0.5f, -0.5f, 0f,  0.5f,  0.5f, 0f, -0.5f,  0.5f, 0f
        };
        quadVao = glGenVertexArrays();
        glBindVertexArray(quadVao);
        quadVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, quadVbo);
        FloatBuffer b = BufferUtils.createFloatBuffer(verts.length);
        b.put(verts).flip();
        glBufferData(GL_ARRAY_BUFFER, b, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 12, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);
    }

    private void buildTriVao() {
        float[] verts = {
            -0.5f,  0.5f, 0f,
            -0.5f, -0.5f, 0f,
             0.5f,  0.0f, 0f
        };
        triVao = glGenVertexArrays();
        glBindVertexArray(triVao);
        triVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, triVbo);
        FloatBuffer b = BufferUtils.createFloatBuffer(verts.length);
        b.put(verts).flip();
        glBufferData(GL_ARRAY_BUFFER, b, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 12, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);
    }

    // =========================================================
    // Frame principal
    // =========================================================

    public void render(Bird[] birds, List<Pipe> pipes, GameState state, float speed) {
        glClearColor(0.47f, 0.73f, 0.87f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);

        glUseProgram(programa);

        drawBackground();
        for (Pipe pipe : pipes) drawPipe(pipe);
        drawGround();
        for (Bird bird : birds) drawBird(bird);
        drawHUD(birds, speed);
        if (state == GameState.START)     drawStartOverlay();
        if (state == GameState.GAME_OVER) drawGameOverOverlay(birds);
    }

    // =========================================================
    // Fondo (req 2.4) — un solo rectángulo con gradiente
    // =========================================================

    private void drawBackground() {
        // gradiente de cielo: azul claro arriba → azul-verde en el horizonte
        rectGrad(0f, 0.11f, 2.0f, 1.78f, 0f,
                 0.53f, 0.81f, 0.93f,   // arriba (cielo)
                 0.40f, 0.70f, 0.82f,   // abajo  (horizonte)
                 1f);
        for (float[] c : CLOUDS) drawCloud(c[0], c[1]);
    }

    private void drawCloud(float cx, float cy) {
        rect(cx,          cy,          0.20f, 0.07f,  0f, 1f, 1f, 1f, 0.88f);
        rect(cx - 0.07f,  cy - 0.025f, 0.11f, 0.055f, 0f, 1f, 1f, 1f, 0.88f);
        rect(cx + 0.07f,  cy - 0.025f, 0.11f, 0.055f, 0f, 1f, 1f, 1f, 0.88f);
    }

    private void drawGround() {
        rect(0f, -0.79f, 2.0f, 0.025f, 0f, 0.42f, 0.72f, 0.18f, 1f);
        rect(0f, -0.89f, 2.0f, 0.22f,  0f, 0.28f, 0.46f, 0.12f, 1f);
    }

    // =========================================================
    // Tuberías con gradiente y highlight (req 2.4)
    // =========================================================

    private void drawPipe(Pipe pipe) {
        float gTop = pipe.gapCenterY + Pipe.GAP_H * 0.5f;
        float gBot = pipe.gapCenterY - Pipe.GAP_H * 0.5f;

        // verde claro arriba → verde oscuro abajo (simula iluminación superior)
        float gr = 0.22f, gg = 0.76f, gb = 0.24f;  // color claro
        float dr = 0.10f, dg = 0.48f, db = 0.14f;  // color oscuro

        // tubería superior
        float hUp = 1.0f - gTop;
        if (hUp > 0) {
            rectGrad(pipe.x, gTop + hUp / 2f, Pipe.W, hUp, 0f, gr,gg,gb, dr,dg,db, 1f);
            // highlight borde izquierdo
            rect(pipe.x - Pipe.W * 0.30f, gTop + hUp / 2f,
                 Pipe.W * 0.13f, hUp, 0f, 0.45f, 0.90f, 0.38f, 0.45f);
            // cap (boca)
            rect(pipe.x, gTop, Pipe.W + 0.025f, 0.040f, 0f, dr, dg, db, 1f);
            rect(pipe.x - (Pipe.W + 0.025f) * 0.28f, gTop,
                 (Pipe.W + 0.025f) * 0.13f, 0.040f, 0f, gr, gg, gb, 0.55f);
        }

        // tubería inferior
        float hDn = gBot + 1.0f;
        if (hDn > 0) {
            rectGrad(pipe.x, -1.0f + hDn / 2f, Pipe.W, hDn, 0f, gr,gg,gb, dr,dg,db, 1f);
            rect(pipe.x - Pipe.W * 0.30f, -1.0f + hDn / 2f,
                 Pipe.W * 0.13f, hDn, 0f, 0.45f, 0.90f, 0.38f, 0.45f);
            rect(pipe.x, gBot, Pipe.W + 0.025f, 0.040f, 0f, dr, dg, db, 1f);
            rect(pipe.x - (Pipe.W + 0.025f) * 0.28f, gBot,
                 (Pipe.W + 0.025f) * 0.13f, 0.040f, 0f, gr, gg, gb, 0.55f);
        }
    }

    // =========================================================
    // Pájaro compuesto por figuras geométricas (req 2.1)
    // =========================================================

    private void drawBird(Bird bird) {
        float bx   = Bird.X;
        float by   = bird.y;
        float tilt = bird.getTilt();
        float[] col = bird.getColor();
        float r = col[0], g = col[1], bv = col[2];

        float wing = (float) Math.sin(bird.wingAnim * 9.0) * 0.022f;

        part(bx, by, tilt, -0.050f, 0.000f, 0.038f, 0.026f,
             (float) Math.PI, r * 0.82f, g * 0.82f, bv * 0.82f, true);

        part(bx, by, tilt, -0.005f, -0.028f + wing, 0.052f, 0.018f,
             0f, r * 0.78f, g * 0.78f, bv * 0.78f, false);

        part(bx, by, tilt, 0f, 0f, 0.084f, 0.074f,
             0f, r, g, bv, false);

        part(bx, by, tilt, 0.054f, 0.002f, 0.042f, 0.030f,
             0f, 0.92f, 0.44f, 0.08f, true);

        part(bx, by, tilt, 0.020f, 0.018f, 0.028f, 0.028f,
             0f, 1f, 1f, 1f, false);

        if (bird.alive) {
            part(bx, by, tilt, 0.025f, 0.016f, 0.013f, 0.013f,
                 0f, 0.08f, 0.08f, 0.08f, false);
        } else {
            part(bx, by, tilt, 0.022f, 0.018f, 0.028f, 0.007f,
                  (float) Math.PI / 4f, 0.1f, 0.1f, 0.1f, false);
            part(bx, by, tilt, 0.022f, 0.018f, 0.028f, 0.007f,
                 -(float) Math.PI / 4f, 0.1f, 0.1f, 0.1f, false);
        }
    }

    private void part(float bx, float by, float tilt,
                       float lx, float ly, float w, float h, float shape,
                       float r, float g, float bv, boolean triangle) {
        float cosT = (float) Math.cos(tilt);
        float sinT = (float) Math.sin(tilt);
        float wx = bx + cosT * lx - sinT * ly;
        float wy = by + sinT * lx + cosT * ly;
        if (triangle) tri(wx, wy, w, h, tilt + shape, r, g, bv, 1f);
        else          rect(wx, wy, w, h, tilt + shape, r, g, bv, 1f);
    }

    // =========================================================
    // HUD con display de 7 segmentos (req 2.4)
    // =========================================================

    private void drawHUD(Bird[] birds, float speed) {
        drawScore(birds[0].score, -0.92f, 0.92f, Bird.COLORS[0]);
        drawScore(birds[1].score,  0.50f, 0.92f, Bird.COLORS[1]);
        drawSpeedBar(speed);

        for (int i = 0; i < 2; i++) {
            if (!birds[i].alive) {
                float ix = (i == 0) ? -0.82f : 0.62f;
                rect(ix, 0.70f, 0.09f, 0.008f,  (float) Math.PI / 4f, 0.9f, 0.15f, 0.15f, 1f);
                rect(ix, 0.70f, 0.09f, 0.008f, -(float) Math.PI / 4f, 0.9f, 0.15f, 0.15f, 1f);
            }
        }
    }

    private void drawSpeedBar(float speed) {
        float maxSpeed  = 1.50f;
        float baseSpeed = 0.62f;
        float ratio     = (speed - baseSpeed) / (maxSpeed - baseSpeed);
        float barW      = ratio * 0.30f;
        if (barW < 0.003f) return;
        rect(0f, 0.95f, 0.32f, 0.018f, 0f, 0.25f, 0.25f, 0.25f, 0.6f);
        float rr = lerp(0.20f, 0.90f, ratio);
        float gg = lerp(0.80f, 0.15f, ratio);
        rect(-0.16f + barW / 2f, 0.95f, barW, 0.018f, 0f, rr, gg, 0.10f, 0.85f);
    }

    // =========================================================
    // Display de 7 segmentos — dígitos 0-9
    // Segments: [top(0), topRight(1), botRight(2), bottom(3), botLeft(4), topLeft(5), mid(6)]
    // =========================================================

    private static final boolean[][] SEG_DIGITS = {
        {true,  true,  true,  true,  true,  true,  false}, // 0
        {false, true,  true,  false, false, false, false}, // 1
        {true,  true,  false, true,  true,  false, true},  // 2
        {true,  true,  true,  true,  false, false, true},  // 3
        {false, true,  true,  false, false, true,  true},  // 4
        {true,  false, true,  true,  false, true,  true},  // 5
        {true,  false, true,  true,  true,  true,  true},  // 6
        {true,  true,  true,  false, false, false, false}, // 7
        {true,  true,  true,  true,  true,  true,  true},  // 8
        {true,  true,  true,  true,  false, true,  true},  // 9
    };

    // =========================================================
    // Fuente de píxeles 5×4 para GAME OVER (índices G=0 A=1 M=2 E=3 O=4 V=5 R=6)
    // Cada letra: boolean[5 filas][4 columnas]
    // =========================================================

    private static final int LG=0, LA=1, LM=2, LE=3, LO=4, LV=5, LR=6;

    private static final boolean[][][] PIXEL_FONT = {
        // G
        {{false,true, true, false},
         {true, false,false,false},
         {true, false,true, true},
         {true, false,false,true},
         {false,true, true, false}},
        // A
        {{false,true, true, false},
         {true, false,false,true},
         {true, true, true, true},
         {true, false,false,true},
         {true, false,false,true}},
        // M  (fila 1 completa = base de los dos picos; sin diagonal)
        {{true, false,false,true},
         {true, true, true, true},
         {true, false,false,true},
         {true, false,false,true},
         {true, false,false,true}},
        // E
        {{true, true, true, false},
         {true, false,false,false},
         {true, true, true, false},
         {true, false,false,false},
         {true, true, true, false}},
        // O
        {{false,true, true, false},
         {true, false,false,true},
         {true, false,false,true},
         {true, false,false,true},
         {false,true, true, false}},
        // V
        {{true, false,false,true},
         {true, false,false,true},
         {false,true, true, false},
         {false,true, true, false},
         {false,false,true, false}},
        // R
        {{true, true, true, false},
         {true, false,false,true},
         {true, true, true, false},
         {true, false,true, false},
         {true, false,false,true}},
    };

    private void drawScore(int score, float startX, float topY, float[] color) {
        String str    = String.valueOf(score);
        float spacing = 0.058f;
        for (int i = 0; i < str.length(); i++) {
            drawSegment(str.charAt(i) - '0', SEG_DIGITS,
                        startX + i * spacing, topY, 0.040f, 0.075f, 0.008f, color);
        }
    }

    /**
     * Dibuja una palabra con la fuente de píxeles PIXEL_FONT.
     * @param ps tamaño de cada píxel en NDC (ej. 0.016f)
     */
    private void drawPixelText(int[] letters, float startX, float topY,
                               float ps, float[] col) {
        float gap = ps * 0.5f;            // hueco entre letras
        float advance = ps * 4 + gap;     // cada letra mide 4 columnas + gap
        for (int li = 0; li < letters.length; li++) {
            boolean[][] glyph = PIXEL_FONT[letters[li]];
            float lx = startX + li * advance;
            for (int row = 0; row < glyph.length; row++) {
                for (int c = 0; c < glyph[row].length; c++) {
                    if (glyph[row][c]) {
                        float px = lx + (c + 0.5f) * ps;
                        float py = topY - (row + 0.5f) * ps;
                        rect(px, py, ps * 0.88f, ps * 0.88f, 0f,
                             col[0], col[1], col[2], 1f);
                    }
                }
            }
        }
    }

    /** Dibuja un dígito 0-9 con 7 segmentos en NDC. */
    private void drawSegment(int idx, boolean[][] table,
                              float x, float y,
                              float sl, float dh, float sw, float[] col) {
        float r  = col[0], g = col[1], bv = col[2];
        float hh = dh / 2f;
        float hs = sl / 2f - sw / 2f;
        boolean[] s  = table[idx];
        float cx = x + sl / 2f;

        if (s[0]) rect(cx, y,      sl - sw, sw, 0f, r, g, bv, 1f);
        if (s[3]) rect(cx, y - dh, sl - sw, sw, 0f, r, g, bv, 1f);
        if (s[6]) rect(cx, y - hh, sl - sw, sw, 0f, r, g, bv, 1f);
        if (s[1]) rect(cx + hs, y - hh / 2f,      sw, hh - sw, 0f, r, g, bv, 1f);
        if (s[2]) rect(cx + hs, y - hh - hh / 2f, sw, hh - sw, 0f, r, g, bv, 1f);
        if (s[5]) rect(cx - hs, y - hh / 2f,      sw, hh - sw, 0f, r, g, bv, 1f);
        if (s[4]) rect(cx - hs, y - hh - hh / 2f, sw, hh - sw, 0f, r, g, bv, 1f);
    }

    // =========================================================
    // Pantalla de inicio (req 2.4)
    // =========================================================

    private void drawStartOverlay() {
        rect(0f, 0f, 2f, 2f, 0f, 0.04f, 0.06f, 0.10f, 0.55f);
        rect(0f, 0.05f, 0.65f, 0.30f, 0f, 0.10f, 0.14f, 0.20f, 0.80f);

        drawMiniBird(-0.12f,  0.14f, Bird.COLORS[0]);
        drawMiniBird(-0.12f, -0.02f, Bird.COLORS[1]);

        rect(0.05f,  0.14f, 0.20f, 0.014f, 0f, 0.98f, 0.85f, 0.20f, 1f);
        rect(0.05f, -0.02f, 0.20f, 0.014f, 0f, 0.20f, 0.60f, 0.98f, 1f);

        tri(0.22f,  0.17f, 0.04f, 0.03f, (float) Math.PI / 2f, 0.98f, 0.85f, 0.20f, 1f);
        tri(0.22f, -0.00f, 0.04f, 0.03f, (float) Math.PI / 2f, 0.20f, 0.60f, 0.98f, 1f);
    }

    private void drawMiniBird(float bx, float by, float[] col) {
        float r = col[0], g = col[1], bv = col[2];
        float s = 0.55f;
        rect(bx,                  by,                  0.050f * s, 0.044f * s, 0f, r, g, bv, 1f);
        tri( bx + 0.032f * s,     by,                  0.026f * s, 0.018f * s, 0f, 0.92f, 0.44f, 0.08f, 1f);
        rect(bx + 0.010f * s,     by + 0.010f * s,     0.016f * s, 0.016f * s, 0f, 1f, 1f, 1f, 1f);
    }

    // =========================================================
    // Pantalla de game over (req 2.4)
    // =========================================================

    private void drawGameOverOverlay(Bird[] birds) {
        // overlay con gradiente rojo dramático
        rectGrad(0f, 0f, 2f, 2f, 0f,
                 0.36f, 0.04f, 0.04f,
                 0.08f, 0.01f, 0.01f,
                 0.56f);

        // panel central
        rect(0f, 0.04f, 0.82f, 0.66f, 0f, 0.06f, 0.08f, 0.12f, 0.90f);

        // "GAME" y "OVER" con fuente de píxeles (4 letras × 4 cols × ps=0.016)
        // ancho total = 4*(4*0.016 + 0.008) - 0.008 = 0.280  → startX = -0.140
        float[] titleCol = {1.0f, 0.28f, 0.08f};
        float ps = 0.016f;
        float startX = -0.140f;
        drawPixelText(new int[]{LG, LA, LM, LE}, startX, 0.315f, ps, titleCol);
        drawPixelText(new int[]{LO, LV, LE, LR}, startX, 0.215f, ps, titleCol);

        // separador
        rect(0f, 0.115f, 0.68f, 0.005f, 0f, 0.5f, 0.5f, 0.5f, 0.40f);

        // barras de puntaje — P1
        float p1W = Math.min(birds[0].score * 0.04f, 0.30f);
        float p2W = Math.min(birds[1].score * 0.04f, 0.30f);

        drawMiniBird(-0.37f, 0.055f, Bird.COLORS[0]);
        rect(0f, 0.055f, 0.34f, 0.042f, 0f, 0.18f, 0.18f, 0.18f, 0.70f);
        if (p1W > 0)
            rect(-0.17f + p1W / 2f, 0.055f, p1W, 0.042f, 0f,
                 Bird.COLORS[0][0], Bird.COLORS[0][1], Bird.COLORS[0][2], 1f);

        // barras de puntaje — P2
        drawMiniBird(-0.37f, -0.060f, Bird.COLORS[1]);
        rect(0f, -0.060f, 0.34f, 0.042f, 0f, 0.18f, 0.18f, 0.18f, 0.70f);
        if (p2W > 0)
            rect(-0.17f + p2W / 2f, -0.060f, p2W, 0.042f, 0f,
                 Bird.COLORS[1][0], Bird.COLORS[1][1], Bird.COLORS[1][2], 1f);

        // resaltado del ganador
        if (birds[0].score > birds[1].score)
            rect(0f, 0.055f,  0.36f, 0.050f, 0f, 1f, 1f, 1f, 0.28f);
        else if (birds[1].score > birds[0].score)
            rect(0f, -0.060f, 0.36f, 0.050f, 0f, 1f, 1f, 1f, 0.28f);
        else {
            rect(0f, 0.055f,  0.36f, 0.050f, 0f, 1f, 1f, 0.2f, 0.20f);
            rect(0f, -0.060f, 0.36f, 0.050f, 0f, 1f, 1f, 0.2f, 0.20f);
        }

        // indicador de reinicio: dos triángulos (uno por jugador)
        rect(0f, -0.175f, 0.46f, 0.005f, 0f, 0.5f, 0.5f, 0.5f, 0.30f);
        tri(-0.055f, -0.215f, 0.065f, 0.045f, 0f, 0.98f, 0.85f, 0.20f, 0.90f);
        tri( 0.055f, -0.215f, 0.065f, 0.045f, 0f, 0.20f, 0.60f, 0.98f, 0.90f);
    }

    // =========================================================
    // Primitivas OpenGL de bajo nivel
    // =========================================================

    /** Rectángulo sólido (uColor2 = uColor → sin gradiente visible). */
    private void rect(float x, float y, float w, float h, float angle,
                       float r, float g, float bv, float alpha) {
        glUniform2f(locOffset, x, y);
        glUniform2f(locScale,  w, h);
        glUniform1f(locAngle,  angle);
        glUniform3f(locColor,  r, g, bv);
        glUniform3f(locColor2, r, g, bv);
        glUniform1f(locAlpha,  alpha);
        glBindVertexArray(quadVao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }

    /** Rectángulo con gradiente arriba→abajo. */
    private void rectGrad(float x, float y, float w, float h, float angle,
                           float r1, float g1, float b1,
                           float r2, float g2, float b2,
                           float alpha) {
        glUniform2f(locOffset, x, y);
        glUniform2f(locScale,  w, h);
        glUniform1f(locAngle,  angle);
        glUniform3f(locColor,  r1, g1, b1);
        glUniform3f(locColor2, r2, g2, b2);
        glUniform1f(locAlpha,  alpha);
        glBindVertexArray(quadVao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }

    /** Triángulo sólido (apuntando a la derecha por defecto, rotado con angle). */
    private void tri(float x, float y, float w, float h, float angle,
                      float r, float g, float bv, float alpha) {
        glUniform2f(locOffset, x, y);
        glUniform2f(locScale,  w, h);
        glUniform1f(locAngle,  angle);
        glUniform3f(locColor,  r, g, bv);
        glUniform3f(locColor2, r, g, bv);
        glUniform1f(locAlpha,  alpha);
        glBindVertexArray(triVao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
    }

    // =========================================================
    // Cleanup
    // =========================================================

    public void cleanup() {
        glDeleteVertexArrays(quadVao);
        glDeleteBuffers(quadVbo);
        glDeleteVertexArrays(triVao);
        glDeleteBuffers(triVbo);
        glDeleteProgram(programa);
    }

    // =========================================================
    // Utilidades
    // =========================================================

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
