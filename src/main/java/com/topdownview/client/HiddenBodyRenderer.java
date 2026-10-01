package com.topdownview.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.topdownview.Config;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * 隠れた体の形（X線シルエット）レンダラー。
 * <p>
 * シルエット表示（設定 {@code playerSilhouetteEnabled}）が有効なとき、カリングON/OFFに関係なく動作する。
 * AFTER_CUTOUT_BLOCKS＝
 * ワールド（壁・地面）の描画が済み、エンティティがまだ描画されていない時点で、
 * プレイヤーを深度GREATERで描画する。深度バッファにはワールドだけが入っているため、
 * 「ワールドのものより奥にある断片」＝障害物に隠れた部位だけが真っ白に描画され、
 * 壁越しに体の形が浮かぶ。その後にバニラがプレイヤーを通常描画するので、
 * 見えている部分は本来の見た目を保つ。プレイヤー自身による自己遮蔽の
 * 誤判定（頭の手前に体が浮く等）も構造的に発生しない。
 * <p>
 * シェーダーパック（Oculus/Iris）下では深度マスクが保証できないため見た目が崩れ得る。
 * その場合はユーザーが設定をオフにすることを想定している。
 */
public final class HiddenBodyRenderer {

    private HiddenBodyRenderer() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            return;
        }
        // 設定トグル（カリングON/OFFから独立）
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

        HiddenBodyState.setActive(true);
        try {
            renderSilhouette(mc, event.getPoseStack());
        } finally {
            HiddenBodyState.setActive(false);
        }
    }

    private static void renderSilhouette(Minecraft mc, PoseStack poseStack) {
        AbstractClientPlayer player = mc.player;
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();

        float partialTick = mc.getFrameTime();
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();

        double rx = Mth.lerp(partialTick, player.xOld, player.getX()) - camPos.x;
        double ry = Mth.lerp(partialTick, player.yOld, player.getY()) - camPos.y;
        double rz = Mth.lerp(partialTick, player.zOld, player.getZ()) - camPos.z;
        float yaw = Mth.lerp(partialTick, player.yRotO, player.getYRot());

        int packedLight = dispatcher.getPackedLightCoords(player, partialTick);

        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        // 深度状態（GREATER・深度書き込み無し）は専用RenderTypeが管理する。
        // シルエットには落下影が不要のためdispatcher側で一時的に無効化する。
        // 2層目スキンはPlayerRendererMixinがsetModelProperties直後に非表示化する
        dispatcher.setRenderShadow(false);
        try {
            poseStack.pushPose();
            poseStack.translate(rx, ry, rz);
            dispatcher.render(player, 0.0, 0.0, 0.0, yaw, partialTick, poseStack, buffer, packedLight);
            poseStack.popPose();

            buffer.endBatch(HiddenBodyRenderType.hiddenBody());
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
