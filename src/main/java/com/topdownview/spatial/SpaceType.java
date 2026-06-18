package com.topdownview.spatial;

/**
 * 空間分類タイプ。
 *
 * <p>SpaceAnalyzer が床・壁・天井の有無と空間形状から判定する。
 */
public enum SpaceType {
    /** 部屋：上方に屋根があり床が存在する閉空間 */
    ROOM,
    /** 廊下：細長い閉空間 */
    CORRIDOR,
    /** 屋外：屋根がない、または広すぎる空間 */
    OUTDOOR,
    /** 洞窟：自然生成されたような閉空間 */
    CAVE,
    /** 判定不能 */
    UNKNOWN;
}
