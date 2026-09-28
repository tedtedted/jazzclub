package com.tedredington.jazzclub.lastfm;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.tedredington.jazzclub.credentials.CredentialsException;
import com.tedredington.jazzclub.event.Event;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(10)
class ScrobblerTest {

    private static final Path CONFIG = Path.of("/home/ted/.config/jazzclub/config");
    private static final Track NARDIS = new Track("Bill Evans", "Nardis", null, Duration.ofSeconds(351));
    private static final Scrobble SCROBBLE = new Scrobble(NARDIS, Instant.ofEpochSecond(1_790_000_000L));

    @TempDir
    Path directory;

    private final FakeLastFmClient client = new FakeLastFmClient();
    private final List<Event.Notice> notices = new ArrayList<>();
    private int passwordLookups;
    private Supplier<Optional<String>> passwords = () -> {
        passwordLookups++;
        return Optional.of("pw");
    };

    private SessionFile sessionFile() {
        return new SessionFile(directory.resolve("lastfm-session"));
    }

    /** Runs the given requests and waits for all of them. */
    private void run(java.util.function.Consumer<Scrobbler> requests) {
        try (Scrobbler scrobbler = new Scrobbler(client, " ted ", passwords, sessionFile(), notices::add, CONFIG,
                Duration.ofSeconds(5))) {
            requests.accept(scrobbler);
        }
    }

    private List<String> noticeTexts() {
        return notices.stream().map(n -> n.type().name() + " " + n.text()).toList();
    }

    @Test
    void signsInOnceThenUsesTheSessionForEverything() {
        run(s -> {
            s.nowPlaying(NARDIS);
            s.scrobble(SCROBBLE);
        });

        assertThat(client.calls).containsExactly("signIn ted/pw", "nowPlaying sk-1 Nardis", "scrobble sk-1 Nardis");
        assertThat(sessionFile().load("ted")).contains(new Session("Ted", "sk-1"));
        assertThat(noticeTexts()).containsExactly("INFO Last.fm: scrobbling as Ted.");
    }

    @Test
    void aSavedSessionNeedsNoPassword() {
        sessionFile().save(new Session("ted", "saved"));

        run(s -> s.nowPlaying(NARDIS));

        assertThat(client.calls).containsExactly("nowPlaying saved Nardis");
        assertThat(passwordLookups).isZero();
    }

    @Test
    void aWrongPasswordIsTriedOnceAndReportedOnce() {
        client.failSignIn.add(LastFmException.of(4, "Authentication Failed"));

        run(s -> {
            s.nowPlaying(NARDIS);
            s.scrobble(SCROBBLE);
            s.nowPlaying(NARDIS);
        });

        assertThat(client.calls).containsExactly("signIn ted/pw");
        assertThat(noticeTexts()).containsExactly("ERROR Last.fm: wrong user name or password for 'ted', "
                + "not scrobbling. Check lastfm_user and lastfm_password in " + CONFIG + ".");
    }

    @Test
    void noPasswordAtAllIsExplained() {
        passwords = Optional::empty;

        run(s -> {
            s.nowPlaying(NARDIS);
            s.scrobble(SCROBBLE);
        });

        assertThat(client.calls).isEmpty();
        assertThat(noticeTexts()).singleElement().asString()
                .startsWith("ERROR Last.fm: lastfm_user is set, but neither lastfm_password nor")
                .contains(CONFIG.toString());
    }

    @Test
    void aFailingPasswordCommandOnlyTurnsScrobblingOff() {
        passwords = () -> {
            passwordLookups++;
            throw new CredentialsException("lastfm_password_command printed nothing");
        };

        run(s -> {
            s.nowPlaying(NARDIS);
            s.scrobble(SCROBBLE);
        });

        assertThat(passwordLookups).isOne();
        assertThat(noticeTexts())
                .containsExactly("ERROR Last.fm: lastfm_password_command printed nothing, not scrobbling.");
    }

    @Test
    void beingUnreachableIsReportedOnceAndSoIsTheRecovery() {
        sessionFile().save(new Session("ted", "saved"));
        client.failNext.add(LastFmException.of(11, "Service Offline"));
        client.failNext.add(new LastFmException.TemporarilyUnavailable(0, "could not reach", null));

        run(s -> {
            s.nowPlaying(NARDIS);
            s.scrobble(SCROBBLE);
            s.nowPlaying(NARDIS);
            s.nowPlaying(NARDIS);
        });

        assertThat(noticeTexts()).containsExactly(
                "ERROR Last.fm is unreachable; songs played meanwhile will not be scrobbled.",
                "INFO Last.fm: scrobbling as ted.");
        assertThat(client.calls).hasSize(4);
    }

