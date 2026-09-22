package com.topdownview.client;

import com.topdownview.Config;
import com.topdownview.state.ModState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * ドロップアイテムにハクスラ風のラベル（アイテム名＋スタック数）を表示する。
 * スクリーン空間に描画し、重なるラベルは下方向へ一定間隔で積み重ねて重なりを防ぐ。
 * 背景はフォントの文字幅に余白を足して決める。
 * 0=非表示 / 1=カーソル時のみ / 2=範囲内は常時 の3モードを設定で切り替える。
 */
public final class DroppedItemLabelRenderer {

    private static final double LABEL_RADIUS = 24.0D;
    private static final double LABEL_RADIUS_SQR = LABEL_RADIUS * LABEL_RADIUS;

    private static final int PADDING = 3;
    private static final int ANCHOR_GAP = 4;
    private static final int MAX_LABELS = 64;
    private static final int MAX_CANDIDATES = 256;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TOOLTIP_FILL = 0xF0100010;
    private static final int TOOLTIP_BORDER_TOP = 0x505000FF;
    private static final int TOOLTIP_BORDER_BOTTOM = 0x5028007F;

    private static final Matrix4f PROJECTION_VIEW = new Matrix4f();
    private static final Vector4f PROJECTION_SCRATCH = new Vector4f();

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static int entryCount = 0;
    private static boolean computedThisFrame = false;

    private DroppedItemLabelRenderer() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /** ワールド座標をスクリーンへ投影し、描画対象を決定する。 */
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        computedThisFrame = false;
        // ラベルが無い間はクリック判定に使う矩形も無効化する
        entryCount = 0;

        if (!ModState.STATUS.isEnabled()) {
            return;
        }
        int mode = Config.getDroppedItemLabelMode();
        if (mode == 0) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        int guiWidth = mc.getWindow().getGuiScaledWidth();
        int guiHeight = mc.getWindow().getGuiScaledHeight();
        if (guiWidth <= 0 || guiHeight <= 0) {
            return;
        }

