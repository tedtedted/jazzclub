package com.tedredington.jazzclub.pandora.error;

/** Pandora answered, but with {@code "stat": "fail"}. */
public final class PandoraApiException extends PandoraException {

    private final PandoraErrorCode errorCode;
    private final int rawCode;

    public PandoraApiException(int rawCode, String serverMessage) {
        this(PandoraErrorCode.fromCode(rawCode), rawCode, serverMessage);
    }

    private PandoraApiException(PandoraErrorCode errorCode, int rawCode, String serverMessage) {
        super(describe(errorCode, rawCode, serverMessage));
        this.errorCode = errorCode;
        this.rawCode = rawCode;
    }

    public PandoraErrorCode errorCode() {
        return errorCode;
    }

    /** The numeric code as sent by Pandora, useful when {@link #errorCode()} is {@code UNKNOWN}. */
    public int rawCode() {
        return rawCode;
    }

    private static String describe(PandoraErrorCode errorCode, int rawCode, String serverMessage) {
        if (errorCode != PandoraErrorCode.UNKNOWN) {
            return errorCode.message();
        }
        String detail = serverMessage == null || serverMessage.isBlank() ? "" : ": " + serverMessage;
        return "Pandora error " + rawCode + detail;
    }
}
