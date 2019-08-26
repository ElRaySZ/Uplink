package io.github.thefrontier.uplink;

import club.minnced.discord.rpc.DiscordEventHandlers;
import club.minnced.discord.rpc.DiscordRPC;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.thefrontier.uplink.config.Config;
import io.github.thefrontier.uplink.config.DisplayDataManager;
import io.github.thefrontier.uplink.util.NativeUtil;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;


@Mod(modid = Uplink.MOD_ID, name = Uplink.MOD_NAME, version = Uplink.VERSION)
public class Uplink {

    // ---------- Statics ---------- //

    public static final String MOD_ID = "uplink";
    public static final String MOD_NAME = "Uplink";
    public static final String VERSION = "@MCVERSION@";
    public static final Logger LOGGER = LogManager.getLogger("Uplink");

    @Mod.Instance(MOD_ID)
    public static Uplink INSTANCE;

    public Path configDir;

    private static DiscordRPC RPC;

    static {
        NativeUtil.loadNativeLibrary();
        RPC = DiscordRPC.INSTANCE;
    }

    // ---------- Instance ---------- //

    private boolean hasErrors = false;

    @Mod.EventHandler
    public void onConstruction(FMLConstructionEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        configDir = event.getModConfigurationDirectory().toPath();
        PresenceManager manager = setupPresenceManager(event.getModConfigurationDirectory().toPath().resolve("Uplink.json"));

        if (hasErrors) {
            return;
        }

        setupRichPresence(manager);

        if (hasErrors) {
            return;
        }

        PresenceListener listener = new PresenceListener(RPC, LOGGER, manager);

        MinecraftForge.EVENT_BUS.register(listener);
        FMLCommonHandler.instance().bus().register(listener);
    }

    private PresenceManager setupPresenceManager(Path configPath) {
        if (Files.notExists(configPath)) {
            try {
                Files.copy(getClass().getResourceAsStream("Uplink.json"), configPath);
            } catch (Exception e) {
                LOGGER.error("Could not copy default config to " + configPath, e);
                hasErrors = true;
                return null;
            }
        }

        Gson gson = new GsonBuilder().create();

        Config config;

        try {
            config = gson.fromJson(Files.newBufferedReader(configPath), Config.class);
        } catch (Exception e) {
            LOGGER.error("Could not load config", e);
            hasErrors = true;
            return null;
        }

        DisplayDataManager dataManager;

        try {
            dataManager = new DisplayDataManager(LOGGER, config);
        } catch (Exception e) {
            LOGGER.error("Could not load display data manager", e);
            hasErrors = true;
            return null;
        }

        return new PresenceManager(dataManager, config);
    }

    private void setupRichPresence(PresenceManager manager) {
        RPC.Discord_Initialize(manager.getConfig().clientId, new DiscordEventHandlers(), false, null);

        Thread callbackHandler = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                RPC.Discord_RunCallbacks();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    RPC.Discord_Shutdown();
                }
            }
        }, "RPC-Callback-Handler");
        callbackHandler.start();

        Runtime.getRuntime().addShutdownHook(new Thread(callbackHandler::interrupt));

        RPC.Discord_UpdatePresence(manager.loadingGame());
    }
}