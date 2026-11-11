package com.tedredington.jazzclub;

import java.io.PrintWriter;
import java.util.Optional;

import com.tedredington.jazzclub.cli.CommandLineParser;
import com.tedredington.jazzclub.cli.LaunchOptions;
import com.tedredington.jazzclub.network.HttpClientFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JazzclubApplication {

    public static void main(String[] args) {
        HttpClientFactory.allowProxyCredentialsForHttps();
        CommandLineParser parser = new CommandLineParser(
                new PrintWriter(System.out, true), new PrintWriter(System.err, true));
        Optional<LaunchOptions> options = parser.parse(args);
        if (options.isEmpty()) {
            System.exit(parser.exitCode());
        }

        SpringApplication application = new SpringApplication(JazzclubApplication.class);
        // Picocli consumed argv; only the translated properties go through Boot's CLI handling.
        System.exit(SpringApplication.exit(application.run(options.get().toSpringArguments())));
    }
}
