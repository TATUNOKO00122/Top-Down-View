package com.topdownview.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤーが既に開いたコンテナ（チェストなど）の位置を追跡するクラス。
 * ディメンション・サーバーごとにファイルへ保存し、次回起動時も状態を維持します。
 */
public final class OpenedContainerTracker {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Set<Long>> DIMENSION_DATA = new HashMap<>();
    private static String currentKey = "";
    private static boolean dirty = false;
    private static Path saveDir;

    private OpenedContainerTracker() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static void init() {
        if (saveDir != null) return;
        saveDir = Minecraft.getInstance().gameDirectory.toPath().resolve("topdown_view_opened");
        try {
            Files.createDirectories(saveDir);
        } catch (IOException e) {
            LOGGER.error("[TopDownView] Failed to create topdown_view_opened directory", e);
        }
    }

    private static String buildStorageKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return "";
        String dim = mc.level.dimension().location().toString()
                .replace(':', '_').replace('/', '_');
        if (mc.isSingleplayer()) {
            return "sp_" + dim;
        }
        ServerData sd = mc.getCurrentServer();
        if (sd != null) {
            String serverId = (sd.ip != null ? sd.ip.replace(':', '_').replace('.', '_') : "unknown");
            return "mp_" + serverId + "_" + dim;
        }
        return "mp_unknown_" + dim;
    }

    public static void onTick() {
        if (saveDir == null) return;
        String newKey = buildStorageKey();
        if (!newKey.equals(currentKey)) {
            saveCurrentDimension();
            currentKey = newKey;
            DIMENSION_DATA.computeIfAbsent(newKey, OpenedContainerTracker::loadDimensionFile);
        }
        if (dirty) {
            saveCurrentDimension();
            dirty = false;
        }
    }

    public static boolean isOpened(BlockPos pos) {
        if (currentKey.isEmpty()) return false;
        Set<Long> set = DIMENSION_DATA.get(currentKey);
        return set != null && set.contains(pos.asLong());
    }

    public static void markOpened(BlockPos pos) {
        if (currentKey.isEmpty()) return;
        Set<Long> set = DIMENSION_DATA.computeIfAbsent(currentKey, k -> ConcurrentHashMap.newKeySet());
        
        // 既に登録済みの場合はスキップ
        if (!set.add(pos.asLong())) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            BlockState state = mc.level.getBlockState(pos);
            if (state.hasProperty(ChestBlock.TYPE)) {
                ChestType type = state.getValue(ChestBlock.TYPE);
                if (type != ChestType.SINGLE) {
                    BlockPos neighbor = pos.offset(ChestBlock.getConnectedDirection(state).getNormal());
                    set.add(neighbor.asLong());
                }
            }
        }
        dirty = true;
    }

    public static void saveCurrentDimension() {
        if (currentKey.isEmpty() || saveDir == null) return;
        Set<Long> set = DIMENSION_DATA.get(currentKey);
        if (set == null || set.isEmpty()) return;

        Path file = saveDir.resolve(currentKey + ".json");
        try {
            StringBuilder sb = new StringBuilder("{\"positions\":[");
            boolean first = true;
            for (Long pos : set) {
                if (!first) sb.append(',');
                sb.append(pos);
                first = false;
            }
            sb.append("]}");
            Files.writeString(file, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("[TopDownView] Failed to save opened containers for {}", currentKey, e);
        }
    }

    public static void saveAll() {
        if (saveDir == null) return;
        for (Map.Entry<String, Set<Long>> entry : DIMENSION_DATA.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            Path file = saveDir.resolve(entry.getKey() + ".json");
            try {
                StringBuilder sb = new StringBuilder("{\"positions\":[");
                boolean first = true;
                for (Long pos : entry.getValue()) {
                    if (!first) sb.append(',');
                    sb.append(pos);
                    first = false;
                }
                sb.append("]}");
                Files.writeString(file, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                LOGGER.error("[TopDownView] Failed to save opened containers for {}", entry.getKey(), e);
            }
        }
    }

    private static Set<Long> loadDimensionFile(String key) {
        if (saveDir == null) return ConcurrentHashMap.newKeySet();
        Path file = saveDir.resolve(key + ".json");
        if (!Files.exists(file)) return ConcurrentHashMap.newKeySet();
        try {
            String content = Files.readString(file);
            int start = content.indexOf('[');
            int end = content.indexOf(']');
            if (start < 0 || end < 0) return ConcurrentHashMap.newKeySet();
            String positions = content.substring(start + 1, end).trim();
            if (positions.isEmpty()) return ConcurrentHashMap.newKeySet();

            Set<Long> set = ConcurrentHashMap.newKeySet();
            for (String s : positions.split(",")) {
                s = s.trim();
                if (!s.isEmpty()) {
                    set.add(Long.parseLong(s));
                }
            }
            return set;
        } catch (Exception e) {
            LOGGER.error("[TopDownView] Failed to load opened containers for {}", key, e);
            return ConcurrentHashMap.newKeySet();
        }
    }

    public static void clearAll() {
        DIMENSION_DATA.clear();
        currentKey = "";
        dirty = false;
    }
}
