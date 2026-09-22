package com.topdownview.mixin;

import com.topdownview.state.ModState;
import com.topdownview.culling.TopDownCuller;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {

    private static final TopDownCuller CULLER = TopDownCuller.getInstance();

    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity> void onRender(E pBlockEntity, float pPartialTick, PoseStack pPoseStack,
            MultiBufferSource pBufferSource, CallbackInfo ci) {
        if (ModState.STATUS.isEnabled()) {
            if (CULLER.isBlockCulled(pBlockEntity.getBlockPos(), pBlockEntity.getLevel())) {
                ci.cancel();
            }
        }
    }

    /**
     * バニラはブロックエンティティの表示距離を「カメラから {@code getViewDistance()}(既定64)以内」で
     * 判定する。トップダウンビューではカメラがプレイヤーから離れるため、チェスト等が消える。
     * 距離の基準をプレイヤー目線位置に置き換える。
     */
    @Redirect(
        method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getPosition()Lnet/minecraft/world/phys/Vec3;")
    )
    private Vec3 topdownview$blockEntityDistanceFromPlayer(Camera camera) {
        if (ModState.STATUS.isEnabled()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                return mc.player.getEyePosition();
            }
        }
        return camera.getPosition();
    }
}
