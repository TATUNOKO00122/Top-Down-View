package com.example.examplemod.mixin;

import com.example.examplemod.client.ClientForgeEvents;
import com.mojang.blaze3d.audio.Listener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * ListenerMixin
 * トップダウンビュー時にサウンドリスナーの位置をプレイヤーの頭に固定する
 * setListenerPositionに渡される位置を直接書き換えることで確実に動作
 */
@Mixin(Listener.class)
public class ListenerMixin {

    /**
     * setListenerPosition に渡される Vec3 引数を、
     * トップダウンビュー時はプレイヤーの頭の位置に置き換える
     */
    @ModifyVariable(method = "setListenerPosition", at = @At("HEAD"), argsOnly = true)
    private Vec3 modifyListenerPosition(Vec3 original) {
        if (!ClientForgeEvents.isTopDownView()) {
            return original;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null) {
            // プレイヤーの頭（アイハイト）の位置を返す
            Vec3 playerEyePos = player.getEyePosition(mc.getFrameTime());
            // デバッグログ追加
            System.out.println("[ListenerMixin] Setting OpenAL listener to Player Eye Pos: " + playerEyePos);
            return playerEyePos;
        }
        return original;
    }
}
