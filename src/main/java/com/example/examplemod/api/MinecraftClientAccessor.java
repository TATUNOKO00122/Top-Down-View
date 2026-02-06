package com.example.examplemod.api;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * MinecraftClientアクセッサー
 * Dungeons Perspective準拠
 */
public interface MinecraftClientAccessor {
    
    /**
     * マウスクールダウン取得
     */
    int getMouseCooldown();
    
    /**
     * 再構築が必要か
     */
    boolean shouldRebuild();
    
    /**
     * 位置を設定
     */
    void setLocation(net.minecraft.world.phys.HitResult vec3d);
    
    /**
     * 位置を取得
     */
    net.minecraft.world.phys.HitResult getLocation();
    
    /**
     * 元の位置を取得
     */
    Vec3 getOriginalLocation();
    
    /**
     * 元の位置を設定
     */
    void setOriginalLocation(Vec3 vec3d);
    
    /**
     * マウスクールダウンを設定
     */
    int setMouseCooldown(int cooldown);
}
