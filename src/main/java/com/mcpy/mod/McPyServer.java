package com.mcpy.mod;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.*;
import java.net.*;
import java.util.*;

public class McPyServer {

    private static final int PORT = 25566;
    private static final Gson GSON = new Gson();

    private static volatile MinecraftServer mcServer = null;
    private static volatile String lastChatMessage = "";
    private static volatile String lastChatSender = "";


    public static void setServer(MinecraftServer server) {
        mcServer = server;
        MinecraftForge.EVENT_BUS.register(ChatListener.INSTANCE);
    }

    public static void clearServer() {
        mcServer = null;
        MinecraftForge.EVENT_BUS.unregister(ChatListener.INSTANCE);
    }

    public static void startSocketThread() {
        Thread t = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                serverSocket.setReuseAddress(true);
                McPyMod.LOGGER.info("[McPy] Listening on TCP port " + PORT);
                while (true) {
                    Socket client = serverSocket.accept();
                    Thread clientThread = new Thread(() -> handleClient(client));
                    clientThread.setDaemon(true);
                    clientThread.start();
                }
            } catch (IOException e) {
                McPyMod.LOGGER.error("[McPy] Socket server error: " + e.getMessage());
            }
        }, "mcpy-socket-server");
        t.setDaemon(true);
        t.start();
    }


    public static class ChatListener {
        public static final ChatListener INSTANCE = new ChatListener();

        @SubscribeEvent
        public void onChat(ServerChatEvent event) {
            lastChatMessage = event.getMessage().getString();
            lastChatSender = event.getPlayer().getName().getString();
        }
    }


    private static void handleClient(Socket socket) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    out.println(handleCommand(line));
                }
            }
        } catch (IOException e) {
            McPyMod.LOGGER.debug("[McPy] Client disconnected: " + e.getMessage());
        }
    }

    private static String handleCommand(String json) {
        if (mcServer == null) return error("Server not ready yet");
        try {
            JsonObject req = GSON.fromJson(json, JsonObject.class);
            String cmd = req.get("cmd").getAsString();
            JsonObject args = req.has("args") ? req.getAsJsonObject("args") : new JsonObject();

            return switch (cmd) {
                case "get_block"      -> getBlock(args);
                case "remove_block"   -> removeBlock(args);
                case "place_block"    -> placeBlock(args);
                case "get_players"    -> getPlayers();
                case "kill"           -> killEntity(args);
                case "gamemode"       -> setGamemode(args);
                case "teleport"       -> teleport(args);
                case "get_health"     -> getHealth(args);
                case "set_health"     -> setHealth(args);
                case "get_inventory"  -> getInventory(args);
                case "get_chat"       -> getChat();
                case "set_time"       -> setTime(args);
                case "get_time"       -> getTime();
                case "set_weather"    -> setWeather(args);
                case "get_weather"    -> getWeather();
                case "set_difficulty" -> setDifficulty(args);
                case "play_sound"     -> playSound(args);
                case "log"            -> logMessage(args);
                case "version"        -> version();
                default               -> error("Unknown command: " + cmd);
            };
        } catch (Exception e) {
            McPyMod.LOGGER.error("[McPy] Command error: " + e.getMessage());
            return error(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }


    private static String getBlock(JsonObject args) {
        int x = args.get("x").getAsInt();
        int y = args.get("y").getAsInt();
        int z = args.get("z").getAsInt();
        BlockState state = getOverworld().getBlockState(new BlockPos(x, y, z));
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return ok(Map.of("block", id != null ? id.toString() : "minecraft:air"));
    }

    private static String removeBlock(JsonObject args) {
        int x = args.get("x").getAsInt();
        int y = args.get("y").getAsInt();
        int z = args.get("z").getAsInt();
        mcServer.execute(() -> getOverworld().removeBlock(new BlockPos(x, y, z), false));
        return ok(Map.of("result", "removed"));
    }

    private static String placeBlock(JsonObject args) {
        int x = args.get("x").getAsInt();
        int y = args.get("y").getAsInt();
        int z = args.get("z").getAsInt();
        String blockId = args.get("block").getAsString();
        if (!blockId.contains(":")) blockId = "minecraft:" + blockId;
        ResourceLocation rl = ResourceLocation.tryParse(blockId);
        if (rl == null) return error("Invalid block id: " + blockId);
        Block block = ForgeRegistries.BLOCKS.getValue(rl);
        if (block == null) return error("Unknown block: " + blockId);
        String finalBlockId = blockId;
        mcServer.execute(() ->
            getOverworld().setBlockAndUpdate(new BlockPos(x, y, z), block.defaultBlockState())
        );
        return ok(Map.of("result", "placed", "block", finalBlockId));
    }


    private static String getPlayers() {
        List<String> names = mcServer.getPlayerList().getPlayers()
            .stream().map(p -> p.getName().getString()).toList();
        return ok(Map.of("players", names));
    }

    private static String killEntity(JsonObject args) {
        String name = args.get("entity").getAsString();
        ServerPlayer player = mcServer.getPlayerList().getPlayerByName(name);
        if (player == null) return error("Player not found: " + name);
        mcServer.execute(() -> player.kill(getOverworld()));
        return ok(Map.of("result", "killed"));
    }

    private static String setGamemode(JsonObject args) {
        String name = args.get("player").getAsString();
        String mode = args.get("gamemode").getAsString().toLowerCase();
        ServerPlayer player = mcServer.getPlayerList().getPlayerByName(name);
        if (player == null) return error("Player not found: " + name);
        GameType gt = switch (mode) {
            case "survival",  "s",  "0" -> GameType.SURVIVAL;
            case "creative",  "c",  "1" -> GameType.CREATIVE;
            case "adventure", "a",  "2" -> GameType.ADVENTURE;
            case "spectator", "sp", "3" -> GameType.SPECTATOR;
            default -> null;
        };
        if (gt == null) return error("Unknown gamemode: " + mode);
        mcServer.execute(() -> player.setGameMode(gt));
        return ok(Map.of("result", "gamemode set", "mode", mode));
    }

    private static String teleport(JsonObject args) {
        String name = args.get("entity").getAsString();
        double x = args.get("x").getAsDouble();
        double y = args.get("y").getAsDouble();
        double z = args.get("z").getAsDouble();
        ServerPlayer player = mcServer.getPlayerList().getPlayerByName(name);
        if (player == null) return error("Player not found: " + name);
        mcServer.execute(() -> player.teleportTo(x, y, z));
        return ok(Map.of("result", "teleported"));
    }

    private static String getHealth(JsonObject args) {
        String name = args.get("player").getAsString();
        ServerPlayer player = mcServer.getPlayerList().getPlayerByName(name);
        if (player == null) return error("Player not found: " + name);
        return ok(Map.of("health", player.getHealth()));
    }

    private static String setHealth(JsonObject args) {
        String name = args.get("player").getAsString();
        float value = args.get("value").getAsFloat();
        ServerPlayer player = mcServer.getPlayerList().getPlayerByName(name);
        if (player == null) return error("Player not found: " + name);
        mcServer.execute(() -> player.setHealth(value));
        return ok(Map.of("result", "health set", "value", value));
    }

    private static String getInventory(JsonObject args) {
        String name = args.get("player").getAsString();
        ServerPlayer player = mcServer.getPlayerList().getPlayerByName(name);
        if (player == null) return error("Player not found: " + name);
        List<String> items = new ArrayList<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                items.add((id != null ? id.toString() : "unknown") + " x" + stack.getCount());
            }
        }
        return ok(Map.of("inventory", items));
    }


    private static String getChat() {
        return ok(Map.of("message", lastChatMessage, "sender", lastChatSender));
    }


    private static String setTime(JsonObject args) {
        long ticks = args.get("ticks").getAsLong();
        mcServer.execute(() -> getOverworld().setDayTime(ticks));
        return ok(Map.of("result", "time set", "ticks", ticks));
    }

    private static String getTime() {
        long time = getOverworld().getDayTime() % 24000;
        String label;
        if      (time <  1000) label = "Dawn";
        else if (time <  6000) label = "Morning";
        else if (time < 12000) label = "Day";
        else if (time < 13000) label = "Sunset";
        else if (time < 23000) label = "Night";
        else                   label = "Late Night";
        return ok(Map.of("ticks", time, "label", label));
    }

    private static String setWeather(JsonObject args) {
        String weather = args.get("weather").getAsString().toLowerCase();
        ServerLevel level = getOverworld();
        mcServer.execute(() -> {
            switch (weather) {
                case "clear"   -> level.setWeatherParameters(6000, 0, false, false);
                case "rain"    -> level.setWeatherParameters(0, 6000, true,  false);
                case "thunder" -> level.setWeatherParameters(0, 6000, true,  true);
            }
        });
        return ok(Map.of("result", "weather set", "weather", weather));
    }

    private static String getWeather() {
        ServerLevel level = getOverworld();
        String weather = level.isThundering() ? "thunder" : level.isRaining() ? "rain" : "clear";
        return ok(Map.of("weather", weather));
    }

    private static String setDifficulty(JsonObject args) {
        String diff = args.get("difficulty").getAsString().toLowerCase();
        Difficulty difficulty = switch (diff) {
            case "peaceful" -> Difficulty.PEACEFUL;
            case "easy"     -> Difficulty.EASY;
            case "normal"   -> Difficulty.NORMAL;
            case "hard"     -> Difficulty.HARD;
            default         -> null;
        };
        if (difficulty == null) return error("Unknown difficulty: " + diff);
        Difficulty finalDiff = difficulty;
        mcServer.execute(() -> mcServer.setDifficulty(finalDiff, true));
        return ok(Map.of("result", "difficulty set", "difficulty", diff));
    }

    private static String playSound(JsonObject args) {
        String sound = args.get("sound").getAsString();
        if (!sound.contains(":")) sound = "minecraft:" + sound;
        double x = args.get("x").getAsDouble();
        double y = args.get("y").getAsDouble();
        double z = args.get("z").getAsDouble();
        ResourceLocation rl = ResourceLocation.tryParse(sound);
        if (rl == null) return error("Invalid sound: " + sound);
        SoundEvent soundEvent = ForgeRegistries.SOUND_EVENTS.getValue(rl);
        if (soundEvent == null) return error("Unknown sound: " + sound);
        String finalSound = sound;
        mcServer.execute(() ->
            getOverworld().playSound(null, x, y, z, soundEvent, SoundSource.MASTER, 1.0f, 1.0f)
        );
        return ok(Map.of("result", "sound played", "sound", finalSound));
    }

    private static String logMessage(JsonObject args) {
        String message = args.get("message").getAsString();
        mcServer.execute(() ->
            mcServer.getPlayerList().broadcastSystemMessage(
                Component.literal("[McPy] " + message), false
            )
        );
        return ok(Map.of("result", "logged"));
    }

    private static String version() {
        return ok(Map.of("version", mcServer.getServerVersion()));
    }


    private static ServerLevel getOverworld() {
        return mcServer.overworld();
    }

    private static String ok(Map<String, ?> data) {
        Map<String, Object> res = new HashMap<>(data);
        res.put("status", "ok");
        return GSON.toJson(res);
    }

    private static String error(String message) {
        return GSON.toJson(Map.of("status", "error", "message", message));
    }
}