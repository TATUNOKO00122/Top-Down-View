package com.topdownview.state;

/**
 * 時間管理
 * トップダウンビュー開始時のゲーム時間を記録
 */
public final class TimeState {

    private static final long DEFAULT_TIME = 0L;

    public static final TimeState INSTANCE = new TimeState();

    private long startTime = DEFAULT_TIME;

    private TimeState() {}

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Time must be non-negative: " + value);
        }
        startTime = value;
    }

    public void reset() {
        startTime = DEFAULT_TIME;
    }
}
