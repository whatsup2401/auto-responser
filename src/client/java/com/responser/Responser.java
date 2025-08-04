package com.responser;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.network.message.MessageType.Parameters;
import net.minecraft.text.Text;
import net.minecraft.client.util.InputUtil;

import com.mojang.authlib.GameProfile;
import com.google.gson.JsonObject;

import com.responser.config.ConfigManager;
import com.responser.utils.GetResponse;
import com.responser.utils.MentionChecker;
import com.responser.utils.Notification;

import org.lwjgl.glfw.GLFW;

import java.text.MessageFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

public class Responser implements ClientModInitializer {
    private static final Map<String, Deque<JsonObject>> chatHistories = new HashMap<>();

    // Scheduler for mention-based replies
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> scheduledTask = null;
    private boolean cancelTask = false;

    // ─── Fields for random‐reply mode ──────────────────────────────────────────
    private String  lastSenderName      = null;
    private String  lastMessage         = null;
    private String  lastPrefix          = "";
    private int     nextAutoReplyTick   = -1;
    private boolean autoReplyInProgress = false;

    @Override
    public void onInitializeClient() {
        // Keybinding to cancel a pending mention‐reply
        KeyBinding cancelKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "Cancel Generation",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_ENTER,
            "Auto Responser"
        ));

        // Clear histories on disconnect
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            chatHistories.clear();
        });

        // Standard chat listener
        ClientReceiveMessageEvents.CHAT.register((Text msg, SignedMessage signed, GameProfile sender, Parameters params, Instant ts) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            if (sender.getName().equals(client.getSession().getUsername())) return;  // ignore self

            ConfigManager cfg = ConfigManager.getInstance();
            if (!cfg.get("enable")) return;

            if (cfg.get("randomReplyMode")) {
                updateAutoReplyState(sender.getName(), msg.getString(), cfg);
            } else {
                processMessage(msg.getString(), sender.getName(), client, cancelKey, cfg);
            }
        });

        // “Game” chat listener (for plugin chat)
        ClientReceiveMessageEvents.GAME.register((Text msg, boolean overlay) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            String text = msg.getString();
            String sender = extractSenderName(text);
            if (sender == null || sender.equals(client.getSession().getUsername())) return;

            ConfigManager cfg = ConfigManager.getInstance();
            if (!cfg.get("enable")) return;

            if (cfg.get("randomReplyMode")) {
                updateAutoReplyState(sender, text, cfg);
            } else {
                processMessage(text, sender, client, cancelKey, cfg);
            }
        });

        // Cancel mention‐reply if chat opens or cancel key pressed
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || scheduledTask == null || scheduledTask.isDone()) return;
            if (client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen
                || cancelKey.wasPressed()) {
                cancelTask = true;
                scheduledTask.cancel(false);
                cancelTask = false;
                Notification.showNotification("canceled", "generation canceled");
            }
        });

        // ─── Random‐reply scheduler ──────────────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            ConfigManager cfg = ConfigManager.getInstance();
            if (!cfg.get("randomReplyMode")) return;

            if (nextAutoReplyTick > 0 && !autoReplyInProgress) {
                nextAutoReplyTick--;
            }
            if (nextAutoReplyTick == 0 && !autoReplyInProgress) {
                nextAutoReplyTick = -1;
                if (lastMessage != null && lastSenderName != null) {
                    sendAutoReply(lastSenderName, lastMessage, lastPrefix, client, cfg);
                }
            }
        });
    }

    // ─── Mention‐based logic (unchanged) ──────────────────────────────────────
    private void processMessage(String text, String sender, MinecraftClient client, KeyBinding cancelKey, ConfigManager cfg) {
        String player = client.getSession().getUsername();
        String cleaned = null, prefix = "";
        // [existing code that sets cleaned & prefix via MentionChecker…]
        // …

        if (cleaned != null) {
            Notification.showNotification(
                "mentioned!",
                MessageFormat.format(
                    "gen in {0}s (press {1} to cancel)",
                    cfg.get("delayS"),
                    I18n.translate(cancelKey.getBoundKeyTranslationKey()).toUpperCase()
                )
            );
            scheduleMessageProcessing(sender, cleaned, client, prefix, cfg);
        }
    }

    // Schedule one‐off mention‐reply
    private void scheduleMessageProcessing(String sender, String text, MinecraftClient client, String prefix, ConfigManager cfg) {
        if (scheduledTask != null && !scheduledTask.isDone()) {
            scheduledTask.cancel(false);
        }
        cancelTask = false;
        scheduledTask = scheduler.schedule(() -> {
            if (!cancelTask) {
                client.execute(() -> response(sender, text, prefix, client, cfg));
            }
        }, ((Number)cfg.get("delayS")).longValue(), TimeUnit.SECONDS);
    }

    // ─── Random‐reply helpers ─────────────────────────────────────────────────
    private void updateAutoReplyState(String sender, String text, ConfigManager cfg) {
        // simplify: no mention check, just store the last message + prefix
        lastSenderName = sender;
        lastMessage    = text;
        lastPrefix     = "";  // or detect whisper/clan/global like in processMessage

        if (nextAutoReplyTick < 0 && !autoReplyInProgress) {
            int min = cfg.get("minDelaySeconds"), max = cfg.get("maxDelaySeconds");
            if (min < 1) min = 1;
            if (max > 3600) max = 3600;
            if (min > max) { int t = min; min = max; max = t; }
            nextAutoReplyTick = ThreadLocalRandom.current().nextInt(min, max + 1) * 20;
        }
    }

    private void sendAutoReply(String sender, String message, String basePrefix, MinecraftClient client, ConfigManager cfg) {
        autoReplyInProgress = true;
        new Thread(() -> {
            try {
                // Build history context
                Deque<JsonObject> hist = chatHistories.computeIfAbsent(sender, u -> new ArrayDeque<>());
                JsonObject userEntry = new JsonObject();
                userEntry.addProperty("role", "user");
                userEntry.addProperty("content", message);
                hist.addLast(userEntry);

                // Prepare LLM call
                StringBuilder builder = new StringBuilder(), acc = new StringBuilder();
                boolean prefixUser = cfg.get("prefixWithUsername") && !basePrefix.startsWith("/");
                String fullPrefix = basePrefix + (prefixUser ? sender + ": " : "");
                int chunkLen = 256 - fullPrefix.length();

                GetResponse.getResponse(message, sender, chatHistories, delta -> {
                    String chunked = GetResponse.sanitizeMinecraftChat(delta);
                    acc.append(chunked);
                    while (acc.length() >= chunkLen) {
                        String out = fullPrefix + acc.substring(0, chunkLen);
                        acc.delete(0, chunkLen);
                        if (basePrefix.startsWith("/")) {
                            client.execute(() -> client.getNetworkHandler().sendChatCommand(out.substring(1)));
                        } else {
                            client.execute(() -> client.getNetworkHandler().sendChatMessage(out));
                        }
                    }
                });

                if (acc.length() > 0) {
                    String out = fullPrefix + acc.toString();
                    if (basePrefix.startsWith("/")) {
                        client.execute(() -> client.getNetworkHandler().sendChatCommand(out.substring(1)));
                    } else {
                        client.execute(() -> client.getNetworkHandler().sendChatMessage(out));
                    }
                }

                // Re‐enqueue assistant entry & prune
                JsonObject assist = new JsonObject();
                assist.addProperty("role", "assistant");
                assist.addProperty("content", builder.toString());
                hist.addLast(assist);
                while (hist.size() > 10) hist.removeFirst();

                // Schedule next
                int min = cfg.get("minDelaySeconds"), max = cfg.get("maxDelaySeconds");
                if (min < 1) min = 1;
                if (max > 3600) max = 3600;
                if (min > max) { int t = min; min = max; max = t; }
                nextAutoReplyTick = ThreadLocalRandom.current().nextInt(min, max + 1) * 20;
            } catch (Exception e) {
                Notification.showNotification("error", "Auto-reply error: " + e.getMessage());
            } finally {
                autoReplyInProgress = false;
            }
        }, "AutoReply-Thread").start();
    }

    // The original streaming response logic
    public static void response(String sender, String text, String prefix, MinecraftClient client, ConfigManager cfg) {
        // … your existing code unchanged …
    }

    // Helper to parse <player>name</player> in GAME chat
    private String extractSenderName(String msg) {
        if (msg == null) return null;
        int o = msg.indexOf('<'), c = msg.indexOf('>', o);
        if (o < 0 || c <= o+1) return null;
        return msg.substring(o+1, c).replaceAll("§.", "");
    }
}
