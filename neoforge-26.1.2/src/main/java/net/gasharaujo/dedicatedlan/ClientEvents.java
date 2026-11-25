package net.gasharaujo.dedicatedlan;

import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLPaths;

final class ClientEvents {
    private ClientEvents() {}

    static void register() {
        var screens = new ClientScreens(FMLPaths.GAMEDIR.get(), Platform.distribution());
        var bus = NeoForge.EVENT_BUS;
        bus.addListener((ScreenEvent.Init.Post event) -> screens.init(event.getScreen(), event::addListener));
        bus.addListener((ClientTickEvent.Post event) -> screens.tick());
    }
}
