# jazzclub

A console client for [Pandora](https://www.pandora.com) internet radio.

jazzclub is a port of the wonderful [pianobar](https://codeberg.org/purplesym/pianobar) to Java. It looks
the same, uses the same keys and reads the same config format, and ships as a single native binary
that starts in a few milliseconds.

```
Welcome to jazzclub (0.1.0)! Press ? for a list of commands.
(i) Login... Ok.
(i) Get stations... Ok.
         0) q   Bill Evans Radio
         1)     Gabriel Fauré Radio
         2)  Q  QuickMix
[?] Select station: 0
|>  Station "Bill Evans Radio" (139224562502996745)
(i) Receiving new playlist... Ok.
|>  "Peace Piece" by "Bill Evans" on "Everybody Digs Bill Evans" <3
#   -05:41/06:43
```

> Pandora is only available in the United States. You need a Pandora account; a free one works.

## Requirements

- **ffmpeg** on your `PATH`. jazzclub uses it to decode the audio stream.

  | System | Install |
  |---|---|
  | macOS | `brew install ffmpeg` |
  | Arch Linux | `sudo pacman -S ffmpeg` |
  | Debian / Ubuntu | `sudo apt install ffmpeg` |

- On **Linux**, the ALSA library, which every desktop system has (`libasound2` on Debian and Ubuntu,
  `alsa-lib` on Arch). With PipeWire or PulseAudio also their ALSA bridge, see
  [Troubleshooting](#troubleshooting).

Nothing else. The binary contains everything it needs; no Java installation is required to run it.

## Install

Packages are on the [releases page](https://github.com/tedtedted/jazzclub/releases). Each pulls in
`ffmpeg`, and on Linux the ALSA library, through your package manager.

**Debian, Ubuntu** (Debian 12 and Ubuntu 22.04 or newer; `amd64` and `arm64`)

```sh
sudo apt install ./jazzclub_0.1.0_amd64.deb
```

**Arch Linux** (`x86_64` and `aarch64`)

```sh
sudo pacman -U jazzclub-0.1.0-1-x86_64.pkg.tar.zst
```

**macOS** on Apple Silicon

```sh
brew install ffmpeg
tar -xzf jazzclub-0.1.0-macos-arm64.tar.gz
sudo install -m 755 jazzclub-0.1.0-macos-arm64/bin/jazzclub /usr/local/bin/
xattr -d com.apple.quarantine /usr/local/bin/jazzclub    # macOS quarantines downloaded binaries
```

Every release comes with `SHA256SUMS-*` files; check your download with `sha256sum -c` (on macOS
`shasum -a 256 -c`). The Linux packages install tab completion for bash; the macOS archive has it
under `share/bash-completion/`.

A Homebrew tap and an AUR package are planned. To build from source instead, see
[Building](#building).

## Quick start

1. Create the config file:

   ```sh
   mkdir -p ~/.config/jazzclub && chmod 700 ~/.config/jazzclub
   $EDITOR ~/.config/jazzclub/config
   ```

   ```ini
   user = you@example.com
   password = your-pandora-password
   ```

   ```sh
   chmod 600 ~/.config/jazzclub/config
   ```

2. Run `jazzclub`, type a station number (or part of its name) and press Enter.

Better than a password in a file: let a password manager hand it over, see
[Keeping your password out of the config file](#keeping-your-password-out-of-the-config-file).

## Usage

```
Usage: jazzclub [-hVv] [-c=FILE]

Options:
  -c, --config=FILE   Use FILE instead of the default config file.
  -v, --verbose       Log what jazzclub is doing. Repeat (-vv) for debug detail
                        including stack traces.
  -h, --help          Show this help message and exit.
  -V, --version       Print version information and exit.
```

Note that `-v` is *verbose*; the version is a capital `-V`.

Exit status: `0` after a normal quit, `1` if jazzclub could not sign in or has no credentials,
`2` for a mistake on the command line.

### Keys

jazzclub is controlled with single key presses while it plays. No Enter needed.

| Key | Action | Config key |
|:---:|---|---|
| `?` | show this list | `act_help` |
| `q` | quit | `act_quit` |
| **Playback** | | |
| `n` | next song | `act_songnext` |
| `p` | pause/resume playback | `act_songpausetoggle` |
| `Space` | pause/resume playback | `act_songpausetoggle2` |
| `S` | pause playback | `act_songpause` |
| `P` | resume playback | `act_songplay` |
| `(` | decrease volume | `act_voldown` |
| `)` | increase volume | `act_volup` |
| `^` | reset volume | `act_volreset` |
| **The playing song** | | |
| `+` | love song | `act_songlove` |
| `-` | ban song | `act_songban` |
| `t` | tired (ban song for 1 month) | `act_songtired` |
| `e` | explain why this song is played | `act_songexplain` |
| `i` | print information about song/station | `act_songinfo` |
| `u` | upcoming songs | `act_upcoming` |
| `h` | song history | `act_history` |
| `b` | bookmark song/artist | `act_bookmark` |
| `v` | create new station from song or artist | `act_stationcreatefromsong` |
| **Stations** | | |
| `s` | change station | `act_stationchange` |
| `c` | create new station | `act_stationcreate` |
| `g` | add genre station | `act_stationaddbygenre` |
| `j` | add shared station | `act_addshared` |
| `a` | add music to station | `act_stationaddmusic` |
| `r` | rename station | `act_stationrename` |
| `d` | delete station | `act_stationdelete` |
| `x` | select quickmix stations | `act_stationselectquickmix` |
| `=` | manage station seeds/feedback/mode | `act_managestation` |
| **Account** | | |
| `!` | change settings | `act_settings` |

`Ctrl-C` quits as well.

### Choosing a station

The station list is sorted by name. At the `Select station:` prompt you can

- type a **number** and press Enter, or
- type **part of a name** to narrow the list. If only one station is left it is selected for you.
- Press Enter on an empty line to go back without changing anything.

The letters in front of a name mean: `q` the station is part of your QuickMix, `Q` it *is* the
QuickMix, `S` it was shared with you by somebody else.

### Song history

`h` lists the songs that played before the current one. Pick one, and jazzclub asks `What to do with
this song?`. Press any song key: `+` to love it after all, `-` or `t` to get rid of it, `b` to
bookmark it, `v` to make a station from it, `e` to have it explained. The key applies to the song you
picked and the station it came from, not to what is playing; banning a past song does not skip the
current one. `history = 0` in the config turns the history off.

### Account settings

`!` shows your Pandora user name and whether the explicit content filter is on, and lets you change
them and your password. Enter a number to change a setting, an empty line when you are done; all
changes are sent together. A new password is not shown while you type it. jazzclub carries on with
the new login for the rest of the session, but it cannot edit your config file: update `user`,
`password` or whatever `password_command` reads yourself, or the next start will fail to log in.

### Creating and editing stations

`c` asks for an artist or a song title, searches Pandora and lets you pick a match. If both artists
and songs were found it first asks which you meant. Every list works like the station menu: a
number selects, text narrows the list, an empty line backs out. `v` makes a station from the song
that is playing, `g` offers Pandora's genre stations, and `j` adds a station somebody shared with
you (the long number from its URL, without the leading `sh`). A new station is added to your list;
press `s` to switch to it.

`a`, `r` and `d` change the station that is playing. Deleting asks for confirmation and needs an
explicit `y`.

`x` edits your QuickMix and only works while QuickMix itself is playing. Pick stations to toggle
their `q` flag; `a` selects all, `n` none, `t` inverts. An empty line saves.

`=` lets you undo what shaped the playing station. It offers only what the station has: delete an
`[a]rtist` or `[s]ong` seed, take back a thumbs up or down under `[f]eedback`, or switch the station's
`[m]ode` (Pandora's "Deep Cuts", "Discovery", "Crowd Faves" and so on). After a mode change the queue
is dropped and the next song already follows the new mode.

A station shared by somebody else is read-only. The first time you rate a song on it or edit it,
jazzclub turns it into your own copy (`Transforming station...`), exactly as pianobar does.

## Configuration

jazzclub reads `~/.config/jazzclub/config`, or `$XDG_CONFIG_HOME/jazzclub/config` if that variable
is set. Pass `--config FILE` to use another file.

jazzclub remembers the volume and the station you were listening to in
`~/.local/state/jazzclub/state` (`$XDG_STATE_HOME`), and picks up there on the next start. Anything
you set in the config file takes precedence. Quit with nothing playing, and the next start shows the
station menu again.

The format is pianobar's: one `key = value` per line, `#` starts a comment. Values are taken
literally, without quotes or escapes. **You can copy your pianobar config as it is**; settings jazzclub
does not support yet are ignored (`-v` lists them).

```ini
# ~/.config/jazzclub/config
user = you@example.com
password_command = pass show pandora

audio_quality = high
volume = -3
autostart_station = 139224562502996745

# vim-ish: love with l, never ban by accident
act_songlove = l
act_songban = disabled
```

### Settings

| Key | Default | Meaning |
|---|---|---|
| `user` | | Your Pandora email address. |
| `password` | | Your password, in plain text. Make the file `chmod 600`; jazzclub warns you otherwise. |
| `password_command` | | A shell command that prints the password. Used when `password` is not set. |
| `audio_quality` | `high` | `low`, `medium` or `high`. Free accounts get AAC at every level. |
| `volume` | what you left it at | Initial volume correction in dB. Usually between -30 and +5. |
| `buffer_seconds` | `5` | How much audio to keep decoded ahead of what you hear, to bridge network hiccups. |
| `sample_rate` | `0` | Output sample rate in Hz. `0` keeps Pandora's 44100. |
| `audio_pipe` | | Write raw audio to this named pipe instead of playing it, see [Multi-room audio](#multi-room-audio). |
| `gain_mul` | `1.0` | How much of Pandora's per-track loudness correction to apply; `0.0` turns it off. |
| `autostart_station` | the last one played | Station id to play right away. Press `i` to see the id of the current station. |
| `sort` | `name_az` | Order of the station list: `name_az`, `name_za`, or with QuickMix pinned last (`quickmix_01_name_az`, `quickmix_01_name_za`) or first (`quickmix_10_name_az`, `quickmix_10_name_za`). |
| `autoselect` | `1` | `0`: never pick a station for you, even if your filter leaves only one. |
| `history` | `5` | How many played songs to remember. |
| `event_command` | | A program to run on every event, see [Event scripts](#event-scripts). |
| `lastfm_user` | | Your Last.fm user name. Turns on [scrobbling](#lastfm). |
| `lastfm_password` | | Your Last.fm password. Only needed once, see [Last.fm](#lastfm). |
| `lastfm_password_command` | | A shell command that prints it. Used when `lastfm_password` is not set. |
| `fifo` | `~/.config/jazzclub/ctl` | Named pipe for [remote control](#remote-control). |
| `max_retry` | `3` | Playback failures in a row before jazzclub stops the station. |
| `timeout` | `30` | Network timeout in seconds. |
| `act_*` | see [Keys](#keys) | A single character, or `disabled`. |

The look of the output can be changed with pianobar's format strings:

| Key | Default | Placeholders |
|---|---|---|
| `format_nowplaying_song` | `"%t" by "%a" on "%l"%r%@%s` | `%t` title, `%a` artist, `%l` album, `%r` rating icon, `%@` the `at_icon` when playing QuickMix, `%s` the song's real station, `%u` detail URL |
| `format_nowplaying_station` | `Station "%n" (%i)` | `%n` name, `%i` id |
| `format_list_song` | `%i) %a - %t%r` | `%i` number, `%a` artist, `%t` title, `%r` rating icon, `%d` duration, `%@`, `%s` |
| `format_time` | `%s%r/%t` | `%e` elapsed, `%r` remaining, `%t` total, `%s` sign |
| `love_icon`, `ban_icon`, `tired_icon`, `at_icon` | ` <3`, ` </3`, ` zZ`, ` @ ` | |

The prefixes in front of every line can be changed as well. Each setting is text with one `%s` where
the message goes; a value without `%s` is ignored.

| Key | Default |
|---|---|
| `format_msg_none` | `%s` |
| `format_msg_info` | `(i) %s` |
| `format_msg_nowplaying` | `\|>  %s` |
| `format_msg_time` | `#   %s` |
| `format_msg_err` | `/!\ %s` |
| `format_msg_question` | `[?] %s` |
| `format_msg_list` | a tab, then `%s` |

ANSI colour codes work inside format strings, exactly as in pianobar.

#### Network

| Key | Meaning |
|---|---|
| `proxy` | `http://user:password@host:port/`. Used for everything, the audio included. Defaults to the `http_proxy` environment variable. Only HTTP proxies are supported, not SOCKS. |
| `control_proxy` | The same, but only for talking to Pandora; the audio is fetched directly. This is the one to use from outside the United States, as only the control connection is checked. |
| `bind_to` | Send the control connection through a particular network interface or local address: `if!tun0`, `host!192.0.2.1`, or just the name. With a VPN set up not to take over the default route, this does the job of `control_proxy`. |
| `ca_bundle` | A PEM file of certificate authorities to trust *instead of* the system's, for networks that intercept TLS with a private authority. |

The connection settings `rpc_host`, `rpc_tls_port`, `partner_user`, `partner_password`, `device`,
`encrypt_password` and `decrypt_password` are supported too and default to pianobar's values. You
will not normally need them.

### Last.fm

jazzclub scrobbles to [Last.fm](https://www.last.fm) by itself. Add two lines to the config file:

```ini
lastfm_user = ted
lastfm_password_command = pass show last.fm
```

That is all: no API account to register, no script. On the next start, once the first song plays:

```
(i) Last.fm: scrobbling as ted.
```

jazzclub trades your password for a Last.fm session key once and keeps the key in
`~/.local/state/jazzclub/lastfm-session`, readable by you only. After that, the password is not needed
again, so a `lastfm_password_command` that asks for a passphrase asks only the first time. To sign in
afresh, delete that file.

What happens while you listen:

- Your Last.fm profile shows the song as it starts.
- A song is scrobbled when it ends, if you heard half of it or 4 minutes of it, whichever is less.
  Songs of 30 seconds or less never count, and neither does time spent paused. These are Last.fm's
  own rules. Skip early, and the song is not scrobbled.
- Banning a song counts the same way: banned after you heard enough of it, it is still scrobbled.
- Quitting in the middle of a song scrobbles it if you had heard enough.

Nothing about Last.fm can stop jazzclub from starting or playing. A problem is reported once, in one
line, whether or not you gave `-v`:

```
/!\ Last.fm: wrong user name or password for 'ted', not scrobbling. Check lastfm_user and lastfm_password in ~/.config/jazzclub/config.
/!\ Last.fm is unreachable; songs played meanwhile will not be scrobbled.
(i) Last.fm is reachable again.
```

A wrong password is tried only once per run, so jazzclub cannot get your Last.fm account locked; fix
the config and restart. `-v` shows every request to Last.fm with its HTTP status.

`lastfm_password` and `lastfm_password_command` follow the same rules as Pandora's `password` and
`password_command`, see [Keeping your password out of the config file](#keeping-your-password-out-of-the-config-file).
The network settings `proxy`, `timeout` and `ca_bundle` apply to Last.fm too; `control_proxy` and
`bind_to` are for Pandora only.

If you also scrobble from an [event script](#event-scripts), every song is scrobbled twice. Use one
or the other.

### Event scripts

jazzclub can tell a program of yours about everything that happens: to pop up a desktop
notification, to log what you listened to, to scrobble somewhere other than Last.fm. The interface is pianobar's, so scripts
written for pianobar work unchanged, including the examples in its
[contrib/eventcmd-examples](https://codeberg.org/purplesym/pianobar/src/branch/master/contrib/eventcmd-examples).

```ini
event_command = ~/.config/jazzclub/eventcmd
```

The program is started with the event name as its only argument and gets the details on standard
input, one `key=value` per line:

```
stationName=Bill Evans Radio
songStationName=
pRet=1
pRetStr=Everything is fine :)
wRet=0
wRetStr=No error
songPlayed=399
artist=Bill Evans
title=Peace Piece
album=Everybody Digs Bill Evans
coverArt=https://...
rating=1
detailUrl=https://...
songDuration=401
stationCount=24
station0=...
```

`songPlayed` and `songDuration` are seconds. `rating` is `0` none, `1` loved, `2` banned, `3` tired.
`pRet=1` and `wRet=0` mean the operation succeeded. Upcoming songs follow as `artistNext0=`,
`titleNext0=` and so on.

Events: `songstart`, `songfinish`, `songlove`, `songban`, `songshelf`, `songexplain`, `songbookmark`,
`artistbookmark`, `stationcreate`, `stationaddgenre`, `stationaddshared`, `stationaddmusic`,
`stationrename`, `stationdelete`, `stationquickmixtoggle`, `stationfetchplaylist`,
`stationfetchgenre`, `usergetstations`, `userlogin`.

A notification on every new song, for macOS:

```sh
#!/bin/sh
[ "$1" = songstart ] || exit 0
while IFS='=' read -r key value; do
    case "$key" in title) title=$value ;; artist) artist=$value ;; esac
done
osascript -e "display notification \"$artist\" with title \"$title\""
```

On Linux replace the last line with `notify-send "$title" "$artist"`. Remember `chmod +x`.

Two differences from pianobar, both deliberate: scripts run in the background, one at a time and in
order, so a slow scrobbler never delays the music or a key press; and their output is discarded
rather than printed into the player. A script that runs longer than 30 seconds is killed.

### Multi-room audio

With `audio_pipe`, jazzclub does not play through the sound card but writes the decoded audio to a
named pipe, where a program like [Snapcast](https://github.com/badaix/snapcast) can pick it up and
distribute it around the house.

```sh
mkfifo /tmp/snapfifo
```

```ini
audio_pipe = /tmp/snapfifo
sample_rate = 48000
```

The format is signed 16 bit little-endian stereo at `sample_rate` (44100 if not set), which in
Snapcast's terms is `sampleformat=48000:16:2`. Volume keys and Pandora's loudness correction still
work; they are applied to the samples. If nothing reads the pipe, the song waits; you can still
skip or quit.

### Remote control

jazzclub can be driven from outside through a named pipe: whatever is written to it is treated
exactly as if you had typed it. That is enough for media keys, a window manager binding, a status bar
button, or a script. Create the pipe once:

```sh
mkfifo ~/.config/jazzclub/ctl
```

jazzclub says `Control fifo at ... opened` on start. Then, from anywhere:

```sh
echo -n p > ~/.config/jazzclub/ctl        # pause or resume
echo -n n > ~/.config/jazzclub/ctl        # next song
echo -n + > ~/.config/jazzclub/ctl        # love this song
printf 's12\n' > ~/.config/jazzclub/ctl   # change to station 12: "s", the number, Enter
```

Use `echo -n` or `printf`: a trailing newline is a key press too, harmless after a command but it
answers the next prompt with an empty line. The characters are your key bindings, so if you rebound
`act_songnext`, send the new key. jazzclub never creates the pipe itself and refuses a path that is
not a pipe. Keep it somewhere only you can write to; anyone who can write to it controls your player
and, through `!`, your account settings.

### Keeping your password out of the config file

`password_command` runs through `/bin/sh` with your terminal attached, so tools that ask for a
passphrase work. The first line it prints is used as the password.

**macOS Keychain**

```sh
security add-generic-password -a "$USER" -s jazzclub -w     # asks for the password once
```

```ini
password_command = security find-generic-password -s jazzclub -w
```

**pass**

```ini
password_command = pass show pandora
```

**GnuPG**

```ini
password_command = gpg --quiet --decrypt ~/.config/jazzclub/password.gpg
```

**GNOME Keyring / KDE Wallet (libsecret)**

```ini
password_command = secret-tool lookup service jazzclub
```

## Troubleshooting

**`/!\ Could not start 'ffmpeg'`**
Install ffmpeg, see [Requirements](#requirements).

**`Error: Wrong email address or password.`**
Check `user` and `password`. If you use `password_command`, run the command by itself and make sure
the password is the *first* line it prints.

**`Error: Pandora is not available in your country.`**
Pandora only serves the United States.

**`/!\ No audio output device is available`**
jazzclub plays through the system's default output. On Linux it goes through ALSA; with PipeWire or
PulseAudio install their ALSA bridge (`pipewire-alsa` or `pulseaudio-alsa`).

**Something else**
Run `jazzclub -vv` and look at what it logs. Auth tokens and your password are never logged.

## Differences from pianobar

jazzclub 0.1 has all of pianobar's keys except the debug dump (`$`). Not there yet:


Deliberately different:

- Every request to Pandora uses HTTPS. pianobar still sends some over plain HTTP.
- jazzclub takes command line options; pianobar has none.
- jazzclub scrobbles to Last.fm by itself. pianobar did too until 2010, and has left it to event
  scripts since.
- Audio is decoded by the `ffmpeg` *program* rather than its libraries. While a song plays, its
  stream URL is therefore visible in the process list to other users of the machine. The URL is
  short-lived and only good for that one song.

## Building

You need [GraalVM](https://www.graalvm.org) for JDK 25. With [SDKMAN!](https://sdkman.io):

```sh
sdk env install        # installs the JDK pinned in .sdkmanrc
./mvnw -Pnative native:compile
./target/jazzclub --version
```

`./mvnw` is the Maven Wrapper: it fetches the Maven version this project is built with, so you do
not need Maven installed. `./mvnw verify` runs the tests and fails below 80 % line coverage.
`./mvnw package` builds a regular `target/jazzclub.jar` that runs on any Java 25 with `java -jar`.

Every push is tested by [GitHub Actions](.github/workflows/ci.yml). Release tags run the
[release workflow](.github/workflows/release.yml), which builds native binaries, smoke-tests them,
packages them for Debian, Arch Linux and macOS, writes checksums and publishes a GitHub release.

## Releasing

jazzclub releases use SemVer tags. Normal code changes land through branches or worktrees into
`main`; packages are only published from immutable tags:

```sh
git tag -a v0.1.0 -m "jazzclub 0.1.0"
git push origin v0.1.0
```

Use `MAJOR` for incompatible config, CLI, packaging or behavior changes, `MINOR` for compatible
features, and `PATCH` for compatible fixes. See [docs/release.md](docs/release.md) for the branch,
versioning and CI/CD policy.

To try the packaging on your own machine:

```sh
./mvnw -Pnative -DskipTests -Drevision=0.0.0-test native:compile
scripts/smoke-test.sh target/jazzclub 0.0.0-test
scripts/package-macos.sh 0.0.0-test arm64 target/jazzclub dist
```

## Licence

MIT, see [LICENSE](LICENSE). jazzclub is derived from pianobar, © 2008-2014 Lars-Dominik Braun, also
MIT; see [NOTICE](NOTICE) for the full attribution.

jazzclub is not affiliated with or endorsed by Pandora Media, LLC.
