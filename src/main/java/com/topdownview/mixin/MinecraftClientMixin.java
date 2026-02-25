package com.topdownview.mixin;

import com.topdownview.state.ModState;
import com.topdownview.TopDownViewMod;
import com.topdownview.api.MinecraftClientAccessor;
import com.topdownview.client.ClientForgeEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MinecraftClient Mixin
 * トップダウンビューの時間管理と状態更新
 */
@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin implements MinecraftClientAccessor {
    
    @Unique
    private Vec3 originalLocation;
    
    @Unique
    private HitResult location;
    
    @Unique
    private int mouseCooldown = 40;
    
    @Override
    public HitResult getLocation() {
        return location;
    }
    
    @Override
    public void setLocation(HitResult location) {
        this.location = location;
    }
    
    @Override
    public Vec3 getOriginalLocation() {
        return originalLocation;
    }
    
    @Override
    public void setOriginalLocation(Vec3 location) {
        this.originalLocation = location;
    }
    
    @Override
    public int getMouseCooldown() {
        return mouseCooldown;
    }
    
    @Override
    public int setMouseCooldown(int cooldown) {
        this.mouseCooldown = cooldown;
        return this.mouseCooldown;
    }
    
    @Inject(method = "tick", at = @At("HEAD"))
    public void tickHead(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();

        // トップダウンモードが有効な場合
        if (ClientForgeEvents.isTopDownView() && mc.player != null && mc.cameraEntity != null) {
            // 時間管理
            if (mc.level != null) {
                if (ModState.TIME.getStartTime() == 0) {
                    ModState.TIME.setStartTime(mc.level.getGameTime());
                }
            }
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void tickTail(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();

        // トップダウンモードが有効な場合
        if (ClientForgeEvents.isTopDownView() && mc.player != null) {
            // クールダウン減少
            mouseCooldown--;

            // endTimeの管理
            long endTime = ModState.TIME.getEndTime();
            ModState.TIME.setEndTime(endTime + 1);

            // ズームアウト時間
            float zoomOutTime = ModState.TIME.getZoomOutTime();
            if (zoomOutTime < 10) {
                ModState.TIME.setZoomOutTime(zoomOutTime + 1);
            }
        }
    }
}