        try {
            PROJECTION_VIEW.set(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose());
            Vec3 cameraPos = event.getCamera().getPosition();
            float partialTick = event.getPartialTick();

            if (mode == 1) {
                ItemEntity hovered = ItemPickupHelper.findHoveredItem(mc, cameraPos);
                if (hovered != null) {
                    addCandidate(mc, hovered, cameraPos, partialTick, guiWidth, guiHeight);
                }
            } else {
                addAllCandidates(mc, cameraPos, partialTick, guiWidth, guiHeight);
            }

            if (entryCount > MAX_LABELS) {
                sortByDistance();
                entryCount = MAX_LABELS;
            }
            sortForLayout();
            finalizeEntries(mc, guiWidth);

            // ラベル矩形はカメラ確定直後（このイベント）で算出し、クリック判定が描画タイミングに依存しないようにする
            float scale = labelScale();
            float labelHeight = (mc.font.lineHeight + 2 * backgroundMargin()) * scale;
            layout(labelHeight, guiWidth, guiHeight);
            computeRects(scale, labelHeight);

            computedThisFrame = entryCount > 0;
        } catch (Throwable t) {
            entryCount = 0;
            computedThisFrame = false;
        }
    }

    /** レイアウト済みのラベルを描画する。HUDより背面に表示するため Pre で行う。 */
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        if (!computedThisFrame) {
            return;
        }
        computedThisFrame = false;
        if (entryCount == 0) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            entryCount = 0;
            return;
        }

        try {
            Font font = mc.font;
            float scale = labelScale();
            boolean tooltipBackground = Config.getDroppedItemLabelBackground() == 1;
            float localHeight = font.lineHeight + 2 * backgroundMargin();
            float labelHeight = localHeight * scale;
            GuiGraphics guiGraphics = event.getGuiGraphics();

            for (int i = 0; i < entryCount; i++) {
                Entry entry = ENTRIES.get(i);
                float boxWidth = entry.localWidth * scale;
                float backgroundLeft = entry.x - boxWidth / 2.0F;
                float backgroundTop = entry.finalY - labelHeight / 2.0F;

                var pose = guiGraphics.pose();
                pose.pushPose();
                // 端数を pose に載せ、GUIピクセル未満でも滑らかに追従させる
                pose.translate(backgroundLeft, backgroundTop, 0.0F);
                pose.scale(scale, scale, 1.0F);

                drawBackground(guiGraphics, entry.localWidth, localHeight, tooltipBackground, entry);

                // 計測した描画範囲の中心が背景中心に来る位置へ文字を置く
                pose.translate(entry.textOffsetX, entry.textOffsetY, 0.0F);
                guiGraphics.drawString(font, entry.label, 0, 0, TEXT_COLOR, false);
                pose.popPose();
            }
        } catch (Throwable t) {
            entryCount = 0;
        }
    }

    /** GUI座標がラベル矩形内にあるアイテムを返す（重なり時は後から描いたものを優先）。 */
    public static ItemEntity findLabelAt(double guiX, double guiY) {
        for (int i = entryCount - 1; i >= 0; i--) {
            Entry entry = ENTRIES.get(i);
            if (guiX >= entry.rectLeft && guiX <= entry.rectRight
                    && guiY >= entry.rectTop && guiY <= entry.rectBottom) {
                return entry.item;
            }
        }
        return null;
    }

    /** 現在カーソルがあるラベルのアイテムを返す。無ければ null。 */
    public static ItemEntity findItemUnderCursor(Minecraft mc) {
        if (mc == null || mc.getWindow() == null || mc.mouseHandler == null) {
            return null;
        }
        double guiScale = mc.getWindow().getGuiScale();
        if (guiScale <= 0.0D) {
            return null;
        }
        return findLabelAt(mc.mouseHandler.xpos() / guiScale, mc.mouseHandler.ypos() / guiScale);
    }

    /**
     * ラベル背景を描く。
     * 単色モード: レアリティ色の枠＋背景。外側に1pxはみ出す背景は四隅を1px削る。
     * ツールチップモード: バニラのツールチップ色（レアリティ非適用）。
     */
    private static void drawBackground(GuiGraphics guiGraphics, float width, float height, boolean tooltip, Entry entry) {
        int w = Math.round(width);
        int h = Math.round(height);

        if (tooltip) {
            // バニラのツールチップ風（レアリティ非適用、枠は外縁）
            guiGraphics.fill(0, 0, w, h, TOOLTIP_FILL);
            guiGraphics.fillGradient(0, 0, 1, h, TOOLTIP_BORDER_TOP, TOOLTIP_BORDER_BOTTOM);
            guiGraphics.fillGradient(w - 1, 0, w, h, TOOLTIP_BORDER_TOP, TOOLTIP_BORDER_BOTTOM);
            guiGraphics.fill(1, 0, w - 1, 1, TOOLTIP_BORDER_TOP);
            guiGraphics.fill(1, h - 1, w - 1, h, TOOLTIP_BORDER_BOTTOM);
            return;
        }

        // 外側に1pxはみ出す背景（四隅を1px削る）
        guiGraphics.fill(1, 0, w - 1, 1, entry.backgroundStart);
        guiGraphics.fill(1, h - 1, w - 1, h, entry.backgroundStart);
        guiGraphics.fill(0, 1, 1, h - 1, entry.backgroundStart);
        guiGraphics.fill(w - 1, 1, w, h - 1, entry.backgroundStart);

        // 枠線（上下グラデーション、外側から1px内側）
        guiGraphics.fill(1, 1, w - 1, 2, entry.borderStart);
        guiGraphics.fill(1, h - 2, w - 1, h - 1, entry.borderEnd);
        guiGraphics.fillGradient(1, 2, 2, h - 2, entry.borderStart, entry.borderEnd);
        guiGraphics.fillGradient(w - 2, 2, w - 1, h - 2, entry.borderStart, entry.borderEnd);

        // 内側の背景
        guiGraphics.fill(2, 2, w - 2, h - 2, entry.backgroundStart);
    }

    private static float labelScale() {
        return (float) Config.getDroppedItemLabelScale();
    }

    /** 背景の外側余白。単色ははみ出し背景1px＋枠線1px、ツールチップは枠線1px分。 */
    private static float backgroundMargin() {
        return PADDING + (Config.getDroppedItemLabelBackground() == 1 ? 1.0F : 2.0F);
    }

    private static void addAllCandidates(Minecraft mc, Vec3 cameraPos, float partialTick, int guiWidth, int guiHeight) {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity item) || item.getItem().isEmpty()) {
                continue;
            }
            if (item.distanceToSqr(mc.player) > LABEL_RADIUS_SQR) {
                continue;
            }
            if (entryCount >= MAX_CANDIDATES) {
                break;
            }
            addCandidate(mc, item, cameraPos, partialTick, guiWidth, guiHeight);
        }
    }

    /** アイテム上部をスクリーンへ投影し、画面内なら候補として追加する。 */
    private static boolean addCandidate(Minecraft mc, ItemEntity item, Vec3 cameraPos, float partialTick,
                                        int guiWidth, int guiHeight) {
        // 実際に描画される補間位置に合わせる（停滞・カクつき防止）
        double itemY = Mth.lerp(partialTick, item.yo, item.getY());
        double relativeX = Mth.lerp(partialTick, item.xo, item.getX()) - cameraPos.x;
        double relativeY = itemY + (item.getBoundingBox().maxY - item.getY()) + 0.1D - cameraPos.y;
        double relativeZ = Mth.lerp(partialTick, item.zo, item.getZ()) - cameraPos.z;

        PROJECTION_SCRATCH.set((float) relativeX, (float) relativeY, (float) relativeZ, 1.0F);
        PROJECTION_SCRATCH.mul(PROJECTION_VIEW);
        float w = PROJECTION_SCRATCH.w;
        if (w <= 0.0F) {
            return false;
        }

        float ndcX = PROJECTION_SCRATCH.x / w;
        float ndcY = PROJECTION_SCRATCH.y / w;
        // 画面外（ラベル分の余白を含む）や異常値は除外する
        if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY)
                || ndcX < -1.2F || ndcX > 1.2F || ndcY < -1.2F || ndcY > 1.2F) {
            return false;
        }

        float scale = labelScale();
        float labelHeight = (mc.font.lineHeight + 2 * backgroundMargin()) * scale;

        Entry entry = obtainEntry(entryCount);
        entry.item = item;
        entry.id = item.getId();
        entry.distanceSqr = (float) item.distanceToSqr(mc.player);
        entry.worldX = (float) item.getX();
        entry.worldZ = (float) item.getZ();
        entry.x = (ndcX * 0.5F + 0.5F) * guiWidth;
        entry.y = (0.5F - ndcY * 0.5F) * guiHeight - labelHeight / 2.0F - ANCHOR_GAP * scale;
        entry.finalY = entry.y;
        entryCount++;
        return true;
    }

    /** 最終的な表示対象についてラベルと、文字幅にもとづく背景サイズを求める。 */
    private static void finalizeEntries(Minecraft mc, int guiWidth) {
        float scale = labelScale();
        Font font = mc.font;
        for (int i = 0; i < entryCount; i++) {
            Entry entry = ENTRIES.get(i);
            Component label = buildLabel(entry.item.getItem());
            entry.label = label;

            // フォントの描画パイプラインを呼ばずに幅を測る。ワールド描画中に drawInBatch を
            // ダミーbufferで走らせると ImmediatelyFast / FancyMenu Smooth Font と衝突して固まる。
            float localWidth = font.width(label) + 2 * backgroundMargin();
            entry.localWidth = localWidth;
            entry.textOffsetX = backgroundMargin();
            entry.textOffsetY = backgroundMargin();
            entry.width = localWidth * scale;

            // 枠・背景の色はツールチップ（LegendaryTooltips標準フレーム）に合わせる
            TooltipRarityColors.Colors colors = TooltipRarityColors.forStack(entry.item.getItem());
            entry.borderStart = colors.borderStart();
            entry.borderEnd = colors.borderEnd();
            entry.backgroundStart = colors.backgroundStart();

            float anchorX = entry.x;
            float halfWidth = entry.width / 2.0F;
            entry.x = Math.max(halfWidth, Math.min(guiWidth - halfWidth, anchorX));
        }
    }

    /**
     * 重ならないよう、画面下端に収まる限りは下方向へ積み重ね、
     * 画面下端を超える場合は左右の空きスペースへ水平シフトして展開する。
     */
    private static void layout(float labelHeight, int guiWidth, int guiHeight) {
        float scale = labelScale();
        float gap = Config.getDroppedItemLabelGap() * scale;
        float step = labelHeight + gap;
        float threshold = step - 0.01F;
        float bottomMargin = labelHeight / 2.0F + 8.0F;

        for (int i = 0; i < entryCount; i++) {
            Entry entry = ENTRIES.get(i);
            float currX = entry.x;
            boolean preferRight = currX < guiWidth / 2.0F;

            float bestX = currX;
            float bestY = entry.y;

            int attempts = 0;
            while (attempts < 6) {
                attempts++;
                float halfW = entry.width / 2.0F;
                currX = Math.max(halfW, Math.min(guiWidth - halfW, currX));

                // 現在の列での縦押し下げ位置を計算
                float y = entry.y;
                boolean moved = true;
                int passes = 0;
                while (moved && passes < entryCount + 4) {
                    passes++;
                    moved = false;
                    for (int j = 0; j < i; j++) {
                        Entry placed = ENTRIES.get(j);
                        boolean horizontalOverlap = Math.abs(currX - placed.x)
                                < (entry.width + placed.width) / 2.0F + gap;
                        if (horizontalOverlap && Math.abs(y - placed.finalY) < threshold) {
                            y = placed.finalY + step;
                            moved = true;
                        }
                    }
                }

                bestX = currX;
                bestY = y;

                // 画面下端に収まるならこの位置で確定
                if (y + bottomMargin <= guiHeight) {
                    break;
                }

                // 画面下端に入り切らない場合、重なっている配置済みラベル群の外側へ水平シフト
                float maxRight = currX;
                float minLeft = currX;
                for (int j = 0; j < i; j++) {
                    Entry placed = ENTRIES.get(j);
                    if (Math.abs(currX - placed.x) < (entry.width + placed.width) / 2.0F + gap) {
                        maxRight = Math.max(maxRight, placed.x + placed.width / 2.0F);
                        minLeft = Math.min(minLeft, placed.x - placed.width / 2.0F);
                    }
                }

                float newX;
                if (preferRight) {
                    newX = maxRight + gap + halfW;
                    if (newX + halfW > guiWidth) {
                        newX = minLeft - gap - halfW;
                    }
                } else {
                    newX = minLeft - gap - halfW;
                    if (newX - halfW < 0.0F) {
                        newX = maxRight + gap + halfW;
                    }
                }

                if (Math.abs(newX - currX) < 1.0F) {
                    break;
                }
                currX = newX;
            }

            entry.x = bestX;
            entry.finalY = bestY;
        }
    }

    /** クリック判定に使う最終的な画面矩形（GUI座標）を求める。 */
    private static void computeRects(float scale, float labelHeight) {
        for (int i = 0; i < entryCount; i++) {
            Entry entry = ENTRIES.get(i);
            float boxWidth = entry.localWidth * scale;
            float left = entry.x - boxWidth / 2.0F;
            float top = entry.finalY - labelHeight / 2.0F;
            entry.rectLeft = left;
            entry.rectTop = top;
            entry.rectRight = left + boxWidth;
            entry.rectBottom = top + labelHeight;
        }
    }

    private static void sortByDistance() {
        for (int i = 1; i < entryCount; i++) {
            Entry key = ENTRIES.get(i);
            int j = i - 1;
            while (j >= 0 && isCloser(key, ENTRIES.get(j))) {
                ENTRIES.set(j + 1, ENTRIES.get(j));
                j--;
            }
            ENTRIES.set(j + 1, key);
        }
    }

    private static boolean isCloser(Entry a, Entry b) {
        int compared = Float.compare(a.distanceSqr, b.distanceSqr);
        return compared != 0 ? compared < 0 : a.id < b.id;
    }

    private static void sortForLayout() {
        for (int i = 1; i < entryCount; i++) {
            Entry key = ENTRIES.get(i);
            int j = i - 1;
            while (j >= 0 && compareLayout(key, ENTRIES.get(j)) < 0) {
                ENTRIES.set(j + 1, ENTRIES.get(j));
                j--;
            }
            ENTRIES.set(j + 1, key);
        }
    }

    /**
     * 並び順はワールド座標で固定する。スクリーン座標で並べるとカメラ移動のたびに
     * 順序が入れ替わりラベル位置が頻繁に交換されるため、静止したアイテムでは不変な座標を使う。
     */
    private static int compareLayout(Entry a, Entry b) {
        int comparedX = Float.compare(a.worldX, b.worldX);
        if (comparedX != 0) {
            return comparedX;
        }
        int comparedZ = Float.compare(a.worldZ, b.worldZ);
        if (comparedZ != 0) {
            return comparedZ;
        }
        return Integer.compare(a.id, b.id);
    }

    private static Entry obtainEntry(int index) {
        while (ENTRIES.size() <= index) {
            ENTRIES.add(new Entry());
        }
        return ENTRIES.get(index);
    }

    /** レアリティ色のアイテム名とスタック数（2以上の場合のみ）を組み立てる。 */
    private static Component buildLabel(ItemStack stack) {
        MutableComponent label = Component.empty().append(stack.getHoverName());
        var rarity = stack.getRarity();
        if (rarity != null && rarity.color != null) {
            label.withStyle(rarity.color);
        }
        int count = stack.getCount();
        if (count > 1) {
            label.append(Component.literal(" x" + count).withStyle(ChatFormatting.GRAY));
        }
        return label;
    }

    private static final class Entry {
        private ItemEntity item;
        private Component label;
        private int id;
        private float distanceSqr;
        private float worldX;
        private float worldZ;
        private float x;
        private float y;
        private float finalY;
        private float width;
        private float localWidth;
        private float textOffsetX;
        private float textOffsetY;
        private float rectLeft;
        private float rectTop;
        private float rectRight;
        private float rectBottom;
        private int borderStart;
        private int borderEnd;
        private int backgroundStart;
    }
}
