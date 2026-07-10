package com.topdownview.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class InteractableBlocks {

    private InteractableBlocks() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /**
     * インタラクト可能ブロックか判定する。
     * コンテナ系（チェスト・かまど等）は BlockEntity のインターフェースでジェネリック判定し、
     * Mod追加ブロックも漏れなく検出する。非コンテナ系（ドア・はしご等）は vanilla instanceof ホワイトリストで補完する。
     */
    public static boolean isInteractable(BlockState state, BlockGetter level, BlockPos pos) {
        if (state == null || level == null || pos == null) return false;

        Block block = state.getBlock();

        // コンテナ系: BaseEntityBlock で BlockEntity が MenuProvider または Container を実装していれば検出
        // MenuProvider: GUI付きコンテナ（チェスト・かまど・作業台等）
        // Container: インベントリ付きブロック（Mod追加のコンテナも含む）
        if (block instanceof BaseEntityBlock) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof MenuProvider || blockEntity instanceof Container) return true;
        }

        // 非コンテナ系: vanilla instanceof ホワイトリスト（ドア・はしご・ボタン等）
        if (isInteractableSimple(state)) return true;

        return false;
    }

    public static boolean isInteractableSimple(BlockState state) {
        if (state == null) return false;

        Block block = state.getBlock();

        // クラス判定(instanceof)による漏れのないインタラクト・昇降可能ブロック判定
        if (block instanceof DoorBlock
                || block instanceof FenceGateBlock
                || block instanceof ButtonBlock
                || block instanceof PressurePlateBlock
                || block instanceof AbstractChestBlock
                || block instanceof BarrelBlock
                || block instanceof ShulkerBoxBlock
                || block instanceof HopperBlock
                || block instanceof DispenserBlock // DropperBlockも含む
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
                || block instanceof NoteBlock
                || block instanceof JukeboxBlock
                || block instanceof BellBlock
                || block instanceof CampfireBlock
                || block instanceof ComposterBlock
                || block instanceof LecternBlock
                || block instanceof RespawnAnchorBlock
                || block instanceof LadderBlock
                || block instanceof ScaffoldingBlock // 足場をカリング保護対象として追加
                || block instanceof LeverBlock // レバー
                || block instanceof BedBlock
                || block instanceof FlowerPotBlock) {
            return true;
        }

        return false;
    }
}
