package com.topdownview.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.topdownview.Config;
import com.topdownview.culling.CullingManager;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * 隠れた体の形（X線シルエット）レンダラー。
 * <p>
 * シルエット表示（設定 {@code playerSilhouetteEnabled}）が有効なとき動作する。
 * カメラからプレイヤーへの視線上に遮蔽物が存在する場合のみ、
 * 障害物に隠れた部位を専用バッファで描画する。
 * 遮蔽されていない平地では描画処理をスキップする。
 */
public final class HiddenBodyRenderer {

    private static final BufferBuilder SILHOUETTE_BUILDER = new BufferBuilder(256);
    private static final MultiBufferSource.BufferSource SILHOUETTE_BUFFER = MultiBufferSource.immediate(SILHOUETTE_BUILDER);

    private HiddenBodyRenderer() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            return;
        }
        if (!ModState.STATUS.isEnabled() || !Config.isPlayerSilhouetteEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameRenderer == null) {
            return;
        }
        if (mc.options.getCameraType().isFirstPerson()) {
            return;
        }

        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        float partialTick = mc.getFrameTime();

        // 遮蔽物がない（完全に見えている）場合はシルエット描画をスキップ
        if (!isPlayerOccluded(mc, mc.player, camPos, partialTick)) {
            return;
        }

        HiddenBodyState.setActive(true);
        try {
            renderSilhouette(mc, mc.player, event.getPoseStack(), camPos, partialTick);
        } finally {
            HiddenBodyState.setActive(false);
        }
    }

    private static boolean isPlayerOccluded(Minecraft mc, AbstractClientPlayer player, Vec3 camPos, float partialTick) {
        if (mc.level == null) {
            return false;
        }

        double px = Mth.lerp(partialTick, player.xOld, player.getX());
        double py = Mth.lerp(partialTick, player.yOld, player.getY());
        double pz = Mth.lerp(partialTick, player.zOld, player.getZ());

        Vec3 head = new Vec3(px, py + player.getEyeHeight(), pz);
        Vec3 chest = new Vec3(px, py + player.getBbHeight() * 0.5, pz);
        Vec3 feet = new Vec3(px, py + 0.1, pz);

        return isPointOccluded(mc, camPos, head)
                || isPointOccluded(mc, camPos, chest)
                || isPointOccluded(mc, camPos, feet);
    }

    private static boolean isPointOccluded(Minecraft mc, Vec3 from, Vec3 to) {
        ClipContext context = new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player);
        BlockHitResult hit = mc.level.clip(context);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }

        // カリング（非表示化）されているブロックは視界を遮らないため遮蔽物とみなさない
        BlockPos hitPos = hit.getBlockPos();
        return !CullingManager.isBlockCulled(hitPos, mc.level);
    }

    private static void renderSilhouette(Minecraft mc, AbstractClientPlayer player, PoseStack poseStack, Vec3 camPos, float partialTick) {
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();

        double rx = Mth.lerp(partialTick, player.xOld, player.getX()) - camPos.x;
        double ry = Mth.lerp(partialTick, player.yOld, player.getY()) - camPos.y;
        double rz = Mth.lerp(partialTick, player.zOld, player.getZ()) - camPos.z;
        float yaw = Mth.lerp(partialTick, player.yRotO, player.getYRot());

        int packedLight = dispatcher.getPackedLightCoords(player, partialTick);

        dispatcher.setRenderShadow(false);
        try {
            poseStack.pushPose();
            poseStack.translate(rx, ry, rz);
            dispatcher.render(player, 0.0, 0.0, 0.0, yaw, partialTick, poseStack, SILHOUETTE_BUFFER, packedLight);
            poseStack.popPose();

            SILHOUETTE_BUFFER.endBatch();
        } finally {
            dispatcher.setRenderShadow(true);
        }
    }

    /**
     * シルエット描画パス中フラグ（描画スレッド専用）。mixinがRenderTypeを差し替える判断に使う。
     */
    public static final class HiddenBodyState {
        private static boolean active = false;

        private HiddenBodyState() {
        }

        public static boolean isActive() {
            return active;
        }

        public static void setActive(boolean value) {
            active = value;
        }
    }
}
