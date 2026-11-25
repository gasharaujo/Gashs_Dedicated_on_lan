package net.gasharaujo.dedicatedlan;

import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class DedicatedLan {
    static final String ID = "gashs_dedicated_on_lan";
    static final Logger LOG = LoggerFactory.getLogger(ID);
    static final String READY = ID + ".ready";
    static final String PARENT = ID + ".parentPid";
    static final String PARENT_START = ID + ".parentStart";

    private DedicatedLan() {}

    static void serverStarted(MinecraftServer server) {
        String ready = System.getProperty(READY);
        Long parentPid = Long.getLong(PARENT);
        if (ready == null || parentPid == null || !server.isDedicatedServer()) {
            return;
        }
        String started = System.getProperty(PARENT_START, "");
        var parent = ProcessHandle.of(parentPid).filter(process -> started.isEmpty()
                || process.info().startInstant().map(time -> time.toString().equals(started)).orElse(true));
        if (parent.isEmpty() || !parent.get().isAlive()) {
            server.halt(false);
            return;
        }
        parent.get().onExit().thenRun(() -> server.halt(false));
        try {
            Files.createFile(Path.of(ready));
        } catch (IOException error) {
            LOG.error("Could not signal the client that the dedicated server is ready", error);
            server.halt(false);
            return;
        }
        LOG.info("Dedicated server ready on port {} with a maximum heap of {} MiB",
                server.getPort(), Runtime.getRuntime().maxMemory() / (1024 * 1024));

        Thread advertiser = new Thread(() -> advertise(server), "Dedicated on LAN announcer");
        advertiser.setDaemon(true);
        advertiser.start();
    }

    static String lanName(String worldName) {
        String clean = worldName.replace('[', '(').replace(']', ')').replace('\r', ' ').replace('\n', ' ');
        return "Dedicated: " + clean;
    }

    private static void advertise(MinecraftServer server) {
        try (var socket = new DatagramSocket()) {
            byte[] message = ("[MOTD]" + lanName(server.getWorldData().getLevelName()) + "[/MOTD][AD]"
                    + server.getPort() + "[/AD]").getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(message, message.length,
                    InetAddress.getByName("224.0.2.60"), 4445);
            while (server.isRunning()) {
                socket.send(packet);
                Thread.sleep(1500);
            }
        } catch (IOException error) {
            LOG.warn("Could not announce the dedicated server on LAN", error);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
