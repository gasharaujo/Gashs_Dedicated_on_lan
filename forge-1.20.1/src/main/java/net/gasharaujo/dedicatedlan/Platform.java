package net.gasharaujo.dedicatedlan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.versions.forge.ForgeVersion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

final class Platform {
    private Platform() {}

    static ServerDistribution distribution() {
        return new ServerDistribution("forge", "1.20.1-" + ForgeVersion.getVersion());
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
        int top = screen instanceof SelectWorldScreen ? 48 : 32;
        list.updateSize(screen.width, screen.height, top, bottom);
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
        LegacyWorld.prepare(path, host, file -> NbtIo.readCompressed(file.toFile()),
                (tag, file) -> NbtIo.writeCompressed(tag, file.toFile()));
    }

    static void connect(Screen parent, DedicatedSession.Endpoint endpoint) {
        var data = new ServerData(DedicatedLan.lanName(endpoint.worldName()), endpoint.address(), false);
        ConnectScreen.startConnecting(parent, Minecraft.getInstance(), ServerAddress.parseString(endpoint.address()), data, false);
    }
}
