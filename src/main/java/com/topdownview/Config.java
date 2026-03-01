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
                        .define("trapdoorTranslucencyEnabled", true);

        private static final ForgeConfigSpec.DoubleValue TRAPDOOR_TRANSPARENCY = BUILDER
                        .comment("遮蔽トラップドアの透明度（0.0=透明、1.0=不透明）")
                        .defineInRange("trapdoorTransparency", 0.3, 0.0, 1.0);

        public static final ForgeConfigSpec SPEC = BUILDER.build();

        // Runtime values
        public static int cylinderRadiusHorizontal;
        public static int cylinderRadiusVertical;
        public static boolean clickToMoveEnabled;
        public static double arrivalThreshold;
        public static double attackRange;
        public static boolean forceAutoJump;
        public static boolean trapdoorTranslucencyEnabled;
        public static double trapdoorTransparency;

        @SubscribeEvent
        static void onLoad(final ModConfigEvent event) {
                cylinderRadiusHorizontal = CYLINDER_RADIUS_HORIZONTAL.get();
                cylinderRadiusVertical = CYLINDER_RADIUS_VERTICAL.get();
                clickToMoveEnabled = CLICK_TO_MOVE_ENABLED.get();
                arrivalThreshold = ARRIVAL_THRESHOLD.get();
                attackRange = ATTACK_RANGE.get();
                forceAutoJump = FORCE_AUTO_JUMP.get();
                trapdoorTranslucencyEnabled = TRAPDOOR_TRANSLUCENCY_ENABLED.get();
                trapdoorTransparency = TRAPDOOR_TRANSPARENCY.get();
        }

        public static void save() {
                CYLINDER_RADIUS_HORIZONTAL.set(cylinderRadiusHorizontal);
                CYLINDER_RADIUS_VERTICAL.set(cylinderRadiusVertical);
                CLICK_TO_MOVE_ENABLED.set(clickToMoveEnabled);
                ARRIVAL_THRESHOLD.set(arrivalThreshold);
                ATTACK_RANGE.set(attackRange);
                FORCE_AUTO_JUMP.set(forceAutoJump);
                TRAPDOOR_TRANSLUCENCY_ENABLED.set(trapdoorTranslucencyEnabled);
                TRAPDOOR_TRANSPARENCY.set(trapdoorTransparency);
                SPEC.save();
        }
}
