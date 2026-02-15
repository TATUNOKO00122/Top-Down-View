package com.example.examplemod.client;

import com.example.examplemod.TopDownViewMod;
import com.example.examplemod.state.ModState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * クライアントサイドの状態管理
 * 状態変更時の検証とイベント発行を行う
 */
@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT)
public final class ClientForgeEvents {

    // 状態の変更を追跡するためのリスナーインターフェース
    @FunctionalInterface
    public interface StateChangeListener {
        void onStateChanged(String propertyName, Object oldValue, Object newValue);
    }

    private static final AtomicBoolean IS_TOP_DOWN_VIEW = new AtomicBoolean(false);
    private static final AtomicReference<Double> CAMERA_DISTANCE = new AtomicReference<>(15.0);

    // 定数はConfigから取得するように変更
    public static double getMinCameraDistance() {
        return com.example.examplemod.Config.minCameraDistance;
    }

    public static double getMaxCameraDistance() {
        return com.example.examplemod.Config.maxCameraDistance;
    }

    public static double getDefaultCameraDistance() {
        return com.example.examplemod.Config.cameraDistance;
    }

    // volatileでスレッド安全性を確保
    private static volatile StateChangeListener stateChangeListener = null;

    private ClientForgeEvents() {
        throw new AssertionError("ユーティリティクラスはインスタンス化できません");
    }

    // ==================== Getters ====================

    public static boolean isTopDownView() {
        return IS_TOP_DOWN_VIEW.get();
    }

    public static double getCameraDistance() {
        return CAMERA_DISTANCE.get();
    }

    // ==================== Setters with Validation ====================

    /**
     * トップダウンビュー状態を設定
     * 
     * @param enabled 有効/無効
     * @throws IllegalStateException 無効な状態遷移の場合
     */
    public static void setTopDownView(boolean enabled) {
        boolean oldValue = IS_TOP_DOWN_VIEW.getAndSet(enabled);
        if (oldValue != enabled) {
            notifyStateChange("isTopDownView", oldValue, enabled);
        }
    }

    /**
     * カメラ距離を設定
     * 
     * @param distance 距離（5.0 ~ 50.0）
     * @throws IllegalArgumentException 範囲外の値
     */
    public static void setCameraDistance(double distance) {
        if (distance < getMinCameraDistance() || distance > getMaxCameraDistance()) {
            throw new IllegalArgumentException(
                    String.format("Camera distance must be between %.1f and %.1f: %.1f",
                            getMinCameraDistance(), getMaxCameraDistance(), distance));
        }
        Double oldValue = CAMERA_DISTANCE.getAndSet(distance);
        if (!oldValue.equals(distance)) {
            notifyStateChange("cameraDistance", oldValue, distance);
        }
    }

    // ==================== State Change Listener ====================

    /**
     * 状態変更リスナーを設定
     * 
     * @param listener リスナー（nullで解除）
     */
    public static void setStateChangeListener(StateChangeListener listener) {
        stateChangeListener = listener;
    }

    private static void notifyStateChange(String propertyName, Object oldValue, Object newValue) {
        if (stateChangeListener != null) {
            stateChangeListener.onStateChanged(propertyName, oldValue, newValue);
        }
    }

    // ==================== Utility Methods ====================

    /**
     * 状態をリセット
     */
    public static void reset() {
        setTopDownView(false);
        setCameraDistance(getDefaultCameraDistance());
    }

    /**
     * カメラ距離を増加
     * 
     * @param delta 増加量
     * @return 新しい距離
     */
    public static double increaseCameraDistance(double delta) {
        double newDistance = Math.min(getMaxCameraDistance(), getCameraDistance() + delta);
        setCameraDistance(newDistance);
        return newDistance;
    }

    /**
     * カメラ距離を減少
     * 
     * @param delta 減少量
     * @return 新しい距離
     */
    public static double decreaseCameraDistance(double delta) {
        double newDistance = Math.max(getMinCameraDistance(), getCameraDistance() - delta);
        setCameraDistance(newDistance);
        return newDistance;
    }

    /**
     * 現在のカメラ位置を取得
     * 
     * @return カメラ位置（トップダウンビュー時）
     */
    public static Vec3 getCameraPosition() {
        if (!isTopDownView()) {
            return null;
        }
        return ModState.CAMERA.getCameraPosition();
    }
}