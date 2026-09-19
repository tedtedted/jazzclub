package com.tedredington.jazzclub.pandora.error;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Error codes of the Pandora JSON API, with the same user-facing wording as pianobar.
 * Pandora reuses 1027 for both "invalid country code" and "invalid gender"; only the former is listed.
 */
public enum PandoraErrorCode {

    INTERNAL(0, "Internal error."),
    MAINTENANCE_MODE(1, "Maintenance mode."),
    URL_PARAM_MISSING_METHOD(2),
    URL_PARAM_MISSING_AUTH_TOKEN(3),
    URL_PARAM_MISSING_PARTNER_ID(4),
    URL_PARAM_MISSING_USER_ID(5),
    SECURE_PROTOCOL_REQUIRED(6),
    CERTIFICATE_REQUIRED(7),
    PARAMETER_TYPE_MISMATCH(8),
    PARAMETER_MISSING(9),
    PARAMETER_VALUE_INVALID(10),
    API_VERSION_NOT_SUPPORTED(11),
    LICENSING_RESTRICTIONS(12, "Pandora is not available in your country."),
    INSUFFICIENT_CONNECTIVITY(13),
    READ_ONLY_MODE(1000, "Read only mode. Try again later."),
    INVALID_AUTH_TOKEN(1001, "Invalid auth token."),
    INVALID_PARTNER_LOGIN(1002, "Invalid partner login."),
    LISTENER_NOT_AUTHORIZED(1003, "Listener not authorized."),
    USER_NOT_AUTHORIZED(1004),
    MAX_STATIONS_REACHED(1005, "Max number of stations reached."),
    STATION_DOES_NOT_EXIST(1006, "Station does not exist."),
    COMPLIMENTARY_PERIOD_ALREADY_IN_USE(1007),
    CALL_NOT_ALLOWED(1008, "Call not allowed."),
    DEVICE_NOT_FOUND(1009),
    PARTNER_NOT_AUTHORIZED(1010, "Invalid partner credentials."),
    INVALID_USERNAME(1011),
    INVALID_PASSWORD(1012),
    USERNAME_ALREADY_EXISTS(1013),
    DEVICE_ALREADY_ASSOCIATED_TO_ACCOUNT(1014),
    UPGRADE_DEVICE_MODEL_INVALID(1015),
    EXPLICIT_PIN_INCORRECT(1018),
    EXPLICIT_PIN_MALFORMED(1020),
    DEVICE_MODEL_INVALID(1023),
    ZIP_CODE_INVALID(1024),
    BIRTH_YEAR_INVALID(1025),
    BIRTH_YEAR_TOO_YOUNG(1026),
    INVALID_COUNTRY_CODE(1027),
    DEVICE_DISABLED(1034),
    DAILY_TRIAL_LIMIT_REACHED(1035),
    INVALID_SPONSOR(1036),
    USER_ALREADY_USED_TRIAL(1037),
    RATE_LIMIT(1039, "Access denied. Try again later."),
    /** Anything Pandora sends that this client does not know about. */
    UNKNOWN(-1);

    private static final String NO_MESSAGE = "No error message available.";

    private static final Map<Integer, PandoraErrorCode> BY_CODE = Arrays.stream(values())
            .filter(c -> c != UNKNOWN)
            .collect(Collectors.toUnmodifiableMap(PandoraErrorCode::code, Function.identity()));

    private final int code;
    private final String message;

    PandoraErrorCode(int code) {
        this(code, null);
    }

    PandoraErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public static PandoraErrorCode fromCode(int code) {
        return BY_CODE.getOrDefault(code, UNKNOWN);
    }

    public int code() {
        return code;
    }

    public String message() {
        return message != null ? message : NO_MESSAGE + " (" + name() + ")";
    }
}
