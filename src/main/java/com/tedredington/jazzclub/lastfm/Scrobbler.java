package com.tedredington.jazzclub.lastfm;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.tedredington.jazzclub.credentials.CredentialsException;
import com.tedredington.jazzclub.event.Event;
import com.tedredington.jazzclub.lastfm.LastFmException.AuthenticationFailed;
import com.tedredington.jazzclub.lastfm.LastFmException.Rejected;
import com.tedredington.jazzclub.lastfm.LastFmException.TemporarilyUnavailable;
import com.tedredington.jazzclub.ui.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Talks to Last.fm on a thread of its own, one request at a time and in order, so a slow or absent
 * Last.fm never holds up the music or a key press.
 *
 * <p>Nothing here ever stops jazzclub. Problems are reported once, when the situation changes, as a
 * {@link Event.Notice} for the main loop to print; details go to the log ({@code -v}). A wrong password
 * is tried once per run and never again, so jazzclub cannot get the Last.fm account locked.
 *
 * <p>All fields but the executor are confined to the worker thread.
 */
final class Scrobbler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Scrobbler.class);

    private final LastFmClient client;
    private final String user;
    private final Supplier<Optional<String>> passwords;
    private final SessionFile sessions;
    private final Consumer<Event.Notice> notices;
    private final Path configFile;
    private final Duration drainOnClose;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            runnable -> Thread.ofPlatform().name("lastfm").daemon(true).unstarted(runnable));

    private Session session;
    /** A session from this run's sign-in; if Last.fm rejects that too, signing in again is pointless. */
    private boolean signedInThisRun;
    /** Kept between attempts while Last.fm is unreachable, so a password command is not run for every song. */
    private String password;
    private boolean everWorked;
    private boolean unreachable;
    private boolean stopped;

    /**
     * @param passwords    {@code lastfm_password} or the output of {@code lastfm_password_command}; empty if
     *                     neither is set; may throw {@link CredentialsException}
     * @param configFile   only named in messages
     * @param drainOnClose how long {@link #close()} lets pending requests finish, so the song cut short by
     *                     quitting is still scrobbled
     */
    Scrobbler(LastFmClient client, String user, Supplier<Optional<String>> passwords, SessionFile sessions,
              Consumer<Event.Notice> notices, Path configFile, Duration drainOnClose) {
        this.client = client;
        this.user = user.strip();
        this.passwords = passwords;
        this.sessions = sessions;
        this.notices = notices;
        this.configFile = configFile;
        this.drainOnClose = drainOnClose;
    }

    void nowPlaying(Track track) {
        worker.execute(() -> attempt(key -> client.nowPlaying(key, track)));
    }

    void scrobble(Scrobble scrobble) {
        worker.execute(() -> attempt(key -> client.scrobble(key, scrobble)));
    }

    void love(Track track) {
        worker.execute(() -> attempt(key -> client.love(key, track)));
    }

    void unlove(Track track) {
        worker.execute(() -> attempt(key -> client.unlove(key, track)));
    }

    @Override
    public void close() {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(drainOnClose.toMillis(), TimeUnit.MILLISECONDS)) {
                log.info("Gave up waiting for Last.fm after {}s", drainOnClose.toSeconds());
                worker.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            worker.shutdownNow();
        }
    }

    private void attempt(Request request) {
        if (stopped) {
            return;
        }
        try {
            sendWithSession(request);
            succeeded();
        } catch (TemporarilyUnavailable e) {
            log.info("Last.fm is unreachable: {}", e.getMessage(), e);
            if (!unreachable) {
                unreachable = true;
                notify(MessageType.ERROR, "Last.fm is unreachable; songs played meanwhile will not be scrobbled.");
            }
        } catch (Rejected e) {
            if (e.isApiKeyProblem()) {
                stop("Last.fm does not accept this build of jazzclub (" + e.getMessage() + "), not scrobbling.");
            } else {
                log.info("Last.fm refused a request: {} (error {})", e.getMessage(), e.code());
            }
        } catch (AuthenticationFailed e) {
            stop("Last.fm keeps rejecting jazzclub (" + e.getMessage() + "), not scrobbling.");
        } catch (CannotSignIn e) {
            stop(e.getMessage());
        } catch (RuntimeException e) {
            // a bug here must not take the worker thread, and with it all later scrobbles, down
            log.warn("Last.fm request failed unexpectedly", e);
        }
    }

    private void sendWithSession(Request request) {
        try {
            request.send(session().key());
        } catch (AuthenticationFailed e) {
            sessions.delete();
            session = null;
            if (signedInThisRun) {
                throw e;
            }
            log.info("Last.fm rejected the saved session: {}", e.getMessage());
            notify(MessageType.ERROR, "Last.fm: jazzclub is no longer allowed to scrobble for you, signing in again...");
            request.send(session().key());
        }
    }

    private Session session() {
        if (session == null) {
            session = sessions.load(user).orElseGet(this::signIn);
        }
        return session;
    }

    private Session signIn() {
        if (password == null) {
            try {
                password = passwords.get().orElseThrow(() -> new CannotSignIn("Last.fm: lastfm_user is set, but "
                        + "neither lastfm_password nor lastfm_password_command is, not scrobbling. Add one to "
                        + configFile + "."));
            } catch (CredentialsException e) {
                log.info("No Last.fm password", e);
                throw new CannotSignIn("Last.fm: " + e.getMessage() + ", not scrobbling.");
            }
        }
        Session fresh;
        try {
            fresh = client.signIn(user, password);
        } catch (AuthenticationFailed e) {
            password = null;
            throw new CannotSignIn("Last.fm: wrong user name or password for '" + user + "', not scrobbling. "
                    + "Check lastfm_user and lastfm_password in " + configFile + ".");
        }
        password = null;
        signedInThisRun = true;
        sessions.save(fresh);
        return fresh;
    }

    private void succeeded() {
        if (!everWorked) {
            everWorked = true;
            unreachable = false;
            notify(MessageType.INFO, "Last.fm: scrobbling as " + session.user() + ".");
        } else if (unreachable) {
            unreachable = false;
            notify(MessageType.INFO, "Last.fm is reachable again.");
        }
    }

    private void stop(String message) {
        stopped = true;
        notify(MessageType.ERROR, message);
    }

    private void notify(MessageType type, String text) {
        notices.accept(new Event.Notice(type, text));
    }

    @FunctionalInterface
    private interface Request {
        void send(String sessionKey);
    }

    /** Signing in is impossible for the rest of this run; the message is for the user. */
    private static final class CannotSignIn extends RuntimeException {
        CannotSignIn(String message) {
            super(message);
        }
    }
}
