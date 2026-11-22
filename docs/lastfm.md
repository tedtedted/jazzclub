# Last.fm scrobbling: plan

Status: **agreed** (decisions at the end), nothing implemented yet. Discuss on the PR; this file becomes the README section
and is deleted once the feature ships.

## Why build it in

pianobar scrobbled internally from 2008 (`libwardrobe`) until March 2010, when it was removed in favour
of `event_command` scripts. jazzclub already speaks that interface, so a script works today. But that
setup is poor: install Python and pylast, register your own Last.fm API account, paste a key, a secret
and your password into a script, and debug it blind, because the script's output is thrown away.

Built in, it should take **two lines of the config file you already have**. This goes against
upstream's decision, on purpose; `event_command` keeps working for anyone who prefers a script.

## What the user sees

### Setting it up

```ini
# ~/.config/jazzclub/config
lastfm_user = ted
lastfm_password_command = pass show last.fm
```

Next start:

```
Welcome to jazzclub!
(i) Login... Ok.
(i) Get stations... Ok.
(i) Last.fm: scrobbling as ted.
```

That is the whole setup. There is no API account to register, no script to write, and no step to
repeat. jazzclub trades the password for a Last.fm session key once and remembers the key, so
`lastfm_password_command` is **not run on later starts**. That matters for anyone whose command asks for
a GPG passphrase.

### Without giving jazzclub your password

Leave the password out and jazzclub asks Last.fm for permission in your browser instead:

```ini
lastfm_user = ted
```

```
(i) Last.fm: to let jazzclub scrobble for you, open
    https://www.last.fm/api/auth?api_key=…&token=…
```

The music starts right away. jazzclub checks in the background every few seconds, and when you click
"Yes, allow access":

```
(i) Last.fm: scrobbling as ted.
```

Nothing to press, and it never asks again. If you don't approve within the hour the link is valid,
jazzclub gives up quietly and asks again on the next start.

### While listening

- **Now playing**: your Last.fm profile shows the song as it starts.
- **Scrobbles** follow Last.fm's rules: the song is longer than 30 seconds and you heard half of it or
  4 minutes, whichever comes first. Time spent paused does not count. Skipping early means no scrobble.
- **Banned songs follow the same rules.** Last.fm is a record of what you listened to, liked or not, so
  a song you banned after hearing enough of it is still scrobbled. Banning early skips the song, so it
  isn't scrobbled.
- **Loves**: `+` also loves the track on Last.fm. `-` removes a Last.fm love, if there is one. It can
  be turned off with `lastfm_love = 0`.
- **Quitting mid-song** still scrobbles the song if you had heard enough of it.

jazzclub stays quiet while things work. It only prints something when the situation changes, and only
once per change:

```
(i) Last.fm is unreachable, keeping scrobbles for later.
(i) Last.fm is back, sent 7 scrobbles.
```

Scrobbles made offline are saved to disk and sent on the next success, even after a restart, as long as
they are younger than Last.fm's 14-day limit.

### When something is wrong

Last.fm problems never stop the music and never cost you your scrobbles. **No Last.fm setting, however
wrong, stops jazzclub from starting or playing.**

Problems are reported as **one line, printed whether or not `-v` is on**. The reasoning: whoever set
`lastfm_user` asked for scrobbling. If it failed silently, they would discover it days later as a gap in
their history, and anything older than Last.fm's 14-day limit would be gone for good. The line appears
once and names the setting to fix. `-v` and `-vv` add the details, such as response codes and which
step failed.

- **Blank `lastfm_user`** means scrobbling is off, with no message, the same as a commented-out line.
- **No password and no command** is not an error. It means sign in with the browser, see above.
- **A failing `lastfm_password_command`** (wrong command, or you cancel the GPG prompt) turns scrobbling
  off for this run, with a message. It must not end the program the way a failing Pandora
  `password_command` does. Pandora cannot do without its password; the music can do without Last.fm.
- **A wrong password** is tried **once per run**. Retrying risks getting the Last.fm account locked.
- While signed out for any of these reasons, **scrobbles still go into the queue**. Fix the setting,
  restart, and they are sent, as long as they are younger than 14 days.

Every message says what to change:

