package com.topdownview.mixin;

import org.spongepowered.asm.mixin.Mixin;

/**
 * MinecraftClient Mixin
 * トップダウンビューの時間管理はInputHandlerで行うため、現在は空の実装
 * 将来的な拡張用に残置
 */
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class MinecraftClientMixin {
}
