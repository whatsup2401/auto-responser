package com.responser.utils;
import com.responser.config.ConfigManager;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MentionChecker {
    public static String checkMention(String text, String name) {
        Set<String> variants = new LinkedHashSet<>();
        String custom = ConfigManager.getInstance().get("customMentions");
        if (custom != null && !custom.isBlank()) {
            for (String s : custom.split(",")) variants.add(s.strip().toLowerCase());
        }
        if (ConfigManager.getInstance().get("autoOutputMentions")) {
            String lower = name.toLowerCase();
            variants.add(lower);
            variants.add(lower.replaceAll("\\d", ""));
        }
        String txt = text.toLowerCase();
        List<String> clean = variants.stream().filter(v -> !v.isBlank()).collect(Collectors.toList());
        for (String v : clean) if (txt.contains(v)) return v;
        return null;
    }
}
