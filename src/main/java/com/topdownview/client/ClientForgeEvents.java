package com.topdownview.client;

import com.topdownview.TopDownViewMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * クライアントサイドの状態管理
 * トップダウンビューの有効/無効状態のみを管理
 * カメラ距離等の状態はCameraStateを参照
 */
@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT)
public final class ClientForgeEvents {

    private static final AtomicBoolean IS_TOP_DOWN_VIEW = new AtomicBoolean(false);

    private ClientForgeEvents() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static boolean isTopDownView() {
        return IS_TOP_DOWN_VIEW.get();
    }

    public static void setTopDownView(boolean enabled) {
        IS_TOP_DOWN_VIEW.set(enabled);
    }

    public static void reset() {
        setTopDownView(false);
    }
}
