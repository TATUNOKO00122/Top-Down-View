package com.example.examplemod.client;

import com.example.examplemod.TopDownViewMod;
import com.example.examplemod.api.cullers.FaceCullingManager;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT)
public class ClientForgeEvents {

    public static boolean isTopDownView = false;
    public static double cameraDistance = 15.0D;
    public static boolean isBlockCullingEnabled = true;

    private static int cullingUpdateTick = 0;

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (ClientModBusEvents.TOGGLE_VIEW_KEY.consumeClick()) {
            isTopDownView = !isTopDownView;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("Top-Down View: " + (isTopDownView ? "ON" : "OFF")),
                        true);
                if (isTopDownView) {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    mc.mouseHandler.releaseMouse();
                } else {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    mc.mouseHandler.grabMouse();
                }
            }
        }

        if (ClientModBusEvents.TOGGLE_CULLING_KEY.consumeClick()) {
            if (isTopDownView) {
                isBlockCullingEnabled = !isBlockCullingEnabled;
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal(
                            "Block Culling: " + (isBlockCullingEnabled ? "ON" : "OFF")), true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (isTopDownView) {
            double scroll = event.getScrollDelta();
            if (scroll != 0) {
                cameraDistance -= scroll * 1.5;
                cameraDistance = Math.max(2.0, Math.min(cameraDistance, 50.0));
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (isTopDownView) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onComputeFovModifier(ComputeFovModifierEvent event) {
        if (isTopDownView) {
            event.setNewFovModifier(1.0f);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (!isTopDownView || mc.player == null)
            return;

        if (mc.screen != null)
            return;

        if (mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.releaseMouse();
        }

        updatePlayerRotationToMouse(mc);

        // カリング更新
        if (isBlockCullingEnabled) {
            FaceCullingManager.getInstance().setEnabled(true);
            scheduleSimpleChunkRebuild(mc);
        } else {
            FaceCullingManager.getInstance().setEnabled(false);
        }
    }

    /**
     * Embeddiumに対してチャンク再構築をスケジュールする
     * 少し余裕を持った範囲で再構築をリクエストして、カリング状態の変更を反映させる
     */
    private static void scheduleSimpleChunkRebuild(Minecraft mc) {
        cullingUpdateTick++;
        if (cullingUpdateTick % 5 != 0)
            return; // 5tick毎に更新

        if (mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null)
            return;

        try {
            Class<?> rendererClass = Class.forName(
                    "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer");
            Object instance = rendererClass.getMethod("instance").invoke(null);

            if (instance != null) {
                Vec3 pos = mc.player.position();
                Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
                // プレイヤーとカメラを含む範囲 + マージン
                int range = (int) (cameraDistance * 0.6);

                int minX = (int) Math.min(pos.x, cam.x) - range;
                int maxX = (int) Math.max(pos.x, cam.x) + range;
                int minY = (int) Math.min(pos.y, cam.y) - 5;
                int maxY = (int) Math.max(pos.y, cam.y) + 5;
                int minZ = (int) Math.min(pos.z, cam.z) - range;
                int maxZ = (int) Math.max(pos.z, cam.z) + range;

                rendererClass.getMethod("scheduleRebuildForBlockArea",
                        int.class, int.class, int.class, int.class, int.class, int.class, boolean.class)
                        .invoke(instance, minX, minY, minZ, maxX, maxY, maxZ, true);
            }
        } catch (Exception ignored) {
            // Embeddiumがない場合などは何もしない
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        if (isTopDownView) {
            TargetHighlightRenderer.onRenderLevelStage(event);
        }
    }

    private static void updatePlayerRotationToMouse(Minecraft mc) {
        HitResult hitResult = MouseRaycast.getHitResult(mc, mc.getFrameTime(), MouseRaycast.getCustomReachDistance());
        Vec3 targetPos = hitResult.getLocation();

        Vec3 playerEyePos = mc.player.getEyePosition(mc.getFrameTime());
        double dx = targetPos.x - playerEyePos.x;
        double dy = targetPos.y - playerEyePos.y;
        double dz = targetPos.z - playerEyePos.z;

        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;

        float pitch;
        if (mc.player.isUsingItem() && mc.player.getUseItem().getItem() instanceof BowItem) {
            pitch = calculateBowPitch(horizontalDist, dy);
        } else {
            pitch = (float) -(Math.atan2(dy, horizontalDist) * (180D / Math.PI));
        }

        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yHeadRot = yaw;
        mc.player.yBodyRot = yaw;
    }

    private static float calculateBowPitch(double horizontalDist, double verticalDist) {
        double arrowSpeed = 3.0 * 20.0;
        double gravity = 0.05 * 20.0 * 20.0;

        double v = arrowSpeed;
        double g = gravity;
        double x = horizontalDist;
        double y = verticalDist;

        double v2 = v * v;
        double v4 = v2 * v2;
        double gx = g * x;
        double discriminant = v4 - g * (g * x * x + 2.0 * y * v2);

        if (discriminant < 0) {
            return (float) -(Math.atan2(y, x) * (180D / Math.PI));
        }

        double sqrtDisc = Math.sqrt(discriminant);
        double tanTheta = (v2 - sqrtDisc) / gx;
        double theta = Math.atan(tanTheta);
        float pitchDegrees = (float) (-theta * (180D / Math.PI));

        return Mth.clamp(pitchDegrees, -90.0F, 90.0F);
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!isTopDownView)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        float forward = event.getInput().forwardImpulse;
        float strafe = event.getInput().leftImpulse;

        float dx = strafe;
        float dz = forward;

        float playerYaw = mc.player.getYRot();
        float rad = (float) Math.toRadians(playerYaw);
        float c = Mth.cos(rad);
        float s = Mth.sin(rad);

        float newForward = -dx * s + dz * c;
        float newStrafe = dx * c + dz * s;

        event.getInput().forwardImpulse = newForward;
        event.getInput().leftImpulse = newStrafe;
    }
}
