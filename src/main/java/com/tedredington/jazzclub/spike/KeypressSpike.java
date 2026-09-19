package com.tedredington.jazzclub.spike;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.springframework.stereotype.Component;

/** Proves single-keypress input (no Enter needed) works, including in the native binary. */
@Component
class KeypressSpike {

    void run() throws Exception {
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            System.out.println("terminal=" + terminal.getClass().getSimpleName()
                    + " type=" + terminal.getType());
            Attributes saved = terminal.enterRawMode();
            try {
                System.out.print("Press keys, q quits:\r\n");
                int c;
                while ((c = terminal.reader().read()) != 'q' && c != -1) {
                    System.out.print("key=" + (char) c + " (" + c + ")\r\n");
                }
                System.out.print("bye\r\n");
            } finally {
                terminal.setAttributes(saved);
            }
        }
    }
}
