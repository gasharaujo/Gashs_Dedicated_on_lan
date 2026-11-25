package net.gasharaujo.dedicatedlan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

final class ClientScreens {
    private final Path instance;
    private final DedicatedSession session;
    private Screen buttonScreen;
    private Button button;
    private LevelSummary shownWorld;
    private DedicatedSession.Endpoint shownServer;
    private Pending pending;

    ClientScreens(Path instance, ServerDistribution distribution) {
        this.instance = instance;
        this.session = new DedicatedSession(instance, distribution);
    }

    void init(Screen screen, Consumer<Button> add) {
        if (!(screen instanceof SelectWorldScreen) && !(screen instanceof JoinMultiplayerScreen)) {
            return;
        }
        if (screen instanceof JoinMultiplayerScreen && session.running() == null) {
            return;
        }
        int y = screen.height - 83;
        screen.children().stream().filter(AbstractSelectionList.class::isInstance).findFirst()
                .ifPresent(list -> Platform.reserveSlot(screen, (AbstractSelectionList<?>) list, y - 4));
        buttonScreen = screen;
        button = Button.builder(text("start"), pressed -> click(screen))
                .bounds(screen.width / 2 - 100, y, 200, 20).build();
        button.active = false;
        button.visible = screen instanceof SelectWorldScreen;
        shownWorld = null;
        shownServer = null;
        add.accept(button);
        updateButton();
    }

    void tick() {
        var minecraft = Minecraft.getInstance();
        if (buttonScreen != null) {
            if (minecraft.screen == buttonScreen) {
                updateButton();
            } else {
                buttonScreen = null;
                button = null;
                shownWorld = null;
                shownServer = null;
            }
        }
        if (pending == null) {
            return;
        }
        var request = pending;
        request.progress().progressStage(text(session.stage()));
        if (!request.result().isDone()) {
            return;
        }
        pending = null;
        try {
            Platform.connect(request.parent(), request.result().join());
        } catch (CompletionException error) {
            showError(request.parent(), error.getCause());
        }
    }

    private void updateButton() {
        var active = session.running();
        LevelSummary world = active == null ? selected(buttonScreen) : null;
        if (active == shownServer && world == shownWorld) {
            return;
        }
        shownServer = active;
        shownWorld = world;
        if (active != null) {
            button.setMessage(text("connect"));
            button.visible = true;
            button.active = true;
            button.setTooltip(Tooltip.create(Component.literal(active.worldName())));
            return;
        }
        button.setMessage(text("start"));
        button.visible = buttonScreen instanceof SelectWorldScreen;
        button.active = eligible(world);
        button.setTooltip(world != null && !button.active ? Tooltip.create(text("unavailable")) : null);
    }

    private void click(Screen parent) {
        var active = session.running();
        if (active != null) {
            Platform.connect(parent, active);
            return;
        }
        LevelSummary summary = selected(parent);
        if (eligible(summary)) {
            start(parent, summary);
        }
    }

    private void start(Screen parent, LevelSummary summary) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            Settings settings = Settings.load(instance);
            if (!settings.eulaAccepted()) {
                minecraft.setScreen(new ConfirmScreen(accepted -> {
                    minecraft.setScreen(parent);
                    if (accepted) {
                        try {
                            Settings.acceptEula(instance);
                            start(parent, summary);
                        } catch (IOException error) {
                            showError(parent, error);
                        }
                    }
                }, text("eula.title"), text("eula.body"), text("eula.accept"), text("cancel")));
                return;
            }
            var source = minecraft.getLevelSource();
            Path saves = source.getBaseDir().toAbsolutePath().normalize();
            Path path = saves.resolve(summary.getLevelId()).normalize();
            if (!saves.equals(path.getParent())) {
                throw new IOException("Invalid world folder: " + summary.getLevelId());
            }
            var user = minecraft.getUser();
            var world = new DedicatedSession.World(path, summary.getLevelName(), summary.getGameMode().getName(),
                    Platform.difficulty(summary), summary.isHardcore(), summary.getSettings().allowCommands(),
                    user.getName(), user.getProfileId());
            var mods = Platform.mods();
            ProgressScreen progress = new ProgressScreen(false);
            progress.progressStartNoAbort(text("progress"));
            minecraft.setScreen(progress);
            var result = session.start(world, settings, mods, () -> {
                try (var access = source.validateAndCreateAccess(summary.getLevelId())) {
                    Platform.prepareWorld(path, world.hostId());
                }
            });
            pending = new Pending(parent, progress, result);
        } catch (Exception error) {
            showError(parent, error);
        }
    }

    private static LevelSummary selected(Screen screen) {
        return screen.children().stream().filter(WorldSelectionList.class::isInstance)
                .map(WorldSelectionList.class::cast).findFirst()
                .flatMap(WorldSelectionList::getSelectedOpt).map(Platform::summary).orElse(null);
    }

    private static boolean eligible(LevelSummary summary) {
        return summary != null && !summary.isDisabled() && !summary.backupStatus().shouldBackup()
                && !Platform.requiresFileFixing(summary);
    }

    private static Component text(String name) {
        return Component.translatable("screen." + DedicatedLan.ID + "." + name);
    }

    private static void showError(Screen parent, Throwable error) {
        Minecraft minecraft = Minecraft.getInstance();
        DedicatedLan.LOG.error("Dedicated on LAN could not open the world", error);
        String message = error.getMessage();
        minecraft.setScreen(new AlertScreen(() -> minecraft.setScreen(parent), text("error"),
                Component.literal(message == null ? error.toString() : message)));
    }

    private record Pending(Screen parent, ProgressScreen progress, CompletableFuture<DedicatedSession.Endpoint> result) {}
}
