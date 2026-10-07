package com.tedredington.jazzclub.config;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.tedredington.jazzclub.app.Radio;
import com.tedredington.jazzclub.player.AudioPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/** Finishes the main loop, including its final events, before Spring destroys event listeners. */
@Component
class PlayerLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(PlayerLifecycle.class);
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(5);

    private final ObjectProvider<Radio> radio;
    private final ObjectProvider<AudioPlayer> player;
    private final Object lock = new Object();
    private volatile boolean running;
    private Thread owner;

    PlayerLifecycle(ObjectProvider<Radio> radio, ObjectProvider<AudioPlayer> player) {
        this.radio = radio;
        this.player = player;
    }

    /** Covers terminal acquisition as well as the loop, so shutdown cannot race with startup. */
    void run(Runnable application) {
        synchronized (lock) {
            if (!running) {
                return;
            }
            owner = Thread.currentThread();
        }
        try {
            application.run();
        } finally {
            synchronized (lock) {
                owner = null;
                lock.notifyAll();
            }
        }
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        Thread thread;
        synchronized (lock) {
            running = false;
            thread = owner;
        }
        if (thread == null || thread == Thread.currentThread()) {
            radio.getObject().shutdown();
            return;
        }
        // Interrupts queue reads, prompts, credential commands, and JDK HTTP requests. Cleanup
        // stays on the main loop's thread, where Radio and PlaybackState are confined.
        thread.interrupt();
        try {
            long deadline = System.nanoTime() + STOP_TIMEOUT.toNanos();
            synchronized (lock) {
                while (owner != null) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        break;
                    }
                    TimeUnit.NANOSECONDS.timedWait(lock, remaining);
                }
                if (owner == null) {
                    return;
                }
            }
            log.warn("Player loop did not exit within {}s; stopping audio", STOP_TIMEOUT.toSeconds());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        player.getObject().stop();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
