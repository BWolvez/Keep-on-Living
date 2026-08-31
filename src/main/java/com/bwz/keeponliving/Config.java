package com.bwz.keeponliving;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MAX_DEATHS;
    public static final ModConfigSpec.ConfigValue<String> GRACE_PERIOD_TIME;
    public static final ModConfigSpec.ConfigValue<String> IFRAME_COOLDOWN_TIME;

    static {
        BUILDER.push("Life Penalty Settings");

        MAX_DEATHS = BUILDER
                .comment("Maximum number of deaths before the penalty caps out. (e.g., 8 deaths = 4 HP / 2 Hearts remaining).")
                .defineInRange("maxDeaths", 8, 1, 100);

        GRACE_PERIOD_TIME = BUILDER
                .comment("Grace period length. Supports formats: 'd' (days), 'm' (minutes), 's' (seconds), 't' (ticks). Example: '5m' or '6000'")
                .define("gracePeriodTime", "5m");

        IFRAME_COOLDOWN_TIME = BUILDER
                .comment("I-Frame cooldown length. Supports formats: 'd' (days), 'm' (minutes), 's' (seconds), 't' (ticks). Example: '3d' or '72000'")
                .define("iframeCooldownTime", "3d");

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    /**
     * Parses a formatted string into raw Minecraft ticks.
     * 1 second (s) = 20 ticks | 1 minute (m) = 1200 ticks | 1 in-game day (d) = 24000 ticks
     */
    public static int parseToTicks(String input, int defaultTicks) {
        if (input == null || input.isBlank()) return defaultTicks;
        input = input.trim().toLowerCase();

        try {
            if (input.endsWith("d")) {
                return Integer.parseInt(input.replace("d", "")) * 24000;
            } else if (input.endsWith("m")) {
                return Integer.parseInt(input.replace("m", "")) * 1200;
            } else if (input.endsWith("s")) {
                return Integer.parseInt(input.replace("s", "")) * 20;
            } else if (input.endsWith("t")) {
                return Integer.parseInt(input.replace("t", ""));
            } else {
                return Integer.parseInt(input);
            }
        } catch (NumberFormatException e) {
            System.err.println("LifePenalty Config Error: Invalid time format '" + input + "'. Using default.");
            return defaultTicks;
        }
    }
}