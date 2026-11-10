package com.example.examplemod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommandmentsHandler {

    private static final Random RANDOM = new Random();

    // ==================== 属性修饰符 UUID ====================
    private static final UUID B1_ATK = UUID.nameUUIDFromBytes("cmd_1_bless_atk".getBytes());
    private static final UUID B1_HEALTH = UUID.nameUUIDFromBytes("cmd_1_bless_health".getBytes());
    private static final UUID B1_ARMOR = UUID.nameUUIDFromBytes("cmd_1_bless_armor".getBytes());
    private static final UUID B1_SPEED = UUID.nameUUIDFromBytes("cmd_1_bless_speed".getBytes());
    private static final UUID C1_ATK = UUID.nameUUIDFromBytes("cmd_1_curse_atk".getBytes());

    private static final UUID B2_HEALTH = UUID.nameUUIDFromBytes("cmd_2_bless_health".getBytes());

    private static final UUID B3_ATK = UUID.nameUUIDFromBytes("cmd_3_bless_atk".getBytes());
    private static final UUID C3_ATK = UUID.nameUUIDFromBytes("cmd_3_curse_atk".getBytes());

    private static final UUID B4_SPEED = UUID.nameUUIDFromBytes("cmd_4_bless_speed".getBytes());

    private static final UUID C6_ATK = UUID.nameUUIDFromBytes("cmd_6_curse_atk".getBytes());
    private static final UUID C6_HEALTH = UUID.nameUUIDFromBytes("cmd_6_curse_health".getBytes());

    private static final UUID B8_LUCK = UUID.nameUUIDFromBytes("cmd_8_bless_luck".getBytes());

    private static final UUID B9_ATK_SPEED = UUID.nameUUIDFromBytes("cmd_9_bless_atk_speed".getBytes());
    private static final UUID C9_ATK_SPEED = UUID.nameUUIDFromBytes("cmd_9_curse_atk_speed".getBytes());

    // 玩家背包快照（打开容器时记录）
    private static final java.util.Map<UUID, List<ItemStack>> CONTAINER_SNAPSHOT = new java.util.HashMap<>();

    // 🚨 超级绑定诅咒 —— 死亡时暂存《圣典》，重生后装回饰品栏
    private static final java.util.Map<UUID, ItemStack> SAVED_SCRIPTURES = new java.util.HashMap<>();

    // ==================== 工具方法 ====================
    private static ItemStack getScripture(Player player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inv -> inv.findFirstCurio(ExampleMod.SCRIPTURE_OF_COMMANDMENTS.get()))
                .map(slotResult -> slotResult.stack())
                .orElse(ItemStack.EMPTY);
    }

    private static boolean isNeutralMob(net.minecraft.world.entity.Entity entity) {
        return entity instanceof net.minecraft.world.entity.monster.EnderMan
                || entity instanceof net.minecraft.world.entity.monster.ZombifiedPiglin
                || entity instanceof net.minecraft.world.entity.monster.piglin.Piglin
                || entity instanceof net.minecraft.world.entity.animal.Bee
                || entity instanceof net.minecraft.world.entity.animal.Wolf
                || entity instanceof net.minecraft.world.entity.animal.Cat
                || entity instanceof net.minecraft.world.entity.animal.horse.Llama
                || entity instanceof net.minecraft.world.entity.animal.PolarBear;
    }

    private static boolean isContainerBlock(net.minecraft.world.level.block.Block block) {
        return block == Blocks.CHEST
                || block == Blocks.TRAPPED_CHEST
                || block == Blocks.BARREL
                || block == Blocks.SHULKER_BOX
                || block == Blocks.HOPPER
                || block == Blocks.DISPENSER
                || block == Blocks.DROPPER
                || block == Blocks.FURNACE
                || block == Blocks.BLAST_FURNACE
                || block == Blocks.SMOKER
                || block == Blocks.BREWING_STAND
                || block == Blocks.ENDER_CHEST;
    }

    // 🚨 判断容器是否为 Curios 饰品栏（打开饰品栏不算“翻容器”，取下饰品不触发第八诫）
    private static boolean isCuriosContainer(net.minecraft.world.inventory.AbstractContainerMenu container) {
        if (container == null) return false;
        String className = container.getClass().getName();
        return className.contains("curio") || className.contains("Curio")
                || className.startsWith("top.theillusivec4.curios");
    }

    // 🚨 判断玩家饰品栏里是否已经戴着《圣典》
    private static boolean hasScriptureEquipped(Player player) {
        return !getScripture(player).isEmpty();
    }

    // ==================== 主 tick ====================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        // 🚨 超级绑定诅咒：检测是否有待恢复的《圣典》
        restoreScriptureIfNeeded(player);

        ItemStack scripture = getScripture(player);
        if (scripture.isEmpty()) {
            removeAllEffects(player);
            return;
        }

        CompoundTag tag = scripture.getOrCreateTag();
        checkCommandment1(player, tag);
        checkCommandment4(player, tag);
        applyCommandment1Effects(player, tag);
        applyCommandment2Effects(player, tag);
        applyCommandment3Effects(player, tag);
        applyCommandment4Effects(player, tag);
        applyCommandment5Blessing(player, tag);
        applyCommandment6Effects(player, tag);
        applyCommandment7Effects(player, tag);
        applyCommandment8Effects(player, tag);
        applyCommandment9Effects(player, tag);
        applyCommandment10Effects(player, tag);
    }

    // 🚨 超级绑定：重生后延迟一 tick 恢复圣典，避开 Curios 尚未就绪的时序问题
    private static void restoreScriptureIfNeeded(Player player) {
        ItemStack saved = SAVED_SCRIPTURES.get(player.getUUID());
        if (saved == null || saved.isEmpty()) return;
        if (player.isDeadOrDying()) return;

        // 已经戴着就不重复装
        if (hasScriptureEquipped(player)) {
            SAVED_SCRIPTURES.remove(player.getUUID());
            return;
        }

        // 尝试装进饰品栏
        boolean equipped = CuriosApi.getCuriosInventory(player).map(handler -> {
            for (var entry : handler.getCurios().entrySet()) {
                var slotHandler = entry.getValue();
                for (int i = 0; i < slotHandler.getSlots(); i++) {
                    if (slotHandler.getStacks().isItemValid(i, saved)) {
                        slotHandler.getStacks().setStackInSlot(i, saved.copy());
                        return true;
                    }
                }
            }
            return false;
        }).orElse(false);

        if (!equipped) {
            // 饰品栏没位置，放背包
            player.getInventory().add(saved.copy());
        }

        SAVED_SCRIPTURES.remove(player.getUUID());
        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§d圣典已随你穿越死亡。"), true);
    }

    // ==================== 第一条 · 独一 ====================
    private static void checkCommandment1(Player player, CompoundTag tag) {
        if (tag.getBoolean("Curse_1")) return;

        boolean hasForeignCurio = CuriosApi.getCuriosInventory(player).resolve()
                .map(handler -> {
                    for (var entry : handler.getCurios().entrySet()) {
                        var slotHandler = entry.getValue();
                        for (int i = 0; i < slotHandler.getSlots(); i++) {
                            ItemStack stack = slotHandler.getStacks().getStackInSlot(i);
                            if (stack.isEmpty()) continue;
                            var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
                            if (key != null && !key.getNamespace().equals(ExampleMod.MODID)) return true;
                        }
                    }
                    return false;
                })
                .orElse(false);

        if (hasForeignCurio) {
            tag.putBoolean("Curse_1", true);
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§4【第一诫 · 独一】已被违逆。"), true);
        }
    }

    private static void applyCommandment1Effects(Player player, CompoundTag tag) {
        var atk = player.getAttribute(Attributes.ATTACK_DAMAGE);
        var health = player.getAttribute(Attributes.MAX_HEALTH);
        var armor = player.getAttribute(Attributes.ARMOR);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (atk == null || health == null || armor == null || speed == null) return;

        if (tag.getBoolean("Curse_1")) {
            atk.removeModifier(B1_ATK);
            health.removeModifier(B1_HEALTH);
            armor.removeModifier(B1_ARMOR);
            speed.removeModifier(B1_SPEED);

            int foreignCount = countForeignCurios(player);
            double penalty = Math.min(0.10 + Math.min(foreignCount, 5) * 0.08, 0.40);
            atk.removeModifier(C1_ATK);
            atk.addTransientModifier(new AttributeModifier(C1_ATK, "cmd_1_curse_atk", -penalty, AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else {
            atk.removeModifier(C1_ATK);
            if (atk.getModifier(B1_ATK) == null)
                atk.addTransientModifier(new AttributeModifier(B1_ATK, "cmd_1_bless_atk", 0.10, AttributeModifier.Operation.MULTIPLY_TOTAL));
            if (health.getModifier(B1_HEALTH) == null)
                health.addTransientModifier(new AttributeModifier(B1_HEALTH, "cmd_1_bless_health", 0.10, AttributeModifier.Operation.MULTIPLY_TOTAL));
            if (armor.getModifier(B1_ARMOR) == null)
                armor.addTransientModifier(new AttributeModifier(B1_ARMOR, "cmd_1_bless_armor", 0.10, AttributeModifier.Operation.MULTIPLY_TOTAL));
            if (speed.getModifier(B1_SPEED) == null)
                speed.addTransientModifier(new AttributeModifier(B1_SPEED, "cmd_1_bless_speed", 0.10, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    // ==================== 第二条 · 无像 ====================
    private static void applyCommandment2Effects(Player player, CompoundTag tag) {
        var health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) return;

        if (tag.getBoolean("Curse_2")) {
            health.removeModifier(B2_HEALTH);
        } else {
            if (health.getModifier(B2_HEALTH) == null)
                health.addTransientModifier(new AttributeModifier(B2_HEALTH, "cmd_2_bless_health", 0.10, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    @SubscribeEvent
    public static void onUseTotem(LivingUseTotemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        ItemStack scripture = getScripture(player);
        if (scripture.isEmpty()) return;
        CompoundTag tag = scripture.getOrCreateTag();

        if (tag.getBoolean("Curse_2")) {
            event.setCanceled(true);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§4【第二诫】图腾已对你失效。"), true);
        } else {
            tag.putBoolean("Curse_2", true);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§4【第二诫 · 无像】已被违逆。图腾从此对你失效。"), true);
        }
    }

    // ==================== 第三条 · 圣名 ====================
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        ItemStack scripture = getScripture(player);
        if (scripture.isEmpty()) return;
        CompoundTag tag = scripture.getOrCreateTag();

        var block = event.getLevel().getBlockState(event.getPos()).getBlock();
        boolean isForbiddenBlock = block == Blocks.ENCHANTING_TABLE
                || block == Blocks.ANVIL
                || block == Blocks.CHIPPED_ANVIL
                || block == Blocks.DAMAGED_ANVIL
                || block == Blocks.GRINDSTONE;

        if (!isForbiddenBlock) return;

        if (tag.getBoolean("Curse_3")) {
            event.setCanceled(true);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§4【第三诫】附魔之力已对你封闭。"), true);
        } else {
            tag.putBoolean("Curse_3", true);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§4【第三诫 · 圣名】已被违逆。附魔之力从此对你封闭。"), true);
        }
    }

    private static void applyCommandment3Effects(Player player, CompoundTag tag) {
        var atk = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk == null) return;

        if (tag.getBoolean("Curse_3")) {
            atk.removeModifier(B3_ATK);
            atk.removeModifier(C3_ATK);
            atk.addTransientModifier(new AttributeModifier(C3_ATK, "cmd_3_curse_atk", -0.15, AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else {
            atk.removeModifier(C3_ATK);
            if (atk.getModifier(B3_ATK) == null)
                atk.addTransientModifier(new AttributeModifier(B3_ATK, "cmd_3_bless_atk", 0.15, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    // ==================== 第四条 · 安息 ====================
    private static void checkCommandment4(Player player, CompoundTag tag) {
        if (tag.getBoolean("Curse_4")) return;

        long dayTime = player.level().getDayTime();
        long currentDay = dayTime / 24000L;
        long lastDay = tag.getLong("LastDay_4");

        if (lastDay == 0) {
            tag.putLong("LastDay_4", currentDay);
            return;
        }

        if (currentDay > lastDay) {
            long restTicks = tag.getLong("RestTicks_4");
            if (restTicks < 1200) {
                tag.putBoolean("Curse_4", true);
                tag.putLong("PunishEnd_4", dayTime + 24000);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§4【第四诫 · 安息】已被违逆。"), true);
            }
            tag.putLong("RestTicks_4", 0);
            tag.putLong("LastDay_4", currentDay);
        }

        double moved = Math.abs(player.getX() - player.xOld)
                + Math.abs(player.getY() - player.yOld)
                + Math.abs(player.getZ() - player.zOld);
        if (moved < 0.001) {
            tag.putLong("RestTicks_4", tag.getLong("RestTicks_4") + 1);
        }
    }

    private static void applyCommandment4Effects(Player player, CompoundTag tag) {
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;

        if (tag.getBoolean("Curse_4")) {
            speed.removeModifier(B4_SPEED);
            long dayTime = player.level().getDayTime();
            long punishEnd = tag.getLong("PunishEnd_4");
            if (dayTime < punishEnd) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, true, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 2, true, false, false));
            }
        } else {
            if (speed.getModifier(B4_SPEED) == null)
                speed.addTransientModifier(new AttributeModifier(B4_SPEED, "cmd_4_bless_speed", 0.15, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    // ==================== 第五条 · 孝道 ====================
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt5(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        var target = event.getEntity();
        boolean isFriendly = target instanceof Villager
                || target instanceof WanderingTrader
                || target instanceof IronGolem
                || target instanceof net.minecraft.world.entity.animal.WaterAnimal
                || (target instanceof net.minecraft.world.entity.animal.Animal
                && !isNeutralMob(target));
        if (!isFriendly) return;

        ItemStack scripture = getScripture(player);
        if (scripture.isEmpty()) return;
        CompoundTag tag = scripture.getOrCreateTag();

        if (tag.getBoolean("Curse_5")) {
            event.setCanceled(true);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2, true, false, false));
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§4【第五诫】你已无法伤害友善生物。"), true);
        } else {
            tag.putBoolean("Curse_5", true);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§4【第五诫 · 孝道】已被违逆。从此你无法伤害友善生物。"), true);
        }
    }

    private static void applyCommandment5Blessing(Player player, CompoundTag tag) {
        if (tag.getBoolean("Curse_5")) return;
        if (player.tickCount % 100 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 120, 0, true, false, false));
        }
    }

    @SubscribeEvent
    public static void onPlayerHurt5(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        ItemStack scripture = getScripture(player);
        if (scripture.isEmpty()) return;
        CompoundTag tag = scripture.getOrCreateTag();
        if (tag.getBoolean("Curse_5")) return;

        if (event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) {
            var golems = player.level().getEntitiesOfClass(
                    net.minecraft.world.entity.animal.IronGolem.class,
                    player.getBoundingBox().inflate(16)
            );
            for (var golem : golems) {
                golem.setTarget(attacker);
            }
        }
    }

    // ==================== 第六条 · 不杀 ====================
    @SubscribeEvent
    public static void onLivingDeath6(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        ItemStack scripture = getScripture(player);
        if (scripture.isEmpty()) return;
        CompoundTag tag = scripture.getOrCreateTag();

        if (tag.getBoolean("Curse_6")) {
            int bloodGuilt = tag.getInt("BloodGuilt_6");
            if (bloodGuilt < 7) {
                tag.putInt("BloodGuilt_6", bloodGuilt + 1);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§4【第六诫 · 不杀】血罪累积：" + (bloodGuilt + 1) + " 层"),
                        true);
            }
        } else {
            if (RANDOM.nextFloat() < 0.50f) {
                tag.putBoolean("Curse_6", true);
                tag.putInt("BloodGuilt_6", 1);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§4【第六诫 · 不杀】已被违逆。血罪累积：1 层"),
                        true);
            }
        }
    }

    private static void applyCommandment6Effects(Player player, CompoundTag tag) {
        var atk = player.getAttribute(Attributes.ATTACK_DAMAGE);
        var health = player.getAttribute(Attributes.MAX_HEALTH);
        if (atk == null || health == null) return;

        if (tag.getBoolean("Curse_6")) {
            int bloodGuilt = tag.getInt("BloodGuilt_6");
            double penalty = Math.min(bloodGuilt, 7) * 0.07;

            atk.removeModifier(C6_ATK);
            health.removeModifier(C6_HEALTH);

            atk.addTransientModifier(new AttributeModifier(C6_ATK, "cmd_6_curse_atk", -penalty, AttributeModifier.Operation.MULTIPLY_TOTAL));
            health.addTransientModifier(new AttributeModifier(C6_HEALTH, "cmd_6_curse_health", -penalty, AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else {
            atk.removeModifier(C6_ATK);
            health.removeModifier(C6_HEALTH);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerHurt6(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        ItemStack scripture = getScripture(player);
        if (scripture.isEmpty()) return;
        CompoundTag tag = scripture.getOrCreateTag();

        if (!tag.getBoolean("Curse_6")) {
            event.setAmount(event.getAmount() * 0.80f);
        }
    }

    // ==================== [修复 P0-2 2026-10-21] ====================
    // 原实现没有任何"玩家是否装备圣典"的判断，无条件 event.setCanceled(true)，
    // 导致【整个存档世界】所有非玩家造成的伤害全部失效（僵尸打不死村民、岩浆烧不死怪、摔伤无效）。
    // 多人服里只要有一个人装了模组，全服生态就被改写。
    //
    // 现在改为：只有当伤害目标周围 16 格内存在"已佩戴圣典且已违逆第六诫"的玩家时，才拦下这次伤害。
    // 另外提供总开关 COMMANDMENTS_NONPLAYER_DAMAGE_GUARD，方便整合包/服务器一键关掉本机制。
    //
    // 原代码（保留备查）：
    // @SubscribeEvent(priority = EventPriority.HIGHEST)
    // public static void onNonPlayerDamage6(LivingHurtEvent event) {
    //     if (event.getEntity() instanceof Player) return;
    //     if (event.getSource().getEntity() instanceof Player) return;
    //     event.setCanceled(true);
    // }
    private static final boolean COMMANDMENTS_NONPLAYER_DAMAGE_GUARD = true;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onNonPlayerDamage6(LivingHurtEvent event) {
        if (!COMMANDMENTS_NONPLAYER_DAMAGE_GUARD) return;
        if (event.getEntity() instanceof Player) return;
        if (event.getSource().getEntity() instanceof Player) return;
        if (event.getEntity().level().isClientSide) return;

        // 只有目标附近存在"已违逆第六诫"的圣典持有者时才生效
        if (!hasActiveCurseNearby(event.getEntity(), "Curse_6", 16.0D)) return;

        event.setCanceled(true);
    }

    /**
     * 判断某个实体周围 radius 格内，是否存在"佩戴圣典且对应诅咒已触发"的玩家。
     * 用于替代原先的全局无差别判定。
     */
    private static boolean hasActiveCurseNearby(net.minecraft.world.entity.Entity target,
                                                String curseKey, double radius) {
        for (Player nearby : target.level().getEntitiesOfClass(
                Player.class,
                new net.minecraft.world.phys.AABB(target.blockPosition()).inflate(radius))) {
            ItemStack scripture = getScripture(nearby);
            if (scripture.isEmpty()) continue;
            if (scripture.getOrCreateTag().getBoolean(curseKey)) return true;
        }
        return false;
    }

    // ==================== 第七条 · 忠贞 ====================
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt7(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        if (!isNeutralMob(event.getEntity())) return;

        ItemStack scripture = getScripture(player);
        if (scripture.isEmpty()) return;
        CompoundTag tag = scripture.getOrCreateTag();

        if (tag.getBoolean("Curse_7")) {
            event.setCanceled(true);
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§4【第七诫】你已无法伤害中立生物。"), true);
        } else {
            tag.putBoolean("Curse_7", true);
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§4【第七诫 · 忠贞】已被违逆。"), true);
        }
    }

    private static void applyCommandment7Effects(Player player, CompoundTag tag) {
        if (!tag.getBoolean("Curse_7")) {
            if (player.tickCount % 40 == 0) {
                var neutrals = player.level().getEntitiesOfClass(
                        net.minecraft.world.entity.LivingEntity.class,
                        player.getBoundingBox().inflate(16)
                );
                for (var entity : neutrals) {
                    if (!isNeutralMob(entity)) continue;
                    if (entity instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == player) {
                        mob.setTarget(null);
                    }
                }
            }
            return;
        }

        if (player.tickCount % 20 != 0) return;

        var neutrals = player.level().getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class,
                player.getBoundingBox().inflate(16)
        );
        for (var entity : neutrals) {
            if (!isNeutralMob(entity)) continue;
            if (entity instanceof net.minecraft.world.entity.Mob mob) {
                mob.setTarget(player);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (!isNeutralMob(event.getEntity())) return;

        ItemStack scripture = getScripture(player);
        if (scripture.isEmpty()) return;
        CompoundTag tag = scripture.getOrCreateTag();
        if (tag.getBoolean("Curse_7")) return;

        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLivingDeath7(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        ItemStack scripture = getScripture(player);
        if (scripture.isEmpty()) return;
        CompoundTag tag = scripture.getOrCreateTag();
        if (!tag.getBoolean("Curse_7")) return;

        if (!(event.getEntity() instanceof net.minecraft.world.entity.animal.Animal)) return;

        var nearby = player.level().getEntitiesOfClass(
                event.getEntity().getClass(),
                event.getEntity().getBoundingBox().inflate(16)
        );
        for (var animal : nearby) {
            animal.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 1, true, false, false));
            animal.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600, 1, true, false, false));
            if (animal instanceof net.minecraft.world.entity.Mob mob) {
                mob.setTarget(player);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract7(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        ItemStack scripture = getScripture(player);
        if (scripture.isEmpty()) return;
        CompoundTag tag = scripture.getOrCreateTag();
        if (!tag.getBoolean("Curse_7")) return;

        var target = event.getTarget();
        boolean isTameable = target instanceof net.minecraft.world.entity.animal.Wolf
                || target instanceof net.minecraft.world.entity.animal.Cat
                || target instanceof net.minecraft.world.entity.animal.horse.Horse
                || target instanceof net.minecraft.world.entity.animal.horse.Llama
                || target instanceof net.minecraft.world.entity.animal.Parrot;
        if (!isTameable) return;

        event.setCanceled(true);
        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§4【第七诫】你已无法驯服任何生物。"), true);
    }

    // ==================== 第八条 · 不盗 ====================
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        // 🚨 排除 Curios 饰品栏，打开饰品栏不算容器操作
        if (isCuriosContainer(event.getContainer())) return;

        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        ItemStack scripture = getScripture(player);
        if (scripture.isEmpty()) return;

        List<ItemStack> snapshot = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            snapshot.add(stack.copy());
        }
        for (ItemStack stack : player.getInventory().offhand) {
            snapshot.add(stack.copy());
        }
        CONTAINER_SNAPSHOT.put(player.getUUID(), snapshot);
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        // 🚨 排除 Curios 饰品栏，从饰品栏取下饰品不触发第八诫
        if (isCuriosContainer(event.getContainer())) return;

        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        List<ItemStack> snapshot = CONTAINER_SNAPSHOT.remove(player.getUUID());
        if (snapshot == null) return;

        ItemStack scripture = getScripture(player);
        if (scripture.isEmpty()) return;
        CompoundTag tag = scripture.getOrCreateTag();

        List<ItemStack> newItems = new ArrayList<>();

        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack current = player.getInventory().items.get(i);
            ItemStack old = (i < snapshot.size()) ? snapshot.get(i) : ItemStack.EMPTY;

            if (!ItemStack.matches(current, old)) {
                if (!current.isEmpty() && (old.isEmpty() || current.getCount() > old.getCount())) {
                    ItemStack added = current.copy();
                    if (!old.isEmpty()) {
                        added.setCount(current.getCount() - old.getCount());
                    }
                    newItems.add(added);
                }
            }
        }

        int offhandOffset = player.getInventory().items.size();
        for (int i = 0; i < player.getInventory().offhand.size(); i++) {
            ItemStack current = player.getInventory().offhand.get(i);
            int snapshotIndex = offhandOffset + i;
            ItemStack old = (snapshotIndex < snapshot.size()) ? snapshot.get(snapshotIndex) : ItemStack.EMPTY;

            if (!ItemStack.matches(current, old)) {
                if (!current.isEmpty() && (old.isEmpty() || current.getCount() > old.getCount())) {
                    ItemStack added = current.copy();
                    if (!old.isEmpty()) {
                        added.setCount(current.getCount() - old.getCount());
                    }
                    newItems.add(added);
                }
            }
        }

        if (newItems.isEmpty()) return;

        if (!tag.getBoolean("Curse_8")) {
            tag.putBoolean("Curse_8", true);
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§4【第八诫 · 不盗】已被违逆。"), true);
            return;
        }

        for (ItemStack newItem : newItems) {
            if (RANDOM.nextFloat() < 0.30f) {
                int toRemove = newItem.getCount();
                boolean removedAny = false;

                for (int i = 0; i < player.getInventory().items.size() && toRemove > 0; i++) {
                    ItemStack stack = player.getInventory().items.get(i);
                    if (stack.isEmpty() || !ItemStack.isSameItemSameTags(stack, newItem)) continue;
                    int removeCount = Math.min(stack.getCount(), toRemove);
                    stack.shrink(removeCount);
                    toRemove -= removeCount;
                    removedAny = true;
                    if (stack.isEmpty()) player.getInventory().items.set(i, ItemStack.EMPTY);
                }

                for (int i = 0; i < player.getInventory().offhand.size() && toRemove > 0; i++) {
                    ItemStack stack = player.getInventory().offhand.get(i);
                    if (stack.isEmpty() || !ItemStack.isSameItemSameTags(stack, newItem)) continue;
                    int removeCount = Math.min(stack.getCount(), toRemove);
                    stack.shrink(removeCount);
                    toRemove -= removeCount;
                    removedAny = true;
                    if (stack.isEmpty()) player.getInventory().offhand.set(i, ItemStack.EMPTY);
                }

                if (removedAny) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§4【第八诫】偷盗之物已消失。"), true);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDrops8(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        ItemStack scripture = getScripture(player);
        if (scripture.isEmpty()) return;
        CompoundTag tag = scripture.getOrCreateTag();
        if (!tag.getBoolean("Curse_8")) return;

        for (var drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (stack.getCount() > 1) {
                stack.setCount(stack.getCount() / 2);
            }
        }
    }

    private static void applyCommandment8Effects(Player player, CompoundTag tag) {
        var luck = player.getAttribute(Attributes.LUCK);
        if (luck == null) return;

        if (tag.getBoolean("Curse_8")) {
            luck.removeModifier(B8_LUCK);
        } else {
            if (luck.getModifier(B8_LUCK) == null)
                luck.addTransientModifier(new AttributeModifier(B8_LUCK, "cmd_8_bless_luck", 2.0, AttributeModifier.Operation.ADDITION));
        }
    }

    // ==================== 第九条 · 真言 ====================
    private static void applyCommandment9Effects(Player player, CompoundTag tag) {
        var atkSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (atkSpeed == null) return;

        if (tag.getBoolean("Curse_9")) {
            atkSpeed.removeModifier(B9_ATK_SPEED);
            atkSpeed.removeModifier(C9_ATK_SPEED);
        } else {
            if (atkSpeed.getModifier(B9_ATK_SPEED) == null)
                atkSpeed.addTransientModifier(new AttributeModifier(B9_ATK_SPEED, "cmd_9_bless_atk_speed", 0.15, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    @SubscribeEvent
    public static void onChatMessage(net.minecraftforge.event.ServerChatEvent event) {
        Player player = event.getPlayer();
        if (player.level().isClientSide) return;

        String message = event.getMessage().getString();
        if (message.startsWith("/")) return;

        ItemStack scripture = getScripture(player);
        if (scripture.isEmpty()) return;
        CompoundTag tag = scripture.getOrCreateTag();
        if (tag.getBoolean("Curse_9")) return;

        tag.putBoolean("Curse_9", true);
        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§4【第九诫 · 真言】已被违逆。"), true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSignPlace(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        var block = event.getLevel().getBlockState(event.getPos()).getBlock();
        var key = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block);
        if (key == null || !key.getPath().contains("sign")) return;

        ItemStack scripture = getScripture(player);
        if (scripture.isEmpty()) return;
        CompoundTag tag = scripture.getOrCreateTag();
        if (tag.getBoolean("Curse_9")) return;

        tag.putBoolean("Curse_9", true);
        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§4【第九诫 · 真言】已被违逆。"), true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onNameTagUse(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        ItemStack heldItem = player.getItemInHand(event.getHand());
        if (!heldItem.is(net.minecraft.world.item.Items.NAME_TAG)) return;
        if (!heldItem.hasCustomHoverName()) return;

        ItemStack scripture = getScripture(player);
        if (scripture.isEmpty()) return;
        CompoundTag tag = scripture.getOrCreateTag();
        if (tag.getBoolean("Curse_9")) return;

        tag.putBoolean("Curse_9", true);
        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§4【第九诫 · 真言】已被违逆。"), true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt9(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        ItemStack scripture = getScripture(player);
        if (scripture.isEmpty()) return;
        CompoundTag tag = scripture.getOrCreateTag();
        if (!tag.getBoolean("Curse_9")) return;

        if (RANDOM.nextFloat() < 0.20f) {
            event.setCanceled(true);
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§4【第九诫】你的攻击落空了。"), true);
        }
    }

    // ==================== 第十条 · 知足 ====================
    private static void applyCommandment10Effects(Player player, CompoundTag tag) {
        if (tag.getBoolean("Curse_10")) return;
        if (player.tickCount % 20 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, 0, true, false, false));
        }
    }

    private static final java.util.Map<net.minecraft.core.BlockPos, Long> NO_DROP_ORES = new java.util.HashMap<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onOreBreak10(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player.level().isClientSide) return;
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

        BlockState state = event.getState();
        boolean isOre = isOreState(state);

        // 既不是矿石、也不吃时运的方块 → 连圣典都不用查，直接放行（性能优先）
        if (!isOre && !isFortuneAffected(state)) return;

        ItemStack scripture = getScripture(player);
        if (scripture.isEmpty()) return;
        CompoundTag tag = scripture.getOrCreateTag();
        boolean blessed = !tag.getBoolean("Curse_10");

        // ==================== [升级 #6a 2026-10-21] 第十诫祝福：时运 +1 ====================
        // 原实现把「时运 +1」妥协成了「急迫」（挖掘速度），但两者是完全不同的维度：
        //   急迫 = 挖得快；时运 = 掉得多。原来的妥协等于这条祝福没实现。
        //
        // 实现说明（重要）：
        //  · Forge 1.20.1 没有 BlockEvent.HarvestDropsEvent（1.20 已移除），不能用它改掉落列表；
        //  · 改为在 BreakEvent 里手工多掉一份：Block.getDrops(...) 取回本该掉的物品，
        //    再 Block.popResource(...) 放一份，与方块自身的正常掉落同 tick 叠加，
        //    等效于"额外一次掉落掷"（对齐原版时运 I 的 1/3 概率）。
        //  · 祝福判定必须在下面的"违逆判定"之前 —— 同一次挖掘会立刻把 Curse_10 翻成 true。
        //
        // 【设计要点 2026-10-21】祝福作用范围 = 所有"吃时运"的方块（矿石 + 农作物 + 树叶 + ……），
        // 而【违逆条件仍然只认矿石】。这样只要不挖矿，就可以一直种地/撸树叶享受时运 ——
        // 祝福终于有了实际生命周期，也正好对应"不可贪恋"的原意（贪的是地下的矿，不是庄稼）。
        if (blessed && player.getRandom().nextFloat() < 1.0F / 3.0F) {
            spawnExtraDrops(event, serverLevel, player);
        }

        // ==================== 违逆判定：只有矿石才触发（原逻辑）====================
        if (!isOre) return;

        if (blessed) {
            tag.putBoolean("Curse_10", true);
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§4【第十诫 · 知足】已被违逆。"), true);
            return;
        }

        if (RANDOM.nextFloat() < 0.25f) {
            NO_DROP_ORES.put(event.getPos().immutable(), serverLevel.getGameTime());
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§4【第十诫】矿石碎裂，却一无所获。"), true);
        }
    }

    /**
     * 按"时运 I"的概率额外掉一份产物。
     * 只使用原版 API：{@code Block.getDrops} + {@code Block.popResource}，不依赖任何 Forge 掉落事件。
     */
    private static void spawnExtraDrops(net.minecraftforge.event.level.BlockEvent.BreakEvent event,
                                        net.minecraft.server.level.ServerLevel level,
                                        Player player) {
        net.minecraft.core.BlockPos pos = event.getPos();
        var drops = net.minecraft.world.level.block.Block.getDrops(
                event.getState(), level, pos, level.getBlockEntity(pos), player, player.getMainHandItem());
        for (ItemStack stack : drops) {
            if (!stack.isEmpty()) {
                net.minecraft.world.level.block.Block.popResource(level, pos, stack.copy());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemSpawn10(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity itemEntity)) return;

        long currentTime = event.getLevel().getGameTime();
        NO_DROP_ORES.entrySet().removeIf(entry -> currentTime - entry.getValue() > 10);

        net.minecraft.core.BlockPos itemPos = itemEntity.blockPosition();
        for (net.minecraft.core.BlockPos pos : NO_DROP_ORES.keySet()) {
            if (pos.distSqr(itemPos) <= 2.0D) {
                event.setCanceled(true);
                NO_DROP_ORES.remove(pos);
                return;
            }
        }
    }

    // ==================== [升级 #6b 2026-10-21] 第八诫诅咒：抢夺无效（结论：只能维持"减半"） ====================
    // 原计划用 LootingLevelEvent 把抢夺等级归零，但【Forge 1.20.1 没有这个事件】，编译期即报错。
    // 该事件在更早的版本存在，1.20 的掉落系统改造（LootParams / LootingModifier）之后被移除了。
    //
    // 1.20.1 可选的精确方案只有两种，成本都偏高，本轮不做：
    //   a) Mixin 注入 LivingEntity#getLootingLevel 或 LootContext 构造处 —— 已决定放弃 Mixin；
    //   b) Global Loot Modifier（GLM）过滤 minecraft:entity 战利品表 —— 只能"减少/替换"掉落，
    //      无法按"无抢夺"重算，语义仍不精确。
    //
    // 因此维持原妥协：onLivingDrops8 里的「掉落数量减半」。
    // 这是本轮唯一没能升级的一项，属于 Forge 1.20.1 的 API 限制，不是实现问题。

    /**
     * 判断方块是否为"矿石类"。抽出来供第十诫的祝福与诅咒共用，避免两处判定漂移。
     */
    private static boolean isOreState(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.COAL_ORES)
                || state.is(net.minecraft.tags.BlockTags.IRON_ORES)
                || state.is(net.minecraft.tags.BlockTags.COPPER_ORES)
                || state.is(net.minecraft.tags.BlockTags.GOLD_ORES)
                || state.is(net.minecraft.tags.BlockTags.DIAMOND_ORES)
                || state.is(net.minecraft.tags.BlockTags.EMERALD_ORES)
                || state.is(net.minecraft.tags.BlockTags.LAPIS_ORES)
                || state.is(net.minecraft.tags.BlockTags.REDSTONE_ORES)
                || state.is(Blocks.ANCIENT_DEBRIS)
                || state.is(Blocks.NETHER_QUARTZ_ORE);
    }

    /**
     * 判断方块是否"受时运影响"——即祝福（时运 +1）的作用范围。
     *
     * <p>这里刻意与 {@link #isOreState} 分开：<b>祝福范围广，诅咒触发只认矿石</b>。
     * 于是不挖矿的玩家（种地、撸树叶、挖砂砾）可以长期享受时运，而不会立刻违逆第十诫。</p>
     *
     * <p>覆盖清单对应原版时运的作用面，只挑高确定度的条目：
     * 矿石/农作物/树叶三类靠 tag，其余用具体方块；未列入的方块不享受时运。</p>
     */
    private static boolean isFortuneAffected(BlockState state) {
        return isOreState(state)
                || state.is(net.minecraft.tags.BlockTags.CROPS)        // 小麦/胡萝卜/马铃薯/甜菜
                || state.is(net.minecraft.tags.BlockTags.LEAVES)       // 树叶 → 树苗/苹果
                || state.is(Blocks.GRAVEL)                             // → 燧石
                || state.is(Blocks.NETHER_WART)
                || state.is(Blocks.COCOA)
                || state.is(Blocks.MELON)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.AMETHYST_CLUSTER)
                || state.is(Blocks.GLOWSTONE)
                || state.is(Blocks.SEA_LANTERN)
                || state.is(Blocks.TALL_GRASS)                         // → 种子
                || state.is(Blocks.LARGE_FERN)                         // → 种子
                || state.is(Blocks.FERN);                              // → 种子
    }

    // ==================== 🚨 超级绑定诅咒 · 死亡不掉落 ====================
    // 优先级用 LOWEST，确保在 Curios 自己的掉落处理之后再剔除圣典
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDropsKeepScripture(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        event.getDrops().removeIf(drop -> {
            ItemStack stack = drop.getItem();
            if (stack.is(ExampleMod.SCRIPTURE_OF_COMMANDMENTS.get())) {
                SAVED_SCRIPTURES.put(player.getUUID(), stack.copy());
                return true;
            }
            return false;
        });
    }

    // 注意：不再使用 PlayerEvent.Clone，改用主 tick 里的 restoreScriptureIfNeeded 延迟装载

    // ==================== 移除所有效果 ====================
    private static void removeAllEffects(Player player) {
        var atk = player.getAttribute(Attributes.ATTACK_DAMAGE);
        var health = player.getAttribute(Attributes.MAX_HEALTH);
        var armor = player.getAttribute(Attributes.ARMOR);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        var luck = player.getAttribute(Attributes.LUCK);
        var atkSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (atk != null) { atk.removeModifier(B1_ATK); atk.removeModifier(C1_ATK); atk.removeModifier(B3_ATK); atk.removeModifier(C3_ATK); atk.removeModifier(C6_ATK); }
        if (health != null) { health.removeModifier(B1_HEALTH); health.removeModifier(B2_HEALTH); health.removeModifier(C6_HEALTH); }
        if (armor != null) armor.removeModifier(B1_ARMOR);
        if (speed != null) { speed.removeModifier(B1_SPEED); speed.removeModifier(B4_SPEED); }
        if (luck != null) luck.removeModifier(B8_LUCK);
        if (atkSpeed != null) { atkSpeed.removeModifier(B9_ATK_SPEED); atkSpeed.removeModifier(C9_ATK_SPEED); }
    }

    private static int countForeignCurios(Player player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .map(handler -> {
                    int count = 0;
                    for (var entry : handler.getCurios().entrySet()) {
                        var slotHandler = entry.getValue();
                        for (int i = 0; i < slotHandler.getSlots(); i++) {
                            ItemStack stack = slotHandler.getStacks().getStackInSlot(i);
                            if (stack.isEmpty()) continue;
                            var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
                            if (key != null && !key.getNamespace().equals(ExampleMod.MODID)) count++;
                        }
                    }
                    return count;
                })
                .orElse(0);
    }

    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        ItemStack scripture = getScripture(player);
        if (scripture.isEmpty()) return;
        if (scripture.getOrCreateTag().getBoolean("Curse_1")) return;
        event.setAmount(event.getAmount() * 2);
    }
}