package com.topdownview.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.topdownview.Config;
import com.topdownview.TopDownViewMod;
import com.topdownview.spatial.DollhouseMask;
import com.topdownview.state.DollhouseState;
import com.topdownview.state.ModState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * ドールハウス表示の描画パス。
 *
 * <p>レベル描画後（{@code AFTER_LEVEL}）に、メインカラーバッファの深度から各ピクセルの
 * カメラ相対ワールド座標を復元し、{@link DollhouseMask} の外側を {@code exteriorBrightness} まで
 * 暗くするポストプロセスを適用する。深度・マスクはポストパスへ補助テクスチャとして渡す。
 */
public final class DollhouseRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation CHAIN =
            new ResourceLocation(TopDownViewMod.MODID, "shaders/post/dollhouse_chain.json");

    private static PostChain chain;
    private static PostPass dollhousePass;
    private static int chainWidth = -1;
    private static int chainHeight = -1;

    private static int maskTextureId = 0;
    private static long uploadedVersion = -1L;
    private static ByteBuffer maskBuffer;

    private static final Matrix4f invProjView = new Matrix4f();

    private DollhouseRenderer() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        if (!ModState.STATUS.isEnabled() || !Config.isDollhouseEnabled()) {
            closeChain();
            return;
        }

        DollhouseState state = ModState.DOLLHOUSE;
        if (!state.isActive() || state.getMask() == null) {
            closeChain();
            return;
        }

        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (!ensureChain(main)) {
            return;
        }
        uploadMask(state);

        // AFTER_LEVEL は GameRenderer から発火され、イベントの poseStack は射影行列側のポーズスタック
        // （view 行列ではない）。view は GameRenderer.renderLevel と同じくカメラ回転から組み立てる。
        Camera camera = event.getCamera();
        Matrix4f view = new Matrix4f()
                .rotate(Axis.XP.rotationDegrees(camera.getXRot()))
                .rotate(Axis.YP.rotationDegrees(camera.getYRot() + 180.0F));
        invProjView.set(event.getProjectionMatrix()).mul(view).invert();

        Vec3 cam = event.getCamera().getPosition();
        EffectInstance effect = dollhousePass.getEffect();
        Uniform uniform = effect.getUniform("InvProjView");
        if (uniform != null) {
            uniform.set(invProjView);
        }
        uniform = effect.getUniform("MaskOrigin");
        if (uniform != null) {
            uniform.set((float) (state.getAnchorX() - cam.x),
                    (float) (state.getAnchorY() - cam.y),
                    (float) (state.getAnchorZ() - cam.z));
        }
        uniform = effect.getUniform("Exterior");
        if (uniform != null) {
            uniform.set((float) Config.getDollhouseExteriorBrightness());
        }
        uniform = effect.getUniform("DollhouseActive");
        if (uniform != null) {
            uniform.set(state.getActive());
        }

        chain.process(event.getPartialTick());
        main.bindWrite(true);
    }

    private static boolean ensureChain(RenderTarget main) {
        if (chain != null && main.width == chainWidth && main.height == chainHeight) {
            return true;
        }
        closeChain();
        try {
            Minecraft mc = Minecraft.getInstance();
            PostChain built = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, CHAIN);
            RenderTarget swap = built.getTempTarget("swap");
            PostPass pass = built.addPass("topdown_dollhouse", main, swap);
            pass.addAuxAsset("DepthSampler", main::getDepthTextureId, main.width, main.height);
            pass.addAuxAsset("MaskSampler", DollhouseRenderer::getMaskTextureId, DollhouseMask.WIDTH, DollhouseMask.HEIGHT);
            built.addPass("blit", swap, main);
            built.resize(main.width, main.height);
            chain = built;
            dollhousePass = pass;
            chainWidth = main.width;
            chainHeight = main.height;
            return true;
        } catch (IOException e) {
            LOGGER.error("[TopDownView] Failed to load dollhouse post chain", e);
            chain = null;
            dollhousePass = null;
            return false;
        }
    }

    private static void closeChain() {
        if (chain != null) {
            chain.close();
            chain = null;
            dollhousePass = null;
        }
        chainWidth = -1;
        chainHeight = -1;
    }

    private static void uploadMask(DollhouseState state) {
        byte[] data = state.getMask();
        if (data == null) {
            return;
        }
        if (maskTextureId == 0) {
            maskTextureId = GL11.glGenTextures();
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(maskTextureId);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, DollhouseMask.WIDTH, DollhouseMask.HEIGHT, 0,
                    GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            uploadedVersion = -1L;
        }

        long version = state.getMaskVersion();
        if (version == uploadedVersion) {
            return;
        }
        if (maskBuffer == null) {
            maskBuffer = ByteBuffer.allocateDirect(DollhouseMask.WIDTH * DollhouseMask.HEIGHT);
        }
        maskBuffer.clear();
        maskBuffer.put(data);
        maskBuffer.flip();
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(maskTextureId);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, DollhouseMask.WIDTH, DollhouseMask.HEIGHT,
                GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, maskBuffer);
        uploadedVersion = version;
    }

    private static int getMaskTextureId() {
        return maskTextureId;
    }
}
