package com.topdownview.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.topdownview.Config;
import com.topdownview.state.ModState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import java.util.List;

/**
 * 看板ホバー時のテキスト表示を行うレンダラークラス
 */
public final class SignHoverRenderer {

    private record SignTargetInfo(BlockPos pos, List<Component> lines) {}

    private SignHoverRenderer() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /**
     * 3Dワールド内の看板の上にテキストを描画します（ネームタグ風）。
     * RenderLevelStageEvent で呼び出されます。
     */
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // AFTER_PARTICLES ステージで描画を行います
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        // トップダウンモードが有効、かつ設定で3D表示（WORLD = 1）が選択されている場合のみ処理
        if (!ModState.STATUS.isEnabled() || Config.getSignHoverDisplayMode() != 1) {
            return;
        }

        // 表示サイズ設定が0以下の場合は描画しない
        if (Config.getSignHoverScale() <= 0.0) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        SignTargetInfo info = getSignTargetInfo(mc);
        if (info == null) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        BlockPos pos = info.pos();

        // 描画位置の計算（看板ブロックの中心から少し上）
        double x = pos.getX() + 0.5 - cameraPos.x;
        double y = pos.getY() + 1.2 - cameraPos.y;
        double z = pos.getZ() + 0.5 - cameraPos.z;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(x, y, z);

        // ビルボード処理：常にカメラの正面を向くように回転
        poseStack.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));

        // スケール調整（カメラからの距離とサイズ設定（Config.getSignHoverScale）を適用し、画面上での大きさを調整）
        double distance = cameraPos.distanceTo(new Vec3(pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5));
        float scale = (float) (0.002F * distance * Config.getSignHoverScale());
        // 極端に小さくなったり大きくなったりするのを防ぐためのクランプ制限値
        scale = Math.max(0.005F * (float) Config.getSignHoverScale(), Math.min(0.15F * (float) Config.getSignHoverScale(), scale));
        poseStack.scale(-scale, -scale, scale);

        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        Font font = mc.font;

        // 最も長い行の幅を求める
        int maxWidth = 0;
        for (Component line : info.lines()) {
            int width = font.width(line);
            if (width > maxWidth) {
                maxWidth = width;
            }
        }

        int halfWidth = maxWidth / 2;
        int lineHeight = font.lineHeight + 2;
        int totalHeight = info.lines().size() * lineHeight;
        int bgYStart = -totalHeight / 2; // 上下中央揃え

        // 背景の黒半透明矩形を描画
        VertexConsumer builder = bufferSource.getBuffer(RenderType.textBackground());
        float backgroundOpacity = 0.4F;
        float xMin = -halfWidth - 4;
        float xMax = halfWidth + 4;
        float yMin = bgYStart - 2;
        float yMax = bgYStart + totalHeight + 2;

        builder.vertex(matrix, xMin, yMin, 0.0F).color(0.0F, 0.0F, 0.0F, backgroundOpacity).uv2(15728880).endVertex();
        builder.vertex(matrix, xMin, yMax, 0.0F).color(0.0F, 0.0F, 0.0F, backgroundOpacity).uv2(15728880).endVertex();
        builder.vertex(matrix, xMax, yMax, 0.0F).color(0.0F, 0.0F, 0.0F, backgroundOpacity).uv2(15728880).endVertex();
        builder.vertex(matrix, xMax, yMin, 0.0F).color(0.0F, 0.0F, 0.0F, backgroundOpacity).uv2(15728880).endVertex();

        // テキストを描画
        float textY = bgYStart;
        for (Component line : info.lines()) {
            float textX = -font.width(line) / 2.0F;
            font.drawInBatch(line, textX, textY, 0xFFFFFFFF, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 15728880);
            textY += lineHeight;
        }

        poseStack.popPose();

        // バッチのフラッシュ
        bufferSource.endBatch(RenderType.textBackground());
        bufferSource.endBatch();
    }

    /**
     * 2DのGUI画面上にマウスカーソルに追従する形でテキストを描画します（ツールチップ）。
     * RenderGuiEvent.Post で呼び出されます。
     */
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        // トップダウンモードが有効、かつ設定で2Dツールチップ表示（TOOLTIP = 2）が選択されている場合のみ処理
        if (!ModState.STATUS.isEnabled() || Config.getSignHoverDisplayMode() != 2) {
            return;
        }

        // 表示サイズ設定が0以下の場合は描画しない
        if (Config.getSignHoverScale() <= 0.0) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        // インベントリ等を開いているときは表示しない
        if (mc.screen != null) {
            return;
        }

        SignTargetInfo info = getSignTargetInfo(mc);
        if (info == null) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font font = mc.font;

        // 現在のマウス座標（GUIのスケール換算）を取得
        double mouseX = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();

        // ツールチップに表示サイズ（スケール）設定を適用
        float scale = (float) Config.getSignHoverScale();
        if (Math.abs(scale - 1.0F) > 1e-4F) {
            PoseStack pose = guiGraphics.pose();
            pose.pushPose();
            // マウス座標を原点にしてスケーリング
            pose.translate(mouseX, mouseY, 0);
            pose.scale(scale, scale, 1.0F);
            pose.translate(-mouseX, -mouseY, 0);

            guiGraphics.renderComponentTooltip(font, info.lines(), (int) mouseX, (int) mouseY);

            pose.popPose();
        } else {
            // バニラのツールチップ描画機能を利用して表示（複数行に自動対応し、枠も描画される）
            guiGraphics.renderComponentTooltip(font, info.lines(), (int) mouseX, (int) mouseY);
        }
    }

    /**
     * 現在ターゲットしている看板の情報を取得します。
     */
    private static SignTargetInfo getSignTargetInfo(Minecraft mc) {
        if (mc.level == null) {
            return null;
        }

        net.minecraft.world.phys.HitResult hitResult = MouseRaycast.INSTANCE.getLastHitResult();
        if (hitResult == null || hitResult.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return null;
        }

        net.minecraft.world.phys.BlockHitResult blockHit = (net.minecraft.world.phys.BlockHitResult) hitResult;
        BlockPos pos = blockHit.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        
        List<Component> lines = SignTextHelper.getSignText(be);
        if (lines.isEmpty()) {
            return null;
        }

        return new SignTargetInfo(pos, lines);
    }
}
