package com.topdownview.client;

public final class MobVisibilityCache {
    private static final ThreadLocal<Float> CURRENT_CONE_ALPHA = ThreadLocal.withInitial(() -> 1.0f);

    private MobVisibilityCache() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static float getCurrentConeAlpha() {
        return CURRENT_CONE_ALPHA.get();
    }

    public static void setCurrentConeAlpha(float value) {
        CURRENT_CONE_ALPHA.set(value);
    }

    public static void clear() {
        CURRENT_CONE_ALPHA.remove();
    }
}
