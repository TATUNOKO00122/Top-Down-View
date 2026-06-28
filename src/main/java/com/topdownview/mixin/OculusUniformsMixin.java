package com.topdownview.mixin;

import com.topdownview.client.MouseRaycast;
import com.topdownview.state.ModState;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shaderpack.IdMap;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Oculus連携: プレイヤー情報をshader uniformとして注入する。
 * Oculus未導入環境ではこのMixinは適用されない（required:false）。
 *
 * shader側で以下を宣言すると毎フレーム値が設定される（未宣言なら安全に無視）:
 * - uniform vec3 topdownPlayerOffset;   : プレイヤー位置 - カメラ位置
 * - uniform vec3 topdownPlayerForward;  : プレイヤー→マウス命中点の水平方向ベクトル（正規化済み、Y=0）
 *                                          視界コーン（fog of war）の向きに使用
 *                                          CameraController.updatePlayerRotationToMouse と同じ計算
 */
@Mixin(value = CommonUniforms.class, remap = false)
public class OculusUniformsMixin {

    private static final Vector3f FORWARD_FALLBACK = new Vector3f(0.0f, 0.0f, -1.0f);

    @Inject(method = "addNonDynamicUniforms", at = @At("TAIL"), remap = false)
    private static void topdownview$injectPlayerOffsetUniform(
            UniformHolder uniforms,
            IdMap idMap,
            PackDirectives directives,
            FrameUpdateNotifier updateNotifier,
            CallbackInfo ci) {
        uniforms.uniform3f(UniformUpdateFrequency.PER_FRAME, "topdownPlayerOffset", () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return new Vector3f();
            }
            Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
            Vec3 playerPos = mc.player.position();
            return new Vector3f(
                    (float) (playerPos.x - camPos.x),
                    (float) (playerPos.y - camPos.y),
                    (float) (playerPos.z - camPos.z)
            );
        });

        uniforms.uniform3f(UniformUpdateFrequency.PER_FRAME, "topdownPlayerForward", () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) {
                return new Vector3f(FORWARD_FALLBACK);
            }
            if (ModState.CAMERA.isFreeCameraMode() || ModState.CAMERA.isDragging()) {
                return playerLookHorizontal(mc);
            }

            float partialTick = mc.getFrameTime();

            // raycast ヒット位置を使わず、マウスレイとプレイヤー目の高さの水平面の交点を使う。
            // 壁・天井にレイが当たってもマウスの水平方向が安定し、コーンが急にずれない。
            Vec3 playerEyePos = mc.player.getEyePosition(partialTick);
            Vec3 target = MouseRaycast.INSTANCE.getMouseHorizontalIntersection(mc, partialTick, playerEyePos.y);
            if (target != null) {
                double dx = target.x - playerEyePos.x;
                double dz = target.z - playerEyePos.z;
                double len = Math.sqrt(dx * dx + dz * dz);
                if (len > 1.0e-6) {
                    return new Vector3f((float) (dx / len), 0.0f, (float) (dz / len));
                }
            }

            return playerLookHorizontal(mc);
        });
    }

    /**
     * プレイヤーの水平視線方向を返す（フォールバック用）。
     */
    private static Vector3f playerLookHorizontal(Minecraft mc) {
        if (mc.player == null) {
            return new Vector3f(FORWARD_FALLBACK);
        }
        Vec3 look = mc.player.getLookAngle();
        double len = Math.sqrt(look.x * look.x + look.z * look.z);
        if (len < 1.0e-6) {
            return new Vector3f(FORWARD_FALLBACK);
        }
        return new Vector3f(
                (float) (look.x / len),
                0.0f,
                (float) (look.z / len)
        );
    }
}
