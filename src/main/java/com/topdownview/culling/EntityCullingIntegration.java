package com.topdownview.culling;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.reflect.Field;

/**
 * EntityCulling(tr7zw) MOD 連携。
 *
 * EntityCulling はカメラ視点からの遮蔽レイキャストでエンティティ／ブロックエンティティを
 * 非表示にする。トップダウンビューではカメラがプレイヤーから離れて見下ろすため、屋内の
 * ドロップアイテムやチェスト等が屋根越しに隠れて消える。トップダウン中はカリング設定
 * フラグを立てて停止し、解除時に元の値へ戻す。
 */
public final class EntityCullingIntegration {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static Field instanceField;
    private static Field configField;
    private static Field skipEntityField;
    private static Field skipBlockEntityField;
    private static boolean loaded = false;
    private static boolean initialized = false;

    private static boolean applied = false;
    private static boolean previousSkipEntity = false;
    private static boolean previousSkipBlockEntity = false;

    private EntityCullingIntegration() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    private static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            Class<?> modBase = Class.forName("dev.tr7zw.entityculling.EntityCullingModBase");
            Class<?> config = Class.forName("dev.tr7zw.entityculling.versionless.Config");
            instanceField = modBase.getField("instance");
            configField = modBase.getField("config");
            skipEntityField = config.getField("skipEntityCulling");
            skipBlockEntityField = config.getField("skipBlockEntityCulling");
            loaded = true;
            LOGGER.debug("[TopDownView] Entity Culling MOD integration initialized");
        } catch (ClassNotFoundException e) {
            LOGGER.debug("[TopDownView] Entity Culling MOD not found, skipping integration");
        } catch (NoSuchFieldException e) {
            LOGGER.warn("[TopDownView] Failed to initialize Entity Culling MOD integration: {}", e.getMessage());
        }
    }

    /** トップダウン中は EntityCulling のカリングを停止する。解除時は元の設定へ戻す。 */
    public static void setSuspended(boolean suspend) {
        if (!initialized) {
            initialize();
        }
        if (!loaded || suspend == applied) {
            return;
        }
        try {
            Object mod = instanceField.get(null);
            if (mod == null) {
                return;
            }
            Object config = configField.get(mod);
            if (suspend) {
                previousSkipEntity = skipEntityField.getBoolean(config);
                previousSkipBlockEntity = skipBlockEntityField.getBoolean(config);
                skipEntityField.setBoolean(config, true);
                skipBlockEntityField.setBoolean(config, true);
            } else {
                skipEntityField.setBoolean(config, previousSkipEntity);
                skipBlockEntityField.setBoolean(config, previousSkipBlockEntity);
            }
            applied = suspend;
        } catch (Throwable e) {
            LOGGER.warn("[TopDownView] Entity Culling MOD reflection failed: {}", e.getMessage());
        }
    }
}
