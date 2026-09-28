package com.tedredington.jazzclub.lastfm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;

import com.tedredington.jazzclub.config.file.ParsedConfig;
import com.tedredington.jazzclub.config.file.UserConfigFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remembers the Last.fm session key between runs, so the password is needed only once. Lives in the
 * state directory, readable by the owner only; in the config file's format.
 */
final class SessionFile {

    private static final Logger log = LoggerFactory.getLogger(SessionFile.class);
    private static final String USER = "user";
    private static final String KEY = "session_key";

    private final Path file;

    SessionFile(Path file) {
        this.file = file;
    }

    /** @return the saved session, if there is one for this user */
    Optional<Session> load(String user) {
        ParsedConfig saved;
        try {
            saved = new UserConfigFile().load(file);
        } catch (RuntimeException e) {
            log.info("Cannot read {}, signing in to Last.fm again: {}", file, e.getMessage());
            return Optional.empty();
        }
        Optional<String> name = saved.get(USER).filter(s -> !s.isBlank());
        Optional<String> key = saved.get(KEY).filter(s -> !s.isBlank());
        if (name.isEmpty() || key.isEmpty()) {
            return Optional.empty();
        }
        Session session = new Session(name.get(), key.get());
        // lastfm_user was changed to another account: the old key would scrobble for the wrong person
        return session.belongsTo(user) ? Optional.of(session) : Optional.empty();
    }

    /** Failing to save costs a sign-in on the next start, nothing more, so it is only logged. */
    void save(Session session) {
        String content = "# written by jazzclub; delete this file to sign in to Last.fm again\n"
                + USER + " = " + session.user() + "\n"
                + KEY + " = " + session.key() + "\n";
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), "lastfm", ".tmp", ownerOnly());
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.info("Could not save the Last.fm session to {}: {}", file, e.getMessage());
        }
    }

    void delete() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.info("Could not delete {}: {}", file, e.getMessage());
        }
    }

    private static FileAttribute<?>[] ownerOnly() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix")
                ? new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))}
                : new FileAttribute<?>[0];
    }
}
