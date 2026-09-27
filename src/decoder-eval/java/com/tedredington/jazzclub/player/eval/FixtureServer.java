package com.tedredington.jazzclub.player.eval;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Serves the audio fixtures over HTTP on localhost, standing in for Pandora's CDN, with ways to
 * misbehave: throttling, dropping the connection, refusing range requests, dying after a drop.
 */
final class FixtureServer implements AutoCloseable {

    /** How one URL misbehaves. */
    record Behaviour(int bytesPerSecond, int dropAfterBytes, boolean deadAfterDrop, boolean ranges) {

        static final Behaviour NORMAL = new Behaviour(0, -1, false, true);

        Behaviour throttled(int bytesPerSecond) {
            return new Behaviour(bytesPerSecond, dropAfterBytes, deadAfterDrop, ranges);
        }

        /** Drops the first response after this many bytes; later requests are served normally. */
        Behaviour droppingOnceAfter(int bytes) {
            return new Behaviour(bytesPerSecond, bytes, false, ranges);
        }

        /** Drops the first response after this many bytes and answers 503 from then on. */
        Behaviour dyingAfter(int bytes) {
            return new Behaviour(bytesPerSecond, bytes, true, ranges);
        }

        Behaviour withoutRanges() {
            return new Behaviour(bytesPerSecond, dropAfterBytes, deadAfterDrop, false);
        }
    }

    record Request(String path, String range, int status) {
    }

    private static final Pattern RANGE = Pattern.compile("bytes=(\\d+)-(\\d*)");

    private final HttpServer server;
    private final Map<String, Mount> mounts = new ConcurrentHashMap<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    private static final class Mount {
        final byte[] body;
        final Behaviour behaviour;
        volatile boolean dropped;

        Mount(byte[] body, Behaviour behaviour) {
            this.body = body;
            this.behaviour = behaviour;
        }
    }

    FixtureServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        server.start();
    }

    /** Makes a fixture available under a fresh path, so each test gets its own drop state. */
    URI mount(String fixture, Behaviour behaviour) {
        byte[] body;
        try (InputStream in = FixtureServer.class.getResourceAsStream("/audio/" + fixture)) {
            if (in == null) {
                throw new IllegalArgumentException("no fixture " + fixture);
            }
            body = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String path = "/" + mounts.size() + "/" + fixture;
        mounts.put(path, new Mount(body, behaviour));
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    URI mount(String fixture) {
        return mount(fixture, Behaviour.NORMAL);
    }

    List<Request> requestsFor(URI uri) {
        return requests.stream().filter(r -> r.path().equals(uri.getPath())).toList();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
            Mount mount = mounts.get(path);
            if (mount == null) {
                respond(exchange, path, rangeHeader, 404);
                return;
            }
            Behaviour behaviour = mount.behaviour;
            if (mount.dropped && behaviour.deadAfterDrop()) {
                respond(exchange, path, rangeHeader, 503);
                return;
            }

            int start = 0;
            int end = mount.body.length - 1;
            int status = 200;
            Matcher range = rangeHeader == null ? null : RANGE.matcher(rangeHeader);
            if (behaviour.ranges() && range != null && range.matches()) {
                start = Integer.parseInt(range.group(1));
                if (!range.group(2).isEmpty()) {
                    end = Math.min(end, Integer.parseInt(range.group(2)));
                }
                if (start > end) {
                    exchange.getResponseHeaders().set("Content-Range", "bytes */" + mount.body.length);
                    respond(exchange, path, rangeHeader, 416);
                    return;
                }
                status = 206;
                exchange.getResponseHeaders().set("Content-Range",
                        "bytes " + start + "-" + end + "/" + mount.body.length);
            }
            if (behaviour.ranges()) {
                exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
            }
            exchange.getResponseHeaders().set("Content-Type", path.endsWith(".aac") ? "audio/aac" : "audio/mp4");
            // With keep-alive, ffmpeg fails to open a whole ADTS file that fits in its probe buffer
            // ("Error reading HTTP response: End of file") against this JDK server but not against
            // others, so connections are closed per response to keep that artifact out of the results.
            exchange.getResponseHeaders().set("Connection", "close");
            requests.add(new Request(path, rangeHeader, status));
            int length = end - start + 1;
            exchange.sendResponseHeaders(status, length);

            boolean dropNow = behaviour.dropAfterBytes() >= 0 && !mount.dropped;
            int limit = dropNow ? Math.min(length, behaviour.dropAfterBytes()) : length;
            OutputStream out = exchange.getResponseBody();
            int chunk = behaviour.bytesPerSecond() > 0 ? Math.max(1, behaviour.bytesPerSecond() / 20) : 16 * 1024;
            for (int sent = 0; sent < limit; sent += chunk) {
                out.write(mount.body, start + sent, Math.min(chunk, limit - sent));
                out.flush();
                if (behaviour.bytesPerSecond() > 0) {
                    Thread.sleep(50);
                }
            }
            if (dropNow) {
                mount.dropped = true;
                // Content-Length promised more, so closing now is a connection drop, not a clean end
                throw new IOException("dropping connection on purpose");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // client went away, or a deliberate drop: either way the exchange is finished
        }
    }

    private void respond(HttpExchange exchange, String path, String range, int status) throws IOException {
        requests.add(new Request(path, range, status));
        exchange.sendResponseHeaders(status, -1);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
