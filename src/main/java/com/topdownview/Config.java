package com.topdownview;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
        private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

        // Culling settings
        private static final ForgeConfigSpec.IntValue CYLINDER_RADIUS_HORIZONTAL = BUILDER
                        .comment("シリンダーの水平方向半径（ブロック数）")
                        .defineInRange("cylinderRadiusHorizontal", 2, 1, 10);

        private static final ForgeConfigSpec.IntValue CYLINDER_RADIUS_VERTICAL = BUILDER
                        .comment("シリンダーの垂直方向半径（ブロック数）")
                        .defineInRange("cylinderRadiusVertical", 4, 1, 10);

        private static final ForgeConfigSpec.IntValue CYLINDER_FORWARD_SHIFT = BUILDER
                        .comment("楕円柱の中心を前方にシフトする距離（ブロック数）")
                        .defineInRange("cylinderForwardShift", 3, 0, 10);

        // Movement settings
        private static final ForgeConfigSpec.BooleanValue CLICK_TO_MOVE_ENABLED = BUILDER
                        .comment("クリックツームーブ機能の有効/無効")
                        .define("clickToMoveEnabled", true);

        private static final ForgeConfigSpec.DoubleValue ARRIVAL_THRESHOLD = BUILDER
                        .comment("目的地到達判定の距離（ブロック数）")
                        .defineInRange("arrivalThreshold", 1.5, 0.5, 5.0);

        private static final ForgeConfigSpec.DoubleValue ATTACK_RANGE = BUILDER
                        .comment("エンティティ攻撃実行距離（ブロック数）")
                        .defineInRange("attackRange", 3.0, 1.0, 6.0);

        private static final ForgeConfigSpec.BooleanValue FORCE_AUTO_JUMP = BUILDER
                        .comment("クリックツームーブ中の自動ジャンプ強制")
                        .define("forceAutoJump", true);

        // Translucent trapdoor settings
        private static final ForgeConfigSpec.BooleanValue TRAPDOOR_TRANSLUCENCY_ENABLED = BUILDER
                        .comment("遮蔽トラップドアの半透明化の有効/無効")
                        .define("trapdoorTranslucencyEnabled", false);

        private static final ForgeConfigSpec.DoubleValue TRAPDOOR_TRANSPARENCY = BUILDER
                        .comment("遮蔽トラップドアの透明度（0.0=透明、1.0=不透明）")
                        .defineInRange("trapdoorTransparency", 0.3, 0.0, 1.0);

        // Fade settings
        private static final ForgeConfigSpec.BooleanValue FADE_ENABLED = BUILDER
                        .comment("カリング境界の距離フェードの有効/無効")
                        .define("fadeEnabled", true);

        private static final ForgeConfigSpec.DoubleValue FADE_START = BUILDER
                        .comment("フェード開始位置（境界=1.0に対する比率、0.7=境界の70%位置から開始）")
                        .defineInRange("fadeStart", 0.7, 0.0, 0.9);

        private static final ForgeConfigSpec.DoubleValue FADE_NEAR_ALPHA = BUILDER
                        .comment("プレイヤー付近の透明度（0.0=透明、1.0=不透明）")
                        .defineInRange("fadeNearAlpha", 0.0, 0.0, 1.0);

        // Pathfinding settings (DISABLED - straight-line movement only)
        // private static final ForgeConfigSpec.IntValue PATHFINDING_RANGE = BUILDER
        // .comment("経路探索の最大距離（ブロック数）")
        // .defineInRange("pathfindingRange", 32, 8, 64);
        // private static final ForgeConfigSpec.IntValue PATH_RECALC_COOLDOWN = BUILDER
        // .comment("経路再計算のクールダウン（tick）")
        // .defineInRange("pathRecalcCooldown", 20, 5, 100);
        // private static final ForgeConfigSpec.BooleanValue PATHFINDING_ENABLED =
        // BUILDER
        // .comment("経路探索（A*）と動的回避の有効/無効")
        // .define("pathfindingEnabled", true);
        // private static final ForgeConfigSpec.DoubleValue AVOIDANCE_RADIUS = BUILDER
        // .comment("回避判定の半径（ブロック数）")
        // .defineInRange("avoidanceRadius", 2.0, 1.0, 5.0);

        public static final ForgeConfigSpec SPEC = BUILDER.build();

        // Runtime values
        public static int cylinderRadiusHorizontal;
        public static int cylinderRadiusVertical;
        public static int cylinderForwardShift;
        public static boolean clickToMoveEnabled;
        public static double arrivalThreshold;
        public static double attackRange;
        public static boolean forceAutoJump;
        public static boolean trapdoorTranslucencyEnabled;
        public static double trapdoorTransparency;
        public static boolean fadeEnabled;
        public static double fadeStart;
        public static double fadeNearAlpha;

        // public static int pathfindingRange;
        // public static int pathRecalcCooldown;
        // public static boolean pathfindingEnabled;
        // public static double avoidanceRadius;

        @SubscribeEvent
        static void onLoad(final ModConfigEvent event) {
                cylinderRadiusHorizontal = CYLINDER_RADIUS_HORIZONTAL.get();
                cylinderRadiusVertical = CYLINDER_RADIUS_VERTICAL.get();
                cylinderForwardShift = CYLINDER_FORWARD_SHIFT.get();
                clickToMoveEnabled = CLICK_TO_MOVE_ENABLED.get();
                arrivalThreshold = ARRIVAL_THRESHOLD.get();
                attackRange = ATTACK_RANGE.get();
                forceAutoJump = FORCE_AUTO_JUMP.get();
                trapdoorTranslucencyEnabled = TRAPDOOR_TRANSLUCENCY_ENABLED.get();
                trapdoorTransparency = TRAPDOOR_TRANSPARENCY.get();
                fadeEnabled = FADE_ENABLED.get();
                fadeStart = FADE_START.get();
                fadeNearAlpha = FADE_NEAR_ALPHA.get();
                // pathfindingRange = PATHFINDING_RANGE.get();
                // pathRecalcCooldown = PATH_RECALC_COOLDOWN.get();
                // pathfindingEnabled = PATHFINDING_ENABLED.get();
                // avoidanceRadius = AVOIDANCE_RADIUS.get();
        }

        public static void save() {
                CYLINDER_RADIUS_HORIZONTAL.set(cylinderRadiusHorizontal);
                CYLINDER_RADIUS_VERTICAL.set(cylinderRadiusVertical);
                CYLINDER_FORWARD_SHIFT.set(cylinderForwardShift);
                CLICK_TO_MOVE_ENABLED.set(clickToMoveEnabled);
                ARRIVAL_THRESHOLD.set(arrivalThreshold);
                ATTACK_RANGE.set(attackRange);
                FORCE_AUTO_JUMP.set(forceAutoJump);
                TRAPDOOR_TRANSLUCENCY_ENABLED.set(trapdoorTranslucencyEnabled);
                TRAPDOOR_TRANSPARENCY.set(trapdoorTransparency);
                FADE_ENABLED.set(fadeEnabled);
                FADE_START.set(fadeStart);
                FADE_NEAR_ALPHA.set(fadeNearAlpha);
                // PATHFINDING_RANGE.set(pathfindingRange);
                // PATH_RECALC_COOLDOWN.set(pathRecalcCooldown);
                // PATHFINDING_ENABLED.set(pathfindingEnabled);
                // AVOIDANCE_RADIUS.set(avoidanceRadius);
                SPEC.save();
        }
}
