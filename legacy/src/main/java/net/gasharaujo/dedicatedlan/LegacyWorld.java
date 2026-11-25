package net.gasharaujo.dedicatedlan;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

final class LegacyWorld {
    private LegacyWorld() {}

    static void prepare(Path world, UUID host, ReadNbt read, WriteNbt write) throws IOException {
        Path levelFile = world.resolve("level.dat");
        CompoundTag root = read.read(levelFile);
        if (root == null || !root.contains("Data", Tag.TAG_COMPOUND)) {
            throw new IOException("Could not read level.dat for " + world.getFileName());
        }
        CompoundTag data = root.getCompound("Data");
        if (!data.contains("Player", Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag player = data.getCompound("Player");
        UUID id = player.hasUUID("UUID") ? player.getUUID("UUID") : host;
        Path playerFile = world.resolve("playerdata").resolve(id + ".dat");
        if (Files.notExists(playerFile)) {
            Files.createDirectories(playerFile.getParent());
            player.putInt("DataVersion", data.getInt("DataVersion"));
            writeAtomically(playerFile, player, write);
        }

        // The embedded snapshot otherwise overrides newer dedicated player data when playing solo.
        data.remove("Player");
        writeAtomically(levelFile, root, write);
    }

    private static void writeAtomically(Path target, CompoundTag tag, WriteNbt write) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), "dedicated-", ".dat");
        try {
            write.write(tag, temporary);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @FunctionalInterface
    interface ReadNbt {
        CompoundTag read(Path file) throws IOException;
    }

    @FunctionalInterface
    interface WriteNbt {
        void write(CompoundTag tag, Path file) throws IOException;
    }
}
