package com.topdownview.state;

public final class DestinationHighlightState {

    public static final DestinationHighlightState INSTANCE = new DestinationHighlightState();

    private long animationStartTime = 0;
    private boolean isAnimating = false;

    private static final float ANIMATION_DURATION_SECONDS = 0.4f;
    private static final float FADE_START_PROGRESS = 0.8f;

    private DestinationHighlightState() {}

    public void startAnimation() {
        animationStartTime = System.nanoTime();
        isAnimating = true;
    }

    public void reset() {
        animationStartTime = 0;
        isAnimating = false;
    }

    public boolean isAnimating() {
        if (!isAnimating) return false;
        
        float progress = getProgress();
        if (progress >= 1.0f) {
            isAnimating = false;
            return false;
        }
        return true;
    }

    public float getProgress() {
        if (animationStartTime == 0) return 1.0f;
        
        long elapsedNanos = System.nanoTime() - animationStartTime;
        float elapsedSeconds = elapsedNanos / 1_000_000_000.0f;
        float progress = elapsedSeconds / ANIMATION_DURATION_SECONDS;
        
        return Math.min(progress, 1.0f);
    }

    public float getAlpha() {
        float progress = getProgress();
        
        if (progress < FADE_START_PROGRESS) {
            return 1.0f;
        }
        
        float fadeProgress = (progress - FADE_START_PROGRESS) / (1.0f - FADE_START_PROGRESS);
        return 1.0f - fadeProgress;
    }
}