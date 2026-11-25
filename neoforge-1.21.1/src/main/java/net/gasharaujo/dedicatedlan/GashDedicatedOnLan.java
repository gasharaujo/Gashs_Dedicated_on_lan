package net.gasharaujo.dedicatedlan;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@Mod(DedicatedLan.ID)
public final class GashDedicatedOnLan {
    public GashDedicatedOnLan() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> DedicatedLan.serverStarted(event.getServer()));
    }

    @Mod(value = DedicatedLan.ID, dist = Dist.CLIENT)
    public static final class ClientEntry {
        public ClientEntry() {
            ClientEvents.register();
        }
    }
}
