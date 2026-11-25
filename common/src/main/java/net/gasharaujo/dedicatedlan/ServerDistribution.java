package net.gasharaujo.dedicatedlan;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;

record ServerDistribution(String loader, String version) {
    ServerDistribution {
        if ((!loader.equals("forge") && !loader.equals("neoforge"))
                || !version.matches("[0-9][0-9A-Za-z.+-]*")) {
            throw new IllegalArgumentException("Unsupported loader: " + loader + " " + version);
        }
    }

    Path directory(Path instance) {
        return instance.resolve("gash-dedicated").resolve(loader + "-" + version);
    }

    Path arguments(Path directory) {
        String name = isWindows() ? "win_args.txt" : "unix_args.txt";
        return directory.resolve("libraries").resolve(artifactPath()).resolve(name);
    }

    boolean isInstalled(Path directory) {
        return Files.isRegularFile(directory.resolve(".installed")) && Files.isRegularFile(arguments(directory));
    }

    void install(Path directory, DedicatedSession session) throws IOException, InterruptedException {
        Path installer = directory.resolve("installer.jar");
        Path download = Files.createTempFile(directory, "installer", ".download");
        try {
            HttpRequest request = HttpRequest.newBuilder(installerUri()).timeout(Duration.ofMinutes(2)).build();
            HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(20)).build();
            HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(download));
            if (response.statusCode() != 200) {
                throw new IOException("Loader installer download returned HTTP " + response.statusCode());
            }
            Files.move(download, installer, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(download);
        }
        Path log = directory.resolve("install.log");
        Process process = session.launch(directory, log, List.of(java(), "-jar", installer.toString(),
                "--installServer", directory.toString()));
        if (!process.waitFor(12, java.util.concurrent.TimeUnit.MINUTES)) {
            throw new IOException("Loader installation timed out. See " + log);
        }
        if (process.exitValue() != 0 || !Files.isRegularFile(arguments(directory))) {
            throw new IOException("Loader installation failed. See " + log);
        }
        Files.writeString(directory.resolve(".installed"), version);
        Files.deleteIfExists(installer);
    }

    private URI installerUri() {
        String repository = loader.equals("forge") ? "https://maven.minecraftforge.net/"
                : "https://maven.neoforged.net/releases/";
        return URI.create(repository + artifactPath() + "/" + loader + "-" + version + "-installer.jar");
    }

    private String artifactPath() {
        return (loader.equals("forge") ? "net/minecraftforge/forge/" : "net/neoforged/neoforge/") + version;
    }

    static String java() {
        return Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").startsWith("Windows");
    }
}
