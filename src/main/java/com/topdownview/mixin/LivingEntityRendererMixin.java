package com.topdownview.mixin;

import com.topdownview.Config;
import com.topdownview.state.ModState;
import com.topdownview.client.MouseRaycast;
import com.topdownview.client.MobVisibilityCache;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {

    private static final ThreadLocal<LivingEntity> CURRENT_ENTITY = new ThreadLocal<>();
    private static final ThreadLocal<Float> CURRENT_PARTIAL_TICKS = new ThreadLocal<>();

    @Inject(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD")
    )
    private void onRenderHead(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        CURRENT_ENTITY.set(entity);
        CURRENT_PARTIAL_TICKS.set(partialTicks);

        Minecraft mc = Minecraft.getInstance();
        float coneAlpha = 1.0f;
        if (mc.player != null && mc.player != entity) {
            coneAlpha = calculateConeAlpha(mc, entity, partialTicks);
        }
        MobVisibilityCache.setCurrentConeAlpha(coneAlpha);
    }

    @Inject(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("RETURN")
    )
    private void onRenderReturn(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        CURRENT_ENTITY.remove();
        CURRENT_PARTIAL_TICKS.remove();
        MobVisibilityCache.clear();
    }

    // MOBの標準的な不透明描画指定を、半透明を許容する描画指定（RenderType.entityTranslucent()）へ変更する
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void onGetRenderType(T entity, boolean bodyVisible, boolean translucent, boolean glowing, CallbackInfoReturnable<RenderType> cir) {
        // MODが無効、かつMobの半透明設定とコーンカリング設定がいずれも無効な場合は何もしない
        if (!ModState.STATUS.isEnabled() || (!Config.isMobTranslucencyEnabled() && !Config.isMobConeCullingEnabled())) {
            return;
        }

        // キャストを介してスーパークラス（EntityRenderer）のgetTextureLocationを呼び出す
        // これにより、extendsによるMixin의 メソッド解決競合を防ぎつつ、難読化実行時のリマップも正しく適用される
        ResourceLocation texture = ((EntityRenderer<T>) (Object) this).getTextureLocation(entity);
        cir.setReturnValue(RenderType.entityTranslucent(texture));
    }

    // 描画時のカラー情報（RGBA）において、A（アルファチャンネル）の数値を1.0未満に設定して描画を実行する
    @ModifyArg(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"),
        index = 7
    )
    private float modifyAlpha(float originalAlpha) {
        // MODが無効な場合は元のアルファ値をそのまま使用する
        if (!ModState.STATUS.isEnabled()) {
            return originalAlpha;
        }

        LivingEntity entity = CURRENT_ENTITY.get();
        if (entity == null) {
            return originalAlpha;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player == entity) {
            return originalAlpha;
        }

        float coneAlpha = MobVisibilityCache.getCurrentConeAlpha();

        // 設定された透明度（アルファ値）を適用する
        if (Config.isMobTranslucencyEnabled()) {
            return originalAlpha * coneAlpha * (float) Config.getMobTranslucencyAlpha();
        } else if (Config.isMobConeCullingEnabled()) {
            return originalAlpha * coneAlpha;
        }

        return originalAlpha;
    }

    @Inject(
        method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onShouldShowName(T entity, CallbackInfoReturnable<Boolean> cir) {
        if (!ModState.STATUS.isEnabled()) {
            return;
        }
        if (Config.isMobConeCullingEnabled()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player != entity) {
                float coneAlpha = MobVisibilityCache.getCurrentConeAlpha();
                if (coneAlpha < 0.01f) {
                    cir.setReturnValue(false); // 完全に視野外なら名前タグを表示しない
                }
            }
        }
    }

    private float calculateConeAlpha(Minecraft mc, LivingEntity entity, float partialTick) {
        if (!Config.isMobConeCullingEnabled()) {
            return 1.0f;
        }

        // プレイヤーとMOBの補間位置を取得して水平距離を算出
        double playerX = Mth.lerp(partialTick, mc.player.xo, mc.player.getX());
        double playerY = Mth.lerp(partialTick, mc.player.yo, mc.player.getY());
        double playerZ = Mth.lerp(partialTick, mc.player.zo, mc.player.getZ());

        double entityX = Mth.lerp(partialTick, entity.xo, entity.getX());
        double entityY = Mth.lerp(partialTick, entity.yo, entity.getY());
        double entityZ = Mth.lerp(partialTick, entity.zo, entity.getZ());

        double dx = entityX - playerX;
        double dz = entityZ - playerZ;
        double dist = Math.sqrt(dx * dx + dz * dz);

        double fogEnd = Config.getMobFogEnd();
        if (dist >= fogEnd) {
            return 0.0f;
        }

        // 距離による基本的な減衰 (distFade)
        float distFade = (float) Math.max(0.0, Math.min(1.0, 1.0 - dist / fogEnd));

        // プレイヤーの前方ベクトル（視線）を算出
        Vec3 forward = getPlayerForward(mc, partialTick);

        // プレイヤーからMOBへの方向ベクトル（水平）
        double len = Math.sqrt(dx * dx + dz * dz);
        double dirX = 0;
        double dirZ = 0;
        if (len > 1.0e-6) {
            dirX = dx / len;
            dirZ = dz / len;
        } else {
            return distFade;
        }

        // 視線とMOB方向とのなす角（角度）を計算
        double cosAng = forward.x * dirX + forward.z * dirZ;
        cosAng = Math.max(-1.0, Math.min(1.0, cosAng));
        double ang = Math.toDegrees(Math.acos(cosAng));

        double coneHalf = Config.getMobConeHalfAngle();
        
        // シェーダー（gbuffers_entities.fsh）に準拠した角度フェード
        double fadeStart = coneHalf - 10.0;
        double fadeEnd = coneHalf + 20.0;
        float angleFade = 1.0f - (float) smoothstep(fadeStart, fadeEnd, ang);

        // 近接補正 (mobProximity)
        double nearRadius = Config.getMobNearRadius();

        // 手持ち光源の明るさを反映して至近可視半径を動的拡張
        int handLight = Math.max(
            getLightValue(mc.player.getMainHandItem()),
            getLightValue(mc.player.getOffhandItem())
        );
        double lightFactor = (double) handLight / 15.0;
        double dynamicNearRadius = nearRadius + lightFactor * 2.5;

        double proxStart = dynamicNearRadius - 1.0;
        double proxEnd = dynamicNearRadius + 1.0;
        float mobProximity = 1.0f - (float) smoothstep(proxStart, proxEnd, dist);

        // コーンフェードと近接表示のブレンド
        float finalAngleFade = lerp(angleFade, 1.0f, mobProximity);

        return distFade * finalAngleFade;
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = Math.max(0.0, Math.min(1.0, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }

    private static float lerp(float start, float end, float t) {
        return start + t * (end - start);
    }

    private static final Vec3 FORWARD_FALLBACK = new Vec3(0.0, 0.0, -1.0);

    private Vec3 getPlayerForward(Minecraft mc, float partialTick) {
        if (mc.player == null) {
            return FORWARD_FALLBACK;
        }
        if (ModState.CAMERA.isFreeCameraMode() || ModState.CAMERA.isDragging()) {
            return getPlayerLookHorizontal(mc);
        }

        Vec3 playerEyePos = mc.player.getEyePosition(partialTick);
        Vec3 target = MouseRaycast.INSTANCE.getMouseHorizontalIntersection(mc, partialTick, playerEyePos.y);
        if (target != null) {
            double dx = target.x - playerEyePos.x;
            double dz = target.z - playerEyePos.z;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 1.0e-6) {
                return new Vec3(dx / len, 0.0, dz / len);
            }
        }

        return getPlayerLookHorizontal(mc);
    }

    private Vec3 getPlayerLookHorizontal(Minecraft mc) {
        if (mc.player == null) {
            return FORWARD_FALLBACK;
        }
        Vec3 look = mc.player.getViewVector(1.0f);
        double len = Math.sqrt(look.x * look.x + look.z * look.z);
        if (len < 1.0e-6) {
            return FORWARD_FALLBACK;
        }
        return new Vec3(look.x / len, 0.0, look.z / len);
    }

    private int getLightValue(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        net.minecraft.world.item.Item item = stack.getItem();
        if (item instanceof net.minecraft.world.item.BlockItem blockItem) {
            return blockItem.getBlock().defaultBlockState().getLightEmission();
        }
        return 0;
    }
}
