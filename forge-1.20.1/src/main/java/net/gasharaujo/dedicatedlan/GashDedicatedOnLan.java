package net.gasharaujo.dedicatedlan;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(DedicatedLan.ID)
public final class GashDedicatedOnLan {
    public GashDedicatedOnLan() {
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> DedicatedLan.serverStarted(event.getServer()));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientEvents.register();
        }
    }
}
