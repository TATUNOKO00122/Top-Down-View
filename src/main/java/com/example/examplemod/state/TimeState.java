package com.example.examplemod.state;

import net.minecraft.client.Minecraft;

/**
 * 時間管理
 * ゲーム内時間に基づく時間追跡
 */
public final class TimeState {

    // 定数
    private static final long DEFAULT_TIME = 0L;
    private static final float DEFAULT_FLOAT_TIME = 0.0f;
    private static final int MAX_END_TIME = 10;
    private static final int MAX_ZOOM_OUT_TIME = 10;

    // シングルトンインスタンス
    public static final TimeState INSTANCE = new TimeState();

    private long startTime = DEFAULT_TIME;
    private long endTime = DEFAULT_TIME;
    private float zoomTime = DEFAULT_FLOAT_TIME;
    private long startZoom = DEFAULT_TIME;
    private float zoomOutTime = DEFAULT_FLOAT_TIME;

    private TimeState() {}

    // ==================== Getters ====================

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public float getZoomTime() {
        return zoomTime;
    }

    public long getStartZoom() {
        return startZoom;
    }

    public float getZoomOutTime() {
        return zoomOutTime;
    }

    // ==================== Setters ====================

    public void setStartTime(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Time must be non-negative: " + value);
        }
        startTime = value;
    }

    public void setEndTime(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Time must be non-negative: " + value);
        }
        endTime = Math.min(value, MAX_END_TIME);
    }

    public void setZoomTime(float value) {
        if (value < 0.0f || Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException("Time must be non-negative and finite: " + value);
        }
        zoomTime = value;
    }

    public void setStartZoom(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Time must be non-negative: " + value);
        }
        startZoom = value;
    }

    public void setZoomOutTime(float value) {
        if (value < 0.0f || Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException("Time must be non-negative and finite: " + value);
        }
        zoomOutTime = Math.min(value, MAX_ZOOM_OUT_TIME);
    }

    /**
     * 現在のゲーム時間を取得
     */
    public long getCurrentGameTime() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return DEFAULT_TIME;
        }
        return mc.level.getGameTime();
    }

    /**
     * 開始時間を現在時刻に設定
     */
    public void markStartTime() {
        startTime = getCurrentGameTime();
    }

    /**
     * endTimeを増加（上限あり）
     */
    public void incrementEndTime() {
        if (endTime < MAX_END_TIME) {
            endTime++;
        }
    }

    /**
     * zoomOutTimeを増加（上限あり）
     */
    public void incrementZoomOutTime() {
        if (zoomOutTime < MAX_ZOOM_OUT_TIME) {
            zoomOutTime++;
        }
    }

    /**
     * 状態をリセット
     */
    public void reset() {
        startTime = DEFAULT_TIME;
        endTime = DEFAULT_TIME;
        zoomTime = DEFAULT_FLOAT_TIME;
        startZoom = DEFAULT_TIME;
        zoomOutTime = DEFAULT_FLOAT_TIME;
    }
}