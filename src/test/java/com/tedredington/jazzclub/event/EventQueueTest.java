package com.tedredington.jazzclub.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class EventQueueTest {

    private final EventQueue queue = new EventQueue();

    @Test
    void deliversInPublicationOrder() throws InterruptedException {
        queue.publish(new Event.KeyPressed('a'));
        queue.publish(new Event.Tick());

        assertThat(queue.take()).isEqualTo(new Event.KeyPressed('a'));
        assertThat(queue.take()).isEqualTo(new Event.Tick());
    }

    @Test
    void putBackGoesToTheFrontKeepingItsOwnOrder() throws InterruptedException {
        queue.publish(new Event.KeyPressed('z'));

        queue.putBack(List.of(new Event.KeyPressed('a'), new Event.KeyPressed('b')));

        assertThat(List.of(queue.take(), queue.take(), queue.take())).containsExactly(
                new Event.KeyPressed('a'), new Event.KeyPressed('b'), new Event.KeyPressed('z'));
    }
}
