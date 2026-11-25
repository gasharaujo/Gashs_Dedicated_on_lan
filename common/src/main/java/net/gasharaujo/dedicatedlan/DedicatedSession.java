package net.gasharaujo.dedicatedlan;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class DedicatedSession {
    private final Path instance;
    private final ServerDistribution distribution;
    private volatile Endpoint endpoint;
    private volatile Process child;
    private volatile String stage = "preparing";
    private CompletableFuture<Endpoint> startup;
    private boolean working;
    private boolean closing;

    DedicatedSession(Path instance, ServerDistribution distribution) {
        this.instance = instance;
        this.distribution = distribution;
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "Dedicated on LAN shutdown"));
    }

    synchronized CompletableFuture<Endpoint> start(World world, Settings settings, List<DedicatedFiles.ModJar> mods,
            PrepareWorld prepareWorld) {
        if (closing) {
            return CompletableFuture.failedFuture(new IOException("The client is shutting down"));
        }
        Endpoint active = running();
        if (active != null) {
            return CompletableFuture.completedFuture(active);
        }
        if (working) {
            return startup.isDone()
                    ? CompletableFuture.failedFuture(new IOException("The previous dedicated server is still closing"))
                    : startup;
        }
        stage = "preparing";
        var result = new CompletableFuture<Endpoint>();
        startup = result;
        working = true;
        Thread worker = new Thread(() -> run(world, settings, mods, prepareWorld, result), "Dedicated on LAN startup");
        worker.setDaemon(true);
        worker.start();
        return result;
    }

    private void run(World world, Settings settings, List<DedicatedFiles.ModJar> mods, PrepareWorld prepareWorld,
            CompletableFuture<Endpoint> result) {
        Path directory = distribution.directory(instance);
        Path instanceState = directory.getParent();
        Path ready = directory.resolve("ready-" + UUID.randomUUID());
        Path log = directory.resolve("dedicated.log");
        Exception failure = null;
        try {
            Files.createDirectories(instanceState);
            try (var channel = FileChannel.open(instanceState.resolve("instance.lock"), StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE); var lock = channel.tryLock()) {
                if (lock == null) {
                    throw new IOException("A dedicated server is already being managed from this instance");
                }
                try {
                    Files.createDirectories(directory);
                    if (!distribution.isInstalled(directory)) {
                        stage = "installing";
                        distribution.install(directory, this);
                    }
                    stage = "preparing";
                    DedicatedFiles.stage(instance, directory, mods, settings.excludedMods());
                    int port;
                    try (var socket = new ServerSocket(0)) {
                        port = socket.getLocalPort();
                    }
                    DedicatedFiles.configure(directory, world, port);
                    prepareWorld.run();

                    stage = "starting";
                    var server = launchServer(directory, log, ready, world, settings.memoryMiB());
                    endpoint = new Endpoint(world.name(), port, server);
                    result.complete(endpoint);
                    server.waitFor();
                } finally {
                    var process = child;
                    if (process != null && process.isAlive()) {
                        stop(process);
                    }
                }
            }
        } catch (OverlappingFileLockException busy) {
            failure = new IOException("A dedicated server is already being managed from this instance", busy);
        } catch (Exception error) {
            failure = error;
        } finally {
            synchronized (this) {
                child = null;
                endpoint = null;
                working = false;
            }
            try {
                Files.deleteIfExists(ready);
            } catch (IOException error) {
                DedicatedLan.LOG.warn("Could not remove startup signal {}", ready, error);
            }
        }
        if (failure != null) {
            DedicatedLan.LOG.error("Could not start the dedicated server", failure);
            result.completeExceptionally(failure);
        }
    }

    private Process launchServer(Path directory, Path log, Path ready, World world, int memoryMiB)
            throws IOException, InterruptedException {
        var parent = ProcessHandle.current();
        var server = launch(directory, log, List.of(ServerDistribution.java(), "-Xmx" + memoryMiB + "M",
                "-Djava.awt.headless=true", "-D" + DedicatedLan.READY + "=" + ready,
                "-D" + DedicatedLan.PARENT + "=" + parent.pid(),
                "-D" + DedicatedLan.PARENT_START + "=" + parent.info().startInstant().map(Instant::toString).orElse(""),
                "@" + distribution.arguments(directory), "nogui", "--universe", world.path().getParent().toString(),
                "--world", world.path().getFileName().toString()));
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
        while (!Files.isRegularFile(ready)) {
            if (server.waitFor(200, TimeUnit.MILLISECONDS)) {
                throw new IOException("Dedicated server stopped during startup (exit " + server.exitValue() + "). See " + log);
            }
            if (System.nanoTime() >= deadline) {
                throw new IOException("Dedicated server startup timed out. See " + log);
            }
        }
        Files.deleteIfExists(ready);
        return server;
    }

    Endpoint running() {
        Endpoint current = endpoint;
        return current != null && current.process().isAlive() ? current : null;
    }

    String stage() {
        return stage;
    }

    synchronized Process launch(Path directory, Path log, List<String> command) throws IOException {
        if (closing) {
            throw new IOException("The client is shutting down");
        }
        Process process = new ProcessBuilder(command).directory(directory.toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        child = process;
        return process;
    }

    private void shutdown() {
        Process process;
        synchronized (this) {
            closing = true;
            process = child;
        }
        if (process != null && process.isAlive()) {
            stop(process);
        }
    }

    private static void stop(Process process) {
        try (OutputStream input = process.getOutputStream()) {
            input.write("stop\n".getBytes(StandardCharsets.UTF_8));
            input.flush();
        } catch (IOException ignored) {
            // The process may already be closing.
        }
        try {
            if (!process.waitFor(40, TimeUnit.SECONDS)) {
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    interface PrepareWorld {
        void run() throws Exception;
    }

    record World(Path path, String name, String gameMode, String difficulty, boolean hardcore, boolean commands,
                 String hostName, UUID hostId) {}

    record Endpoint(String worldName, int port, Process process) {
        String address() {
            return "127.0.0.1:" + port;
        }
    }
}
