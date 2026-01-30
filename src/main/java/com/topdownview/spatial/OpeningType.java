package com.topdownview.spatial;

/**
 * 開口部の種類。
 *
 * <p>壁や天井に空いた穴の性質を表す。
 * 境界内の小穴は空間を分断しない（同一空間維持）。
 * 大きな開口部は別空間への接続点として記録される。
 */
public enum OpeningType {
    /** 境界内の小穴（MAX_HOLE_SIZE 以下）。空間を分断しない */
    BOUNDARY_HOLE,
    /** ドア程度の開口（1x2 など）。同一空間内の通り抜け点 */
    DOOR,
    /** 窓程度の開口。視線は通すが通行は不可の可能性 */
    WINDOW,
    /** 別空間への接続開口（MAX_HOLE_SIZE 超過） */
    PASSAGE;
}
