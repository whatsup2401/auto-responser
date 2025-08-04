package com.responser.config;

import com.terraformersmc.modmenu.api.ModMenuApi;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import net.minecraft.text.Text;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        ConfigManager cfg = ConfigManager.getInstance();
        return parent -> {
            ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("Auto Responser"));

            // ===== MAIN category =====
            ConfigCategory main = builder.getOrCreateCategory(Text.translatable("MAIN"));

            main.addEntry(builder.entryBuilder()
                .startBooleanToggle(Text.translatable("ENABLE"), (Boolean) cfg.get("enable"))
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> cfg.set("enable", newValue))
                .build());

            main.addEntry(builder.entryBuilder()
                .startStrField(Text.translatable("API KEY"), (String) cfg.get("apiKey"))
                .setDefaultValue("sk-...")
                .setSaveConsumer(newValue -> cfg.set("apiKey", newValue))
                .build());

            main.addEntry(builder.entryBuilder()
                .startStrField(Text.translatable("MODEL"), (String) cfg.get("modelId"))
                .setDefaultValue("meta-llama/llama-3.3-70b-instruct")
                .setSaveConsumer(newValue -> cfg.set("modelId", newValue))
                .build());

            // Waiting time as a SLIDER in SECONDS (1–120)
            main.addEntry(builder.entryBuilder()
                .startIntSlider(Text.translatable("WAITING TIME (seconds)"), ((Number) cfg.get("delayS")).intValue(), 1, 120)
                .setDefaultValue(3)
                .setTooltip(Text.literal("How many seconds to wait before replying (1–120)."))
                .setSaveConsumer(newValue -> cfg.set("delayS", newValue))
                .build());

            main.addEntry(builder.entryBuilder()
                .startStrField(Text.translatable("CUSTOM MENTIONS (split for \",\")"), (String) cfg.get("customMentions"))
                .setDefaultValue("")
                .setSaveConsumer(newValue -> cfg.set("customMentions", newValue))
                .build());

            main.addEntry(builder.entryBuilder()
                .startBooleanToggle(Text.translatable("AUTO MENTIONS OUTPUT"), (Boolean) cfg.get("autoOutputMentions"))
                .setTooltip(Text.translatable("your username will be decomposed into 4 options: just username, username translated into Russian letters, and these 2 options without numbers"))
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> cfg.set("autoOutputMentions", newValue))
                .build());

            // NEW: Random Responses toggle
            main.addEntry(builder.entryBuilder()
                .startBooleanToggle(Text.translatable("RANDOM RESPONSES"), (Boolean) cfg.get("randomResponses"))
                .setDefaultValue(false)
                .setTooltip(Text.literal("If ON, reply with a random one-liner instead of AI."))
                .setSaveConsumer(newValue -> cfg.set("randomResponses", newValue))
                .build());

            // ===== DELAY category (antispam) =====
            ConfigCategory delayCategory = builder.getOrCreateCategory(Text.translatable("MESSAGE SEND DELAY (for antispam system)"));

            // Replace old ms field with seconds slider already present above.
            // Keep jitter (random factor) in milliseconds so users can add/subtract a little randomness.
            delayCategory.addEntry(builder.entryBuilder()
                .startIntField(Text.translatable("DELAY RANDOM FACTOR (ms)"), ((Number) cfg.get("delayRandomFactor")).intValue())
                .setDefaultValue(500)
                .setMin(0)
                .setMax(10000)
                .setTooltip(Text.literal("Adds/subtracts this many ms randomly to the delay."))
                .setSaveConsumer(newValue -> cfg.set("delayRandomFactor", newValue))
                .build());

            return builder.build();
        };
    }
}
