package com.topdownview.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.topdownview.TopDownViewMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * Minecraftの {@link InputConstants.Key} をKenney input-promptsのアイコンへマッピングするユーティリティ。
 * outline版（線画スタイル）のアイコンのみ収録。アセットは
 * {@code assets/topdown_view/textures/gui/keys/} に配置済み。
 *
 * <p>未対応のキー（スキャンコードや特殊キー）は {@code keyboard_any} アイコンへフォールバックする。
 * テキストフォールバックが必要な場合は {@link #hasIcon(InputConstants.Key)} で判定可能。</p>
 */
public final class KeyIconMapper {

    /** アイコンテクスチャのルートパス */
    private static final String TEX_PATH = "textures/gui/keys/";

    /** 未対応キーのフォールバックアイコン */
    private static final ResourceLocation DEFAULT_ICON = rl("keyboard_any");

    /** キーボード（GLFWキーコード）→アイコン */
    private static final Map<Integer, ResourceLocation> KEYBOARD_MAP = new HashMap<>();

    /** マウス（GLFWマウスボタン番号）→アイコン */
    private static final Map<Integer, ResourceLocation> MOUSE_MAP = new HashMap<>();

    static {
        // ==================== アルファベット A-Z ====================
        for (int i = 0; i < 26; i++) {
            KEYBOARD_MAP.put(GLFW.GLFW_KEY_A + i, rl("keyboard_" + (char) ('a' + i)));
        }

        // ==================== 数字 0-9 ====================
        for (int i = 0; i < 10; i++) {
            KEYBOARD_MAP.put(GLFW.GLFW_KEY_0 + i, rl("keyboard_" + i));
        }

        // ==================== F1-F12 ====================
        for (int i = 1; i <= 12; i++) {
            KEYBOARD_MAP.put(GLFW.GLFW_KEY_F1 + i - 1, rl("keyboard_f" + i));
        }

        // ==================== 記号 ====================
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_MINUS, rl("keyboard_minus"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_EQUAL, rl("keyboard_equals"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_COMMA, rl("keyboard_comma"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_PERIOD, rl("keyboard_period"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_SLASH, rl("keyboard_slash_forward"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_APOSTROPHE, rl("keyboard_apostrophe"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_SEMICOLON, rl("keyboard_semicolon"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_LEFT_BRACKET, rl("keyboard_bracket_open"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_RIGHT_BRACKET, rl("keyboard_bracket_close"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_BACKSLASH, rl("keyboard_slash_back"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_GRAVE_ACCENT, rl("keyboard_tilde"));

        // ==================== 修飾キー（アイコン型を採用：小さくても視認可能） ====================
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_SPACE, rl("keyboard_space_icon"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_LEFT_SHIFT, rl("keyboard_shift_icon"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_RIGHT_SHIFT, rl("keyboard_shift_icon"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_LEFT_CONTROL, rl("keyboard_ctrl"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_RIGHT_CONTROL, rl("keyboard_ctrl"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_LEFT_ALT, rl("keyboard_alt"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_RIGHT_ALT, rl("keyboard_alt"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_LEFT_SUPER, rl("keyboard_win"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_RIGHT_SUPER, rl("keyboard_win"));

        // ==================== 編集・機能キー ====================
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_TAB, rl("keyboard_tab_icon"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_ENTER, rl("keyboard_return"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_KP_ENTER, rl("keyboard_numpad_enter"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_BACKSPACE, rl("keyboard_backspace_icon"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_ESCAPE, rl("keyboard_escape"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_DELETE, rl("keyboard_delete"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_INSERT, rl("keyboard_insert"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_HOME, rl("keyboard_home"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_END, rl("keyboard_end"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_PAGE_UP, rl("keyboard_page_up"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_PAGE_DOWN, rl("keyboard_page_down"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_CAPS_LOCK, rl("keyboard_capslock_icon"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_NUM_LOCK, rl("keyboard_numlock"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_SCROLL_LOCK, rl("keyboard_scroll_lock"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_PRINT_SCREEN, rl("keyboard_printscreen"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_PAUSE, rl("keyboard_pause"));

        // ==================== 矢印キー ====================
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_UP, rl("keyboard_arrow_up"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_DOWN, rl("keyboard_arrow_down"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_LEFT, rl("keyboard_arrow_left"));
        KEYBOARD_MAP.put(GLFW.GLFW_KEY_RIGHT, rl("keyboard_arrow_right"));

        // ==================== マウス ====================
        // 中クリックはスクロールホイールのクリックと同義なので scroll アイコンで代用
        MOUSE_MAP.put(GLFW.GLFW_MOUSE_BUTTON_LEFT, rl("mouse_left"));
        MOUSE_MAP.put(GLFW.GLFW_MOUSE_BUTTON_RIGHT, rl("mouse_right"));
        MOUSE_MAP.put(GLFW.GLFW_MOUSE_BUTTON_MIDDLE, rl("mouse_scroll"));
        // マウスのサイドボタン（戻る/進む）
        MOUSE_MAP.put(GLFW.GLFW_MOUSE_BUTTON_4, rl("mouse_side_back"));
        MOUSE_MAP.put(GLFW.GLFW_MOUSE_BUTTON_5, rl("mouse_side_forward"));
    }

    private KeyIconMapper() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /**
     * 指定されたキーに対応するアイコンの {@link ResourceLocation} を返す。
     * 未対応キーの場合は {@code keyboard_any} アイコンを返す。
     */
    public static ResourceLocation getIcon(InputConstants.Key key) {
        if (key == null) {
            return DEFAULT_ICON;
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            return MOUSE_MAP.getOrDefault(key.getValue(), DEFAULT_ICON);
        }
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return KEYBOARD_MAP.getOrDefault(key.getValue(), DEFAULT_ICON);
        }
        return DEFAULT_ICON;
    }

    /**
     * 指定された {@link KeyMapping} にバインドされているキーのアイコンを返す。
     */
    public static ResourceLocation getIcon(KeyMapping mapping) {
        if (mapping == null) {
            return DEFAULT_ICON;
        }
        return getIcon(mapping.getKey());
    }

    /**
     * 指定されたキーに専用アイコンが存在するか判定する。
     * {@code false} の場合はテキストフォールバックの使用を推奨。
     */
    public static boolean hasIcon(InputConstants.Key key) {
        if (key == null) {
            return false;
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            return MOUSE_MAP.containsKey(key.getValue());
        }
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return KEYBOARD_MAP.containsKey(key.getValue());
        }
        return false;
    }

    /**
     * フォールバック用デフォルトアイコン（{@code keyboard_any}）を返す。
     */
    public static ResourceLocation getDefaultIcon() {
        return DEFAULT_ICON;
    }

    private static ResourceLocation rl(String name) {
        return new ResourceLocation(TopDownViewMod.MODID, TEX_PATH + name + ".png");
    }
}
