package com.mcpy.mod;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(McPyMod.MOD_ID)
public class McPyMod {

    public static final String MOD_ID = "mcpy";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public McPyMod() {
        MinecraftForge.EVENT_BUS.register(this);
        McPyServer.startSocketThread();
        LOGGER.info("[McPy] Mod loaded. Socket server thread started.");
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        McPyServer.setServer(event.getServer());
        LOGGER.info("[McPy] Server reference acquired. McPy is ready on port 25566.");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        McPyServer.clearServer();
        LOGGER.info("[McPy] Server stopping — clearing server reference.");
    }
}