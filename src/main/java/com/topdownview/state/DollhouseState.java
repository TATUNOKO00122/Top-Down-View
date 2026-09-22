package com.topdownview.state;

import com.topdownview.spatial.DollhouseMask;

/**
 * ドールハウス表示（部屋の外を黒くする）の状態。
 *
 * <p>マスク本体は tick スレッド（クライアント）で構築し、描画スレッドが参照する。
 * 配列とバージョンは volatile で公開し、描画側はバージョンが変わった時だけ GPU へ再アップロードする。
 */
public final class DollhouseState {

    public static final DollhouseState INSTANCE = new DollhouseState();

    private volatile byte[] mask = null;
    private volatile long maskVersion = 0L;
    private volatile int anchorX;
    private volatile int anchorY;
    private volatile int anchorZ;
    private volatile boolean enclosed = false;
    private volatile float active = 0.0f;

    private DollhouseState() {
    }

    public byte[] getMask() { return mask; }

    public long getMaskVersion() { return maskVersion; }

    public int getAnchorX() { return anchorX; }

    public int getAnchorY() { return anchorY; }

    public int getAnchorZ() { return anchorZ; }

    public boolean isEnclosed() { return enclosed; }

    public float getActive() { return active; }

    public boolean isActive() { return active > 0.001f; }

    /** 構築済みマスクを公開する。{@code data} の長さは {@link DollhouseMask#WIDTH} × {@link DollhouseMask#HEIGHT}。 */
    public void publish(byte[] data, int ax, int ay, int az) {
        this.mask = data;
        this.anchorX = ax;
        this.anchorY = ay;
        this.anchorZ = az;
        this.enclosed = true;
        this.maskVersion++;
    }

    public void clearEnclosure() {
        this.enclosed = false;
    }

    public void setActive(float value) {
        this.active = Math.max(0.0f, Math.min(1.0f, value));
    }

    public void reset() {
        this.mask = null;
        this.maskVersion = 0L;
        this.anchorX = 0;
        this.anchorY = 0;
        this.anchorZ = 0;
        this.enclosed = false;
        this.active = 0.0f;
    }
}