    @Test
    void recoveryAfterItHadWorkedSaysSo() {
        sessionFile().save(new Session("ted", "saved"));

        client.failNext.add(FakeLastFmClient.SUCCEED);
        client.failNext.add(LastFmException.of(16, "Temporary error"));

        run(s -> {
            s.nowPlaying(NARDIS);
            s.scrobble(SCROBBLE);
            s.nowPlaying(NARDIS);
        });

        assertThat(noticeTexts()).containsExactly(
                "INFO Last.fm: scrobbling as ted.",
                "ERROR Last.fm is unreachable; songs played meanwhile will not be scrobbled.",
                "INFO Last.fm is reachable again.");
    }

    @Test
    void thePasswordCommandRunsOnceWhileLastFmIsUnreachableForSignIn() {
        client.failSignIn.add(LastFmException.of(11, "Service Offline"));
        client.failSignIn.add(LastFmException.of(11, "Service Offline"));

        run(s -> {
            s.nowPlaying(NARDIS);
            s.nowPlaying(NARDIS);
            s.nowPlaying(NARDIS);
        });

        assertThat(passwordLookups).isOne();
        assertThat(client.calls).containsExactly("signIn ted/pw", "signIn ted/pw", "signIn ted/pw",
                "nowPlaying sk-1 Nardis");
    }

    @Test
    void aRevokedSavedSessionMeansSigningInAgainAndRetrying() {
        sessionFile().save(new Session("ted", "revoked"));
        client.failNext.add(LastFmException.of(9, "Invalid session key"));

        run(s -> s.scrobble(SCROBBLE));

        assertThat(client.calls).containsExactly("scrobble revoked Nardis", "signIn ted/pw", "scrobble sk-1 Nardis");
        assertThat(sessionFile().load("ted")).contains(new Session("Ted", "sk-1"));
        assertThat(noticeTexts()).containsExactly(
                "ERROR Last.fm: jazzclub is no longer allowed to scrobble for you, signing in again...",
                "INFO Last.fm: scrobbling as Ted.");
    }

    @Test
    void aFreshSessionThatIsRejectedStopsScrobblingInsteadOfLooping() {
        client.failNext.add(LastFmException.of(9, "Invalid session key"));

        run(s -> {
            s.nowPlaying(NARDIS);
            s.nowPlaying(NARDIS);
        });

        assertThat(client.calls).containsExactly("signIn ted/pw", "nowPlaying sk-1 Nardis");
        assertThat(sessionFile().load("ted")).isEmpty();
        assertThat(noticeTexts()).containsExactly(
                "ERROR Last.fm keeps rejecting jazzclub (Invalid session key), not scrobbling.");
    }

    @Test
    void aBadApiKeyStopsScrobbling() {
        sessionFile().save(new Session("ted", "saved"));
        client.failNext.add(LastFmException.of(26, "Suspended API key"));

        run(s -> {
            s.nowPlaying(NARDIS);
            s.nowPlaying(NARDIS);
        });

        assertThat(client.calls).hasSize(1);
        assertThat(noticeTexts()).containsExactly(
                "ERROR Last.fm does not accept this build of jazzclub (Suspended API key), not scrobbling.");
    }

    @Test
    void aRejectedRequestIsDroppedQuietlyAndTheNextOneStillGoes() {
        sessionFile().save(new Session("ted", "saved"));
        client.failNext.add(LastFmException.of(6, "Invalid parameters"));

        run(s -> {
            s.scrobble(SCROBBLE);
            s.nowPlaying(NARDIS);
        });

        assertThat(client.calls).hasSize(2);
        assertThat(noticeTexts()).containsExactly("INFO Last.fm: scrobbling as ted.");
    }

    @Test
    void aBugInOneRequestDoesNotStopTheNext() {
        sessionFile().save(new Session("ted", "saved"));
        client.failNext.add(new IllegalStateException("bug"));

        run(s -> {
            s.nowPlaying(NARDIS);
            s.nowPlaying(NARDIS);
        });

        assertThat(client.calls).hasSize(2);
    }

    @Test
    void closingWaitsForTheLastScrobble() {
        sessionFile().save(new Session("ted", "saved"));
        client.delay = Duration.ofMillis(300);

        run(s -> s.scrobble(SCROBBLE));

        assertThat(client.calls).containsExactly("scrobble saved Nardis");
    }
}
