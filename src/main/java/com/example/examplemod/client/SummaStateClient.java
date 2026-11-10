package com.example.examplemod.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 《神学大全》三枚印记的客户端镜像。
 *
 * <p>只做一件事：让物品介绍里「［子］［父］［灵］」三个字按是否生效换颜色。
 * 计时与效果全部在服务端，这里不参与任何结算。
 */
@OnlyIn(Dist.CLIENT)
public final class SummaStateClient {
    public static final int SON = 1;
    public static final int FATHER = 2;
    public static final int SPIRIT = 4;

    private static int bits;

    private SummaStateClient() {
    }

    public static void accept(int value) {
        bits = value & (SON | FATHER | SPIRIT);
    }

    public static boolean active(int mask) {
        return (bits & mask) != 0;
    }
}
