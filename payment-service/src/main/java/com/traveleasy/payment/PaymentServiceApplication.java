package com.traveleasy.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        loadDotEnvIfPresent();
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

    public static void loadDotEnvIfPresent() {
        String[] possiblePaths = {".env", "../.env", "../../.env"};
        for (String p : possiblePaths) {
            Path path = Paths.get(p);
            if (Files.exists(path)) {
                try (BufferedReader reader = Files.newBufferedReader(path)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                            continue;
                        }
                        int idx = line.indexOf('=');
                        String key = line.substring(0, idx).trim();
                        String val = line.substring(idx + 1).trim();
                        if (System.getenv(key) == null && System.getProperty(key) == null) {
                            System.setProperty(key, val);
                        }
                    }
                } catch (Exception ignored) {
                }
                break;
            }
        }
    }
}
