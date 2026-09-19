package com.tedredington.jazzclub;

import java.io.PrintWriter;
import java.util.Optional;

import com.tedredington.jazzclub.cli.CommandLineParser;
import com.tedredington.jazzclub.cli.LaunchOptions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JazzclubApplication {

    public static void main(String[] args) {
        CommandLineParser parser = new CommandLineParser(
                new PrintWriter(System.out, true), new PrintWriter(System.err, true));
        Optional<LaunchOptions> options = parser.parse(args);
        if (options.isEmpty()) {
            System.exit(parser.exitCode());
        }

        SpringApplication application = new SpringApplication(JazzclubApplication.class);
        application.setDefaultProperties(options.get().toProperties());
        // argv was consumed above; Spring must not reinterpret it as properties
        application.setAddCommandLineProperties(false);
        System.exit(SpringApplication.exit(application.run()));
    }
}
