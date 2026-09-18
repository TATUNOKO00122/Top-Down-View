package com.topdownview.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 操作判定のデータ駆動オーバーライド。
 * config/topdown_view/interactions.json の block id → 種別を読み込み、
 * {@link InteractableBlocks} のヒューリスティックより優先する。
 * これにより操作できない BlockEntity の除外や、操作できる非 BlockEntity の追加が可能。
 *
 * OPEN / INTERACT はプロンプト表示とカリング保護の対象。NONE はプロンプトのみ除外し保護は維持、
 * EXCLUDE はプロンプトとカリング保護の両方から除外する。
 *
 * 読み手は描画スレッドとチャンク構築ワーカーの両方から来るため、読み込み結果は再読み込み時に
 * 丸ごと差し替える（volatile で公開）。読み取り専用のため同期は不要。
 */
public final class InteractionRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final String OVERRIDE_DIR = "topdown_view";
    private static final String FILE_NAME = "interactions.json";
    private static final String EXCLUDE_TOKEN = "EXCLUDE";

    // マップに存在しない = オーバーライド無し。byte 0 (NONE) と区別する。
    private static final byte ABSENT = -1;
    // enum.values() は毎回配列を複製するため、ホットパス用に一度だけ確保する
    private static final InteractableBlocks.InteractionKind[] KINDS = InteractableBlocks.InteractionKind.values();

    /** 種別と EXCLUDE 集合をまとめて差し替えるための不変ホルダー。 */
    private record Data(Object2ByteOpenHashMap<Block> kinds, ObjectOpenHashSet<Block> unprotected) {}

    private static volatile Data data = new Data(emptyMap(), new ObjectOpenHashSet<>());

    private InteractionRegistry() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /**
     * 指定ブロックのオーバーライド種別。無ければ null。EXCLUDE は NONE として返す。
     */
    public static InteractableBlocks.InteractionKind getOverride(Block block) {
        byte value = data.kinds().getByte(block);
        if (value == ABSENT) {
            return null;
        }
        return KINDS[value];
    }

    /** EXCLUDE 指定（プロンプトとカリング保護の両方から除外）か。 */
    public static boolean isUnprotected(Block block) {
        return data.unprotected().contains(block);
    }

    /**
     * 設定ファイルを読み込む。ファイルが無ければ既定ファイルを生成する。
     * ビルドした結果を最後に volatile へ差し替えるため、どのスレッドから呼んでも安全。
     */
    public static void reload() {
        Path file = configPath();
        Object2ByteOpenHashMap<Block> kinds = emptyMap();
        ObjectOpenHashSet<Block> unprotected = new ObjectOpenHashSet<>();

        try {
            if (Files.notExists(file)) {
                createDefaultFile(file);
            } else {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    parse(GSON.fromJson(reader, JsonElement.class), kinds, unprotected);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[TopDownView] Failed to load interaction overrides from {}", file, e);
        }

        data = new Data(kinds, unprotected);
        LOGGER.info("[TopDownView] Loaded {} interaction override(s)", kinds.size());
    }

    private static void parse(JsonElement root, Object2ByteOpenHashMap<Block> kinds, ObjectOpenHashSet<Block> unprotected) {
        if (root == null || !root.isJsonObject()) {
            return;
        }
        for (var entry : root.getAsJsonObject().entrySet()) {
            parseEntry(kinds, unprotected, entry.getKey(), entry.getValue());
        }
    }

    private static void parseEntry(Object2ByteOpenHashMap<Block> kinds, ObjectOpenHashSet<Block> unprotected,
                                   String blockId, JsonElement value) {
        // "_readme" などのメタキーを無視する
        if (blockId.startsWith("_") || value == null || !value.isJsonPrimitive()) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null || !ForgeRegistries.BLOCKS.containsKey(id)) {
            LOGGER.warn("[TopDownView] Unknown block id in interactions.json: {}", blockId);
            return;
        }
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        String token = value.getAsString().trim().toUpperCase();
        if (EXCLUDE_TOKEN.equals(token)) {
            kinds.put(block, (byte) InteractableBlocks.InteractionKind.NONE.ordinal());
            unprotected.add(block);
            return;
        }
        InteractableBlocks.InteractionKind kind;
        try {
            kind = InteractableBlocks.InteractionKind.valueOf(token);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("[TopDownView] Unknown interaction kind '{}' for {}", value.getAsString(), blockId);
            return;
        }
        kinds.put(block, (byte) kind.ordinal());
    }

    private static Object2ByteOpenHashMap<Block> emptyMap() {
        Object2ByteOpenHashMap<Block> map = new Object2ByteOpenHashMap<>();
        map.defaultReturnValue(ABSENT);
        return map;
    }

    private static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve(OVERRIDE_DIR).resolve(FILE_NAME);
    }

    private static void createDefaultFile(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                {
                  "_readme": "Add one line per block id. Values: OPEN (container, shows the '?' marker), INTERACT (other blocks), NONE (hides the prompt, keeps culling protection), EXCLUDE (hides the prompt and removes culling protection).",
                  "_example": {
                    "modid:block_id": "NONE"
                  }
                }
                """, StandardCharsets.UTF_8);
    }
}
