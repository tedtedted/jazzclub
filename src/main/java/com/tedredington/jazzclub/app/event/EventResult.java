package com.tedredington.jazzclub.app.event;

import com.tedredington.jazzclub.pandora.error.PandoraApiException;
import com.tedredington.jazzclub.pandora.error.PandoraException;

/**
 * Whether the operation behind an event worked, in pianobar's terms: {@code pRet} is its Pandora
 * result code and {@code wRet} its network (curl) result code, each with a text.
 */
public record EventResult(int pandoraCode, String pandoraMessage, int networkCode, String networkMessage) {

    /** pianobar's {@code PIANO_RET_OK}. */
    private static final int PIANO_OK = 1;
    /** pianobar adds this to the API's own error codes. */
    private static final int PIANO_API_OFFSET = 1024;
    private static final int NETWORK_OK = 0;
    /** curl's {@code CURLE_COULDNT_CONNECT}; jazzclub does not distinguish network failures further. */
    private static final int NETWORK_FAILED = 7;

    public static final EventResult OK = new EventResult(PIANO_OK, "Everything is fine :)", NETWORK_OK, "No error");

    public static EventResult of(PandoraException failure) {
        return switch (failure) {
            case PandoraApiException api ->
                    new EventResult(api.rawCode() + PIANO_API_OFFSET, api.getMessage(), NETWORK_OK, "No error");
            case com.tedredington.jazzclub.pandora.error.PandoraTransportException transport ->
                    new EventResult(PIANO_OK, "Everything is fine :)", NETWORK_FAILED, transport.getMessage());
            // pianobar's generic PIANO_RET_ERR
            default -> new EventResult(0, failure.getMessage(), NETWORK_OK, "No error");
        };
    }

    public boolean isOk() {
        return pandoraCode == PIANO_OK && networkCode == NETWORK_OK;
    }
}
