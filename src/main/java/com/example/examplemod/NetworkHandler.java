package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new net.minecraft.resources.ResourceLocation(ExampleMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, RecordEnchantPacket.class,
                RecordEnchantPacket::encode, RecordEnchantPacket::decode, RecordEnchantPacket::handle);
        CHANNEL.registerMessage(id++, BlankPageActionPacket.class,
                BlankPageActionPacket::encode, BlankPageActionPacket::decode, BlankPageActionPacket::handle);
        CHANNEL.registerMessage(id++, BookOfMeActionPacket.class,
                BookOfMeActionPacket::encode, BookOfMeActionPacket::decode, BookOfMeActionPacket::handle);
        CHANNEL.registerMessage(id++, BookOfMeEnchantPacket.class,
                BookOfMeEnchantPacket::encode, BookOfMeEnchantPacket::decode, BookOfMeEnchantPacket::handle);
        CHANNEL.registerMessage(id++, BookOfMeTimeStopPacket.class,
                BookOfMeTimeStopPacket::encode,
                BookOfMeTimeStopPacket::decode,
                BookOfMeTimeStopPacket::handle);
        CHANNEL.registerMessage(id++, EscapePacket.class,
                EscapePacket::encode,
                EscapePacket::decode,
                EscapePacket::handle);
        CHANNEL.registerMessage(id++, CovenantJudgementPacket.class,
                CovenantJudgementPacket::encode,
                CovenantJudgementPacket::decode,
                CovenantJudgementPacket::handle);
        CHANNEL.registerMessage(id++, AltStatePacket.class,
                AltStatePacket::encode,
                AltStatePacket::decode,
                AltStatePacket::handle);
        CHANNEL.registerMessage(id++, CovenantFlightPacket.class,
                CovenantFlightPacket::encode,
                CovenantFlightPacket::decode,
                CovenantFlightPacket::handle);
        CHANNEL.registerMessage(id++, MobEffectRequestPacket.class,
                MobEffectRequestPacket::encode,
                MobEffectRequestPacket::decode,
                MobEffectRequestPacket::handle);
        CHANNEL.registerMessage(id++, MobEffectSyncPacket.class,
                MobEffectSyncPacket::encode,
                MobEffectSyncPacket::decode,
                MobEffectSyncPacket::handle);
        CHANNEL.registerMessage(id++, CovenantStatsPacket.class,
                CovenantStatsPacket::encode,
                CovenantStatsPacket::decode,
                CovenantStatsPacket::handle);
        CHANNEL.registerMessage(id++, DpsPacket.class,
                DpsPacket::encode,
                DpsPacket::decode,
                DpsPacket::handle);
        CHANNEL.registerMessage(id++, BookTableMaterialsPacket.class,
                BookTableMaterialsPacket::encode,
                BookTableMaterialsPacket::decode,
                BookTableMaterialsPacket::handle);
        CHANNEL.registerMessage(id++, ComedyTeleportPacket.class,
                ComedyTeleportPacket::encode,
                ComedyTeleportPacket::decode,
                ComedyTeleportPacket::handle);
        CHANNEL.registerMessage(id++, BookTableCyclePacket.class,
                BookTableCyclePacket::encode,
                BookTableCyclePacket::decode,
                BookTableCyclePacket::handle);
        CHANNEL.registerMessage(id++, FaustPactPacket.class,
                FaustPactPacket::encode,
                FaustPactPacket::decode,
                FaustPactPacket::handle);
        CHANNEL.registerMessage(id++, OdysseyReturnPacket.class,
                OdysseyReturnPacket::encode,
                OdysseyReturnPacket::decode,
                OdysseyReturnPacket::handle);
        CHANNEL.registerMessage(id++, DreamEnterPacket.class,
                DreamEnterPacket::encode,
                DreamEnterPacket::decode,
                DreamEnterPacket::handle);
        CHANNEL.registerMessage(id++, DreamStatePacket.class,
                DreamStatePacket::encode,
                DreamStatePacket::decode,
                DreamStatePacket::handle);
        CHANNEL.registerMessage(id++, DamageNumberPacket.class,
                DamageNumberPacket::encode,
                DamageNumberPacket::decode,
                DamageNumberPacket::handle);
        CHANNEL.registerMessage(id++, CrimeLayerPacket.class,
                CrimeLayerPacket::encode,
                CrimeLayerPacket::decode,
                CrimeLayerPacket::handle);
        CHANNEL.registerMessage(id++, MoralePacket.class,
                MoralePacket::encode,
                MoralePacket::decode,
                MoralePacket::handle);
        CHANNEL.registerMessage(id++, EnumaCastPacket.class,
                EnumaCastPacket::encode,
                EnumaCastPacket::decode,
                EnumaCastPacket::handle);
        CHANNEL.registerMessage(id++, CapitalPacket.class,
                CapitalPacket::encode,
                CapitalPacket::decode,
                CapitalPacket::handle);
        CHANNEL.registerMessage(id++, SummaStatePacket.class,
                SummaStatePacket::encode,
                SummaStatePacket::decode,
                SummaStatePacket::handle);
    }

    // ==================== 《神学大全》三枚印记（只用于给物品介绍里的三个字上色） ====================

    /**
     * 服务端 → 玩家：三枚印记当前是否生效（bit0 = 子，bit1 = 父，bit2 = 灵）。
     */
    public static class SummaStatePacket {
        private final int bits;

        public SummaStatePacket(int bits) {
            this.bits = bits;
        }

        public static void encode(SummaStatePacket msg, FriendlyByteBuf buffer) {
            buffer.writeByte(msg.bits);
        }

        public static SummaStatePacket decode(FriendlyByteBuf buffer) {
            return new SummaStatePacket(buffer.readByte());
        }

        public static void handle(SummaStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.examplemod.client.SummaStateClient.accept(msg.bits)));
            ctx.get().setPacketHandled(true);
        }
    }

    // ==================== 《资本论》资本（右上角 HUD，在士气条下面） ====================

    /**
     * 服务端 → 玩家：当前资本。传 -1 表示「没戴书，别显示」。
     */
    public static class CapitalPacket {
        private final long capital;

        public CapitalPacket(long capital) {
            this.capital = capital;
        }

        public static void encode(CapitalPacket msg, FriendlyByteBuf buffer) {
            buffer.writeLong(msg.capital);
        }

        public static CapitalPacket decode(FriendlyByteBuf buffer) {
            return new CapitalPacket(buffer.readLong());
        }

        public static void handle(CapitalPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.examplemod.client.CapitalHud.accept(msg.capital)));
            ctx.get().setPacketHandled(true);
        }
    }

    // ==================== 《战争论》士气（右上角 HUD） ====================

    /**
     * 服务端 → 玩家：当前士气与上限。
     *
     * <p>士气记在玩家持久数据里，客户端读不到；作者要求显示数值与上限，
     * 而且不要放在左下角（那里已经有 DPH 与罪孽层数），所以画在右上角。
     */
    public static class MoralePacket {
        private final float morale;
        private final float cap;

        public MoralePacket(float morale, float cap) {
            this.morale = morale;
            this.cap = cap;
        }

        public static void encode(MoralePacket msg, FriendlyByteBuf buffer) {
            buffer.writeFloat(msg.morale);
            buffer.writeFloat(msg.cap);
        }

        public static MoralePacket decode(FriendlyByteBuf buffer) {
            return new MoralePacket(buffer.readFloat(), buffer.readFloat());
        }

        public static void handle(MoralePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.examplemod.client.MoraleHud.accept(msg.morale, msg.cap)));
            ctx.get().setPacketHandled(true);
        }
    }

    // ==================== 《罪与罚》罪孽层数（左下角 HUD） ====================

    /**
     * 服务端 → 玩家：当前的罪孽层数。
     *
     * <p>层数记在玩家持久数据里，客户端读不到，所以每 20 刻同步一次；
     * 客户端收到后画在左下角 DPH 上面（{@code CrimeLayerHud}），0 层不显示。
     */
    public static class CrimeLayerPacket {
        private final int layers;

        public CrimeLayerPacket(int layers) {
            this.layers = layers;
        }

        public static void encode(CrimeLayerPacket msg, FriendlyByteBuf buffer) {
            buffer.writeVarInt(msg.layers);
        }

        public static CrimeLayerPacket decode(FriendlyByteBuf buffer) {
            return new CrimeLayerPacket(buffer.readVarInt());
        }

        public static void handle(CrimeLayerPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.examplemod.client.CrimeLayerHud.accept(msg.layers)));
            ctx.get().setPacketHandled(true);
        }
    }

    // ==================== 《神曲》穿越维度 ====================
    /** 客户端按 Shift+T 时发包；能不能传由服务端按佩戴与冷却决定。 */
    public static class ComedyTeleportPacket {
        public static void encode(ComedyTeleportPacket msg, FriendlyByteBuf buffer) {}
        public static ComedyTeleportPacket decode(FriendlyByteBuf buffer) { return new ComedyTeleportPacket(); }

        public static void handle(ComedyTeleportPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                ClassicBooksHandler.teleportAcrossDimensions(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * 著书台界面上的上下箭头：让方块实体在候选配方之间挪一格。
     *
     * <p>只做「挪一格」这一件事——候选列表由服务端现算，
     * 客户端算出来的列表可能因为同步延迟和服务端不一致。</p>
     */
    public static class BookTableCyclePacket {
        private final BlockPos pos;
        private final int delta;

        public BookTableCyclePacket(BlockPos pos, int delta) {
            this.pos = pos;
            this.delta = delta;
        }

        public static void encode(BookTableCyclePacket msg, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(msg.pos);
            buffer.writeVarInt(msg.delta);
        }

        public static BookTableCyclePacket decode(FriendlyByteBuf buffer) {
            return new BookTableCyclePacket(buffer.readBlockPos(), buffer.readVarInt());
        }

        public static void handle(BookTableCyclePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) {
                    return;
                }
                int delta = msg.delta == 1 ? 1 : -1;
                if (player.level().getBlockEntity(msg.pos) instanceof BookWritingTableBlockEntity table) {
                    if (player.distanceToSqr(msg.pos.getX() + 0.5D, msg.pos.getY() + 0.5D,
                            msg.pos.getZ() + 0.5D) <= 64.0D) {
                        table.cycleSelection(delta);
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ==================== 第二批名著的 Shift + 右键 ====================
    /** 《浮士德》签契约：客户端只负责报「我按了」，扣不扣上限由服务端判。 */
    public static class FaustPactPacket {
        public static void encode(FaustPactPacket msg, FriendlyByteBuf buffer) {}

        public static FaustPactPacket decode(FriendlyByteBuf buffer) { return new FaustPactPacket(); }

        public static void handle(FaustPactPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) {
                    return;
                }
                FaustHandler.pact(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 《奥德赛》回死亡点：同样只报按键，游戏规则与有没有记录都由服务端查。 */
    public static class OdysseyReturnPacket {
        public static void encode(OdysseyReturnPacket msg, FriendlyByteBuf buffer) {}

        public static OdysseyReturnPacket decode(FriendlyByteBuf buffer) { return new OdysseyReturnPacket(); }

        public static void handle(OdysseyReturnPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) {
                    return;
                }
                OdysseyHandler.returnToDeathPoint(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 《梦的解析》入梦：Shift + D。梦境里的所有规则都由服务端写。 */
    public static class DreamEnterPacket {
        public static void encode(DreamEnterPacket msg, FriendlyByteBuf buffer) {}

        public static DreamEnterPacket decode(FriendlyByteBuf buffer) { return new DreamEnterPacket(); }

        public static void handle(DreamEnterPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) {
                    return;
                }
                InterpretationOfDreamsHandler.enter(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * 服务端 → 客户端：梦境开关。
     *
     * <p>为什么要发这个包：{@code noPhysics} 是**客户端自己**判断穿墙用的，服务端把它设成 true
     * 并不会让客户端放下碰撞（玩家移动是客户端先算再上报的）。只设服务端就会出现「说起来在梦里，
     * 结果还是撞墙」——作者 2026-11-01 报的就是这个。飞行同理，本地能力也要一起改，
     * 否则要等服务端那一帧的 abilities 包才生效。
     */
    public static class DreamStatePacket {
        private final boolean active;

        public DreamStatePacket(boolean active) {
            this.active = active;
        }

        public static void encode(DreamStatePacket msg, FriendlyByteBuf buffer) {
            buffer.writeBoolean(msg.active);
        }

        public static DreamStatePacket decode(FriendlyByteBuf buffer) {
            return new DreamStatePacket(buffer.readBoolean());
        }

        public static void handle(DreamStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.examplemod.client.DreamStateClient.accept(msg.active)));
            ctx.get().setPacketHandled(true);
        }
    }

    // ==================== 命中伤害飘字（DPH 的「实际命中」那一半） ====================

    /**
     * 服务端 → 附近所有玩家：某个实体刚挨了一下，金额多少、是不是暴击。
     *
     * <p>为什么不让客户端自己算：伤害是服务端权威的，客户端既收不到别人打怪的事件，
     * 也不知道护甲/抗性算完之后是多少。这里只带「实体 id + 金额 + 暴击标记」，
     * 客户端按实体 id 把它画在头顶上（{@code client/DamageNumberRenderer}）。
     */
    public static class DamageNumberPacket {
        private final int entityId;
        private final float amount;
        private final boolean crit;
        /**
         * 额外的角标文字，例如《基督山伯爵》的「复仇」。
         *
         * <p>空串表示普通伤害飘字。复用同一个包而不是新开一个，是因为两者渲染在同一个位置、
         * 共用同一套生命周期（900 毫秒淡出）。
         */
        private final String label;

        public DamageNumberPacket(int entityId, float amount, boolean crit) {
            this(entityId, amount, crit, "");
        }

        public DamageNumberPacket(int entityId, float amount, boolean crit, String label) {
            this.entityId = entityId;
            this.amount = amount;
            this.crit = crit;
            this.label = label == null ? "" : label;
        }

        public static void encode(DamageNumberPacket msg, FriendlyByteBuf buffer) {
            buffer.writeVarInt(msg.entityId);
            buffer.writeFloat(msg.amount);
            buffer.writeBoolean(msg.crit);
            buffer.writeUtf(msg.label);
        }

        public static DamageNumberPacket decode(FriendlyByteBuf buffer) {
            return new DamageNumberPacket(buffer.readVarInt(), buffer.readFloat(), buffer.readBoolean(),
                    buffer.readUtf());
        }

        public static void handle(DamageNumberPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.examplemod.client.DamageNumberRenderer.accept(
                            msg.entityId, msg.amount, msg.crit, msg.label)));
            ctx.get().setPacketHandled(true);
        }
    }

    // ==================== 全局 DPS ====================
    /** 服务端每秒推送一次当前 DPS、命中数与暴击数。与饰品无关。 */
    public static class DpsPacket {
        private final int dps;
        private final int hits;
        private final int crits;

        public DpsPacket(int dps, int hits, int crits) {
            this.dps = dps;
            this.hits = hits;
            this.crits = crits;
        }

        public int dps() {
            return dps;
        }

        public int hits() {
            return hits;
        }

        public int crits() {
            return crits;
        }

        public static void encode(DpsPacket msg, FriendlyByteBuf buffer) {
            buffer.writeVarInt(msg.dps);
            buffer.writeVarInt(msg.hits);
            buffer.writeVarInt(msg.crits);
        }

        public static DpsPacket decode(FriendlyByteBuf buffer) {
            return new DpsPacket(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
        }

        public static void handle(DpsPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DpsHud.accept(msg.dps(), msg.hits(), msg.crits()));
            ctx.get().setPacketHandled(true);
        }
    }

    // ==================== 圣约 HUD 数据（特殊生命 + DPS） ====================
    /**
     * 服务端每 10 刻把面板要显示的两个数字推给客户端。
     *
     * <p>原来是把数值写进饰品 NBT，指望 Curios 自动同步 —— 实测<b>不会</b>：
     * 直接改 {@code stack.getOrCreateTag()} 不经过 {@code setStackInSlot}，
     * Curios 不认为它变了，于是客户端一直是旧值（或者干脆没有）。
     * 改成显式发包，不依赖任何同步机制。
     */
    public static class CovenantStatsPacket {
        private final int specialHp;
        private final int dps;

        public CovenantStatsPacket(int specialHp, int dps) {
            this.specialHp = specialHp;
            this.dps = dps;
        }

        public int specialHp() {
            return specialHp;
        }

        public int dps() {
            return dps;
        }

        public static void encode(CovenantStatsPacket msg, FriendlyByteBuf buffer) {
            buffer.writeVarInt(msg.specialHp);
            buffer.writeVarInt(msg.dps);
        }

        public static CovenantStatsPacket decode(FriendlyByteBuf buffer) {
            return new CovenantStatsPacket(buffer.readVarInt(), buffer.readVarInt());
        }

        public static void handle(CovenantStatsPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> CovenantHud.accept(msg));
            ctx.get().setPacketHandled(true);
        }
    }

    // ==================== 生物状态：服务端直推 ====================
    /**
     * 客户端请求某个实体的状态列表。
     *
     * <p>不再依赖原版的效果同步：走自己的包，服务端读到什么就发什么，
     * 客户端拿到的必然与服务端一致。
     */
    public static class MobEffectRequestPacket {
        private final int entityId;

        public MobEffectRequestPacket(int entityId) {
            this.entityId = entityId;
        }

        public static void encode(MobEffectRequestPacket msg, FriendlyByteBuf buffer) {
            buffer.writeVarInt(msg.entityId);
        }

        public static MobEffectRequestPacket decode(FriendlyByteBuf buffer) {
            return new MobEffectRequestPacket(buffer.readVarInt());
        }

        public static void handle(MobEffectRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                var entity = player.level().getEntity(msg.entityId);
                if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) return;
                CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                        MobEffectSyncPacket.of(living));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 服务端把实体身上的效果列表发回客户端。 */
    public static class MobEffectSyncPacket {
        private final int entityId;
        private final java.util.List<String> ids;
        private final int[] amplifiers;
        private final int[] durations;
        private final boolean[] beneficial;

        public MobEffectSyncPacket(int entityId, java.util.List<String> ids,
                                   int[] amplifiers, int[] durations, boolean[] beneficial) {
            this.entityId = entityId;
            this.ids = ids;
            this.amplifiers = amplifiers;
            this.durations = durations;
            this.beneficial = beneficial;
        }

        public static MobEffectSyncPacket of(net.minecraft.world.entity.LivingEntity entity) {
            var effects = entity.getActiveEffects();
            java.util.List<String> ids = new java.util.ArrayList<>(effects.size());
            int[] amps = new int[effects.size()];
            int[] durs = new int[effects.size()];
            boolean[] good = new boolean[effects.size()];
            int i = 0;
            for (var instance : effects) {
                var key = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                        .getKey(instance.getEffect());
                ids.add(key == null ? "minecraft:luck" : key.toString());
                amps[i] = instance.getAmplifier();
                durs[i] = instance.getDuration();
                good[i] = instance.getEffect().isBeneficial();
                i++;
            }
            return new MobEffectSyncPacket(entity.getId(), ids, amps, durs, good);
        }

        public int entityId() {
            return entityId;
        }

        public java.util.List<String> ids() {
            return ids;
        }

        public int[] amplifiers() {
            return amplifiers;
        }

        public int[] durations() {
            return durations;
        }

        public boolean[] beneficial() {
            return beneficial;
        }

        public static void encode(MobEffectSyncPacket msg, FriendlyByteBuf buffer) {
            buffer.writeVarInt(msg.entityId);
            buffer.writeVarInt(msg.ids.size());
            for (int i = 0; i < msg.ids.size(); i++) {
                buffer.writeUtf(msg.ids.get(i));
                buffer.writeVarInt(msg.amplifiers[i]);
                buffer.writeVarInt(msg.durations[i]);
                buffer.writeBoolean(msg.beneficial[i]);
            }
        }

        public static MobEffectSyncPacket decode(FriendlyByteBuf buffer) {
            int entityId = buffer.readVarInt();
            int size = buffer.readVarInt();
            java.util.List<String> ids = new java.util.ArrayList<>(size);
            int[] amps = new int[size];
            int[] durs = new int[size];
            boolean[] good = new boolean[size];
            for (int i = 0; i < size; i++) {
                ids.add(buffer.readUtf());
                amps[i] = buffer.readVarInt();
                durs[i] = buffer.readVarInt();
                good[i] = buffer.readBoolean();
            }
            return new MobEffectSyncPacket(entityId, ids, amps, durs, good);
        }

        public static void handle(MobEffectSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> MobEffectHud.accept(msg));
            ctx.get().setPacketHandled(true);
        }
    }

    // ==================== 飞行开关 ====================
    public static class CovenantFlightPacket {
        public static void encode(CovenantFlightPacket msg, FriendlyByteBuf buffer) {}
        public static CovenantFlightPacket decode(FriendlyByteBuf buffer) { return new CovenantFlightPacket(); }

        public static void handle(CovenantFlightPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                boolean hasCovenant = CuriosApi.getCuriosInventory(player).resolve()
                        .flatMap(inv -> inv.findFirstCurio(ExampleMod.COVENANT_CODEX.get()))
                        .isPresent();
                if (!hasCovenant) return;

                // 切换「整体飞行开关」并立刻生效：
                // 关掉之后本模组的 tick 会每刻把 mayfly/flying 按死，
                // 不会再出现「关了还能双击空格飞起来」的情况。
                boolean denied = !player.getPersistentData()
                        .getBoolean(CovenantCodexHandler.TAG_FLY_DENIED);
                player.getPersistentData().putBoolean(CovenantCodexHandler.TAG_FLY_DENIED, denied);

                if (denied) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                } else {
                    player.getAbilities().mayfly = true;
                }
                player.onUpdateAbilities();

                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        denied ? "§7飞行：已彻底关闭" : "§f飞行：开启"), true);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ==================== Alt 键状态同步 ====================
    // Player 没有公开接口能读到 Alt 是否按下（Shift 有 isShiftKeyDown，Alt 没有），
    // 所以客户端在状态变化时同步一次，服务端据此区分「赐福 / 降灾」。
    public static class AltStatePacket {
        private final boolean down;

        public AltStatePacket(boolean down) {
            this.down = down;
        }

        public static void encode(AltStatePacket msg, FriendlyByteBuf buffer) {
            buffer.writeBoolean(msg.down);
        }

        public static AltStatePacket decode(FriendlyByteBuf buffer) {
            return new AltStatePacket(buffer.readBoolean());
        }

        public static void handle(AltStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    CovenantCodexHandler.setAltDown(player, msg.down);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ==================== 七碗倾倒 ====================
    public static class CovenantJudgementPacket {
        public static void encode(CovenantJudgementPacket msg, FriendlyByteBuf buffer) {}
        public static CovenantJudgementPacket decode(FriendlyByteBuf buffer) { return new CovenantJudgementPacket(); }

        public static void handle(CovenantJudgementPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                boolean hasCovenant = CuriosApi.getCuriosInventory(player).resolve()
                        .flatMap(inv -> inv.findFirstCurio(ExampleMod.COVENANT_CODEX.get()))
                        .isPresent();
                if (!hasCovenant) return;
                JudgementController.activate(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ==================== 主动脱困 ====================
    /** 按下脱困键时发送；实际判定与冷却都在 {@link EscapeController} 里。 */
    public static class EscapePacket {
        public static void encode(EscapePacket msg, FriendlyByteBuf buffer) {}
        public static EscapePacket decode(FriendlyByteBuf buffer) { return new EscapePacket(); }

        public static void handle(EscapePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    EscapeController.forceEscape(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ==================== 1. 记录附魔的数据包 ====================
    public static class RecordEnchantPacket {
        private final List<String> enchantmentIds;

        public RecordEnchantPacket(List<String> enchantmentIds) {
            this.enchantmentIds = enchantmentIds;
        }

        public static void encode(RecordEnchantPacket msg, FriendlyByteBuf buffer) {
            buffer.writeCollection(msg.enchantmentIds, FriendlyByteBuf::writeUtf);
        }

        public static RecordEnchantPacket decode(FriendlyByteBuf buffer) {
            return new RecordEnchantPacket(buffer.readCollection(ArrayList::new, FriendlyByteBuf::readUtf));
        }

        public static void handle(RecordEnchantPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                // ==================== [修复 P0-3 2026-10-21] ====================
                // 原实现直接把客户端发来的任意字符串当作附魔 ID 写入物品 NBT，
                // 而 BlankPageItem 的加成是按"可用附魔数量"线性计算的且没有上限。
                // 改造过的客户端只要发一万个假 ID 就能拿到 +5000 最大生命 / +5000 攻击。
                // 这里补两道：① 单包条目上限；② 必须存在于附魔注册表中的白名单校验。
                if (msg.enchantmentIds.size() > 128) return;

                CuriosApi.getCuriosInventory(player).resolve().ifPresent(handler -> {
                    handler.findFirstCurio(ExampleMod.BLANK_PAGE.get()).ifPresent(slotResult -> {
                        for (String id : msg.enchantmentIds) {
                            // [修复 P0-3] 白名单：不存在的附魔 ID 一律丢弃
                            var rl = net.minecraft.resources.ResourceLocation.tryParse(id);
                            if (rl == null || !ForgeRegistries.ENCHANTMENTS.containsKey(rl)) continue;
                            BlankPageItem.addEnchantment(slotResult.stack(), id);
                        }
                    });
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ==================== 2. 空白的书页：按键操作 ====================
    public static class BlankPageActionPacket {
        public static void encode(BlankPageActionPacket msg, FriendlyByteBuf buffer) {}
        public static BlankPageActionPacket decode(FriendlyByteBuf buffer) { return new BlankPageActionPacket(); }

        public static void handle(BlankPageActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                CuriosApi.getCuriosInventory(player).resolve().ifPresent(handler -> {
                    handler.findFirstCurio(ExampleMod.BLANK_PAGE.get()).ifPresent(slotResult -> {
                        BlankPageKeyHandler.useBlankPage(player, slotResult.stack());
                    });
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ==================== 3. 如我所书：记录/回溯 ====================
    public static class BookOfMeActionPacket {
        public static void encode(BookOfMeActionPacket msg, FriendlyByteBuf buffer) {}
        public static BookOfMeActionPacket decode(FriendlyByteBuf buffer) { return new BookOfMeActionPacket(); }

        public static void handle(BookOfMeActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                CuriosApi.getCuriosInventory(player).resolve().ifPresent(handler -> {
                    handler.findFirstCurio(ExampleMod.BOOK_OF_ME.get()).ifPresent(slotResult -> {
                        BookOfMeKeyHandler.useBookOfMeServer(player, slotResult.stack());
                    });
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ==================== 4. 如我所书·理性：Shift+L 附魔复制 ====================
    public static class BookOfMeEnchantPacket {
        public static void encode(BookOfMeEnchantPacket msg, FriendlyByteBuf buffer) {}
        public static BookOfMeEnchantPacket decode(FriendlyByteBuf buffer) { return new BookOfMeEnchantPacket(); }

        public static void handle(BookOfMeEnchantPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                // 检查是否装备《如我所书》
                boolean hasBook = CuriosApi.getCuriosInventory(player).resolve()
                        .flatMap(inv -> inv.findFirstCurio(ExampleMod.BOOK_OF_ME.get()))
                        .isPresent();
                if (!hasBook) return;

                // 检查冷却（10 秒）
                long now = System.currentTimeMillis();
                if (player.getPersistentData().getLong("BookOfMe_Rational_CD") > now) return;
                player.getPersistentData().putLong("BookOfMe_Rational_CD", now + 10000);

                // 获取主手物品
                ItemStack stack = player.getMainHandItem();
                if (stack.isEmpty()) return;

                // 遍历所有附魔，给物品附上其可获得的最高级
                boolean applied = false;
                for (var entry : ForgeRegistries.ENCHANTMENTS.getEntries()) {
                    Enchantment ench = entry.getValue();
                    if (ench.isCurse()) continue; // 跳过诅咒附魔
                    if (ench.canEnchant(stack)) {
                        int maxLevel = ench.getMaxLevel();
                        int currentLevel = stack.getEnchantmentLevel(ench);
                        if (currentLevel < maxLevel) {
                            stack.enchant(ench, maxLevel);
                            applied = true;
                        }
                    }
                }

                if (applied) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§b【理性】手中之物已被赋予终极知识。"), true);
                } else {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§7【理性】此物无法承载任何知识。"), true);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
    // 5. 如我所书·真我：Z 键时停
    public static class BookOfMeTimeStopPacket {
        public static void encode(BookOfMeTimeStopPacket msg, FriendlyByteBuf buffer) {}
        public static BookOfMeTimeStopPacket decode(FriendlyByteBuf buffer) { return new BookOfMeTimeStopPacket(); }

        public static void handle(BookOfMeTimeStopPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                // 佩戴判定：圣约优先（7 秒 / 半径 64 / 冷却 30 秒），否则《如我所书》（20 秒 / 半径 64 / 冷却 300 秒）
                boolean hasCovenant = CuriosApi.getCuriosInventory(player).resolve()
                        .flatMap(inv -> inv.findFirstCurio(ExampleMod.COVENANT_CODEX.get()))
                        .isPresent();
                boolean hasBook = CuriosApi.getCuriosInventory(player).resolve()
                        .flatMap(inv -> inv.findFirstCurio(ExampleMod.BOOK_OF_ME.get()))
                        .isPresent();
                if (!hasCovenant && !hasBook) {
                    return;
                }

                boolean covenant = hasCovenant;
                String cdKey = covenant ? "Covenant_TimeStop_CD" : "BookOfMe_TimeStop_CD";
                int durationTicks = covenant ? 140 : 400;      // 圣约 7 秒，如我所书 20 秒
                long cooldownMs = covenant ? 30_000L : 300_000L; // 圣约 30 秒，如我所书 300 秒
                double radius = 64.0D;

                long now = System.currentTimeMillis();
                long cd = player.getPersistentData().getLong(cdKey);
                if (now < cd) {
                    long remaining = (cd - now) / 1000 + 1;
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§c【时停】冷却中，剩余 " + remaining + " 秒。"), true);
                    return;
                }

                player.getPersistentData().putLong(cdKey, now + cooldownMs);
                TimeStopController.activate(player, durationTicks, radius);

                if (covenant) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§f日影停住，日晷退回第七度。"), true);
                } else {
                    player.getPersistentData().putBoolean("BookOfMe_TimeStop_Active", true);
                    player.getPersistentData().putLong("BookOfMe_TimeStop_End", now + 20000L);
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§b【时停】时间开始凝固。"), true);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
