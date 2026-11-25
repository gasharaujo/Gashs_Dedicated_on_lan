package net.gasharaujo.dedicatedlan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.level.storage.LevelSummary;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
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
        list.updateSizeAndPosition(screen.width, bottom - top, top);
    }

    static LevelSummary summary(WorldSelectionList.WorldListEntry entry) {
        return entry.getLevelSummary();
    }

    static boolean requiresFileFixing(LevelSummary summary) {
        return summary.requiresFileFixing();
    }

    static String difficulty(LevelSummary summary) {
        return summary.getSettings().difficultySettings().difficulty().name().toLowerCase(Locale.ROOT);
    }

    static void prepareWorld(Path path, UUID host) {
        // This version stores the single-player owner's data in the playerdata directory.
    }

    static void connect(Screen parent, DedicatedSession.Endpoint endpoint) {
        var data = new ServerData(DedicatedLan.lanName(endpoint.worldName()), endpoint.address(), ServerData.Type.OTHER);
        ConnectScreen.startConnecting(parent, Minecraft.getInstance(), ServerAddress.parseString(endpoint.address()), data, false, null);
    }
}
