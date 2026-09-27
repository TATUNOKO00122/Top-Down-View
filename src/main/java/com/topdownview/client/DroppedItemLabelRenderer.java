package com.topdownview.client;

import com.topdownview.Config;
import com.topdownview.culling.Cullable;
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
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * ドロップアイテムにラベル（アイテム名＋スタック数）を表示する。
 * 各ラベルは初回配置で確定したオフセットを基準位置へ足した位置へ追従し、以後は配置を選び直さない
 * ため、カメラ移動・アイテム移動・拾得で位置が入れ替わらない。落下・投擲・バウンド中はラベルを
 * 表示しない（設置面に接地して静止してから表示・配置する）が、一度表示したラベルは多少動いても
 * 消さない（点滅させない）。点滅するとその間だけ障害物でなくなり、他ラベルが席を奪って重なる。
 * 生存中の画面外エントリも席を予約して新規配置を妨げるので再入場しても重ならず、拾われたエントリは
 * 削除されてその席は新規に使える。重なった場合は最小移動で押し離す。画面内へ収める処理は行わない。
 */
public final class DroppedItemLabelRenderer {

    private static final double LABEL_RADIUS = 24.0D;
    private static final double LABEL_RADIUS_SQR = LABEL_RADIUS * LABEL_RADIUS;

    private static final int PADDING = 3;
    private static final int ANCHOR_GAP = 4;
    private static final int MAX_LABELS = 64;
    private static final int MAX_CANDIDATES = 256;
    private static final int MAX_TRACKED = 256;
    private static final int RESOLVE_PASSES = 8;
    private static final double MOVING_DISTANCE_SQR = 1.0E-6D;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TOOLTIP_FILL = 0xF0100010;
    private static final int TOOLTIP_BORDER_TOP = 0x505000FF;
    private static final int TOOLTIP_BORDER_BOTTOM = 0x5028007F;

    private static final Matrix4f PROJECTION_VIEW = new Matrix4f();
    private static final Vector4f PROJECTION_SCRATCH = new Vector4f();

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final Int2ObjectOpenHashMap<Entry> ENTRIES_BY_ID = new Int2ObjectOpenHashMap<>();
    private static final float[] BLOCK_START = new float[MAX_TRACKED + MAX_LABELS];
    private static final float[] BLOCK_END = new float[MAX_TRACKED + MAX_LABELS];
    private static boolean computedThisFrame = false;
    private static int presentCount = 0;
    private static float candidateBestX;
    private static float candidateBestY;
    private static float candidateBestDist;

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
        if (!ModState.STATUS.isEnabled()) {
            clearEntries();
            return;
        }
        if (Config.getDroppedItemLabelMode() == 0) {
            clearEntries();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            clearEntries();
            return;
        }
        int guiWidth = mc.getWindow().getGuiScaledWidth();
        int guiHeight = mc.getWindow().getGuiScaledHeight();
        if (guiWidth <= 0 || guiHeight <= 0) {
            clearEntries();
            return;
        }

