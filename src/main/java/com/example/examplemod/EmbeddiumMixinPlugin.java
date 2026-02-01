package com.example.examplemod;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * MixinConfigPlugin to conditionally apply Embeddium mixins.
 *
 * This plugin checks if Embeddium is present at runtime, and only applies
 * Embeddium-related mixins if the mod is loaded. This prevents compilation
 * errors
 * and runtime crashes when Embeddium is not installed.
 */
public class EmbeddiumMixinPlugin implements IMixinConfigPlugin {

    private boolean modernEmbeddiumLoaded;
    private boolean legacySodiumLoaded;

    @Override
    public void onLoad(String mixinPackage) {
        // Check for Modern Embeddium
        String modernClass = "org.embeddedt.embeddium.impl.render.chunk.compile.pipeline.BlockRenderer";
        String modernResource = modernClass.replace('.', '/') + ".class";

        if (this.getClass().getClassLoader().getResource(modernResource) != null) {
            modernEmbeddiumLoaded = true;
            System.out.println("[TopDownView] Modern Embeddium detected!");
        } else {
            modernEmbeddiumLoaded = false;
        }

        // Check for Legacy Sodium/Embeddium
        String legacyClass = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer";
        String legacyResource = legacyClass.replace('.', '/') + ".class";

        if (this.getClass().getClassLoader().getResource(legacyResource) != null) {
            legacySodiumLoaded = true;
            System.out.println("[TopDownView] Legacy Sodium/Embeddium detected!");
        } else {
            legacySodiumLoaded = false;
        }

        if (!modernEmbeddiumLoaded && !legacySodiumLoaded) {
            System.out.println("[TopDownView] No Embeddium/Sodium BlockRenderer found.");
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("EmbeddiumBlockRendererMixin") ||
                mixinClassName.endsWith("EmbeddiumFaceCullingMixin")) {
            // These mixins target me.jellysquid.mods.sodium package
            boolean shouldApply = legacySodiumLoaded || modernEmbeddiumLoaded;
            System.out.println("[TopDownView] Mixin " + mixinClassName + " shouldApply: " + shouldApply);
            return shouldApply;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
