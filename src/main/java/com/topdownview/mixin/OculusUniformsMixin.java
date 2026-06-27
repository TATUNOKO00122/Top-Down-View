package com.topdownview.mixin;

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
 * Oculus連携: プレイヤー位置オフセットをshader uniformとして注入する。
 * Oculus未導入環境ではこのMixinは適用されない（required:false）。
 *
 * shader側で `uniform vec3 topdownPlayerOffset;` を宣言すると、
 * 毎フレーム「プレイヤー位置 - カメラ位置」が設定される。
 * 宣言されていないプログラムでは何も起こらない（安全）。
 */
@Mixin(value = CommonUniforms.class, remap = false)
public class OculusUniformsMixin {

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
    }
}
