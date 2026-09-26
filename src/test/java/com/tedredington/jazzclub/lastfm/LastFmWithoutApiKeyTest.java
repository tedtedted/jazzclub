package com.tedredington.jazzclub.lastfm;

import static org.assertj.core.api.Assertions.assertThat;

import com.tedredington.jazzclub.event.Event;
import com.tedredington.jazzclub.event.EventQueue;
import com.tedredington.jazzclub.ui.MessageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** A build without jazzclub's API key still starts; the user is told why nothing is scrobbled. */
@SpringBootTest(properties = {"jazzclub.lastfm.user=ted", "jazzclub.lastfm.api-key=", "jazzclub.lastfm.api-secret="})
@Timeout(30)
class LastFmWithoutApiKeyTest {

    @Autowired
    private EventQueue events;

    @Test
    void theUserIsToldOnce() throws InterruptedException {
        assertThat(events.take()).isEqualTo(new Event.Notice(MessageType.ERROR,
                "Last.fm: this build of jazzclub has no Last.fm API key, not scrobbling."));
    }
}
