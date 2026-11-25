package net.gasharaujo.dedicatedlan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.world.level.storage.LevelSummary;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

final class Platform {
    private Platform() {}

    static ServerDistribution distribution() {
        var version = ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion();
        return new ServerDistribution("neoforge", version.toString());
    }

    static List<DedicatedFiles.ModJar> mods() {
        Path modDirectory = FMLPaths.MODSDIR.get().toAbsolutePath().normalize();
        return ModList.get().getModFiles().stream().flatMap(file -> {
            Path path = file.getFile().getFilePath().toAbsolutePath().normalize();
            if (!path.getFileSystem().equals(modDirectory.getFileSystem()) || !path.startsWith(modDirectory)
                    || !Files.isRegularFile(path) || !DedicatedFiles.isJar(path)) {
                return java.util.stream.Stream.empty();
            }
            var ids = file.getMods().stream().map(mod -> mod.getModId()).collect(Collectors.toUnmodifiableSet());
            boolean clientOnly = file.getConfig().getConfigElement("clientSideOnly").filter(Boolean.TRUE::equals).isPresent();
            return java.util.stream.Stream.of(new DedicatedFiles.ModJar(path, ids, clientOnly));
        }).toList();
    }

    static void reserveSlot(Screen screen, AbstractSelectionList<?> list, int bottom) {
        int top = list.getY();
        list.setRectangle(screen.width, bottom - top, 0, top);
    }

    static LevelSummary summary(WorldSelectionList.WorldListEntry entry) {
        return entry.summary;
    }

    static boolean requiresFileFixing(LevelSummary summary) {
        return false;
    }

    static String difficulty(LevelSummary summary) {
        return summary.getSettings().difficulty().getKey();
    }

    static void prepareWorld(Path path, UUID host) throws IOException {
        LegacyWorld.prepare(path, host, file -> NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()),
                NbtIo::writeCompressed);
    }

    static void connect(Screen parent, DedicatedSession.Endpoint endpoint) {
        var data = new ServerData(DedicatedLan.lanName(endpoint.worldName()), endpoint.address(), ServerData.Type.OTHER);
        ConnectScreen.startConnecting(parent, Minecraft.getInstance(), ServerAddress.parseString(endpoint.address()), data, false, null);
    }
}
