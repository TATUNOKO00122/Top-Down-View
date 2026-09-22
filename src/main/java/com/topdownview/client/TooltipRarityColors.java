package com.topdownview.client;

import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;

/**
 * LegendaryTooltips の標準フレーム（レアリティ連動）の配色アルゴリズムを再現する。
 * アイテムのレアリティ色を HSV に変換し、枠線・背景のグラデーション色を求める。
 * ドロップアイテムのラベルをツールチップと同じ色合いにするために使う。
 */
public final class TooltipRarityColors {

    public record Colors(int borderStart, int borderEnd, int backgroundStart, int backgroundEnd) {
    }

    private TooltipRarityColors() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static Colors forStack(ItemStack stack) {
        // LegendaryTooltips と同様に、アイテム名の明示色を優先し、無ければレアリティ色を使う
        TextColor nameColor = stack.getHoverName().getStyle().getColor();
        Integer rgb = null;
        if (nameColor != null) {
            rgb = nameColor.getValue();
        } else if (stack.getRarity() != null && stack.getRarity().color != null) {
            rgb = stack.getRarity().color.getColor();
        }
        int base = rgb != null ? rgb : 0xFFFFFF;

        float[] hsv = rgbToHsv((base >> 16) & 0xFF, (base >> 8) & 0xFF, base & 0xFF);
        int hue = (int) (hsv[0] * 360.0F);
        int saturation = (int) (hsv[1] * 255.0F);
        int value = (int) (hsv[2] * 255.0F);

        // LegendaryTooltips と同じ色相シフト（暖色/寒色で回転方向を変える）
        boolean addHue = hue >= 62 && hue <= 240;
        int startHue = wrapHue(addHue ? hue - 4 : hue + 4);
        int endHue = wrapHue(addHue ? hue + 18 : hue - 18);
        int startBackgroundHue = wrapHue(addHue ? hue - 3 : hue + 3);
        int endBackgroundHue = wrapHue(addHue ? hue + 13 : hue - 13);

        int borderStart = ahsvToArgb(255, startHue, saturation, value);
        int borderEnd = ahsvToArgb(255, endHue, saturation, (int) (value * 0.95F));
        int backgroundStart = ahsvToArgb(228, startBackgroundHue, (int) (saturation * 0.9F), 14);
        int backgroundEnd = ahsvToArgb(253, endBackgroundHue, (int) (saturation * 0.8F), 18);
        return new Colors(borderStart, borderEnd, backgroundStart, backgroundEnd);
    }

    private static int wrapHue(int hue) {
        return ((hue % 360) + 360) % 360;
    }

    /** ARGB各成分(0-255)をHSV(hue 0-1, saturation 0-1, value 0-1)へ。 */
    private static float[] rgbToHsv(int r, int g, int b) {
        int cmax = Math.max(r, Math.max(g, b));
        int cmin = Math.min(r, Math.min(g, b));
        float value = cmax / 255.0F;
        float saturation = cmax != 0 ? (float) (cmax - cmin) / cmax : 0.0F;

        float hue;
        if (saturation == 0.0F) {
            hue = 0.0F;
        } else {
            float redc = (float) (cmax - r) / (cmax - cmin);
            float greenc = (float) (cmax - g) / (cmax - cmin);
            float bluec = (float) (cmax - b) / (cmax - cmin);
            if (r == cmax) {
                hue = bluec - greenc;
            } else if (g == cmax) {
                hue = 2.0F + redc - bluec;
            } else {
                hue = 4.0F + greenc - redc;
            }
            hue /= 6.0F;
            if (hue < 0.0F) {
                hue++;
            }
        }
        return new float[]{hue, saturation, value};
    }

    /** HSV(hue 0-360, saturation 0-255, value 0-255)をARGBへ。 */
    private static int ahsvToArgb(int alpha, int hue, int saturation, int value) {
        float a = clamp01(alpha / 255.0F);
        float h = clamp01(hue / 360.0F);
        float s = clamp01(saturation / 255.0F);
        float v = clamp01(value / 255.0F);

        int r;
        int g;
        int b;
        if (s == 0.0F) {
            r = g = b = round(v);
        } else {
            int sector = (int) (h * 6.0F);
            float f = h * 6.0F - sector;
            float p = v * (1.0F - s);
            float q = v * (1.0F - s * f);
            float t = v * (1.0F - s * (1.0F - f));
            switch (sector) {
                case 0 -> { r = round(v); g = round(t); b = round(p); }
                case 1 -> { r = round(q); g = round(v); b = round(p); }
                case 2 -> { r = round(p); g = round(v); b = round(t); }
                case 3 -> { r = round(p); g = round(q); b = round(v); }
                case 4 -> { r = round(t); g = round(p); b = round(v); }
                default -> { r = round(v); g = round(p); b = round(q); }
            }
        }
        return (round(a) << 24) | (r << 16) | (g << 8) | b;
    }

    private static int round(float value) {
        return (int) (value * 255.0F + 0.5F);
    }

    private static float clamp01(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        return value > 1.0F ? 1.0F : value;
    }
}
