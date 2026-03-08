package com.topdownview.baritone;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class BaritoneIntegration {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Boolean baritoneAvailable = null;
    private static boolean settingsConfigured = false;
    private static Class<?> baritoneApiClass;
    private static Class<?> goalBlockClass;
    private static Class<?> goalNearClass;
    private static Method getProviderMethod;
    private static Method getPrimaryBaritoneMethod;
    private static Method getCustomGoalProcessMethod;
    private static Method setGoalAndPathMethod;
    private static Method getPathingBehaviorMethod;
    private static Method cancelEverythingMethod;
    private static Method isPathingMethod;
    private static Method getSettingsMethod;
    private static Field allowBreakField;
    private static Field allowPlaceField;
    private static Field settingValueField;
    
    private BaritoneIntegration() {
        throw new IllegalStateException("ユーティリティクラス");
    }
    
    public static boolean isBaritoneAvailable() {
        if (baritoneAvailable != null) {
            return baritoneAvailable;
        }
        
        try {
            baritoneApiClass = Class.forName("baritone.api.BaritoneAPI");
            goalBlockClass = Class.forName("baritone.api.pathing.goals.GoalBlock");
            goalNearClass = Class.forName("baritone.api.pathing.goals.GoalNear");
            
            getProviderMethod = baritoneApiClass.getMethod("getProvider");
            getSettingsMethod = baritoneApiClass.getMethod("getSettings");
            
            Class<?> providerClass = Class.forName("baritone.api.IBaritoneProvider");
            getPrimaryBaritoneMethod = providerClass.getMethod("getPrimaryBaritone");
            
            Class<?> baritoneClass = Class.forName("baritone.api.IBaritone");
            getCustomGoalProcessMethod = baritoneClass.getMethod("getCustomGoalProcess");
            getPathingBehaviorMethod = baritoneClass.getMethod("getPathingBehavior");
            
            Class<?> customGoalProcessClass = Class.forName("baritone.api.process.ICustomGoalProcess");
            setGoalAndPathMethod = customGoalProcessClass.getMethod("setGoalAndPath", 
                Class.forName("baritone.api.pathing.goals.Goal"));
            
            Class<?> pathingBehaviorClass = Class.forName("baritone.api.behavior.IPathingBehavior");
            cancelEverythingMethod = pathingBehaviorClass.getMethod("cancelEverything");
            isPathingMethod = pathingBehaviorClass.getMethod("isPathing");
            
            Class<?> settingsClass = Class.forName("baritone.api.Settings");
            allowBreakField = settingsClass.getField("allowBreak");
            allowPlaceField = settingsClass.getField("allowPlace");
            
            Class<?> settingClass = Class.forName("baritone.api.Settings$Setting");
            settingValueField = settingClass.getField("value");
            
            baritoneAvailable = true;
            LOGGER.info("[Baritone] Baritone検出: 有効");
            
            configureSettings();
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException e) {
            baritoneAvailable = false;
            LOGGER.info("[Baritone] Baritone未検出: 直線移動モード - {}", e.getMessage());
        }
        
        return baritoneAvailable;
    }
    
    private static void configureSettings() {
        if (settingsConfigured || !baritoneAvailable) return;
        
        try {
            Object settings = getSettingsMethod.invoke(null);
            if (settings == null) {
                LOGGER.warn("[Baritone] Settings取得失敗");
                return;
            }
            
            Object allowBreakSetting = allowBreakField.get(settings);
            Object allowPlaceSetting = allowPlaceField.get(settings);
            
            settingValueField.set(allowBreakSetting, false);
            settingValueField.set(allowPlaceSetting, false);
            
            settingsConfigured = true;
            LOGGER.info("[Baritone] 設定変更: 破壊・設置無効化（移動のみモード）");
        } catch (Exception e) {
            LOGGER.warn("[Baritone] 設定変更エラー: {}", e.getMessage());
        }
    }
    
    private static Object getBaritone() {
        if (!isBaritoneAvailable()) return null;
        
        try {
            Object provider = getProviderMethod.invoke(null);
            if (provider == null) {
                LOGGER.warn("[Baritone] Provider取得失敗");
                return null;
            }
            return getPrimaryBaritoneMethod.invoke(provider);
        } catch (Exception e) {
            LOGGER.warn("[Baritone] Baritone取得エラー: {}", e.getMessage());
            return null;
        }
    }
    
    public static void pathTo(BlockPos target) {
        if (!isBaritoneAvailable()) return;
        configureSettings();
        
        try {
            Object baritone = getBaritone();
            if (baritone == null) return;
            
            Object goalProcess = getCustomGoalProcessMethod.invoke(baritone);
            Object goal = goalBlockClass.getConstructor(BlockPos.class).newInstance(target);
            setGoalAndPathMethod.invoke(goalProcess, goal);
            
            LOGGER.info("[Baritone] 経路探索開始: {}", target);
        } catch (Exception e) {
            LOGGER.warn("[Baritone] 経路探索エラー: {}", e.getMessage());
        }
    }
    
    public static void pathTo(Vec3 target) {
        pathTo(BlockPos.containing(target));
    }
    
    public static void pathToEntity(Entity entity) {
        if (!isBaritoneAvailable()) return;
        configureSettings();
        
        try {
            Object baritone = getBaritone();
            if (baritone == null) return;
            
            Object goalProcess = getCustomGoalProcessMethod.invoke(baritone);
            Object goal = goalNearClass.getConstructor(BlockPos.class, int.class)
                .newInstance(entity.blockPosition(), 2);
            setGoalAndPathMethod.invoke(goalProcess, goal);
            
            LOGGER.info("[Baritone] エンティティ追跡開始: {}", entity.getName().getString());
        } catch (Exception e) {
            LOGGER.warn("[Baritone] エンティティ追跡エラー: {}", e.getMessage());
        }
    }
    
    public static void stop() {
        if (!isBaritoneAvailable()) return;
        
        try {
            Object baritone = getBaritone();
            if (baritone == null) return;
            
            Object pathingBehavior = getPathingBehaviorMethod.invoke(baritone);
            cancelEverythingMethod.invoke(pathingBehavior);
            LOGGER.info("[Baritone] 移動停止");
        } catch (Exception e) {
            LOGGER.warn("[Baritone] 停止エラー: {}", e.getMessage());
        }
    }
    
    public static boolean isPathing() {
        if (!isBaritoneAvailable()) return false;
        
        try {
            Object baritone = getBaritone();
            if (baritone == null) return false;
            
            Object pathingBehavior = getPathingBehaviorMethod.invoke(baritone);
            return (boolean) isPathingMethod.invoke(pathingBehavior);
        } catch (Exception e) {
            return false;
        }
    }
}
