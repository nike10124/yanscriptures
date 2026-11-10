package com.example.examplemod.client;

import com.example.examplemod.BookWritingTableBlock;
import com.example.examplemod.BookWritingTableBlockEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/**
 * 把书槽里的书画成<b>摊开的一本书</b>。
 *
 * <p>做法和原版讲台一致：借 {@link BookModel}（附魔台用的那个）与它的贴图，
 * 摆位也照抄原版 {@code LecternRenderer} —— 抬到台面高度、按朝向转、再沿 Z 轴压 67.5°，
 * 于是书脊朝上、书页向两侧摊开。</p>
 */
public class BookWritingTableRenderer implements BlockEntityRenderer<BookWritingTableBlockEntity> {

    private final BookModel bookModel;

    public BookWritingTableRenderer(BlockEntityRendererProvider.Context context) {
        this.bookModel = new BookModel(context.bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public void render(BookWritingTableBlockEntity table, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        ItemStack book = table.getBook();
        if (book.isEmpty()) {
            return;
        }

        Direction facing = table.getBlockState().getValue(BookWritingTableBlock.FACING);

        pose.pushPose();
        // 与 LecternRenderer 完全一致的摆位
        pose.translate(0.5F, 1.0625F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.getClockWise().toYRot()));
        pose.mulPose(Axis.ZP.rotationDegrees(67.5F));
        pose.translate(0.0F, -0.125F, 0.0F);

        // 原版讲台用的翻页参数
        this.bookModel.setupAnim(0.0F, 0.1F, 0.9F, 1.2F);

        VertexConsumer consumer = EnchantTableRenderer.BOOK_LOCATION
                .buffer(buffer, RenderType::entitySolid);
        this.bookModel.render(pose, consumer, packedLight, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);

        pose.popPose();
    }
}
