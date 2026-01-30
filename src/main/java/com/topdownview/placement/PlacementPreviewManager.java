package com.topdownview.placement;

import com.mojang.logging.LogUtils;
import com.topdownview.Config;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ブロック配置プレビューのTickハンドラ。
 *
 * クライアントTickごとに視点先ブロックを解析し、
 * FakeBlockGetter 上でアイテム使用をシミュレートして
 * 配置予定ブロックの情報を収集する。
 *
 * トップダウンモード中のみ動作する。
 */
public final class PlacementPreviewManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final PlacementPreviewManager INSTANCE = new PlacementPreviewManager();

    private final Minecraft mc = Minecraft.getInstance();

    /** シミュレーション用プロキシワールド（null = 未初期化） */
    private FakeBlockGetter fakeBlockGetter;

    /** 前回の計算結果（描画スレッドへ渡す） */
    private final List<PlacementEntry> entries = new ArrayList<>();

    /** 差分計算用: 前回の視点先情報 */
    private BlockPos lastPos = null;
    private Direction lastSide = null;
    private Vec3 lastHitVec = null;
    private Direction lastPlacementFacing = null;

    /** セッション中に例外が出たアイテムのブラックリスト */
    private final Set<net.minecraft.resources.ResourceLocation> blacklistedItems = new HashSet<>();

    private PlacementPreviewManager() {}

    public static PlacementPreviewManager getInstance() {
        return INSTANCE;
    }

    /**
     * ClientTickEvent.END で毎tick呼び出す
     */
    public void onClientTick() {
        // 機能が無効、またはトップダウンモード以外は何もしない
        if (!Config.isPlacementPreviewEnabled() || !ModState.STATUS.isEnabled()) {
            if (!entries.isEmpty()) {
                entries.clear();
            }
            lastPos = null;
            return;
        }

        Level level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            entries.clear();
            lastPos = null;
            return;
        }

        // 視点先ブロックのHitResultを取得
        if (!(mc.hitResult instanceof BlockHitResult hitResult)) {
            entries.clear();
            lastPos = null;
            return;
        }

        // 手持ちアイテムがブロックアイテムでなければスキップ（オフハンドはプレビュー対象外）
        ItemStack mainHand = player.getMainHandItem();
        if (!isPlaceableItem(mainHand)) {
            entries.clear();
            lastPos = null;
            return;
        }

        BlockPos hitPos = hitResult.getBlockPos();
        Direction hitSide = hitResult.getDirection();
        Vec3 hitVec = hitResult.getLocation();

        // 配置方向指定の現在値を取得
        Direction currentPlacementFacing = ModState.PLACEMENT_ROTATION.hasOverride()
                ? ModState.PLACEMENT_ROTATION.getCurrentFacing() : null;

        // 視点・持ち物・配置方向が変化していなければ再計算不要
        boolean changed = !hitPos.equals(lastPos)
                || hitSide != lastSide
                || (lastHitVec != null && !hitVec.equals(lastHitVec))
                || hasItemChanged(player)
                || currentPlacementFacing != lastPlacementFacing;

        if (!changed) {
            return;
        }

        lastPos = hitPos;
        lastSide = hitSide;
        lastHitVec = hitVec;
        lastPlacementFacing = currentPlacementFacing;

        // 周辺ブロックをFakeBlockGetterにコピーして配置シミュレーション
        updateEntries(level, player, hitResult, mainHand);
    }

    /**
     * アイテム使用をシミュレートし、変更されたブロックをentriesに記録する。
     */
    private void updateEntries(Level level, LocalPlayer player,
                               BlockHitResult hitResult, ItemStack mainHand) {
        if (fakeBlockGetter == null) {
            fakeBlockGetter = new FakeBlockGetter(level);
        } else {
            fakeBlockGetter.clear();
        }

        // 半径分の実ブロックをコピー（FakeBlockGetterはdelegateから読むので不要）
        // → FakeBlockGetter は実ワールドに委譲するため、コピー不要

        // メインハンドで試行（オフハンドはプレビュー対象外）
        if (isPlaceableItem(mainHand)) {
            trySimulatePlacement(level, player, hitResult, mainHand, InteractionHand.MAIN_HAND);
        }

        // 変更位置をentriesに変換
        entries.clear();
        for (BlockPos pos : fakeBlockGetter.getChangedPositions()) {
            BlockState realState = level.getBlockState(pos);
            BlockState fakeState = fakeBlockGetter.getBlockState(pos);
            if (!fakeState.isAir() && !fakeState.equals(realState)) {
                entries.add(new PlacementEntry(pos, fakeState));
            }
        }
    }

    /**
     * 指定アイテム・手でブロック配置をシミュレートする。
     * 実際のブロックをsetBlockするのではなく、FakeBlockGetterに書き込む。
     *
     * @return 配置成功した場合 true
     */
    private boolean trySimulatePlacement(Level level, LocalPlayer player,
                                          BlockHitResult hitResult, ItemStack stack, InteractionHand hand) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }

        ResourceLocation regName = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (blacklistedItems.contains(regName)) {
            return false;
        }

        BlockPos targetPos = hitResult.getBlockPos().relative(hitResult.getDirection());

        try {
            // BlockPlaceContext を使って配置後の BlockState を取得
            UseOnContext useCtx = new UseOnContext(level, player, hand, stack, hitResult);
            BlockPlaceContext placeCtx = new BlockPlaceContext(useCtx);

            if (!placeCtx.canPlace()) {
                return false;
            }

            // BlockPlaceContext.getClickedPos() はスラブ統合などの場合に
            // クリックしたブロック自身の位置を返す（canBeReplaced考慮済み）
            BlockPos actualPos = placeCtx.getClickedPos();

            BlockState stateToBePlaced = blockItem.getBlock().getStateForPlacement(placeCtx);
            if (stateToBePlaced == null || stateToBePlaced.isAir()) {
                return false;
            }

            // 配置方向手動指定が有効なら向きを反映
            stateToBePlaced = applyPlacementRotation(stateToBePlaced, placeCtx);

            fakeBlockGetter.setFakeBlock(actualPos, stateToBePlaced);
            handleMultiBlockPlacement(actualPos, stateToBePlaced);
            return true;

        } catch (Throwable t) {
            LOGGER.debug("[PlacementPreview] シミュレーション中に例外: item={}, {}", regName, t.getMessage());
            blacklistedItems.add(regName);
            return false;
        }
    }

    /**
     * アイテムがブロック配置可能かチェック
     */
    private boolean isPlaceableItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem;
    }

    /**
     * 配置方向手動指定が有効な場合、またはクリック位置配置が有効な場合、
     * BlockState の向き関連プロパティを差し替える。
     */
    private BlockState applyPlacementRotation(BlockState state, BlockPlaceContext context) {
        if (ModState.PLACEMENT_ROTATION.hasOverride()) {
            Direction facing = ModState.PLACEMENT_ROTATION.getCurrentFacing();
            return PlacementHandler.applyFacing(state, facing);
        }

        if (Config.isClickPositionPlacementEnabled() && ModState.STATUS.isEnabled()) {
            // 看板や松明など、すでにバニラでクリック面に沿って配向されているブロックは上書きしない
            if (!PlacementHandler.isAlreadyAlignedToFace(state, context.getClickedFace())) {
                Direction calculated = PlacementHandler.calculateClickPositionFacing(context);
                if (calculated != null) {
                    return PlacementHandler.applyFacing(state, calculated);
                }
            }
        }
        return state;
    }

    /**
     * プレイヤーの手持ちアイテムが前回から変化したか
     * （毎tick の再計算を回避するための簡易チェック）
     */
    private ItemStack lastMain = ItemStack.EMPTY;

    private boolean hasItemChanged(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        boolean changed = !ItemStack.isSameItem(main, lastMain);
        lastMain = main.copy();
        return changed;
    }

    /**
     * 描画スレッドから呼び出す: 現在の配置エントリ一覧を返す
     */
    public List<PlacementEntry> getEntries() {
        return entries;
    }

    /**
     * ワールドロードアウト時のクリーンアップ
     */
    public void reset() {
        entries.clear();
        if (fakeBlockGetter != null) {
            fakeBlockGetter.clear();
        }
        lastPos = null;
        lastSide = null;
        lastHitVec = null;
        lastPlacementFacing = null;
        lastMain = ItemStack.EMPTY;
    }

    /**
     * ドアやベッドなどのマルチブロック構成ブロックの追加パーツを配置シミュレーションに追加する
     */
    private void handleMultiBlockPlacement(BlockPos targetPos, BlockState state) {
        // 1. ドアや背の高い植物などの上下2マスのブロック
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
            if (half == DoubleBlockHalf.LOWER) {
                BlockPos upperPos = targetPos.above();
                BlockState upperState = state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
                fakeBlockGetter.setFakeBlock(upperPos, upperState);
            } else {
                BlockPos lowerPos = targetPos.below();
                BlockState lowerState = state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
                fakeBlockGetter.setFakeBlock(lowerPos, lowerState);
            }
        }

        // 2. ベッドなどの前後2マスのブロック
        if (state.hasProperty(BlockStateProperties.BED_PART) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            BedPart part = state.getValue(BlockStateProperties.BED_PART);
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            if (part == BedPart.FOOT) {
                BlockPos headPos = targetPos.relative(facing);
                BlockState headState = state.setValue(BlockStateProperties.BED_PART, BedPart.HEAD);
                fakeBlockGetter.setFakeBlock(headPos, headState);
            } else {
                BlockPos footPos = targetPos.relative(facing.getOpposite());
                BlockState footState = state.setValue(BlockStateProperties.BED_PART, BedPart.FOOT);
                fakeBlockGetter.setFakeBlock(footPos, footState);
            }
        }
    }

    // ==================== 内部データクラス ====================

    /**
     * 配置予定ブロックの情報
     */
    public record PlacementEntry(BlockPos pos, BlockState state) {}
}
