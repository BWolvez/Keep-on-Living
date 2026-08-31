package com.bwz.keeponliving;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

@Mod(LifePenalty.MODID)
public class LifePenalty {
    public static final String MODID = "keeponliving";
    public static final ResourceLocation HEALTH_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(MODID, "death_penalty");

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MODID);

    public static final Supplier<AttachmentType<PlayerDeathData>> PLAYER_DATA = ATTACHMENT_TYPES.register(
            "player_data",
            () -> AttachmentType.builder(PlayerDeathData::new).serialize(PlayerDeathData.CODEC).build()
    );

    public LifePenalty(IEventBus modEventBus, ModContainer modContainer) {
        // Register the server config
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        ATTACHMENT_TYPES.register(modEventBus);
        NeoForge.EVENT_BUS.register(this);
    }

    private void updateMaxHealth(Player player) {
        PlayerDeathData data = player.getData(PLAYER_DATA);
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);

        if (maxHealth != null) {
            maxHealth.removeModifier(HEALTH_MODIFIER_ID);

            // 1 death = 2.0 HP (1 Whole Heart)
            int maxDeaths = Config.MAX_DEATHS.get();
            double penalty = Math.min(data.deathCount * 2.0, maxDeaths * 2.0);

            if (penalty > 0) {
                maxHealth.addPermanentModifier(new AttributeModifier(HEALTH_MODIFIER_ID, -penalty, AttributeModifier.Operation.ADD_VALUE));
            }
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            Player original = event.getOriginal();
            Player newPlayer = event.getEntity();

            PlayerDeathData oldData = original.getData(PLAYER_DATA);
            PlayerDeathData newData = newPlayer.getData(PLAYER_DATA);

            newData.deathCount = oldData.deathCount + 1;
            newData.iframeCooldown = oldData.iframeCooldown;
            newData.graceTimer = 0;
            newData.iframeTimer = 0;
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        updateMaxHealth(player);

        PlayerDeathData data = player.getData(PLAYER_DATA);

        if (data.deathCount >= Config.MAX_DEATHS.get()) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 2 * 60 * 20, 9, true,true, true));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 5 * 60 * 20, 9, true, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 5 * 60 * 20, 9, true, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 5 * 60 * 20, 9, true, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 5 * 60 * 20, 9, true, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 5 * 60 * 20, 9, true, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 5 * 60 * 20, 9, true, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 5 * 60 * 20, 9, true, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 5 * 60 * 20, 9, true, true, true));

            // Set Grace Timer using the configured string, fallback to 5m (6000 ticks)
            data.graceTimer = Config.parseToTicks(Config.GRACE_PERIOD_TIME.get(), 6000);
            player.displayClientMessage(Component.literal("Run While you can...").withStyle(ChatFormatting.RED), false);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GHAST_HURT, SoundSource.PLAYERS, 5.0f, 1.2f);
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        updateMaxHealth(event.getEntity());
    }

    @SubscribeEvent
    public void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            PlayerDeathData data = player.getData(PLAYER_DATA);
            if (data.graceTimer > 0 || data.iframeTimer > 0) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof Player player) {
            PlayerDeathData data = player.getData(PLAYER_DATA);
            float health = player.getHealth();
            float damage = event.getNewDamage();

            if (damage <= 0) return;

            // One-Shot Prevention
            if (health >= player.getMaxHealth() && damage >= health) {
                event.setNewDamage(health - 1.0f);
                damage = event.getNewDamage();
            }

            // I-Frame Trigger (<= Half Heart)
            if (health - damage <= 1.0f && data.iframeCooldown <= 0) {
                if (health > 1.0f) {
                    event.setNewDamage(health - 1.0f);
                } else {
                    event.setNewDamage(0.0f);
                }

                data.iframeTimer = 40; // 2 seconds
                data.iframeCooldown = Config.parseToTicks(Config.IFRAME_COOLDOWN_TIME.get(), 72000);
                player.displayClientMessage(Component.literal("!!!").withStyle(ChatFormatting.GOLD), true);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 5.0f, 1.2f);
            }
        }
    }

    @SubscribeEvent
    public void onTargetChange(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof Player player) {
            PlayerDeathData data = player.getData(PLAYER_DATA);
            if (data.graceTimer > 0) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!event.getEntity().level().isClientSide && event.getEntity() instanceof Player player) {
            ItemStack item = event.getItem();

            if (item.is(Items.GOLDEN_APPLE)) {
                PlayerDeathData data = player.getData(PLAYER_DATA);
                if (data.deathCount > 0) {
                    data.deathCount--;
                    updateMaxHealth(player);
                    player.displayClientMessage(Component.literal("Revitalized!").withStyle(ChatFormatting.GREEN), true);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 5.0f, 1.2f);
                }
                if (data.deathCount == 0) {
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 5.0f, 1.0f);
                }
            } else if (item.is(Items.ENCHANTED_GOLDEN_APPLE)) {
                PlayerDeathData data = player.getData(PLAYER_DATA);
                if (data.iframeCooldown > 0) {
                    data.iframeCooldown = 0;
                    player.displayClientMessage(Component.literal("Favor Gained!").withStyle(ChatFormatting.YELLOW), true);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 5.0f, 1.35f);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 5.0f, 0.45f);
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide) {
            PlayerDeathData data = player.getData(PLAYER_DATA);

            if (data.iframeCooldown > 0) data.iframeCooldown--;
            if (data.iframeTimer > 0) data.iframeTimer--;

            if (data.graceTimer > 0) {
                data.graceTimer--;

                // --- Visual Action Bar Construction ---
                int MAX_GRACE = Config.parseToTicks(Config.GRACE_PERIOD_TIME.get(), 6000);
                float percentage = MAX_GRACE > 0 ? (float) data.graceTimer / MAX_GRACE : 0.0f;

                int barLength = 20;
                int filledBars = Math.max(0, Math.min(barLength, (int) (percentage * barLength)));
                int emptyBars = barLength - filledBars;

                MutableComponent actionBarText = Component.literal("Grace: [")
                        .append(Component.literal("█".repeat(filledBars)).withStyle(ChatFormatting.GREEN))
                        .append(Component.literal("▒".repeat(emptyBars)).withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.literal("] " + (int)(percentage * 100) + "%").withStyle(ChatFormatting.WHITE));

                player.displayClientMessage(actionBarText, true);

                // --- Play Ticking Sound (Last 10 Seconds) ---
                if (data.graceTimer <= 200 && data.graceTimer % 20 == 0 && data.graceTimer > 0) {
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_PLING, SoundSource.PLAYERS, 5.0f, 1.2f);
                }

                // --- Play Scary Sound (Timer Ends) ---
                if (data.graceTimer == 1) {
                    player.displayClientMessage(Component.literal("Grace Lost, you're now on your own").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 5.0f, 1.0f);
                }
            }
        }
    }
}