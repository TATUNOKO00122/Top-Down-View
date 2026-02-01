package me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.BlockGetter;

/**
 * Dummy class for Embeddium 0.3.31 compatibility.
 * This matches the actual class structure in Embeddium 0.3.31.
 * This class is EXCLUDED from the final JAR via build.gradle.
 */
public class BlockRenderContext {
    public BlockPos pos() {
        return null;
    }

    public BlockState state() {
        return null;
    }

    public BlockGetter world() {
        return null;
    }
}
