package com.tedredington.jazzclub.pandora;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Replays scripted responses in order and remembers every request it saw. */
final class FakeTransport implements PandoraTransport {

    record Request(URI uri, String body) {
    }

    private final Deque<String> responses = new ArrayDeque<>();
    private final List<Request> requests = new ArrayList<>();

    FakeTransport respond(String body) {
        responses.add(body);
        return this;
    }

    @Override
    public String post(URI uri, String body) {
        requests.add(new Request(uri, body));
        if (responses.isEmpty()) {
            throw new AssertionError("Unexpected request: " + uri);
        }
        return responses.remove();
    }

    List<Request> requests() {
        return requests;
    }

    Request request(int index) {
        return requests.get(index);
    }
}
