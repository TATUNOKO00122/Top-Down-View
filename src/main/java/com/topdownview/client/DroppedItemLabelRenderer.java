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
 * 各ラベルは、接した相手と1グループにまとめ、グループの基準アイテムの画面上位置へ確定した
 * オフセットを足した位置へ置く。グループ内の相対配置は固定なので、カメラ移動や視点変化でも
 * 崩れず入れ替わらない。別グループが触れ合った場合も統合し（位置は維持）、1つの塊として扱う。
 * 落下・投擲・バウンド中は表示せず（接地して静止してから表示・配置）、一度表示したラベルは多少
 * 動いても消さない（点滅させない）。生存中の画面外エントリは席を予約して新規配置を妨げ、拾われた
 * エントリは削除されてその席は新規に使える。グループ間の重なりは最小移動で押し離すが保存しない。
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
    private static final int ROW_ALIGN_RANGE = 3;
    private static final float GROUP_JOIN_EPS = 1.0F;
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
    private static boolean replanRequested = false;
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
            if (replanRequested || ModState.CAMERA.isAnimating() || ModState.CAMERA.isDragging()
                    || ModState.CAMERA.isFreeCameraMode()) {
                // カメラ回転中は毎フレーム配置を解き直す（エントリは保持するので消えない）
                resetLayoutForReplan();
                replanRequested = false;
            }
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
     * 画面外（生存中）のエントリの基準位置をワールド座標から画面へ投影する。基準位置は常に更新する
     * ため、グループの基準が画面外でもグループの相対配置は崩れない。表示対象のものだけ席を予約し、
     * 新規ラベルがその場所を奪わないようにして再入場時の重なりを防ぐ。
     */
    private static void projectReservedSlots(Minecraft mc, Vec3 cameraPos, int guiWidth, int guiHeight) {
        float height = labelHeight(mc);
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            if (entry.present) {
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

            // 基準位置はグループの基準として常に更新する
            entry.x = (ndcX * 0.5F + 0.5F) * guiWidth;
            entry.y = (0.5F - ndcY * 0.5F) * guiHeight - height / 2.0F - ANCHOR_GAP * labelScale();

            // 席の予約（障害物扱い）は、カリングされておらず範囲内で静止しているアイテムのみ
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
            entry.inLayout = true;
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
        reassignGroup(removed);
    }

    /**
     * グループの基準が消えたとき、残りのうち（表示中を優先して）最小 ID を新しい基準にし、
     * 各オフセットを補正する。補正により各ラベルの絶対位置は変わらない。
     */
    private static void reassignGroup(Entry removed) {
        Entry newBase = null;
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            if (entry.groupId != removed.id) {
                continue;
            }
            if (newBase == null || (entry.present && !newBase.present)
                    || (entry.present == newBase.present && entry.id < newBase.id)) {
                newBase = entry;
            }
        }
        if (newBase == null) {
            return;
        }
        float shiftX = removed.x - newBase.x;
        float shiftY = removed.y - newBase.y;
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            if (entry.groupId == removed.id) {
                entry.groupId = newBase.id;
                entry.offX += shiftX;
                entry.offY += shiftY;
            }
        }
    }

    private static void clearEntries() {
        ENTRIES.clear();
        ENTRIES_BY_ID.clear();
        presentCount = 0;
        computedThisFrame = false;
    }

    /** 全ラベルを破棄して次フレームで再配置させる（再生成キー用）。 */
    public static void regenerate() {
        clearEntries();
    }

    /** 次の描画で配置を解き直させる（回転の即時整列など、アニメーションを伴わない回転用）。 */
    public static void requestReplan() {
        replanRequested = true;
    }

    /** エントリは保持したまま、配置だけを未確定に戻して解き直させる。 */
    private static void resetLayoutForReplan() {
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            if (!entry.present) {
                continue;
            }
            entry.placed = false;
            entry.groupId = 0;
            entry.offX = 0.0F;
            entry.offY = 0.0F;
        }
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
     * 各ラベルは、接した相手と1グループにまとめ、グループの基準アイテムの画面上位置へ確定した
     * オフセットを足した位置へ置く。グループ内の相対配置は固定なので、どの視点から見ても崩れない。
     * 未配置のラベルだけ新規に空き位置へ確定する。
     */
    private static void resolveLayout(Minecraft mc) {
        float height = labelHeight(mc);
        float gap = Config.getDroppedItemLabelGap() * labelScale();
        float step = height + gap;

        int n = ENTRIES.size();

        // 1) 配置済みはグループ基準＋オフセット。未配置は自分の位置を暫定にする
        for (int i = 0; i < n; i++) {
            Entry entry = ENTRIES.get(i);
            if (!entry.inLayout) {
                continue;
            }
            if (entry.placed) {
                entry.finalX = baseAnchorX(entry) + entry.offX;
                entry.finalY = baseAnchorY(entry) + entry.offY;
            } else {
                entry.finalX = entry.x;
                entry.finalY = entry.y;
            }
        }

        // 2) 未配置のラベルを空き位置へ置き、接した相手のグループへ参加させる
        for (int i = 0; i < n; i++) {
            Entry entry = ENTRIES.get(i);
            if (!entry.present || entry.placed || !entry.inLayout) {
                continue;
            }
            layOutEntry(entry, step, gap);
            entry.placed = true;
        }

        // 3) 別グループが触れ合ったら1つにまとめる（位置は維持し、入れ替えはしない）
        mergeTouchingGroups(step, gap);

        // 4) グループ間で重なった場合の一時的な押し離し（保存せず毎フレーム計算）
        resolveOverlaps(step, gap);
    }

    /**
     * 触れ合っている別グループを1つに統合する。オフセットを基準アイテムの差で補正するため、
     * 各ラベルの絶対位置は変わらない（＝入れ替えは起きない）。
     */
    private static void mergeTouchingGroups(float step, float gap) {
        int n = ENTRIES.size();
        for (int guard = 0; guard < n; guard++) {
            boolean merged = false;
            for (int i = 0; i < n && !merged; i++) {
                Entry a = ENTRIES.get(i);
                if (!a.inLayout || !a.placed) {
                    continue;
                }
                for (int j = i + 1; j < n; j++) {
                    Entry b = ENTRIES.get(j);
                    if (!b.inLayout || !b.placed || a.groupId == b.groupId) {
                        continue;
                    }
                    if (!rectsTouch(a, b, step, gap)) {
                        continue;
                    }
                    Entry baseA = ENTRIES_BY_ID.get(a.groupId);
                    Entry baseB = ENTRIES_BY_ID.get(b.groupId);
                    if (baseA == null || baseB == null) {
                        continue;
                    }
                    if (baseA.id <= baseB.id) {
                        mergeGroups(baseA, baseB);
                    } else {
                        mergeGroups(baseB, baseA);
                    }
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                break;
            }
        }
    }

    /** dropBase のグループを keepBase のグループへ統合する。絶対位置が変わらないようオフセットを補正。 */
    private static void mergeGroups(Entry keepBase, Entry dropBase) {
        float dx = dropBase.x - keepBase.x;
        float dy = dropBase.y - keepBase.y;
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            if (entry.groupId == dropBase.id) {
                entry.groupId = keepBase.id;
                entry.offX += dx;
                entry.offY += dy;
                entry.finalX = keepBase.x + entry.offX;
                entry.finalY = keepBase.y + entry.offY;
            }
        }
    }

    /** 2つのラベル矩形が触れ合っている（端の隙間が gap 以内）か。 */
    private static boolean rectsTouch(Entry a, Entry b, float step, float gap) {
        float height = step - gap;
        float hGap = Math.abs(a.finalX - b.finalX) - (a.width + b.width) / 2.0F;
        float vGap = Math.abs(a.finalY - b.finalY) - height;
        return Math.max(hGap, vGap) <= gap + GROUP_JOIN_EPS;
    }

    /** グループの基準アイテムの画面上位置X。基準が無ければ自分の位置。 */
    private static float baseAnchorX(Entry entry) {
        Entry base = ENTRIES_BY_ID.get(entry.groupId);
        return base != null ? base.x : entry.x;
    }

    /** グループの基準アイテムの画面上位置Y。基準が無ければ自分の位置。 */
    private static float baseAnchorY(Entry entry) {
        Entry base = ENTRIES_BY_ID.get(entry.groupId);
        return base != null ? base.y : entry.y;
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
     * 基準位置の段、または障害物の段（±複数段）を候補とし、各段で障害物の端に密着する最も近い
     * 空き位置を求めて、基準位置に最も近い候補へ置く。接した相手がいればそのグループへ参加し、
     * 位置はグループ基準からのオフセットとして保存する。新規ラベルの初期配置にのみ使う。
     */
    private static void layOutEntry(Entry entry, float step, float gap) {
        float anchorX = entry.x;
        float anchorY = entry.y;
        float width = entry.width;

        candidateBestDist = Float.MAX_VALUE;
        candidateBestX = anchorX;
        candidateBestY = anchorY;

        considerCandidate(entry, anchorX, anchorX, anchorY, anchorY, width, gap, step);
        for (int j = 0; j < ENTRIES.size(); j++) {
            Entry obstacle = ENTRIES.get(j);
            if (!isObstacle(entry, obstacle)) {
                continue;
            }
            for (int k = -ROW_ALIGN_RANGE; k <= ROW_ALIGN_RANGE; k++) {
                // 障害物のXに揃えて密着配置する候補（列が揃い隙間が出にくい）
                considerCandidate(entry, obstacle.finalX, anchorX, anchorY,
                        obstacle.finalY + k * step, width, gap, step);
                // 自分のXでその段に置く候補
                considerCandidate(entry, anchorX, anchorX, anchorY,
                        obstacle.finalY + k * step, width, gap, step);
            }
        }

        float placedX = candidateBestX;
        float placedY = candidateBestY;

        // 密着した相手がいればそのグループへ参加する（＝くっついた分を1つの塊として固定）
        Entry neighbor = findTouchingNeighbor(entry, placedX, placedY, step, gap);
        entry.groupId = neighbor != null ? neighbor.groupId : entry.id;

        Entry base = ENTRIES_BY_ID.get(entry.groupId);
        float baseX = (base != null && base.inLayout) ? base.x : anchorX;
        float baseY = (base != null && base.inLayout) ? base.y : anchorY;
        entry.offX = placedX - baseX;
        entry.offY = placedY - baseY;
        entry.finalX = placedX;
        entry.finalY = placedY;
    }

    /** 配置したラベルに密着している（端の隙間が gap 以内の）障害物を返す。無ければ null。 */
    private static Entry findTouchingNeighbor(Entry entry, float x, float y, float step, float gap) {
        float height = step - gap;
        Entry best = null;
        float bestSep = gap + GROUP_JOIN_EPS;
        for (int j = 0; j < ENTRIES.size(); j++) {
            Entry obstacle = ENTRIES.get(j);
            if (!isObstacle(entry, obstacle)) {
                continue;
            }
            float hGap = Math.abs(x - obstacle.finalX) - (entry.width + obstacle.width) / 2.0F;
            float vGap = Math.abs(y - obstacle.finalY) - height;
            float sep = Math.max(Math.max(hGap, vGap), 0.0F);
            if (sep < bestSep) {
                bestSep = sep;
                best = obstacle;
            }
        }
        return best;
    }

    /** 配置の障害物か。生存中で席を予約している配置済みラベル。 */
    private static boolean isObstacle(Entry entry, Entry obstacle) {
        return obstacle != entry && obstacle.inLayout && obstacle.placed;
    }

    /**
     * 指定した段 y での最良位置を求める。配置は基準X(refX)に最も近い空き位置とし、良し悪しは
     * 自分の基準位置(anchorX, anchorY)からの距離で評価する。障害物のXを refX にすると列が揃う。
     */
    private static void considerCandidate(Entry entry, float refX, float anchorX, float anchorY, float y,
                                          float width, float gap, float step) {
        float x = resolveX(entry, refX, y, width, gap, step);
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
        private int groupId;
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
