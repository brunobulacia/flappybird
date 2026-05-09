package com.graphics;

import javax.sound.sampled.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Genera y reproduce efectos de sonido con síntesis PCM.
 *
 * En macOS con -XstartOnFirstThread, las llamadas a AudioSystem y Clip
 * bloquean el hilo principal (que es el hilo de GLFW), causando freezes.
 * Solución: toda interacción con la API de audio se ejecuta en un hilo
 * dedicado (audioThread). Las llamadas públicas (playX) solo encolan
 * tareas y retornan inmediatamente sin bloquear el game loop.
 */
public class SoundManager {

    private static final int SAMPLE_RATE = 44100;

    // volatile: escritas en audioThread, leídas en hilo main (cleanup)
    private volatile Clip jumpClip;
    private volatile Clip scoreClip;
    private volatile Clip gameOverClip;

    // hilo dedicado para toda la API de audio
    private final ExecutorService audioThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "audio");
        t.setDaemon(true);
        return t;
    });

    public SoundManager() {
        // 1) Generar datos PCM en el hilo actual (solo matemática, sin AudioSystem)
        byte[] jumpPCM     = sweep(550, 950, 85,  0.32f);
        byte[] scorePCM    = twoNote(660, 880, 110, 35, 0.48f);
        byte[] gameOverPCM = sweep(440, 75,  750, 0.58f);

        // 2) Abrir Clips en el hilo de audio (AudioSystem puede bloquear en macOS
        //    cuando se llama desde el hilo de GLFW con -XstartOnFirstThread)
        audioThread.submit(() -> {
            jumpClip     = openClip(jumpPCM,     sampleCount(85));
            scoreClip    = openClip(scorePCM,    sampleCount(110) * 2 + sampleCount(35));
            gameOverClip = openClip(gameOverPCM, sampleCount(750));
        });
    }

    // =========================================================
    // API pública — no-bloqueante, encola en audioThread
    // =========================================================

    public void playJump()    { audioThread.submit(() -> play(jumpClip)); }
    public void playScore()   { audioThread.submit(() -> play(scoreClip)); }
    public void playGameOver(){ audioThread.submit(() -> play(gameOverClip)); }

    private static void play(Clip clip) {
        if (clip == null) return;
        clip.stop();
        clip.setFramePosition(0);
        clip.start();
    }

    // =========================================================
    // Síntesis PCM — solo matemática, sin llamadas a AudioSystem
    // =========================================================

    /**
     * Barrido de frecuencia de f0 a f1 durante ms milisegundos.
     * Fase calculada por integración exacta: phi(t) = 2π*(f0*t + (f1-f0)*t²/(2T))
     */
    private static byte[] sweep(double f0, double f1, int ms, float vol) {
        int n = sampleCount(ms);
        byte[] data = new byte[n * 2];
        double T = ms / 1000.0;
        for (int i = 0; i < n; i++) {
            double t   = (double) i / SAMPLE_RATE;
            double phi = 2 * Math.PI * (f0 * t + (f1 - f0) * t * t / (2.0 * T));
            writeLE(data, i, quantize(Math.sin(phi) * vol * env(t, T)));
        }
        return data;
    }

    /** Dos notas separadas por un breve silencio. */
    private static byte[] twoNote(double f1, double f2, int noteMs, int gapMs, float vol) {
        int n1  = sampleCount(noteMs);
        int gap = sampleCount(gapMs);
        int n   = n1 * 2 + gap;
        byte[] data = new byte[n * 2];
        double T = noteMs / 1000.0;
        for (int i = 0; i < n; i++) {
            if (i >= n1 && i < n1 + gap) { writeLE(data, i, (short) 0); continue; }
            double localT = (i < n1) ? (double) i / SAMPLE_RATE
                                     : (double) (i - n1 - gap) / SAMPLE_RATE;
            double freq   = (i < n1) ? f1 : f2;
            writeLE(data, i, quantize(Math.sin(2 * Math.PI * freq * localT) * vol * env(localT, T)));
        }
        return data;
    }

    // =========================================================
    // Carga de Clip — debe ejecutarse en audioThread
    // =========================================================

    private static Clip openClip(byte[] pcm, int samples) {
        try {
            AudioFormat fmt = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            Clip clip = AudioSystem.getClip();
            clip.open(fmt, pcm, 0, samples * 2);
            return clip;
        } catch (Exception e) {
            return null; // sin dispositivo de audio → el juego sigue funcionando
        }
    }

    // =========================================================
    // Helpers
    // =========================================================

    /** Envolvente ADSR simplificada: fade-in 5 %, sustain, fade-out 30 %. */
    private static float env(double t, double T) {
        if (t < T * 0.05) return (float) (t / (T * 0.05));
        if (t > T * 0.70) return (float) ((T - t) / (T * 0.30));
        return 1f;
    }

    private static short quantize(double v) {
        return (short) Math.max(-32768, Math.min(32767, v * 32767));
    }

    private static void writeLE(byte[] buf, int i, short v) {
        buf[i * 2]     = (byte) (v & 0xFF);
        buf[i * 2 + 1] = (byte) (v >> 8);
    }

    private static int sampleCount(int ms) {
        return SAMPLE_RATE * ms / 1000;
    }

    // =========================================================
    // Cleanup
    // =========================================================

    public void cleanup() {
        audioThread.shutdownNow();
        close(jumpClip);
        close(scoreClip);
        close(gameOverClip);
    }

    private static void close(Clip c) {
        if (c != null) c.close();
    }
}
