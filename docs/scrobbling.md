# Design: scrobbling to more than one service

Status: draft, for discussion. Nothing here is implemented yet.

## Why

Last.fm announced on 18 November 2026 that from **1 December 2026** an API key keeps working only while
the account that owns it has Last.fm Pro
([announcement](https://support.last.fm/t/api-keys-will-require-pro-from-1st-december/121030)).
jazzclub ships one key, owned by the maintainer, so every user's scrobbling now depends on one paid
subscription. There is no exemption for free or open-source clients; small tools are told to email
partners@last.fm.

[ListenBrainz](https://listenbrainz.org), run by the MetaBrainz Foundation alongside MusicBrainz, offers
the same service with no application key at all: each user pastes a token from their own settings page.

The goal is to add ListenBrainz, keep Last.fm for the people who still want it, and make a third
service a contained piece of work rather than a rewrite.

## Goals

- Scrobble to **any combination** of services at once, each switched on by its own settings.
- One service failing, being slow, or rejecting its credentials never affects another, and never
  affects playback (as today).
- Adding a service means one new package implementing one small interface, plus its config keys.
- Last.fm keeps working for users who bring their own key, without the maintainer paying for everyone.
- Existing Last.fm config keeps working unchanged.

## Non-goals

- **Plugins loaded at runtime.** The native image fixes its classes and beans at build time, and there
  are only a handful of scrobbling services worth supporting. Services are compiled in.
- A shared "scrobble queue" that survives restarts. Today a scrobble that cannot be sent is dropped
  with a notice; that stays the same per service.
- Importing or syncing history between services.

## What users see

### Config

A service is on when its credentials are set, exactly as `lastfm_user` works today. There is no
master list of services: having the credentials already says "on", and a second switch would only add
a way for the two to disagree.

```ini
# ~/.config/jazzclub/config

# ListenBrainz: token from https://listenbrainz.org/settings/
listenbrainz_token_command = pass show listenbrainz
listenbrainz_feedback = 1          # + loves, - hates; 0 leaves ListenBrainz feedback alone

# Last.fm, unchanged...
lastfm_user = ted
lastfm_password_command = pass show last.fm
lastfm_love = 1
# ...plus, new and optional: your own API account (needs Last.fm Pro after 1 December 2026)
lastfm_api_key = 0123456789abcdef0123456789abcdef
lastfm_api_secret_command = pass show last.fm-api-secret
```

New keys:

| Key | Default | Meaning |
|---|---|---|
| `listenbrainz_token` | | Your ListenBrainz user token. Turns on ListenBrainz. |
| `listenbrainz_token_command` | | A shell command that prints it. Used when `listenbrainz_token` is not set. |
| `listenbrainz_feedback` | `1` | `0`: loving or banning a song leaves ListenBrainz alone. |
| `listenbrainz_url` | `https://api.listenbrainz.org` | For self-hosted ListenBrainz servers. |
| `lastfm_api_key` | built in | Your own Last.fm API key. |
| `lastfm_api_secret` / `_command` | built in | Its shared secret. |

`lastfm_url` (pointing Last.fm's client at a compatible server such as Libre.fm) falls out of the same
work and may be worth adding, but is not required.

### Command line

`--no-scrobble` turns every service off for one run (testing, or someone else at the keyboard).

### Messages

One line at startup naming what is on, then every message names its service:

```
(i) Scrobbling to ListenBrainz as ted, Last.fm as ted.
(!) Last.fm: this API key needs Last.fm Pro, not scrobbling to Last.fm.
(!) ListenBrainz: unreachable; songs played meanwhile will not be scrobbled there.
(!) ListenBrainz: the token was rejected, not scrobbling to ListenBrainz. Check listenbrainz_token.
```

Each service reports a given problem once, as `Scrobbler` does today.

### Thumbs

| Pandora | Last.fm (`lastfm_love`) | ListenBrainz (`listenbrainz_feedback`) |
|---|---|---|
| `+` love | `track.love` | feedback score `1` |
| `-` ban | `track.unlove` | feedback score `-1` |
| song already loved starts playing | `track.love`, once per run | feedback `1`, once per run |

ListenBrainz can record a dislike, which Last.fm cannot; a ban maps to it. Whether a ban should
instead *clear* feedback (score `0`) is an open question below.

## Code shape

```
scrobble/                       knows no service
  ScrobbleService               the interface every service implements
  ScrobbleListener              the one @EventListener: song start / finish / love / ban
  ScrobbleRules                 moved from lastfm/, unchanged
  ServiceWorker                 one per service: queue, thread, notices, drain on exit
  ScrobbleException             Unavailable | Rejected | CredentialsRejected | Fatal
  Track, Listen, Feedback       what the player hands to services
  ScrobbleConfiguration         collects the services that are on, builds the listener
lastfm/                         LastFmService implements ScrobbleService
listenbrainz/                   ListenBrainzService implements ScrobbleService
```

### The interface

```java
public interface ScrobbleService {

    /** "Last.fm", "ListenBrainz": the prefix on every message. */
    String name();

    /** Who is signed in, for the startup line. May sign in, so it runs on the worker. */
    String user();

    void nowPlaying(Track track);

    /** Called only for listens that pass ScrobbleRules. */
    void scrobble(Listen listen);

    /** Called only when the user wants feedback sent to this service. */
    void feedback(Track track, Feedback feedback);
}
```

Every method may throw a `ScrobbleException`. Each service maps its own error codes onto the four
kinds, which is what `LastFmException` already does for Last.fm's codes:

| Kind | Worker does | Last.fm | ListenBrainz |
|---|---|---|---|
| `Unavailable` | report once, keep going | 8, 11, 16, 29, network errors | 429, 5xx, network errors |
| `Rejected` | log, keep going | other codes | 400 on one request |
| `CredentialsRejected` | stop this service, say which setting | 4, 9 after re-signing in | 401 |
| `Fatal` | stop this service | 10, 13, 26 (key invalid / needs Pro) | |

### What moves where

- `LastFmListener` becomes `ScrobbleListener`, unchanged in substance: it remembers when the song
  started, applies `ScrobbleRules` at the end, and turns ratings into `Feedback`. It calls every service
  that is on.
- The "loved this run" set moves into each service, because what counts as the same song differs
  (artist and title, case-insensitive, for Last.fm; a recording identifier for ListenBrainz).
- `Scrobbler`'s thread, queue, notices and drain become `ServiceWorker`, one per service, so a slow
  Last.fm never delays ListenBrainz.
- Sign-in, sessions, `ApiSignature` and `SessionFile` stay inside `lastfm/`. ListenBrainz needs none of
  them.
- `Track` moves to `scrobble/` and gains nothing yet. If a later source provides MusicBrainz IDs or
  ISRCs, they go here.

### Native image

Every service's `@Configuration` is always present, as `LastFmConfiguration` is today. Each contributes
a `ScrobbleService.Provider` bean whose `create()` reads the config file at startup and returns either
a service or nothing. The set of beans is fixed at build time; the config decides what runs.

## ListenBrainz specifics

All calls send `Authorization: Token <user token>`
([API docs](https://listenbrainz.readthedocs.io/en/latest/users/api/core.html)).

| jazzclub event | Request |
|---|---|
| song starts | `POST /1/submit-listens?return_msid=true`, `listen_type: playing_now`, no timestamp |
| song finishes, passes rules | `POST /1/submit-listens`, `listen_type: single`, `listened_at` = start time |
| love / ban / already loved | `POST /1/feedback/recording-feedback`, score `1` / `-1` |
| startup | `GET /1/validate-token`, gives the user name for the startup line |

Listen payload, from the
[JSON docs](https://listenbrainz.readthedocs.io/en/latest/users/json.html):

```json
{
  "listen_type": "single",
  "payload": [{
    "listened_at": 1763480000,
    "track_metadata": {
      "artist_name": "Bill Evans Trio",
      "track_name": "Waltz for Debby",
      "release_name": "Waltz for Debby",
      "additional_info": {
        "duration_ms": 412000,
        "media_player": "jazzclub",
        "submission_client": "jazzclub",
        "submission_client_version": "0.3.0",
        "music_service": "pandora.com"
      }
    }
  }]
}
```

ListenBrainz asks for the same rule jazzclub already applies (half the track or four minutes,
whichever comes first), so `ScrobbleRules` is shared as is.

### Identifying a song for feedback

Last.fm identifies a song by its text: `track.love` takes artist and title, and Last.fm matches them
itself. ListenBrainz feedback takes a **recording identifier** instead, and Pandora gives us none.
There are three ways to get one:

1. **`recording_msid`, from the "now playing" call.** Every listen ListenBrainz receives gets a
   MessyBrainz ID (msid) for its text as submitted. `submit-listens` with `return_msid=true` returns it
   for a `playing_now` listen, and the docs say it exists so a client can "submit love/hate feedback
   before a full listen is sent". Feedback accepts `{"recording_msid": …, "score": 1}`.
   This is the direct equivalent of what we do with Last.fm: ListenBrainz identifies the song from our
   text, with no lookup on our side and no extra request.
2. **`recording_mbid`, from ListenBrainz's lookup.** `GET /1/metadata/lookup/?artist_name=…&recording_name=…&release_name=…`
   with the same user token returns `recording_mbid` when ListenBrainz's fuzzy matcher finds the
   recording. A 200 with no `recording_mbid` means no match; there is no confidence score.
3. **`recording_mbid`, from MusicBrainz search.** `GET https://musicbrainz.org/ws/2/recording?query=…`
   with artist, title, release and duration. Gives candidates with a relevance score, but MusicBrainz
   allows about one request per second per IP and needs a descriptive User-Agent
   ([rate limiting](https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting)).

**Proposal: use the msid, and only the msid, in the first version.** Every feedback jazzclub sends is
about the song that is playing (`+` and `-` act on the current song, and an already-loved song is loved
as it starts), so the msid from that song's "now playing" call is always at hand. It costs no extra
request, needs no matching code, and cannot pick the wrong recording.

ListenBrainz also maps submitted listens to MusicBrainz recordings on its own, so a listen sent with
text only still shows up linked to the right recording when the matcher finds it. Feedback given by
msid is, as far as the docs say, tied to the same text, so it should follow that mapping too. **This
needs checking against a real account** before we rely on it (see the spike below).

Options 2 and 3 stay available if feedback by msid turns out not to show up on recordings. Option 2 is
the one to try first: one request with the token we already have, and no separate rate limit to
respect. Option 3 would need a cache and a throttle and is a last resort.

If the "now playing" call failed and there is no msid, feedback for that song is skipped and logged.

## Spike before implementing

A throwaway script against a test ListenBrainz account, about an hour's work:

1. Submit `playing_now` with `return_msid=true` for five Pandora songs; confirm an msid comes back.
2. Send feedback by msid for each; confirm it appears on the user's "loved" page and, where
   ListenBrainz mapped the listen, on the MusicBrainz recording.
3. Call `/1/metadata/lookup/` for twenty songs from a real Pandora session (jazz, with live takes and
   compilations) and count how many come back with the right `recording_mbid`. This decides whether
   option 2 is worth adding later.
4. Note any rate-limit headers in the responses; the docs do not state ListenBrainz's limits.

## Plan

Each step is its own PR and leaves a working player.

1. **Last.fm: bring your own key.** `lastfm_api_key` / `lastfm_api_secret[_command]`, falling back to the
   built-in key. Clearer message for "this key needs Pro". Small, and useful before 1 December
   regardless of anything else.
2. **Extract `scrobble/`.** Move `ScrobbleRules`, `Track`, the listener and the worker out of `lastfm/`
   behind `ScrobbleService`, with Last.fm as the only implementation. No change in behaviour; the
   existing Last.fm tests should pass with only package changes.
3. **ListenBrainz.** `listenbrainz/` with listens, now playing and msid feedback. README section.
4. **`--no-scrobble`.**
5. **Decide the built-in Last.fm key's future** once step 1 has shipped: keep paying for Pro, apply to
   partners@last.fm, or drop the built-in key and require users to bring their own.

## Open questions

- **Ban on ListenBrainz: hate (`-1`) or clear (`0`)?** Pandora's ban means "never play this again",
  which is closer to hate. But Last.fm's mapping (ban takes the love back) suggests clear. Proposal:
  hate, since ListenBrainz can say what we mean.
- **Keep the built-in Last.fm key at all?** It is the easiest experience for users, but it ties
  everyone to one subscription and one rate limit. Decided in step 5.
- **`music_service`.** The docs want a canonical domain for the streaming service; Pandora is not on
  their list. Proposal: `pandora.com`.
- **Should "now playing" failures be reported?** Today they are logged only. With the msid depending
  on that call, a failing "now playing" also silently drops feedback. Proposal: still log only, but
  count it toward the "unreachable" notice.
