package com.responser.config;

import com.terraformersmc.modmenu.api.ModMenuApi;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import net.minecraft.text.Text;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        ConfigManager cfg = ConfigManager.getInstance();
        return parent -> {
            ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("Auto Responser"));

            // ─── Main Settings ───────────────────────────────────────────────
            var main = builder.getOrCreateCategory(Text.translatable("MAIN"));
            main.addEntry(builder.entryBuilder()
                .startBooleanToggle(Text.translatable("ENABLE"), cfg.get("enable"))
                .setDefaultValue(true)
                .setSaveConsumer(val -> cfg.set("enable", val))
                .build())
            .addEntry(builder.entryBuilder()
                .startStrField(Text.translatable("API KEY"), cfg.get("apiKey"))
                .setDefaultValue("sk-...")
                .setSaveConsumer(val -> cfg.set("apiKey", val))
                .build())
            .addEntry(builder.entryBuilder()
                .startStrField(Text.translatable("MODEL"), cfg.get("modelId"))
                .setDefaultValue("meta-llama/llama-3.3-70b-instruct")
                .setSaveConsumer(val -> cfg.set("modelId", val))
                .build())
            .addEntry(builder.entryBuilder()
                .startIntField(Text.translatable("WAITING TIME (seconds)"), cfg.get("delayS"))
                .setDefaultValue(3)
                .setMin(0).setMax(60)
                .setSaveConsumer(val -> cfg.set("delayS", val))
                .build())
            .addEntry(builder.entryBuilder()
                .startStrField(Text.translatable("CUSTOM MENTIONS (split for \"\")"), cfg.get("customMentions"))
                .setDefaultValue("")
                .setSaveConsumer(val -> cfg.set("customMentions", val))
                .build())
            .addEntry(builder.entryBuilder()
                .startBooleanToggle(Text.translatable("AUTO MENTIONS OUTPUT"), cfg.get("autoOutputMentions"))
                .setTooltip(Text.translatable("your username will be decomposed into 4 options: just username, username translated into Russian letters, and these 2 options without numbers"))
                .setDefaultValue(true)
                .setSaveConsumer(val -> cfg.set("autoOutputMentions", val))
                .build());

            // ─── Message Delay Settings ─────────────────────────────────────
            var delayCategory = builder.getOrCreateCategory(Text.translatable("MESSAGE SEND DELAY (for antispam system)"));
            delayCategory.addEntry(builder.entryBuilder()
                .startIntField(Text.translatable("DELAY (ms)"), cfg.get("sendDelay"))
                .setDefaultValue(1000)
                .setMin(0).setMax(10000)
                .setSaveConsumer(val -> cfg.set("sendDelay", val))
                .build());
            delayCategory.addEntry(builder.entryBuilder()
                .startIntField(Text.translatable("DELAY RANDOM FACTOR (ms)"), cfg.get("delayRandomFactor"))
                .setDefaultValue(500)
                .setMin(0).setMax(5000)
                .setSaveConsumer(val -> cfg.set("delayRandomFactor", val))
                .build());

            // ─── Auto-Reply Settings ─────────────────────────────────────────
            var autoReply = builder.getOrCreateCategory(Text.translatable("AUTO REPLY"));
            autoReply.addEntry(builder.entryBuilder()
                .startBooleanToggle(Text.translatable("PREFIX WITH USERNAME"), cfg.get("prefixWithUsername"))
                .setDefaultValue(false)
                .setTooltip(Text.translatable("When enabled, each reply starts with the last sender’s name + colon."))
                .setSaveConsumer(val -> cfg.set("prefixWithUsername", val))
                .build());
            autoReply.addEntry(builder.entryBuilder()
                .startBooleanToggle(Text.translatable("RANDOM REPLY MODE"), cfg.get("randomReplyMode"))
                .setDefaultValue(false)
                .setTooltip(Text.translatable("Automatically reply to the latest message at random intervals."))
                .setSaveConsumer(val -> cfg.set("randomReplyMode", val))
                .build());
            autoReply.addEntry(builder.entryBuilder()
                .startIntField(Text.translatable("MIN DELAY (s)"), cfg.get("minDelaySeconds"))
                .setDefaultValue(30)
                .setMin(1).setMax(3600)
                .setTooltip(Text.translatable("Minimum delay in seconds before auto-reply."))
                .setSaveConsumer(val -> cfg.set("minDelaySeconds", val))
                .build());
            autoReply.addEntry(builder.entryBuilder()
                .startIntField(Text.translatable("MAX DELAY (s)"), cfg.get("maxDelaySeconds"))
                .setDefaultValue(120)
                .setMin(1).setMax(3600)
                .setTooltip(Text.translatable("Maximum delay in seconds before auto-reply."))
                .setSaveConsumer(val -> cfg.set("maxDelaySeconds", val))
                .build());

            return builder.build();
        };
    }
}
