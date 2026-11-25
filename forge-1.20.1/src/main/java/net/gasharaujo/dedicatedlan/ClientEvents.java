package net.gasharaujo.dedicatedlan;

import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLPaths;

final class ClientEvents {
    private ClientEvents() {}

    static void register() {
        var screens = new ClientScreens(FMLPaths.GAMEDIR.get(), Platform.distribution());
        var bus = MinecraftForge.EVENT_BUS;
        bus.addListener((ScreenEvent.Init.Post event) -> screens.init(event.getScreen(), event::addListener));
        bus.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                screens.tick();
            }
        });
    }
}
