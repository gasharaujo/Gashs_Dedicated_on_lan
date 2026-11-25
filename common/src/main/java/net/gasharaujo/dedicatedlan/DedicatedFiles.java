package net.gasharaujo.dedicatedlan;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

final class DedicatedFiles {
    private static final List<String> SERVER_CONFIG = List.of("config", "defaultconfigs", "kubejs", "scripts",
            "global_packs", "openloader");

    private DedicatedFiles() {}

    static void stage(Path instance, Path directory, List<ModJar> mods, Set<String> excluded) throws IOException {
        Path target = directory.resolve("mods");
        Files.createDirectories(target);
        Set<String> installed = new HashSet<>();
        for (ModJar mod : mods) {
            String filename = mod.path().getFileName().toString();
            if (!mod.ids().contains(DedicatedLan.ID)
                    && (mod.clientOnly() || excluded.contains(filename) || mod.ids().stream().anyMatch(excluded::contains))) {
                continue;
            }
            copyMod(target, installed, mod.path());
        }
        if (mods.stream().noneMatch(mod -> mod.ids().contains(DedicatedLan.ID))) {
            throw new IOException("The Dedicated on Lan mod JAR is missing from the instance mods folder");
        }
        Path extras = directory.resolve("server-mods");
        Files.createDirectories(extras);
        try (Stream<Path> files = Files.list(extras)) {
            for (Path file : files.filter(path -> Files.isRegularFile(path) && isJar(path)).toList()) {
                copyMod(target, installed, file);
            }
        }
        try (Stream<Path> files = Files.list(target)) {
            for (Path file : files.filter(path -> isJar(path) && Files.isRegularFile(path)).toList()) {
                if (!installed.contains(file.getFileName().toString())) {
                    Files.delete(file);
                }
            }
        }

        for (String name : SERVER_CONFIG) {
            Path source = instance.resolve(name);
            if (!Files.isDirectory(source)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(source)) {
                for (Path file : files.filter(path -> Files.isRegularFile(path) && !Files.isSymbolicLink(path)).toList()) {
                    Path relative = source.relativize(file);
                    if (name.equals("config") && relative.toString().equals("gashs_dedicated_on_lan.properties")) {
                        continue;
                    }
                    Path destination = directory.resolve(name).resolve(relative);
                    if (Files.notExists(destination)) {
                        Files.createDirectories(destination.getParent());
                        Files.copy(file, destination);
                    }
                }
            }
        }
    }

    static void configure(Path directory, DedicatedSession.World world, int port) throws IOException {
        Path file = directory.resolve("server.properties");
        Properties values = Settings.read(file);
        values.putIfAbsent("online-mode", "true");
        values.setProperty("server-ip", "");
        values.setProperty("server-port", Integer.toString(port));
        values.setProperty("motd", DedicatedLan.lanName(world.name()));
        values.setProperty("difficulty", world.difficulty());
        values.setProperty("gamemode", world.gameMode());
        values.setProperty("hardcore", Boolean.toString(world.hardcore()));
        values.setProperty("force-gamemode", "false");
        values.setProperty("pause-when-empty-seconds", "0");
        Settings.write(file, values);
        Files.writeString(directory.resolve("eula.txt"), "eula=true\n");
        configureOperator(directory.resolve("ops.json"), world, Boolean.parseBoolean(values.getProperty("online-mode")));
    }

    private static void configureOperator(Path file, DedicatedSession.World world, boolean online) throws IOException {
        UUID host = online ? world.hostId() : UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + world.hostName()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        JsonArray ops = new JsonArray();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                JsonElement stored = JsonParser.parseReader(reader);
                if (stored.isJsonArray()) {
                    ops = stored.getAsJsonArray();
                }
            }
        }
        // The saved world's command setting controls the host's privileges for this session.
        for (int index = ops.size() - 1; index >= 0; index--) {
            JsonElement entry = ops.get(index);
            if (entry.isJsonObject() && entry.getAsJsonObject().has("uuid")
                    && host.toString().equalsIgnoreCase(entry.getAsJsonObject().get("uuid").getAsString())) {
                ops.remove(index);
            }
        }
        if (world.commands()) {
            JsonObject op = new JsonObject();
            op.addProperty("uuid", host.toString());
            op.addProperty("name", world.hostName());
            op.addProperty("level", 4);
            op.addProperty("bypassesPlayerLimit", false);
            ops.add(op);
        }
        try (Writer writer = Files.newBufferedWriter(file)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(ops, writer);
        }
    }

    private static void copyMod(Path target, Set<String> installed, Path source) throws IOException {
        String name = source.getFileName().toString();
        if (!installed.add(name)) {
            throw new IOException("Multiple server mods are named " + name);
        }
        Path dest = target.resolve(name);
        if (Files.isRegularFile(dest) && Files.mismatch(source, dest) == -1) {
            return;
        }
        Path temp = Files.createTempFile(target, "mod-", ".part");
        try {
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp, dest, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static boolean isJar(Path path) {
        return path.getFileName().toString().endsWith(".jar");
    }

    record ModJar(Path path, Set<String> ids, boolean clientOnly) {}
}