        try {
            PROJECTION_VIEW.set(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose());
            Vec3 cameraPos = event.getCamera().getPosition();
            float partialTick = event.getPartialTick();

            for (int i = 0; i < ENTRIES.size(); i++) {
                Entry entry = ENTRIES.get(i);
                entry.present = false;
                entry.inLayout = false;
            }
            presentCount = 0;

            addAllCandidates(mc, cameraPos, partialTick, guiWidth, guiHeight);
            pruneMissingEntries(mc);
            projectReservedSlots(mc, cameraPos, guiWidth, guiHeight);
            finalizeLabels(mc);
            resolveLayout(mc);
            computeRects(mc);

            computedThisFrame = presentCount > 0;
        } catch (Throwable t) {
            clearEntries();
        }
    }

    /** レイアウト済みのラベルを描画する。HUDより背面に表示するため Pre で行う。 */
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        if (!computedThisFrame) {
            return;
        }
        computedThisFrame = false;
        if (ENTRIES.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            // 画面表示中は描かないが、閉じたときに並びが変わらないようエントリは保持する
            return;
        }

        try {
            Font font = mc.font;
            float scale = labelScale();
            boolean tooltipBackground = Config.getDroppedItemLabelBackground() == 1;
            float localHeight = font.lineHeight + 2 * backgroundMargin();
            float labelHeight = localHeight * scale;
            GuiGraphics guiGraphics = event.getGuiGraphics();

            for (int i = 0; i < ENTRIES.size(); i++) {
                Entry entry = ENTRIES.get(i);
                if (!entry.present) {
                    continue;
                }
                float boxWidth = entry.localWidth * scale;
                float backgroundLeft = entry.finalX - boxWidth / 2.0F;
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
            clearEntries();
        }
    }

    /** GUI座標がラベル矩形内にあるアイテムを返す（重なり時は後から描いたものを優先）。 */
    public static ItemEntity findLabelAt(double guiX, double guiY) {
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            Entry entry = ENTRIES.get(i);
            if (!entry.present) {
                continue;
            }
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

    private static float labelHeight(Minecraft mc) {
        return (mc.font.lineHeight + 2 * backgroundMargin()) * labelScale();
    }

    private static void addAllCandidates(Minecraft mc, Vec3 cameraPos, float partialTick, int guiWidth, int guiHeight) {
        int processed = 0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity item) || item.getItem().isEmpty()) {
                continue;
            }
            // カリングされたブロックの上に落ちているアイテムは本体ごと消えているため、ラベルも出さない
            if (ModState.STATUS.isCullingEnabled() && item instanceof Cullable cullable
                    && cullable.topdownview_isCulled()) {
                continue;
            }
            if (item.distanceToSqr(mc.player) > LABEL_RADIUS_SQR) {
                continue;
            }
            if (processed >= MAX_CANDIDATES) {
                break;
            }
            processed++;
            addCandidate(mc, item, cameraPos, partialTick, guiWidth, guiHeight);
        }
    }

    /** アイテム上部をスクリーンへ投影し、画面内なら候補として追加する。 */
    private static void addCandidate(Minecraft mc, ItemEntity item, Vec3 cameraPos, float partialTick,
                                     int guiWidth, int guiHeight) {
        int id = item.getId();
        Entry entry = ENTRIES_BY_ID.get(id);

        // 落下・投擲・バウンド中は表示しない。ただし一度表示した（placed）ラベルは多少動いても消さない。
        // 消すと点滅し、その間だけ障害物でなくなって他ラベルが席を奪い、再表示時に重なる。
        double movedX = item.getX() - item.xo;
        double movedY = item.getY() - item.yo;
        double movedZ = item.getZ() - item.zo;
        boolean settled = item.onGround()
                && movedX * movedX + movedY * movedY + movedZ * movedZ <= MOVING_DISTANCE_SQR;
        if (!settled && (entry == null || !entry.placed)) {
            return;
        }

        // 実際に描画される補間位置に合わせる（停滞・カクつき防止）
        double itemY = Mth.lerp(partialTick, item.yo, item.getY());
        double relativeX = Mth.lerp(partialTick, item.xo, item.getX()) - cameraPos.x;
        double relativeY = itemY + (item.getBoundingBox().maxY - item.getY()) + 0.1D - cameraPos.y;
        double relativeZ = Mth.lerp(partialTick, item.zo, item.getZ()) - cameraPos.z;

        PROJECTION_SCRATCH.set((float) relativeX, (float) relativeY, (float) relativeZ, 1.0F);
        PROJECTION_SCRATCH.mul(PROJECTION_VIEW);
        float w = PROJECTION_SCRATCH.w;
        if (w <= 0.0F) {
            return;
        }

        float ndcX = PROJECTION_SCRATCH.x / w;
        float ndcY = PROJECTION_SCRATCH.y / w;
        // 画面外（ラベル分の余白を含む）や異常値は除外する
        if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY)
                || ndcX < -1.2F || ndcX > 1.2F || ndcY < -1.2F || ndcY > 1.2F) {
            return;
        }

        float height = labelHeight(mc);
        if (entry == null) {
            if (presentCount >= MAX_LABELS) {
                return;
            }
            entry = new Entry();
            entry.id = id;
            ENTRIES_BY_ID.put(id, entry);
            insertSorted(entry);
        }
        if (!entry.present) {
            presentCount++;
        }

        entry.item = item;
        entry.present = true;
        entry.inLayout = true;
        // 画面外でも席を予約できるよう、基準位置をワールド座標でも覚えておく
        entry.anchorWorldX = Mth.lerp(partialTick, item.xo, item.getX());
        entry.anchorWorldY = itemY + (item.getBoundingBox().maxY - item.getY()) + 0.1D;
        entry.anchorWorldZ = Mth.lerp(partialTick, item.zo, item.getZ());
        entry.x = (ndcX * 0.5F + 0.5F) * guiWidth;
        entry.y = (0.5F - ndcY * 0.5F) * guiHeight - height / 2.0F - ANCHOR_GAP * labelScale();
    }

    /**
     * 画面外（生存中）のエントリの席をワールド基準位置から画面へ投影する。ラベル表示対象のものだけを
     * 障害物として席を予約し、新規ラベルがその場所を奪わないようにして再入場時の重なりを防ぐ。
     */
    private static void projectReservedSlots(Minecraft mc, Vec3 cameraPos, int guiWidth, int guiHeight) {
        float height = labelHeight(mc);
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            if (entry.present) {
                continue;
            }
            // カリングされたブロック上のアイテムや、表示範囲外の遠いアイテムは席を予約しない
            ItemEntity item = entry.item;
            if (item == null || item.distanceToSqr(mc.player) > LABEL_RADIUS_SQR) {
                continue;
            }
            double movedX = item.getX() - item.xo;
            double movedY = item.getY() - item.yo;
            double movedZ = item.getZ() - item.zo;
            if (!item.onGround()
                    || movedX * movedX + movedY * movedY + movedZ * movedZ > MOVING_DISTANCE_SQR) {
                continue;
            }
            if (ModState.STATUS.isCullingEnabled() && item instanceof Cullable cullable
                    && cullable.topdownview_isCulled()) {
                continue;
            }
            float relativeX = (float) (entry.anchorWorldX - cameraPos.x);
            float relativeY = (float) (entry.anchorWorldY - cameraPos.y);
            float relativeZ = (float) (entry.anchorWorldZ - cameraPos.z);

            PROJECTION_SCRATCH.set(relativeX, relativeY, relativeZ, 1.0F);
            PROJECTION_SCRATCH.mul(PROJECTION_VIEW);
            float w = PROJECTION_SCRATCH.w;
            if (w <= 0.0F) {
                continue;
            }
            float ndcX = PROJECTION_SCRATCH.x / w;
            float ndcY = PROJECTION_SCRATCH.y / w;
            if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY)) {
                continue;
            }

            entry.inLayout = true;
            entry.x = (ndcX * 0.5F + 0.5F) * guiWidth;
            entry.y = (0.5F - ndcY * 0.5F) * guiHeight - height / 2.0F - ANCHOR_GAP * labelScale();
            entry.finalX = entry.x + entry.offX;
            entry.finalY = entry.y + entry.offY;
        }
    }

    /**
     * 画面外へ出たアイテムは ENTRIES から外れるため、単純に末尾へ追加すると
     * カメラパンで再入場した順に並びが変わり重なりラベルが入れ替わる。
     * id の昇順で挿入し、出入りのタイミングに依存しない安定した並びを保つ。
     */
    private static void insertSorted(Entry entry) {
        int lo = 0;
        int hi = ENTRIES.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (ENTRIES.get(mid).id < entry.id) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        ENTRIES.add(lo, entry);
    }

    /**
     * 拾われて消えたアイテムをゴースト化して席を保持する。範囲外・画面外で一時的に見えないだけの
     * エントリはそのまま残し、戻ってきたときに同じ位置へ描く。
     */
    /**
     * 拾われて消えたアイテムのエントリを破棄して席を解放する。範囲外・画面外で一時的に見えない
     * だけのエントリは席を予約したまま残し、戻ってきたときに同じ位置へ描く。
     */
    private static void pruneMissingEntries(Minecraft mc) {
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            Entry entry = ENTRIES.get(i);
            if (entry.present) {
                continue;
            }
            Entity entity = mc.level.getEntity(entry.id);
            if (!(entity instanceof ItemEntity item) || item.isRemoved() || item.getItem().isEmpty()) {
                removeEntryAt(i);
            }
        }

        // 遠方で残り続けるエントリが増えすぎないよう、見えていない古いものから間引く
        int excess = ENTRIES.size() - MAX_TRACKED;
        for (int i = 0; i < ENTRIES.size() && excess > 0; ) {
            if (!ENTRIES.get(i).present) {
                removeEntryAt(i);
                excess--;
            } else {
                i++;
            }
        }
    }

    private static void removeEntryAt(int index) {
        Entry removed = ENTRIES.remove(index);
        ENTRIES_BY_ID.remove(removed.id);
    }

    private static void clearEntries() {
        ENTRIES.clear();
        ENTRIES_BY_ID.clear();
        presentCount = 0;
        computedThisFrame = false;
    }

    /** ラベル文字列と、文字幅にもとづく背景サイズ・色を求める。 */
    private static void finalizeLabels(Minecraft mc) {
        float scale = labelScale();
        Font font = mc.font;
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            if (!entry.present) {
                continue;
            }
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
        }
    }

    /**
     * 確定済みオフセットを基準位置へ適用し、未配置のラベルだけ新規に空き位置へ確定する。
     * 毎フレーム配置を選び直さないため、カメラ移動・アイテム移動・拾得で位置が入れ替わらない。
     * 生存中の画面外エントリは席を予約しているので、再入場しても重ならない。
     */
    private static void resolveLayout(Minecraft mc) {
        float height = labelHeight(mc);
        float gap = Config.getDroppedItemLabelGap() * labelScale();
        float step = height + gap;

        int n = ENTRIES.size();
        for (int i = 0; i < n; i++) {
            Entry entry = ENTRIES.get(i);
            if (!entry.present) {
                continue;
            }
            if (entry.placed) {
                entry.finalX = entry.x + entry.offX;
                entry.finalY = entry.y + entry.offY;
            } else if (entry.inLayout) {
                layOutEntry(entry, step, gap);
                entry.placed = true;
            }
        }

        // 万一重なった場合の安全網。最小移動で押し離すだけで、配置は選び直さない。
        resolveOverlaps(step, gap);

        // 押し離した結果をオフセットとして保存し、次フレームで元に戻らないようにする
        for (int i = 0; i < n; i++) {
            Entry entry = ENTRIES.get(i);
            if (entry.present && entry.placed) {
                entry.offX = entry.finalX - entry.x;
                entry.offY = entry.finalY - entry.y;
            }
        }
    }

    /**
     * 重なっているラベルを低 ID（先着）優先で最小移動だけ押し離す。画面外の予約席（非表示エントリ）は
     * 動かさず障害物として扱う。ID 順に押すため相対順序は入れ替わらない。
     */
    private static void resolveOverlaps(float step, float gap) {
        int n = ENTRIES.size();
        for (int pass = 0; pass < RESOLVE_PASSES; pass++) {
            boolean changed = false;
            for (int i = 0; i < n; i++) {
                Entry a = ENTRIES.get(i);
                if (!a.present || !a.inLayout || !a.placed) {
                    continue;
                }
                for (int j = 0; j < n; j++) {
                    if (i == j) {
                        continue;
                    }
                    Entry b = ENTRIES.get(j);
                    if (!b.inLayout) {
                        continue;
                    }
                    // 通常ラベル同士は高 ID 側からのみ押す。画面外の予約席（非表示）は常に障害物。
                    if (b.present && j > i) {
                        continue;
                    }
                    float overlapX = (a.width + b.width) / 2.0F + gap - Math.abs(a.finalX - b.finalX);
                    if (overlapX <= 0.0F) {
                        continue;
                    }
                    float overlapY = step - Math.abs(a.finalY - b.finalY);
                    if (overlapY <= 0.0F) {
                        continue;
                    }
                    if (overlapY <= overlapX) {
                        a.finalY += (a.finalY >= b.finalY ? 1.0F : -1.0F) * overlapY;
                    } else {
                        a.finalX += (a.finalX >= b.finalX ? 1.0F : -1.0F) * overlapX;
                    }
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
    }

    /**
     * 基準位置の段、または障害物の段・上下1段を候補とし、各段で障害物の端に密着する最も近い
     * 空き位置を求めて、基準位置に最も近い候補へ置く。新規ラベルの初期配置にのみ使う。
     */
    private static void layOutEntry(Entry entry, float step, float gap) {
        float anchorX = entry.x;
        float anchorY = entry.y;
        float width = entry.width;

        candidateBestDist = Float.MAX_VALUE;
        candidateBestX = anchorX;
        candidateBestY = anchorY;

        considerCandidate(entry, anchorX, anchorY, anchorY, width, gap, step);
        for (int j = 0; j < ENTRIES.size(); j++) {
            Entry obstacle = ENTRIES.get(j);
            if (!isObstacle(entry, obstacle)) {
                continue;
            }
            considerCandidate(entry, anchorX, anchorY, obstacle.finalY, width, gap, step);
            considerCandidate(entry, anchorX, anchorY, obstacle.finalY + step, width, gap, step);
            considerCandidate(entry, anchorX, anchorY, obstacle.finalY - step, width, gap, step);
        }

        entry.finalX = candidateBestX;
        entry.finalY = candidateBestY;
        entry.offX = candidateBestX - anchorX;
        entry.offY = candidateBestY - anchorY;
    }

    /** 配置の障害物か。生存中で席を予約しているラベル（画面内の通常ラベル、画面外の生存エントリ）。 */
    private static boolean isObstacle(Entry entry, Entry obstacle) {
        return obstacle != entry && obstacle.inLayout;
    }

    /** 指定した段 y での最良位置を求め、基準位置に最も近ければ候補を更新する。 */
    private static void considerCandidate(Entry entry, float anchorX, float anchorY, float y,
                                          float width, float gap, float step) {
        float x = resolveX(entry, anchorX, y, width, gap, step);
        float dx = x - anchorX;
        float dy = y - anchorY;
        float dist = dx * dx + dy * dy;
        if (dist < candidateBestDist) {
            candidateBestDist = dist;
            candidateBestX = x;
            candidateBestY = y;
        }
    }

    /**
     * 与えられた段 y に障害物と重ならないよう、基準Xに最も近いXを返す。
     * 障害物を区間として扱い、その端の外側で最も近い空き位置を選ぶ。
     */
    private static float resolveX(Entry entry, float anchorX, float y, float width, float gap, float step) {
        int n = 0;
        for (int j = 0; j < ENTRIES.size(); j++) {
            Entry obstacle = ENTRIES.get(j);
            if (!isObstacle(entry, obstacle) || Math.abs(obstacle.finalY - y) >= step) {
                continue;
            }
            BLOCK_START[n] = obstacle.finalX - obstacle.width / 2.0F - gap;
            BLOCK_END[n] = obstacle.finalX + obstacle.width / 2.0F + gap;
            n++;
        }
        if (n == 0) {
            return anchorX;
        }

        float half = width / 2.0F;
        float best = anchorX;
        float bestDist = Float.MAX_VALUE;
        if (fits(anchorX, half, n)) {
            return anchorX;
        }
        for (int i = 0; i < n; i++) {
            float left = BLOCK_START[i] - half;
            if (fits(left, half, n)) {
                float dist = Math.abs(left - anchorX);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = left;
                }
            }
            float right = BLOCK_END[i] + half;
            if (fits(right, half, n)) {
                float dist = Math.abs(right - anchorX);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = right;
                }
            }
        }
        return best;
    }

    /** 幅 half*2 のラベルを中心 x に置いたとき、障害区間のいずれとも重ならないか。 */
    private static boolean fits(float x, float half, int count) {
        for (int i = 0; i < count; i++) {
            if (x + half > BLOCK_START[i] && x - half < BLOCK_END[i]) {
                return false;
            }
        }
        return true;
    }

    /** 最終描画位置からクリック判定用の画面矩形（GUI座標）を求める。 */
    private static void computeRects(Minecraft mc) {
        float height = labelHeight(mc);
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            if (!entry.present) {
                continue;
            }
            float left = entry.finalX - entry.width / 2.0F;
            float top = entry.finalY - height / 2.0F;
            entry.rectLeft = left;
            entry.rectTop = top;
            entry.rectRight = left + entry.width;
            entry.rectBottom = top + height;
        }
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
        private boolean present;
        private boolean inLayout;
        private boolean placed;
        private float offX;
        private float offY;
        private double anchorWorldX;
        private double anchorWorldY;
        private double anchorWorldZ;
        private float x;
        private float y;
        private float finalX;
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
