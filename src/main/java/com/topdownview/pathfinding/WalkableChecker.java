package com.topdownview.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class WalkableChecker {
    
    private static final double MAX_STEP_HEIGHT = 1.0;
    private static final double MIN_PASSAGE_HEIGHT = 2.0;
    
    private WalkableChecker() {
        throw new IllegalStateException("ユーティリティクラス");
    }
    
    public static boolean isWalkable(BlockGetter level, BlockPos pos) {
        return isWalkable(level, pos.getX(), pos.getY(), pos.getZ());
    }
    
    public static boolean isWalkable(BlockGetter level, int x, int y, int z) {
        BlockPos feetPos = new BlockPos(x, y, z);
        BlockPos headPos = feetPos.above();
        BlockPos groundPos = feetPos.below();
        
        BlockState feetState = level.getBlockState(feetPos);
        BlockState headState = level.getBlockState(headPos);
        BlockState groundState = level.getBlockState(groundPos);
        
        if (isDangerous(feetState) || isDangerous(headState)) {
            return false;
        }
        
        if (!hasSolidGround(groundState, level, groundPos)) {
            return false;
        }
        
        if (!isPassable(feetState, level, feetPos) || !isPassable(headState, level, headPos)) {
            return false;
        }
        
        return true;
    }
    
    public static boolean canStandAt(BlockGetter level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        
        if (belowState.isAir()) {
            return false;
        }
        
        VoxelShape collision = belowState.getCollisionShape(level, below);
        if (collision.isEmpty()) {
            return false;
        }
        
        double maxY = collision.max(Direction.Axis.Y);
        return maxY >= 0.5;
    }
    
    public static boolean canJumpUpTo(BlockGetter level, BlockPos from, BlockPos to) {
        int heightDiff = to.getY() - from.getY();
        if (heightDiff <= 0) return true;
        if (heightDiff > MAX_STEP_HEIGHT) return false;
        
        BlockPos feetPos = to;
        BlockPos headPos = to.above();
        
        BlockState feetState = level.getBlockState(feetPos);
        BlockState headState = level.getBlockState(headPos);
        
        return isPassable(feetState, level, feetPos) && isPassable(headState, level, headPos);
    }
    
    private static boolean isDangerous(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.LAVA 
            || block == Blocks.FIRE 
            || block == Blocks.SOUL_FIRE
            || block == Blocks.MAGMA_BLOCK
            || block == Blocks.SWEET_BERRY_BUSH
            || block == Blocks.CACTUS
            || block instanceof BaseFireBlock;
    }
    
    public static boolean isPassable(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.isAir()) return true;
        
        if (state.getBlock() instanceof LiquidBlock) {
            return false;
        }
        
        VoxelShape shape = state.getCollisionShape(level, pos, CollisionContext.empty());
        return shape.isEmpty();
    }
    
    private static boolean hasSolidGround(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.isAir()) return false;
        
        if (state.getBlock() instanceof LiquidBlock) {
            return false;
        }
        
        VoxelShape shape = state.getCollisionShape(level, pos, CollisionContext.empty());
        return !shape.isEmpty();
    }
    
    public static boolean isWater(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof LiquidBlock;
    }
    
    public static double getGroundHeight(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return pos.getY();
        
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return pos.getY();
        
        return pos.getY() + shape.max(Direction.Axis.Y);
    }
}
