package com.topdownview.culling;

import com.topdownview.Config;

import java.util.HashSet;
import java.util.Set;

/**
 * 自然木の原木保護を担うハンドラー。
 *
 * <p>検出した自然木の原木をカリング対象から除外し、木々が不自然に欠けないようにする。
 */
public final class TreeCullingHandler {

    /** チャンク構築ワーカーから読まれるため、集合は volatile 参照ごと差し替える。 */
    private volatile Set<Long> protectedTreeLogPositions = Set.of();

    public void clearCache() {
        protectedTreeLogPositions = Set.of();
    }

    public boolean isProtectedLog(long posLong) {
        return Config.isProtectNaturalTreeLogs() && protectedTreeLogPositions.contains(posLong);
    }

    public void updateLogs() {
        protectedTreeLogPositions = new HashSet<>(NaturalTreeDetector.getNaturalTreeLogs());
    }
}
