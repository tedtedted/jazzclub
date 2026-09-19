package com.tedredington.jazzclub.spike;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Throwaway feasibility checks, selected with {@code --spike=keys|audio|pandora}.
 * Delete this package once the architecture is settled.
 */
@Component
class SpikeRunner implements ApplicationRunner {

    private final KeypressSpike keypressSpike;
    private final AudioSpike audioSpike;
    private final PandoraSpike pandoraSpike;

    SpikeRunner(KeypressSpike keypressSpike, AudioSpike audioSpike, PandoraSpike pandoraSpike) {
        this.keypressSpike = keypressSpike;
        this.audioSpike = audioSpike;
        this.pandoraSpike = pandoraSpike;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> spike = args.getOptionValues("spike");
        if (spike == null || spike.isEmpty()) {
            System.out.println("jazzclub skeleton up. Try --spike=keys or --spike=audio --file=<path>");
            return;
        }
        switch (spike.getFirst()) {
            case "keys" -> keypressSpike.run();
            case "pandora" -> pandoraSpike.run();
            case "audio" -> audioSpike.run(args.getOptionValues("file").getFirst());
            default -> System.out.println("unknown spike: " + spike.getFirst());
        }
    }
}
