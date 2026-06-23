package com.topdownview.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

public final class InteractableBlocks {



    private InteractableBlocks() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static boolean isInteractable(BlockState state, Level level, BlockPos pos) {
        if (state == null || level == null || pos == null) return false;

        Block block = state.getBlock();

        if (block instanceof BaseEntityBlock) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof MenuProvider) return true;
        }

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
