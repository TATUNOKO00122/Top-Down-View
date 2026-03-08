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

    private static final Set<Block> INTERACTABLE_BLOCKS = new HashSet<>();

    static {
        INTERACTABLE_BLOCKS.addAll(Set.of(
                Blocks.OAK_DOOR, Blocks.IRON_DOOR,
                Blocks.DARK_OAK_DOOR, Blocks.ACACIA_DOOR, Blocks.BIRCH_DOOR,
                Blocks.JUNGLE_DOOR, Blocks.SPRUCE_DOOR, Blocks.MANGROVE_DOOR,
                Blocks.CRIMSON_DOOR, Blocks.WARPED_DOOR,
                Blocks.OAK_FENCE_GATE, Blocks.DARK_OAK_FENCE_GATE, Blocks.ACACIA_FENCE_GATE,
                Blocks.BIRCH_FENCE_GATE, Blocks.JUNGLE_FENCE_GATE, Blocks.SPRUCE_FENCE_GATE,
                Blocks.MANGROVE_FENCE_GATE, Blocks.CRIMSON_FENCE_GATE, Blocks.WARPED_FENCE_GATE,
                Blocks.STONE_BUTTON, Blocks.OAK_BUTTON, Blocks.SPRUCE_BUTTON, Blocks.BIRCH_BUTTON,
                Blocks.JUNGLE_BUTTON, Blocks.DARK_OAK_BUTTON, Blocks.ACACIA_BUTTON, Blocks.MANGROVE_BUTTON,
                Blocks.CRIMSON_BUTTON, Blocks.WARPED_BUTTON, Blocks.POLISHED_BLACKSTONE_BUTTON,
                Blocks.LEVER,
                Blocks.STONE_PRESSURE_PLATE, Blocks.OAK_PRESSURE_PLATE, Blocks.SPRUCE_PRESSURE_PLATE,
                Blocks.BIRCH_PRESSURE_PLATE, Blocks.JUNGLE_PRESSURE_PLATE, Blocks.DARK_OAK_PRESSURE_PLATE,
                Blocks.ACACIA_PRESSURE_PLATE, Blocks.MANGROVE_PRESSURE_PLATE,
                Blocks.CRIMSON_PRESSURE_PLATE, Blocks.WARPED_PRESSURE_PLATE,
                Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE,
                Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE,
                Blocks.CHEST, Blocks.TRAPPED_CHEST,
                Blocks.ENDER_CHEST,
                Blocks.BARREL,
                Blocks.SHULKER_BOX, Blocks.BLACK_SHULKER_BOX, Blocks.BLUE_SHULKER_BOX,
                Blocks.BROWN_SHULKER_BOX, Blocks.CYAN_SHULKER_BOX, Blocks.GRAY_SHULKER_BOX,
                Blocks.GREEN_SHULKER_BOX, Blocks.LIGHT_BLUE_SHULKER_BOX, Blocks.LIGHT_GRAY_SHULKER_BOX,
                Blocks.LIME_SHULKER_BOX, Blocks.MAGENTA_SHULKER_BOX, Blocks.ORANGE_SHULKER_BOX,
                Blocks.PINK_SHULKER_BOX, Blocks.PURPLE_SHULKER_BOX, Blocks.RED_SHULKER_BOX,
                Blocks.WHITE_SHULKER_BOX, Blocks.YELLOW_SHULKER_BOX,
                Blocks.HOPPER,
                Blocks.DISPENSER, Blocks.DROPPER,
                Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER,
                Blocks.BREWING_STAND,
                Blocks.BEACON,
                Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL,
                Blocks.SMITHING_TABLE,
                Blocks.GRINDSTONE,
                Blocks.STONECUTTER,
                Blocks.CARTOGRAPHY_TABLE,
                Blocks.LOOM,
                Blocks.CRAFTING_TABLE,
                Blocks.ENCHANTING_TABLE,
                Blocks.NOTE_BLOCK,
                Blocks.JUKEBOX,
                Blocks.BELL,
                Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE,
                Blocks.COMPOSTER,
                Blocks.LECTERN,
                Blocks.RESPAWN_ANCHOR,
                Blocks.LADDER,
                Blocks.OAK_SIGN, Blocks.SPRUCE_SIGN, Blocks.BIRCH_SIGN, Blocks.JUNGLE_SIGN,
                Blocks.ACACIA_SIGN, Blocks.DARK_OAK_SIGN, Blocks.MANGROVE_SIGN,
                Blocks.CRIMSON_SIGN, Blocks.WARPED_SIGN,
                Blocks.OAK_WALL_SIGN, Blocks.SPRUCE_WALL_SIGN, Blocks.BIRCH_WALL_SIGN,
                Blocks.JUNGLE_WALL_SIGN, Blocks.ACACIA_WALL_SIGN, Blocks.DARK_OAK_WALL_SIGN,
                Blocks.MANGROVE_WALL_SIGN, Blocks.CRIMSON_WALL_SIGN, Blocks.WARPED_WALL_SIGN,
                Blocks.OAK_HANGING_SIGN, Blocks.SPRUCE_HANGING_SIGN, Blocks.BIRCH_HANGING_SIGN,
                Blocks.JUNGLE_HANGING_SIGN, Blocks.ACACIA_HANGING_SIGN, Blocks.DARK_OAK_HANGING_SIGN,
                Blocks.MANGROVE_HANGING_SIGN, Blocks.CRIMSON_HANGING_SIGN, Blocks.WARPED_HANGING_SIGN,
                Blocks.OAK_WALL_HANGING_SIGN, Blocks.SPRUCE_WALL_HANGING_SIGN, Blocks.BIRCH_WALL_HANGING_SIGN,
                Blocks.JUNGLE_WALL_HANGING_SIGN, Blocks.ACACIA_WALL_HANGING_SIGN, Blocks.DARK_OAK_WALL_HANGING_SIGN,
                Blocks.MANGROVE_WALL_HANGING_SIGN, Blocks.CRIMSON_WALL_HANGING_SIGN, Blocks.WARPED_WALL_HANGING_SIGN
        ));
    }

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

        if (INTERACTABLE_BLOCKS.contains(block)) return true;

        if (block instanceof BedBlock) return true;
        if (block instanceof FlowerPotBlock) return true;
        if (block instanceof CampfireBlock) return true;

        return false;
    }
}
