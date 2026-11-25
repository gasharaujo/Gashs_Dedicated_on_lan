package net.gasharaujo.dedicatedlan;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

record Settings(int memoryMiB, boolean eulaAccepted, Set<String> excludedMods) {
    private static final int MIN_MEMORY_MIB = 256;

    static Settings load(Path instance) throws IOException {
        Path file = file(instance);
        Properties defaults = new Properties();
        defaults.setProperty("server-memory-mib", "0");
        defaults.setProperty("eula-accepted", "false");
        defaults.setProperty("excluded-mods", "");
        if (Files.notExists(file)) {
            write(file, defaults);
        }
        Properties values = read(file);
        int memory;
        try {
            int configured = Integer.parseInt(values.getProperty("server-memory-mib", "0").strip());
            if (configured != 0 && configured < MIN_MEMORY_MIB) {
                throw new NumberFormatException();
            }
            memory = configured == 0 ? (int) (Runtime.getRuntime().maxMemory() / (2L * 1024 * 1024)) : configured;
            if (memory < MIN_MEMORY_MIB) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException invalid) {
            throw new IOException("server-memory-mib must be 0 (half of the client heap) or at least " + MIN_MEMORY_MIB, invalid);
        }
        Set<String> excluded = Arrays.stream(values.getProperty("excluded-mods", "").split(","))
                .map(String::strip).filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
        return new Settings(memory, Boolean.parseBoolean(values.getProperty("eula-accepted")), excluded);
    }

    static void acceptEula(Path instance) throws IOException {
        Path file = file(instance);
        Properties values = read(file);
        values.setProperty("eula-accepted", "true");
        write(file, values);
    }

    static Properties read(Path file) throws IOException {
        Properties values = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                values.load(reader);
            }
        }
        return values;
    }

    static void write(Path file, Properties values) throws IOException {
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file)) {
            values.store(writer, "Gash's Dedicated on Lan");
        }
    }

    private static Path file(Path instance) {
        return instance.resolve("config/gashs_dedicated_on_lan.properties");
    }
}