| Situation | Message |
|---|---|
| Wrong password | `/!\ Last.fm: wrong user name or password for 'ted', not scrobbling. Check lastfm_user and lastfm_password in ~/.config/jazzclub/config.` |
| `lastfm_password_command` failed | `/!\ Last.fm: lastfm_password_command failed (exit status 2), not scrobbling.` |
| Access revoked on last.fm | `/!\ Last.fm: jazzclub is no longer allowed to scrobble for you, signing in again...` followed by a new sign-in, using the password or the browser |
| Both `lastfm_password` and `lastfm_password_command` set | the same rule as for Pandora: `lastfm_password` wins |
| Signed in in the browser as someone else | `/!\ Last.fm: you allowed access as 'tedred', but lastfm_user is 'ted'. Scrobbling as 'tedred'.` |

`-v` logs every request and response code; `-vv` adds the bodies (without secrets).

### Settings

| Key | Default | Meaning |
|---|---|---|
| `lastfm_user` | | Your Last.fm user name. Setting it turns scrobbling on. |
| `lastfm_password` | | Your Last.fm password. Only needed once, see above. |
| `lastfm_password_command` | | A shell command that prints it. Used when `lastfm_password` is not set. |
| `lastfm_love` | `1` | `0`: loving or banning a song on Pandora leaves Last.fm alone. |

