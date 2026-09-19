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

Nothing else. The binary contains everything it needs; no Java installation is required to run it.

## Install

Packages for Homebrew, the AUR and Debian are planned. Until then, build from source (see
[Building](#building)) and put the binary somewhere on your `PATH`:

```sh
install -m 755 target/jazzclub ~/.local/bin/jazzclub
```

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
| `+` | love song | `act_songlove` |
| `-` | ban song | `act_songban` |
| `t` | tired (ban song for 1 month) | `act_songtired` |
| `e` | explain why this song is played | `act_songexplain` |
| `i` | print information about song/station | `act_songinfo` |
| `u` | upcoming songs | `act_upcoming` |
| `n` | next song | `act_songnext` |
| `p` | pause/resume playback | `act_songpausetoggle` |
| `Space` | pause/resume playback | `act_songpausetoggle2` |
| `S` | pause playback | `act_songpause` |
| `P` | resume playback | `act_songplay` |
| `s` | change station | `act_stationchange` |
| `(` | decrease volume | `act_voldown` |
| `)` | increase volume | `act_volup` |
| `^` | reset volume | `act_volreset` |
| `q` | quit | `act_quit` |

`Ctrl-C` quits as well.

### Choosing a station

The station list is sorted by name. At the `Select station:` prompt you can

- type a **number** and press Enter, or
- type **part of a name** to narrow the list. If only one station is left it is selected for you.
- Press Enter on an empty line to go back without changing anything.

The letters in front of a name mean: `q` the station is part of your QuickMix, `Q` it *is* the
QuickMix, `S` it was shared with you by somebody else.

## Configuration

jazzclub reads `~/.config/jazzclub/config`, or `$XDG_CONFIG_HOME/jazzclub/config` if that variable
is set. Pass `--config FILE` to use another file.

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
| `volume` | `0` | Initial volume correction in dB. Usually between -30 and +5. |
| `gain_mul` | `1.0` | How much of Pandora's per-track loudness correction to apply; `0.0` turns it off. |
| `autostart_station` | | Station id to play right away. Press `i` to see the id of the current station. |
| `history` | `5` | How many played songs to remember. |
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

ANSI colour codes work inside format strings, exactly as in pianobar.

The connection settings `rpc_host`, `rpc_tls_port`, `partner_user`, `partner_password`, `device`,
`encrypt_password` and `decrypt_password` are supported too and default to pianobar's values. You
will not normally need them.

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

jazzclub 0.1 covers listening, rating and switching stations. Not there yet:

- creating, renaming, deleting and managing stations (`c`, `v`, `a`, `g`, `j`, `r`, `d`, `x`, `=`)
- song history (`h`), bookmarks (`b`), account settings (`!`)
- `event_command` and the remote-control `fifo`
- `proxy`, `control_proxy`, `bind_to`, `ca_bundle`, `sort`, `audio_pipe`, `sample_rate`

Deliberately different:

- Every request to Pandora uses HTTPS. pianobar still sends some over plain HTTP.
- jazzclub takes command line options; pianobar has none.
- Audio is decoded by the `ffmpeg` *program* rather than its libraries. While a song plays, its
  stream URL is therefore visible in the process list to other users of the machine. The URL is
  short-lived and only good for that one song.

## Building

You need [GraalVM](https://www.graalvm.org) for JDK 25 and Maven. With [SDKMAN!](https://sdkman.io):

```sh
sdk env install        # installs the JDK pinned in .sdkmanrc
mvn -Pnative native:compile
./target/jazzclub --version
```

`mvn verify` runs the tests and fails below 80 % line coverage. `mvn package` builds a regular
`target/jazzclub.jar` that runs on any Java 25 with `java -jar`.

## Licence

MIT, see [LICENSE](LICENSE). jazzclub is derived from pianobar, © 2008-2014 Lars-Dominik Braun, also
MIT; see [NOTICE](NOTICE) for the full attribution.

jazzclub is not affiliated with or endorsed by Pandora Media, LLC.
