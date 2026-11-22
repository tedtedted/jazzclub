#!/usr/bin/env python3
"""Exercise the shipped player against local HTTPS API and AAC fixtures, without an account or sound card."""
import argparse
import array
import http.server
import json
import math
import os
import signal
from pathlib import Path
import ssl
import subprocess
import sys
import tempfile
import threading
import time
import urllib.parse

ROOT = Path(__file__).resolve().parent.parent
FIXTURES = ROOT / "src/test/resources/fixtures"


def run(command, signal_shutdown=False):
    with tempfile.TemporaryDirectory(prefix="jazzclub-audio-") as directory:
        work = Path(directory)
        cert, key = work / "cert.pem", work / "key.pem"
        config = work / "openssl.cnf"
        config.write_text("""[req]
 distinguished_name = dn
 x509_extensions = extensions
 prompt = no
 [dn]
 CN = localhost
 [extensions]
 subjectAltName = DNS:localhost,IP:127.0.0.1
 basicConstraints = critical,CA:TRUE
 keyUsage = critical,digitalSignature,keyEncipherment,keyCertSign
""")
        subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1",
                        "-config", str(config), "-keyout", str(key), "-out", str(cert)],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        requests, errors = [], []
        playlist_count = 0

        class Handler(http.server.BaseHTTPRequestHandler):
            def log_message(self, *_):
                pass

            def respond(self, body, content_type):
                self.send_response(200)
                self.send_header("Content-Type", content_type)
                self.send_header("Content-Length", str(len(body)))
                self.send_header("Connection", "close")
                self.end_headers()
                self.wfile.write(body)

            def do_GET(self):
                requests.append("audio")
                self.respond((FIXTURES / "audio/lc-1k.m4a").read_bytes(), "audio/mp4")

            def do_POST(self):
                nonlocal playlist_count
                self.rfile.read(int(self.headers.get("Content-Length", "0")))
                method = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query).get("method", [""])[0]
                requests.append(method)
                if method == "auth.partnerLogin":
                    response = {"stat": "ok", "result": {"syncTime": "43269bad8ab5c0f2da2b4339dd203985",
                                "partnerAuthToken": "fixture-partner", "partnerId": "42"}}
                elif method in ("auth.userLogin", "user.getStationList"):
                    name = "user-login-ok.json" if method == "auth.userLogin" else "station-list.json"
                    response = json.loads((FIXTURES / "pandora" / name).read_text())
                elif method == "station.getPlaylist":
                    playlist_count += 1
                    response = json.loads((FIXTURES / "pandora/playlist.json").read_text())
                    item = response["result"]["items"][0]
                    item.update(trackGain=0, trackLength=3, songRating=0)
                    item["audioUrlMap"] = {"highQuality": {"encoding": "aacplus", "bitrate": "64",
                        "audioUrl": f"http://127.0.0.1:{audio.server_port}/tone.m4a"}}
                    response["result"]["items"] = [item] if playlist_count == 1 else []
                else:
                    errors.append(f"Unexpected API method: {method}")
                    response = {"stat": "fail", "code": 0, "message": "unexpected fixture request"}
                self.respond(json.dumps(response).encode(), "application/json")

        audio = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        api = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        tls.load_cert_chain(cert, key)
        api.socket = tls.wrap_socket(api.socket, server_side=True)
        for server in (audio, api):
            threading.Thread(target=server.serve_forever, daemon=True).start()

        fifo, pcm_file, finished = work / "audio", work / "audio.pcm", work / "finished"
        os.mkfifo(fifo, 0o600)
        hook = work / "event.sh"
        hook.write_text(f'#!/bin/sh\ncat > /dev/null\n[ "$1" != songfinish ] || touch "{finished}"\n')
        hook.chmod(0o700)
        player_config = work / "config"
        player_config.write_text(f"""user = fixture@example.com
password = fixture-only
rpc_host = 127.0.0.1
rpc_tls_port = {api.server_port}
ca_bundle = {cert}
autostart_station = 200
audio_pipe = {fifo}
decoder = lavaplayer
event_command = {hook}
volume = 0
timeout = 5
""")
        player_config.chmod(0o600)
        env = {k: v for k, v in os.environ.items() if not k.startswith("JAZZCLUB_") and k.lower() not in
               ("http_proxy", "https_proxy", "all_proxy", "spring_application_json", "java_tool_options", "jdk_java_options")}
        for name in ("CONFIG", "STATE", "CACHE"):
            env[f"XDG_{name}_HOME"] = str(work / name.lower())
        env["JAZZCLUB_FFMPEG"] = str(work / "ffmpeg-must-not-be-used")
        player = reader = None
        try:
            with pcm_file.open("wb") as pcm, (work / "player.log").open("wb") as log:
                if signal_shutdown:
                    # Keep playback active by reading more slowly than the decoder writes.
                    slow_reader = """import sys, time
with open(sys.argv[1], 'rb', buffering=0) as source:
    while chunk := source.read(4096):
        sys.stdout.buffer.write(chunk)
        sys.stdout.buffer.flush()
        time.sleep(0.02)
"""
                    reader = subprocess.Popen([sys.executable, "-c", slow_reader, str(fifo)], stdout=pcm)
                else:
                    reader = subprocess.Popen(["cat", str(fifo)], stdout=pcm)
                player = subprocess.Popen(command + ["--config", str(player_config), "-vv"],
                                          stdin=subprocess.PIPE, stdout=log, stderr=log, env=env)
                deadline = time.monotonic() + 30
                while not (pcm_file.stat().st_size >= 40_000 if signal_shutdown else finished.exists()):
                    if player.poll() is not None:
                        raise AssertionError(f"Player exited before finishing audio: {player.returncode}")
                    if time.monotonic() >= deadline:
                        raise AssertionError("Native audio test timed out")
                    time.sleep(0.05)
                if signal_shutdown:
                    assert not finished.exists(), "Track ended before the signal shutdown check"
                    player.send_signal(signal.SIGTERM)
                    assert player.wait(timeout=10) in (0, 143, -signal.SIGTERM), "Unexpected SIGTERM exit status"
                    assert finished.exists(), "Shutdown lost the final songfinish event"
                    assert "autostart_station = 200" in (work / "state/jazzclub/state").read_text(), \
                        "Shutdown failed to persist playback state"
                else:
                    player.stdin.write(b"q")
                    player.stdin.flush()
                    assert player.wait(timeout=10) == 0, "Player failed on quit"
                reader.wait(timeout=5)
            output = (work / "player.log").read_text(errors="replace")
            assert "Decoding with ffmpeg" not in output, "Built-in decoder fell back to ffmpeg"
            assert "Decoding failed" not in output, "Audio decoding failed"
            assert "RejectedExecutionException" not in output, "Shutdown delivered an event to a closed worker"
            assert "Player loop did not exit" not in output, "Shutdown exceeded its main-loop wait budget"
            assert not errors, errors
            for expected in ("auth.partnerLogin", "auth.userLogin", "user.getStationList", "station.getPlaylist", "audio"):
                assert expected in requests, f"Missing request: {expected}"
            data = pcm_file.read_bytes()
            assert len(data) % 4 == 0, "PCM output is not stereo frame aligned"
            duration = len(data) / (44100 * 4)
            if signal_shutdown:
                assert 0.2 <= duration < 3.0, f"Expected interrupted playback, got {duration:.3f}s"
            else:
                assert 3.0 <= duration <= 3.25, f"Expected a full three-second track, got {duration:.3f}s"
            samples = array.array("h", data)
            if sys.byteorder != "little":
                samples.byteswap()
            # Check signal strength as well as byte count: silence is not successful decoding.
            middle = samples[len(samples)//4:3*len(samples)//4]
            rms = math.sqrt(sum((sample / 32768) ** 2 for sample in middle) / len(middle))
            db = 20 * math.log10(max(rms, 1e-12))
            assert (-25 if signal_shutdown else -17) <= db <= -13, f"Unexpected fixture level: {db:.1f} dBFS"
            ending = "SIGTERM with final event and saved state" if signal_shutdown else "clean quit"
            print(f"Native audio passed: local HTTPS login, AAC decode, {duration:.3f}s stereo PCM ({db:.1f} dBFS), {ending}")
        except Exception:
            if (work / "player.log").exists():
                print((work / "player.log").read_text(errors="replace"), file=sys.stderr)
            raise
        finally:
            for child in (player, reader):
                if child is not None and child.poll() is None:
                    child.kill()
                    child.wait(timeout=5)
            for server in (audio, api):
                server.shutdown()
                server.server_close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path, help="Native binary, or application jar with --jar")
    parser.add_argument("--jar", action="store_true", help="Use the JVM build while developing this test")
    parser.add_argument("--signal", action="store_true", help="Send SIGTERM during playback and check final cleanup")
    args = parser.parse_args()
    command = ["java", "-jar", str(args.binary.absolute())] if args.jar else [str(args.binary.absolute())]
    run(command, args.signal)
