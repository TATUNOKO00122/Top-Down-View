package com.topdownview.mixin.compat.soundphysics;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.topdownview.state.ModState;

/**
 * Sound Physics Remastered 互換 Mixin。
 *
 * SoundPhysics#evaluateEnvironment() 内で音響・遮蔽計算の基準点として使用されている
 * カメラ座標（Camera.getPosition）を、トップダウン視点有効時にプレイヤーの目線座標へ偽装する。
 * これにより、カメラが上空にある場合でもプレイヤー周辺を基準として正確に音響・残響が計算される。
 */
@Pseudo
@Mixin(targets = "com.sonicether.soundphysics.SoundPhysics")
public class SoundPhysicsRemasteredMixin {

    @Redirect(
        method = "evaluateEnvironment",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getPosition()Lnet/minecraft/world/phys/Vec3;"),
        require = 0
    )
    private static Vec3 redirectSoundPhysicsCameraPosition(Camera camera) {
        if (ModState.STATUS.isEnabled() && Minecraft.getInstance().player != null) {
            return Minecraft.getInstance().player.getEyePosition();
        }
        return camera.getPosition();
    }
}
