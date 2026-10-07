# Security review and follow-up plan

Reviewed on October 6, 2026. This document tracks findings from a source review and
targeted local checks. It does not change application behavior or represent a
complete penetration test, dependency audit, or native-code audit. No critical
vulnerability or remote code execution was demonstrated.

The application is a local CLI without a web server. Its principal trust boundaries
are remote API responses, audio downloads and decoders, terminal output, local
configuration and state, and optional control pipes and scripts.

## 1. Verify audio transport consistently

**Priority: first. Estimated severity: medium. Status: open.**

`src/main/java/com/tedredington/jazzclub/player/ffmpeg/FfmpegCommand.java` does not
explicitly enable TLS certificate verification. This path is used when FFmpeg is
selected, when the built-in decoder cannot load, or when resampling is required.

A local check on macOS with FFmpeg 8.1.2 used the application's generated command
against a loopback HTTPS server with a self-signed certificate and synthetic AAC
audio. The command exited successfully and produced 36,864 bytes of PCM. Adding
`-tls_verify 1` to the same command rejected the certificate and produced no PCM.
This establishes missing certificate verification in that configuration; it does
not independently test hostname verification or establish a decoder exploit.

Both decoder paths accept HTTP audio URLs. The built-in downloader in
`src/main/java/com/tedredington/jazzclub/player/lavaplayer/SongDownload.java` also
allows HTTPS redirects to HTTP. These are source-review observations; a live
Pandora compatibility check and a separate downgrade reproduction remain to be done.

A network attacker able to intercept an unverified or cleartext audio connection
could substitute media. Code execution would require an additional decoder flaw
that this review did not establish. Pandora and Last.fm account API calls use a
separate HTTPS client; this finding does not demonstrate password interception.

- [ ] Choose an audio transport approach: fetch with the existing JDK HTTP client
      and feed FFmpeg, or enforce certificate and hostname verification in supported
      FFmpeg configurations.
- [ ] Reject HTTPS-to-HTTP redirects. Confirm Pandora CDN compatibility before
      requiring HTTPS for all initial audio URLs; explicitly document any necessary
      cleartext exception.
- [ ] Add regression checks for untrusted certificates, trusted certificates with
      the wrong hostname, valid certificates, downgrade redirects, and FFmpeg fallback.

