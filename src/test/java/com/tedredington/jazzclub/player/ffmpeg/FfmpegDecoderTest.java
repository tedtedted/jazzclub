package com.tedredington.jazzclub.player.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * How ffmpeg's exit status and log turn into a result. Running the real process is
 * {@code DecoderConformanceTest}'s job.
 */
class FfmpegDecoderTest {

    @Test
    void aCleanExitIsASuccess() {
        assertThat(FfmpegDecoder.failure(0, "")).isNull();
    }

    /** A dropped connection that ffmpeg resumed logs this, and the song is complete. */
    @Test
    void aResumedConnectionIsASuccess() {
        assertThat(FfmpegDecoder.failure(0, "[http @ 0x1] Stream ends prematurely at 20000, should be 60425"))
                .isNull();
    }

    /** Server gone or file cut off: ffmpeg still exits 0, but its MP4 reader says so. */
    @Test
    void aPartialFileIsAFailureDespiteTheCleanExit() {
        String log = """
                [http @ 0x1] Stream ends prematurely at 20000, should be 60425
                [in#0/mov,mp4,m4a,3gp,3g2,mj2 @ 0x2] stream 0, offset 0x4e3d: partial file
                [aac @ 0x3] decode_band_types: Input buffer exhausted before END element found""";

        assertThat(FfmpegDecoder.failure(0, log)).isEqualTo(
                "Decoding failed: the stream ended early ([in#0/mov,mp4,m4a,3gp,3g2,mj2 @ 0x2] stream 0, "
                        + "offset 0x4e3d: partial file)");
    }

    @Test
    void anErrorExitReportsTheLastLine() {
        assertThat(FfmpegDecoder.failure(1, "first\nServer returned 404 Not Found"))
                .isEqualTo("Decoding failed: Server returned 404 Not Found");
        assertThat(FfmpegDecoder.failure(187, "")).isEqualTo("Decoding failed: exit status 187");
    }
}
