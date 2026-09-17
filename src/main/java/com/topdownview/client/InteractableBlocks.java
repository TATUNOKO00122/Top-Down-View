package com.topdownview.client;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * ブロックのインタラクト可否と操作種別を判定する。
 * プロンプト表示・カリング保護・クリック分岐はすべてこのクラスを単一情報源とする。
 *
 * 判定は Mod 追加ブロックを漏れなく拾うため、次の順でフォールバックする:
 * バニラ基底クラス instanceof → GUI の MenuProvider → バニラタグ → BlockState プロパティ。
 */
public final class InteractableBlocks {

    public enum InteractionKind {
        NONE,
        OPEN_CLOSE, // ドア・トラップドア・フェンスゲート
        TOGGLE,     // ボタン・レバー・レッドストーン素子
        SLEEP,      // ベッド
        OPEN,       // チェスト系コンテナ（「開く」）
        USE,        // GUI 持ち（作業台・かまど等）
        INTERACT    // その他の右クリック操作（鐘・ケーキ等）
    }

    private InteractableBlocks() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    // ==================== プロンプト用分類 ====================

    /**
     * ブロックの右クリック操作種別を判定する。MenuProvider の取得に {@link Level} を要する。
     */
    public static InteractionKind classify(BlockState state, Level level, BlockPos pos) {
        if (state == null || level == null || pos == null || state.isAir()) {
            return InteractionKind.NONE;
        }

        InteractionKind byBlock = classifyByBlock(state);
        if (byBlock != InteractionKind.NONE) {
            return byBlock;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);

        // バニラ基底を継承しない MOD 産コンテナも、ブロックエンティティが
        // インベントリ（Container）なら「開く」として扱う。
        // かまど・ホッパー等の既知ユーティリティは保護対象なので除外し、USE に委ねる。
        if (blockEntity instanceof Container && !isProtectionOnlyBlock(state)) {
            return InteractionKind.OPEN;
        }

        // GUI 持ち: BaseEntityBlock 以外の独自実装も含めてジェネリックに検出
        if (blockEntity instanceof MenuProvider || state.getMenuProvider(level, pos) != null) {
            return InteractionKind.USE;
        }

        return classifyByProperty(state);
    }

    /**
     * カリング保護対象か判定する。プロンプト対象に加え、
     * 昇降・装飾・レッドストーン等の可視保護が必要なブロックも含む。
     */
    public static boolean isInteractable(BlockState state, BlockGetter level, BlockPos pos) {
        if (state == null || level == null || pos == null) return false;

        if (classifyByBlock(state) != InteractionKind.NONE) return true;

        Block block = state.getBlock();
        if (block instanceof BaseEntityBlock) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof MenuProvider || blockEntity instanceof Container) return true;
        }

        if (classifyByProperty(state) != InteractionKind.NONE) return true;

        return isProtectionOnlyBlock(state);
    }

    // ==================== 判定ヘルパー ====================

    /**
     * クラス・タグで解決できる操作種別を返す（Level 不要）。
     */
    private static InteractionKind classifyByBlock(BlockState state) {
        Block block = state.getBlock();

        if (block instanceof DoorBlock || state.is(BlockTags.DOORS)
                || block instanceof TrapDoorBlock || state.is(BlockTags.TRAPDOORS)
                || block instanceof FenceGateBlock || state.is(BlockTags.FENCE_GATES)) {
            return InteractionKind.OPEN_CLOSE;
        }

        if (block instanceof ButtonBlock || state.is(BlockTags.BUTTONS)
                || block instanceof LeverBlock
                || block instanceof RepeaterBlock
                || block instanceof ComparatorBlock) {
            return InteractionKind.TOGGLE;
        }

        if (block instanceof BedBlock || state.is(BlockTags.BEDS)) {
            return InteractionKind.SLEEP;
        }

        if (isOpenContainer(block)) {
            return InteractionKind.OPEN;
        }

        if (block instanceof BellBlock
                || block instanceof CakeBlock
                || block instanceof JukeboxBlock
                || block instanceof NoteBlock) {
            return InteractionKind.INTERACT;
        }

        return InteractionKind.NONE;
    }

    /**
     * バニラ基底を継承しない Mod ブロックを標準プロパティで拾うヒューリスティック。
     * OPEN は開閉系、FACING+POWERED はボタン/レバー系にほぼ固有（オブザーバーのみ除外）。
     */
    private static InteractionKind classifyByProperty(BlockState state) {
        if (state.hasProperty(BlockStateProperties.OPEN)) {
            return InteractionKind.OPEN_CLOSE;
        }
        if (state.hasProperty(BlockStateProperties.POWERED)
                && state.hasProperty(BlockStateProperties.FACING)
                && !(state.getBlock() instanceof ObserverBlock)) {
            return InteractionKind.TOGGLE;
        }
        return InteractionKind.NONE;
    }

    private static boolean isOpenContainer(Block block) {
        return block instanceof AbstractChestBlock
                || block instanceof BarrelBlock
                || block instanceof ShulkerBoxBlock
                || block instanceof EnderChestBlock;
    }

    /**
     * 右クリック操作は持たないがカリング保護が必要なブロック（圧力板・はしご・足場・植木鉢等）。
     */
    private static boolean isProtectionOnlyBlock(BlockState state) {
        Block block = state.getBlock();
        return block instanceof PressurePlateBlock
                || block instanceof HopperBlock
                || block instanceof DispenserBlock
                || block instanceof AbstractFurnaceBlock
                || block instanceof BrewingStandBlock
                || block instanceof BeaconBlock
                || block instanceof AnvilBlock
                || block instanceof SmithingTableBlock
                || block instanceof GrindstoneBlock
                || block instanceof StonecutterBlock
                || block instanceof CartographyTableBlock
                || block instanceof LoomBlock
                || block instanceof CraftingTableBlock
                || block instanceof EnchantmentTableBlock
                || block instanceof CampfireBlock
                || block instanceof ComposterBlock
                || block instanceof LecternBlock
                || block instanceof RespawnAnchorBlock
                || block instanceof LadderBlock
                || block instanceof ScaffoldingBlock
                || block instanceof FlowerPotBlock;
    }
}