FFmpeg's defaults vary across versions and backends. Consult the supported release's
[TLS implementation](https://ffmpeg.org/doxygen/8.1/tls_8h_source.html) and verify
behavior rather than relying solely on current online protocol documentation.

## 2. Sanitize external text before terminal rendering

**Priority: first. Estimated severity: medium, conditional on input control and
terminal behavior. Status: open.**

`src/main/java/com/tedredington/jazzclub/ui/Renderer.java` inserts station names and
song metadata without escaping terminal controls. `AnsiConsole.java` prints the
result directly. External error messages can also reach the terminal.

A local check passed a dummy station name containing an OSC52 sequence through the
actual renderer and console, capturing the result in memory. The sequence survived
unchanged. It was not sent to a terminal, and this review did not establish that
Pandora accepts such station names or that an attacker can deliver them to a victim.

If an attacker can influence displayed text, escape sequences may spoof the screen
or modify the clipboard on terminals that permit OSC52. This is not a demonstrated
arbitrary-command-execution vulnerability.

- [ ] Sanitize external metadata and error text before inserting it into trusted
      application formatting; preserve intentional controls in the application's UI.
- [ ] Cover station lists, now-playing text, search results, information screens,
      remote errors, and relevant console logs.
- [ ] Add regression checks for ESC/OSC/CSI, C1 controls, embedded line breaks, and
      ordinary Unicode metadata.

## 3. Enforce local ownership and permission boundaries

**Priority: next. Impact can be high if another local user can write these paths.
Status: open.**

`src/main/java/com/tedredington/jazzclub/remote/ControlFifo.java` validates the file
type but not ownership or write permissions. A local check confirmed that it opens
a FIFO with mode `0666`. Pipe input enters the same event queue as keyboard input,
including answers to account-setting prompts. Source review shows that account
changes use credentials already held by the application; an insecure pipe can
therefore expose more than playback controls. No real account was modified.

`src/main/java/com/tedredington/jazzclub/config/file/UserConfigFile.java` warns about
readable plaintext passwords but does not warn about unsafe write permissions. A
dummy config with mode `0666` containing `password_command` produced no warnings.
An attacker who can modify that configuration can arrange command execution as the
application's user when the command is next read and run.

The README's private `0700` config directory protects the documented normal setup.
These are conditional risks for permissive directories, custom paths, or shared
machines, not evidence that another user can reach the default private installation.

- [ ] Check ownership and unsafe group/other write permissions on configuration,
      control FIFOs, and their relevant parent directories.
- [ ] Decide whether unsafe paths should fail closed or require an explicit opt-in
      for intentionally shared remote control.
- [ ] Document `mkfifo -m 600` and test an inaccessible private default, permissive
      custom paths, and supported sharing exceptions.
- [ ] Review native-library installation and cache directories as executable-code
      trust boundaries, including replacement and symlink races.

## 4. Keep signed audio URLs out of diagnostics

**Priority: next. Estimated severity: low. Status: open.**

The FFmpeg TLS failure check included the dummy audio URL query token in stderr.
`src/main/java/com/tedredington/jazzclub/player/ffmpeg/FfmpegDecoder.java` retains
stderr and uses selected lines in failure messages. The review did not demonstrate
exposure of a Pandora account token or password through this path, and not every
captured line is necessarily displayed.

- [ ] Test displayed and logged FFmpeg failures with dummy signed URLs, and redact
      URL credentials/query tokens before exposing diagnostic text.

## Last.fm: accepted tradeoff and optional authentication improvement

The API key and shared secret compiled into distributed builds should be treated
as extractable application credentials. Keeping them out of Git does not make them
secret in a distributed native binary. Their exposure permits application
impersonation; abuse could lead to API-key suspension and interrupt scrobbling for
users. This is a risk assessment, not an observed suspension or abuse incident.

These application credentials alone do not authorize writes to arbitrary users'
accounts. Authenticated calls also require a user session key. The application
already creates its saved session file with owner-only POSIX permissions and avoids
logging authentication response bodies.

A proxy is not required for the following improvement:

- [ ] Consider the desktop flow `auth.getToken` -> browser approval ->
      `auth.getSession`, so jazzclub no longer needs to collect a Last.fm password.
      The current implementation uses `auth.getMobileSession`.
- [ ] Document session revocation and API-key rotation/release recovery. Consider
      user-supplied application credentials as an optional recovery mechanism.

References: [Last.fm desktop authentication](https://www.last.fm/api/desktopauth),
[authentication specification](https://www.last.fm/api/authspec), and
[mobile session method and suspension error](https://www.last.fm/api/show/auth.getMobileSession).

## Dependency alerts observed when creating this tracker

**Priority: triage alongside the transport and terminal fixes. Status: open.**

On October 6, 2026, GitHub's Dependabot API reported 18 open alerts on the default
branch: 12 high, 5 medium (shown as moderate in GitHub's push summary), and 1 low.
This is a point-in-time dependency finding, not proof of 18 exploitable application
vulnerabilities. Some advisories affect both Jackson major versions and therefore
appear in more than one alert.

| Dependency group | Alerts | Highest severity | Patched versions reported by GitHub |
| --- | --- | --- | --- |
| Jackson 3 core/databind (`tools.jackson.core`) | 4, 6, 8, 11, 13, 15, 17 | High | Individual fixes in 3.1.6; all listed fixes in 3.1.7 |
| Jackson 2 core/databind (`com.fasterxml.jackson.core`) | 5, 7, 9, 10, 12, 14, 16 | High | Individual fixes in 2.21.6; all listed fixes in 2.21.7 |
| jsoup | 3, 18 | High | 1.23.1 and 1.23.2 respectively |
| Commons IO | 1 | High | 2.14.0 |
| Rhino | 2 | Low | 1.7.14.1 |

The review's runtime dependency tree includes both Jackson major versions, jsoup
1.16.1, Commons IO 2.13.0, and Rhino 1.7.14. Jackson 3 is used directly for API JSON;
the other listed packages arrive through LavaPlayer. The app reads API JSON as
trees and uses only a subset of LavaPlayer. Reachability of the particular affected
parsers, coercions, deserializers, source managers, and scripting functions remains
to be checked before judging application impact.

- [ ] Triage every open alert against reachable production behavior; document any
      dismissal with a specific code-path rationale.
- [ ] Update affected dependencies or the parent/library that manages them, and
      verify compatibility with the native build and both decoders.
- [ ] Check GitHub's alert state after remediation; do not rely on the counts in
      this snapshot remaining current.

Source: [repository Dependabot alerts](https://github.com/tedtedted/jazzclub/security/dependabot).

## Focused security testing beyond these findings

- [ ] Inventory and scan runtime dependencies, including the native decoder code
      embedded in `libconnector`; evaluate whether reported vulnerable code is
      reachable through jazzclub's restricted use of LavaPlayer.
- [ ] Exercise malformed MP4/AAC inputs, excessive parser allocations and CPU use,
      stalled streams, and resource cleanup with synthetic fixtures and fuzzing.
- [ ] Verify credentials and session keys stay out of normal/debug diagnostics and
      crash reports using dummy secrets.

Existing protections include the absence of an inbound web server, HTTPS account
API defaults, owner-only Last.fm session-file creation, redacted credential
representations, bounded audio downloads, and direct event-script invocation that
does not interpolate metadata into a shell command.
