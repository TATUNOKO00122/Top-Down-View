package com.topdownview.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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

        if (block instanceof DoorBlock) return true;
        if (block instanceof TrapDoorBlock) return true;
        if (block instanceof FenceGateBlock) return true;
        if (block instanceof ButtonBlock) return true;
        if (block instanceof LeverBlock) return true;
        if (block instanceof PressurePlateBlock) return true;
        if (block instanceof WeightedPressurePlateBlock) return true;

        if (block instanceof ChestBlock) return true;
        if (block instanceof EnderChestBlock) return true;
        if (block instanceof BarrelBlock) return true;
        if (block instanceof ShulkerBoxBlock) return true;
        if (block instanceof HopperBlock) return true;
        if (block instanceof DispenserBlock) return true;
        if (block instanceof DropperBlock) return true;

        if (block instanceof FurnaceBlock) return true;
        if (block instanceof BlastFurnaceBlock) return true;
        if (block instanceof SmokerBlock) return true;
        if (block instanceof BrewingStandBlock) return true;
        if (block instanceof BeaconBlock) return true;
        if (block instanceof AnvilBlock) return true;
        if (block instanceof SmithingTableBlock) return true;
        if (block instanceof GrindstoneBlock) return true;
        if (block instanceof StonecutterBlock) return true;
        if (block instanceof CartographyTableBlock) return true;
        if (block instanceof LoomBlock) return true;

        if (block instanceof BedBlock) return true;
        if (block instanceof CraftingTableBlock) return true;
        if (block instanceof EnchantmentTableBlock) return true;
        if (block instanceof FlowerPotBlock) return true;
        if (block instanceof NoteBlock) return true;
        if (block instanceof JukeboxBlock) return true;
        if (block instanceof BellBlock) return true;
        if (block instanceof CampfireBlock) return true;
        if (block instanceof ComposterBlock) return true;
        if (block instanceof LecternBlock) return true;
        if (block instanceof RespawnAnchorBlock) return true;

        if (block instanceof LadderBlock) return true;
        if (block instanceof StandingSignBlock) return true;
        if (block instanceof WallSignBlock) return true;
        if (block instanceof CeilingHangingSignBlock) return true;
        if (block instanceof WallHangingSignBlock) return true;

        return false;
    }
}