That is the whole list. Last.fm's own rules decide what counts as a scrobble, so there is no threshold
setting (pianobar's `lastfm_scrobble_percent` from 2008 is not coming back). To stop scrobbling, comment
out `lastfm_user`.

## Design

### Nothing in the player knows about Last.fm

```
app (Radio, PlayerEvents) ──PlayerEvent──▶ Spring events ──┬─▶ eventcmd.EventCommandListener
                                                           └─▶ lastfm.LastFmListener (new)
```

The player already publishes `PlayerEvent`s through Spring, and `EventCommandListener` is one listener
among possibly many. Its own javadoc anticipates "a built-in scrobbler would be another class like this
one". The new `com.tedredington.jazzclub.lastfm` package depends on `app.event` and `pandora.model.Song`.
**Nothing depends on it.** An ArchUnit test (new test dependency) enforces this: no class outside
`..lastfm..` may import it. Deleting the package must leave a compiling, working player.

The only changes outside the package are these:

- `ConfigKey`: the four new keys. `lastfm_password` and `lastfm_password_command` are secrets, so they
  never reach Spring's `Environment`.
- `ConfigFileCredentialsProvider`: take its key names as parameters instead of hard-coding
  `user`/`password`/`password_command`, so Pandora and Last.fm share one implementation of the
  password rules (password wins over command, first line only, clear errors).
- `Event`: a new `Notice(MessageType, String)` record. It lets a background thread hand a message to the
  main loop, which prints it between key presses. That way the message cannot land in the middle of a
  station prompt the user is typing into. `AnsiConsole.print` is synchronized, but that does not stop a
  message breaking up a prompt line.

### Package layout

| Class | Job |
|---|---|
| `LastFmProperties` | `@ConfigurationProperties("jazzclub.lastfm")`: `user`, `love`, API key/secret overrides for tests. |
| `LastFmConfiguration` | `@ConditionalOnProperty("jazzclub.lastfm.user")`: with no user set, not a single Last.fm bean exists. |
| `LastFmListener` | `@EventListener` for `PlayerEvent`. Turns `songstart`/`songfinish`/`songlove`/`songban` into work for the `Scrobbler`. Remembers each song's start time by track token. |
| `Scrobbler` | Owns one worker thread (like `EventCommandRunner`): now-playing, scrobble, love. Flushes the queue, keeps track of online/offline state and reports changes. Drains for up to 5 s on exit. |
| `ScrobbleRules` | Pure function: `(Song, Duration played) → boolean`. The rating plays no part. |
| `Scrobble` | Record: artist, track, album, timestamp, duration. |
| `ScrobbleQueue` | Pending scrobbles in `~/.local/state/jazzclub/lastfm-queue`, one JSON object per line, rewritten atomically like `StateFile`. Capped at 2,000 entries (about 5 days of listening), oldest dropped first. |
| `LastFmSession` | Gets a session key (from the password or the browser), and saves and loads it in `~/.local/state/jazzclub/lastfm-session`, mode `600`. |
| `LastFmClient` | The HTTP API: `auth.getMobileSession`, `auth.getToken`, `auth.getSession`, `track.updateNowPlaying`, `track.scrobble` (batches of up to 50), `track.love`, `track.unlove`. `RestClient` + Jackson `JsonNode`, like the Pandora transport, so native-image needs no reflection config. |
| `ApiSignature` | `api_sig` = MD5 of the sorted parameters plus the shared secret. |
| `LastFmException` | Sealed: `AuthenticationFailed` (codes 4, 9, 14), `TemporarilyUnavailable` (11, 16, 29, network), `Rejected` (the rest). |

What each kind of error does:

- `TemporarilyUnavailable`: keep the scrobble queued and try again at the next song.
- `AuthenticationFailed`: forget the session key and sign in again, once. If that fails too, report it
  and stop signing in for this run, but keep queuing scrobbles.
- `CredentialsException` from the shared password code: caught in `LastFmSession` and treated like
  `AuthenticationFailed`. It must never reach the code that ends the program on a Pandora login failure.
- `Rejected`: log it and drop that one scrobble. Retrying it will never succeed.

### The API key

Every Last.fm client needs an API key and a shared secret, from an API account registered at
last.fm/api/account/create. jazzclub will ship its own, compiled in. That is standard practice for
open-source desktop scrobblers, and the only way to keep setup at two lines. The secret in a desktop
client cannot be kept secret. What it identifies is the application, not the user. Users' sessions are
still protected by their own password or their browser approval. `jazzclub.lastfm.api-key`/`api-secret`
remain as overrides for tests and forks.

**Ted needs to do this before phase 1 can be tried against the real service** (name: jazzclub,
callback URL: none).

### Testing

- `ScrobbleRules`: a table-driven test for the boundaries: 30 s, half the length, 4 minutes,
  zero-length songs.
- `ApiSignature`: an example with a known result.
- `LastFmClient`: `MockRestServiceServer`, like `RestClientPandoraTransportTest`. Covers every call, and
  every error code mapped to the right exception.
- `Scrobbler`: fakes for the client and a clock. Covers the queue across a restart, batching above 50,
  dropping anything older than 14 days, the offline/back-online messages appearing once, and signing in
  again after code 9.
- `LastFmListener`: sequences of `PlayerEvent`s: skip, early ban (no scrobble), late ban (scrobble
  and unlove), quit mid-song, love, `lastfm_love = 0`.
- Wiring: without `lastfm_user` the context has no Last.fm beans; with it, it does.
- Starting with bad settings still plays music: a wrong password, a failing password command and a
  blank user each start the app, print the expected line (or nothing, for the blank user), and still
  queue scrobbles.
- The ArchUnit dependency rule.
- A native-image smoke test: one scrobble against a local stub, if the existing native test setup
  allows it.

Coverage target as for the rest of the project, 80–90 %.

## Phases

Each phase is one PR onto `main`, green CI, usable by itself.

1. **Scrobbling with a password.** Settings, `LastFmClient` (mobile session, now playing, scrobble),
   session key saved on disk, `ScrobbleRules`, `Scrobbler` without the offline queue, `Notice` event,
   ArchUnit rule, README section. All of the "never stops jazzclub" behaviour is in this phase.
   Scrobbles made while signed out are only kept once phase 2 adds the queue.
2. **Never lose a scrobble.** `ScrobbleQueue`, batching, the 14-day limit, the offline/back messages,
   signing in again after code 9.
3. **Browser sign-in.** `auth.getToken`/`auth.getSession` with background polling, no password needed.
4. **Loves.** `track.love`/`unlove`, `lastfm_love`.

Phases 2–4 are independent of each other once phase 1 is in.

## Decisions

Agreed with Ted on 2026-09-26:

1. **Banned songs** are scrobbled under the same rules as any other song. Last.fm records what you
   heard, not what you liked.
2. **`lastfm_love`** is on by default.
3. **Signing in with a password comes first** (phase 1); browser sign-in follows in phase 3.
4. **Scrobbling from `event_command` too** would scrobble every song twice. The README says so. There is
   no start-up warning, because jazzclub cannot tell what a script does, and a warning would nag
   everyone who uses `event_command` for notifications.
5. **Out of scope:** Libre.fm, ListenBrainz and a key to pause scrobbling. They are parked in
   tedtedted/jazzclub#6.
