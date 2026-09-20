package com.tedredington.jazzclub.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.tedredington.jazzclub.event.Event;
import com.tedredington.jazzclub.event.EventQueue;
import com.tedredington.jazzclub.player.PlaybackResult;
import com.tedredington.jazzclub.testsupport.RecordingConsole;
import org.junit.jupiter.api.Test;

class EventQueuePrompterTest {

    private static final char ENTER = '\r';
    private static final char BACKSPACE = (char) 8;
    private static final char DELETE = (char) 127;
    private static final char CTRL_D = (char) 4;
    private static final char CTRL_U = (char) 21;
    private static final char ESCAPE = (char) 27;
    private static final String ERASE = BACKSPACE + " " + BACKSPACE;

    private final EventQueue events = new EventQueue();
    private final RecordingConsole console = new RecordingConsole();
    private final Prompter prompter = new EventQueuePrompter(events, console);

    private void type(Object... keys) {
        for (Object key : keys) {
            String.valueOf(key).chars().forEach(c -> events.publish(new Event.KeyPressed((char) c)));
        }
    }

    @Test
    void readsUntilEnterAndEchoesWhatIsTyped() {
        type("12", ENTER);

        assertThat(prompter.readLine()).contains("12");
        assertThat(console.output()).isEqualTo("12\n");
    }

    @Test
    void lineFeedAlsoEndsTheLine() {
        type("x", '\n');

        assertThat(prompter.readLine()).contains("x");
    }

    @Test
    void backspaceAndDeleteRemoveTheLastCharacterOnScreenToo() {
        type("ab", DELETE, "c", BACKSPACE, "d", ENTER);

        assertThat(prompter.readLine()).contains("ad");
        assertThat(console.output()).isEqualTo("ab" + ERASE + "c" + ERASE + "d\n");
    }

    @Test
    void backspaceOnAnEmptyLineDoesNothing() {
        type(DELETE, ENTER);

        assertThat(prompter.readLine()).isEmpty();
        assertThat(console.output()).isEqualTo("\n");
    }

    @Test
    void ctrlUClearsTheLine() {
        type("abc", CTRL_U, "z", ENTER);

        assertThat(prompter.readLine()).contains("z");
    }

    @Test
    void otherControlCharactersAreIgnored() {
        type("a", ESCAPE, "b", ENTER);

        assertThat(prompter.readLine()).contains("ab");
    }

    @Test
    void justEnterOrCtrlDMeansNoAnswer() {
        type(ENTER);
        assertThat(prompter.readLine()).isEmpty();

        type(CTRL_D);
        assertThat(prompter.readLine()).isEmpty();
    }

    @Test
    void ticksAreDroppedSoTheClockDoesNotOverwriteThePrompt() throws InterruptedException {
        events.publish(new Event.Tick());
        type("1", ENTER);
        events.publish(new Event.KeyPressed('n'));

        prompter.readLine();

        assertThat(events.take()).isEqualTo(new Event.KeyPressed('n'));
    }

    @Test
    void aSongEndingDuringThePromptIsHandledAfterwards() throws InterruptedException {
        Event.TrackFinished finished = new Event.TrackFinished(7, PlaybackResult.completed());
        type("1");
        events.publish(finished);
        type("9", ENTER);

        assertThat(prompter.readLine()).contains("19");
        assertThat(events.take()).isEqualTo(finished);
    }

    @Test
    void closedInputEndsThePromptAndStillReachesTheMainLoop() throws InterruptedException {
        type("ab");
        events.publish(new Event.InputClosed());

        assertThat(prompter.readLine()).isEmpty();
        assertThat(events.take()).isEqualTo(new Event.InputClosed());
    }

    @Test
    void readCharReturnsOnTheFirstAllowedKeyWithoutWaitingForEnter() {
        type("xyzs", "never read");

        assertThat(prompter.readChar("sa")).contains('s');
        assertThat(console.output()).isEqualTo("s\n");
    }

    @Test
    void readCharTreatsEnterAsNoAnswer() {
        type(ENTER);

        assertThat(prompter.readChar("sa")).isEmpty();
    }

    @Test
    void readCharAlsoSetsAsideWhatIsNotForIt() throws InterruptedException {
        Event.TrackFinished finished = new Event.TrackFinished(3, PlaybackResult.completed());
        events.publish(new Event.Tick());
        events.publish(finished);
        type("a");

        assertThat(prompter.readChar("sa")).contains('a');
        assertThat(events.take()).isEqualTo(finished);
    }

    @Test
    void readCharEndsWhenInputCloses() throws InterruptedException {
        events.publish(new Event.InputClosed());

        assertThat(prompter.readChar("sa")).isEmpty();
        assertThat(events.take()).isEqualTo(new Event.InputClosed());
    }

    @Test
    void aRestrictedLineIgnoresEverythingElse() {
        type("1a2-3", ENTER);

        assertThat(prompter.readLine("0123456789")).contains("123");
        assertThat(console.output()).isEqualTo("123\n");
    }

    @Test
    void readNumberParsesDigitsAndRefusesAbsurdLengths() {
        type("42", ENTER);
        assertThat(prompter.readNumber()).contains(42);

        type("12345678901234567890", ENTER);
        assertThat(prompter.readNumber()).isEmpty();

        type(ENTER);
        assertThat(prompter.readNumber()).isEmpty();
    }

    @Test
    void confirmTakesOneKeyAndFallsBackToTheDefault() {
        type("y");
        assertThat(prompter.confirm(false)).isTrue();
        type("N");
        assertThat(prompter.confirm(true)).isFalse();
        type(ENTER);
        assertThat(prompter.confirm(false)).isFalse();
        type(ENTER);
        assertThat(prompter.confirm(true)).isTrue();
    }

    @Test
    void interruptionEndsThePrompt() {
        Thread.currentThread().interrupt();

        assertThat(prompter.readLine()).isEmpty();
        assertThat(Thread.interrupted()).isTrue();
    }
}
