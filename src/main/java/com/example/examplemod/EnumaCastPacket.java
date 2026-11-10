package com.example.examplemod;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * 客户端长按 5 秒后发来的一次 Enuma Elish 释放请求。
 *
 * <p>没有载荷：长按计时在 {@code client/GilgameshClient} 里做，服务端只校验
 * 「有没有戴《吉尔伽美什史诗》、手里是不是剑、冷却好没好」这三件它能自己验证的事
 * （见 {@link GilgameshHandler#cast(ServerPlayer)}）。
 */
public class EnumaCastPacket {

    public static void encode(EnumaCastPacket msg, FriendlyByteBuf buffer) {
    }

    public static EnumaCastPacket decode(FriendlyByteBuf buffer) {
        return new EnumaCastPacket();
    }

    public static void handle(EnumaCastPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            GilgameshHandler.cast(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
