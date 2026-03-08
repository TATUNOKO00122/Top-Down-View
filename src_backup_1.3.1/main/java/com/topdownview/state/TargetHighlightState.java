package com.topdownview.state;

import com.topdownview.Config;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import org.jetbrains.annotations.Nullable;

/**
 * ターゲット発光状態管理
 * EpicFight式のターゲットアウトラインシステム用
 */
public final class TargetHighlightState {
    public static final TargetHighlightState INSTANCE = new TargetHighlightState();

    // 現在フォーカスしているエンティティ
    @Nullable
    private LivingEntity currentTarget = null;

    // ターゲットが射程内かどうか
    private boolean isInRange = false;

    private TargetHighlightState() {
    }

    /**
     * 現在のターゲットを設定
     */
    public void setCurrentTarget(@Nullable LivingEntity target) {
        this.currentTarget = target;
    }

    /**
     * 現在のターゲットを取得
     */
    @Nullable
    public LivingEntity getCurrentTarget() {
        return this.currentTarget;
    }

    /**
     * 射程内状態を設定
     */
    public void setInRange(boolean inRange) {
        this.isInRange = inRange;
    }

    /**
     * ターゲットが射程内かどうか
     */
    public boolean isInRange() {
        return this.isInRange;
    }

    /**
     * 指定エンティティが現在のターゲットかどうか
     */
    public boolean isTarget(Entity entity) {
        return this.currentTarget != null && this.currentTarget.is(entity);
    }

    /**
     * 発光すべきかどうか（Mixin用）
     */
    public boolean shouldHighlight(Entity entity) {
        if (!ModState.STATUS.isEnabled()) return false;
        return isTarget(entity);
    }

    /**
     * アウトライン色を取得（RGBA）
     * @return int[4] = {r, g, b, a}
     */
    public int[] getOutlineColor() {
        // 射程外赤表示が無効の場合は常に白色
        if (!Config.rangeIndicatorEnabled) {
            return new int[]{255, 255, 255, 255};
        }
        
        if (isInRange) {
            // 射程内：白色 (255, 255, 255, 255)
            return new int[]{255, 255, 255, 255};
        } else {
            // 射程外：赤色 (255, 0, 0, 255)
            return new int[]{255, 0, 0, 255};
        }
    }

    /**
     * 攻撃射程距離を取得（武器種別に基づく）
     * @param player プレイヤー
     * @return 射程距離（ブロック単位）
     */
    public static double getAttackRange(Player player) {
        if (player == null) {
            return Config.rangeEmptyHand;
        }
        
        ItemStack mainHand = player.getMainHandItem();
        
        // 素手チェック
        if (mainHand.isEmpty()) {
            return Config.rangeEmptyHand;
        }
        
        Item item = mainHand.getItem();
        
        // 武器種別に応じた射程を返す
        if (item instanceof SwordItem) {
            return Config.rangeSword;
        } else if (item instanceof AxeItem) {
            return Config.rangeAxe;
        } else if (item instanceof PickaxeItem) {
            return Config.rangePickaxe;
        } else if (item instanceof ShovelItem) {
            return Config.rangeShovel;
        } else {
            return Config.rangeOther;
        }
    }

    /**
     * プレイヤーが遠距離武器（弓・クロスボウ）を持っているかチェック
     * @param player プレイヤー
     * @return 遠距離武器を持っている場合はtrue
     */
    public static boolean isRangedWeapon(Player player) {
        if (player == null) {
            return false;
        }
        
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.isEmpty()) {
            return false;
        }
        
        Item item = mainHand.getItem();
        return item instanceof BowItem || item instanceof CrossbowItem;
    }

    /**
     * 状態をリセット
     */
    public void reset() {
        this.currentTarget = null;
        this.isInRange = false;
    }
}
