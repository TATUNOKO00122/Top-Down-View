package com.topdownview.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 操作判定のデータ駆動オーバーライド。
 * jar 同梱の既定値 ({@value #DEFAULT_RESOURCE}) と config/topdown_view/interactions.json の
 * block id → 種別を読み込み、{@link InteractableBlocks} のヒューリスティックより優先する。
 * これにより操作できない BlockEntity の除外や、操作できる非 BlockEntity の追加が可能。
 * ユーザーファイルの同 id エントリは既定値を上書きするため、既定の追加を無効化（ブラックリスト化）
 * することもできる。
 *
 * 既定値のうち導入済み MOD のブロックは読み込み時にユーザーファイルへ追記される。MOD を導入しても
 * config に現れないと保護状況を確認できないため。ユーザーが記入した行は常に優先される。
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
    // HTML エスケープを切らないと readme の引用符が \u0027 になり可読性が落ちる
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String OVERRIDE_DIR = "topdown_view";
    private static final String FILE_NAME = "interactions.json";
    private static final String DEFAULT_RESOURCE = "/assets/topdown_view/interactions_default.json";
    private static final String EXCLUDE_TOKEN = "EXCLUDE";
    private static final String README = "Entries here override the mod's built-in defaults. Built-in defaults for "
            + "installed mods are added automatically. Values: OPEN (container, shows the '?' marker), INTERACT "
            + "(other right-click blocks), NONE (hides the prompt, keeps culling protection), EXCLUDE (blacklist: "
            + "hides the prompt and removes culling protection).";

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
     * 設定ファイルを読み込む。ファイルが無ければ生成する。
     * ビルドした結果を最後に volatile へ差し替えるため、どのスレッドから呼んでも安全。
     */
    public static void reload() {
        Path file = configPath();
        Object2ByteOpenHashMap<Block> kinds = emptyMap();
        ObjectOpenHashSet<Block> unprotected = new ObjectOpenHashSet<>();

        // 同梱の既定値を先に入れ、ユーザー設定で同 id を上書きできるようにする。
        JsonObject bundled = readBundledDefaults();
        if (bundled != null) {
            parse(bundled, kinds, unprotected, false);
        }

        try {
            String original = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
            JsonObject user = parseObject(original);
            parse(user, kinds, unprotected, true);

            // 導入済み MOD の既定値をユーザーファイルへ追記し、config から保護状況を確認できるようにする。
            String updated = buildConfig(user, bundled);
            if (!updated.equals(original)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, updated, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            LOGGER.error("[TopDownView] Failed to load interaction overrides from {}", file, e);
        }

        data = new Data(kinds, unprotected);
        LOGGER.info("[TopDownView] Loaded {} interaction override(s)", kinds.size());
    }

    /** jar 同梱の既定エントリを読み込む。未導入 MOD の id は黙って無視する。 */
    private static JsonObject readBundledDefaults() {
        try (InputStream stream = InteractionRegistry.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                return null;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement root = GSON.fromJson(reader, JsonElement.class);
                return root != null && root.isJsonObject() ? root.getAsJsonObject() : null;
            }
        } catch (Exception e) {
            LOGGER.error("[TopDownView] Failed to load bundled interaction defaults", e);
            return null;
        }
    }

    private static JsonObject parseObject(String json) {
        if (json == null || json.isBlank()) {
            return new JsonObject();
        }
        JsonElement root = GSON.fromJson(json, JsonElement.class);
        return root != null && root.isJsonObject() ? root.getAsJsonObject() : new JsonObject();
    }

    /**
     * 保存する設定内容を組み立てる。ユーザー記入を優先しつつ、未記載の既定値のうち
     * 導入済み MOD のブロックだけを追記する（未導入 MOD の id は残さない）。
     */
    private static String buildConfig(JsonObject user, JsonObject bundled) {
        JsonObject out = new JsonObject();
        out.addProperty("_readme", README);
        JsonObject example = new JsonObject();
        example.addProperty("modid:block_id", "OPEN");
        out.add("_example", example);

        for (var entry : user.entrySet()) {
            if (!entry.getKey().startsWith("_")) {
                out.add(entry.getKey(), entry.getValue());
            }
        }

        if (bundled != null) {
            for (var entry : bundled.entrySet()) {
                String id = entry.getKey();
                if (id.startsWith("_") || out.has(id) || !entry.getValue().isJsonPrimitive()) {
                    continue;
                }
                if (!isRegisteredBlockId(id)) {
                    continue;
                }
                out.add(id, entry.getValue());
            }
        }

        return PRETTY_GSON.toJson(out) + "\n";
    }

    private static boolean isRegisteredBlockId(String blockId) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        return id != null && ForgeRegistries.BLOCKS.containsKey(id);
    }

    private static void parse(JsonElement root, Object2ByteOpenHashMap<Block> kinds,
                              ObjectOpenHashSet<Block> unprotected, boolean warnUnknown) {
        if (root == null || !root.isJsonObject()) {
            return;
        }
        for (var entry : root.getAsJsonObject().entrySet()) {
            parseEntry(kinds, unprotected, entry.getKey(), entry.getValue(), warnUnknown);
        }
    }

    private static void parseEntry(Object2ByteOpenHashMap<Block> kinds, ObjectOpenHashSet<Block> unprotected,
                                   String blockId, JsonElement value, boolean warnUnknown) {
        // "_readme" などのメタキーを無視する
        if (blockId.startsWith("_") || value == null || !value.isJsonPrimitive()) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null || !ForgeRegistries.BLOCKS.containsKey(id)) {
            if (warnUnknown) {
                LOGGER.warn("[TopDownView] Unknown block id in interactions.json: {}", blockId);
            }
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
        // 既定の EXCLUDE をユーザーが OPEN 等で上書きした場合は保護を戻す。
        unprotected.remove(block);
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
}
