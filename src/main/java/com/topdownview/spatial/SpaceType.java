package com.topdownview.spatial;

/**
 * 空間分類タイプ。
 *
 * <p>SpaceAnalyzer が床・壁・天井の有無と空間形状から判定する。
 */
public enum SpaceType {
    /** 空間：上方に屋根が存在する閉鎖空間 */
    ENCLOSED,
    /** 空間以外：屋外など */
    OUTDOOR,
    /** 判定不能 */
    UNKNOWN;
}
