package com.tedredington.jazzclub.lastfm;

import java.util.Set;

/**
 * Last.fm said no, or could not be asked. The subtype says what to do about it; see
 * https://www.last.fm/api/errorcodes.
 */
abstract sealed class LastFmException extends RuntimeException {

    /** Not a Last.fm code: the request never got an answer Last.fm itself wrote. */
    static final int NO_CODE = 0;

    private static final Set<Integer> AUTHENTICATION = Set.of(
            4,  // authentication failed: wrong user name or password
            9,  // invalid session key: access revoked
            14  // unauthorized token: not approved in the browser (yet)
    );
    private static final Set<Integer> TEMPORARY = Set.of(
            8,  // operation failed, "most likely the backend service failed"
            11, // service offline
            16, // temporary error
            29  // rate limit exceeded
    );
    private static final Set<Integer> API_KEY = Set.of(
            10, // invalid API key
            13, // invalid method signature: the shared secret is wrong
            26  // suspended API key
    );

    private final int code;

    private LastFmException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    static LastFmException of(int code, String message) {
        String text = message == null || message.isBlank() ? "error " + code : message.strip();
        if (AUTHENTICATION.contains(code)) {
            return new AuthenticationFailed(code, text);
        }
        if (TEMPORARY.contains(code)) {
            return new TemporarilyUnavailable(code, text, null);
        }
        return new Rejected(code, text);
    }

    int code() {
        return code;
    }

    /** The credentials or the session are no good. Signing in again may help; retrying as is will not. */
    static final class AuthenticationFailed extends LastFmException {
        AuthenticationFailed(int code, String message) {
            super(code, message, null);
        }
    }

    /** Try again later: Last.fm is down, busy, or out of reach. */
    static final class TemporarilyUnavailable extends LastFmException {
        TemporarilyUnavailable(int code, String message, Throwable cause) {
            super(code, message, cause);
        }
    }

    /** This request will never succeed. */
    static final class Rejected extends LastFmException {
        Rejected(int code, String message) {
            super(code, message, null);
        }

        /** The fault is in this build of jazzclub, not in the request: no request will succeed. */
        boolean isApiKeyProblem() {
            return API_KEY.contains(code());
        }
    }
}
