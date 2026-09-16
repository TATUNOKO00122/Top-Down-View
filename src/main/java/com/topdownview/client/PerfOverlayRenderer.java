package com.topdownview.client;

import com.topdownview.util.PerfMonitor;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * パフォーマンス計測の画面オーバーレイ。
 *
 * <p>{@link PerfMonitor} の値を右上にライブ表示し、FPS低下が描画由来かロジック由来かを
 * 目視で切り分けるための計測用。トップダウンビュー有効中は常時表示する。
 */
public final class PerfOverlayRenderer {

    private PerfOverlayRenderer() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) {
            return;
        }

        int x = graphics.guiWidth() - 4;
        int y = 4;
        int lineHeight = mc.font.lineHeight + 1;

        y = draw(graphics, mc, x, y, lineHeight, 0xFFFFFFFF,
                String.format(Locale.ROOT, "Perf fps %.1f  frame %.1f/%.1fms",
                        PerfMonitor.getWindowFps(), PerfMonitor.getWindowAvgFrameMs(),
                        PerfMonitor.getWindowMaxFrameMs()));
        y = draw(graphics, mc, x, y, lineHeight, 0xFFFFDD55,
                String.format(Locale.ROOT, "spikes >25ms %d  >100ms %d",
                        PerfMonitor.getDropFrames(), PerfMonitor.getFreezeFrames()));
        y = draw(graphics, mc, x, y, lineHeight, 0xFF66DDFF,
                String.format(Locale.ROOT, "render fade %.2fms (collect %.2fms)  overlay %.2fms",
                        PerfMonitor.FADE_RENDER.lastMs(), PerfMonitor.FADE_COLLECT.lastMs(),
                        PerfMonitor.OVERLAY_RENDER.lastMs()));
        y = draw(graphics, mc, x, y, lineHeight, 0xFF88FF88,
                String.format(Locale.ROOT, "tick cull %.2fms  entity %.2fms  probe %.2fms",
                        PerfMonitor.CULL_UPDATE.lastMs(), PerfMonitor.ENTITY_CULL.lastMs(),
                        PerfMonitor.PROBE.lastMs()));
        y = draw(graphics, mc, x, y, lineHeight, 0xFFCCCCCC,
                String.format(Locale.ROOT, "chunk rebuild %d (%.2fms)  culled %d",
                        PerfMonitor.getChunkRebuildCount(), PerfMonitor.CHUNK_REBUILD.lastMs(),
                        PerfMonitor.getCulledCallCount()));
    }

    private static int draw(GuiGraphics graphics, Minecraft mc, int rightX, int y, int lineHeight,
            int color, String text) {
        graphics.drawString(mc.font, text, rightX - mc.font.width(text), y, color, true);
        return y + lineHeight;
    }
}
