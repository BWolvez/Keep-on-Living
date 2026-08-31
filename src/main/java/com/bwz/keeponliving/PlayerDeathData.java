package com.bwz.keeponliving;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class PlayerDeathData {
    public int deathCount = 0;
    public int iframeCooldown = 0;
    public int graceTimer = 0;
    public int iframeTimer = 0;

    // Codec for automatic saving/loading by NeoForge in 1.21.1
    public static final Codec<PlayerDeathData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("deathCount", 0).forGetter(d -> d.deathCount),
            Codec.INT.optionalFieldOf("iframeCooldown", 0).forGetter(d -> d.iframeCooldown),
            Codec.INT.optionalFieldOf("graceTimer", 0).forGetter(d -> d.graceTimer),
            Codec.INT.optionalFieldOf("iframeTimer", 0).forGetter(d -> d.iframeTimer)
    ).apply(instance, PlayerDeathData::new));

    public PlayerDeathData() {}

    public PlayerDeathData(int deathCount, int iframeCooldown, int graceTimer, int iframeTimer) {
        this.deathCount = deathCount;
        this.iframeCooldown = iframeCooldown;
        this.graceTimer = graceTimer;
        this.iframeTimer = iframeTimer;
    }
}