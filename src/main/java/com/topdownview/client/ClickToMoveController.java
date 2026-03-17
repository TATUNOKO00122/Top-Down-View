package com.topdownview.client;

import com.topdownview.Config;
import com.topdownview.TopDownViewMod;
import com.topdownview.baritone.BaritoneIntegration;
import com.topdownview.state.ClickToMoveState;
import com.topdownview.state.ModState;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT)
public final class ClickToMoveController {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double STOP_THRESHOLD = 0.5;

    public enum EntityAction {
        ATTACK,
        INTERACT,
        IGNORE
    }

    private ClickToMoveController() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static EntityAction getEntityAction(Entity entity) {
        if (entity instanceof Player) return EntityAction.IGNORE;

        // 村人、商人 → インタラクト
        if (entity instanceof Villager || entity instanceof WanderingTrader || entity instanceof AbstractVillager)
            return EntityAction.INTERACT;

        // 乗り物系 → インタラクト
        if (entity instanceof AbstractHorse || entity instanceof Boat)
            return EntityAction.INTERACT;

        // 中立Mob → 無視
        if (entity instanceof IronGolem) return EntityAction.IGNORE;
        if (entity instanceof Wolf) return EntityAction.IGNORE;
        if (entity instanceof PolarBear) return EntityAction.IGNORE;
        if (entity instanceof Panda) return EntityAction.IGNORE;
        if (entity instanceof Fox) return EntityAction.IGNORE;
        if (entity instanceof Bee) return EntityAction.IGNORE;
        if (entity instanceof Dolphin) return EntityAction.IGNORE;
        if (entity instanceof Llama) return EntityAction.IGNORE;
        if (entity instanceof TraderLlama) return EntityAction.IGNORE;

        // 敵対Mob → 攻撃
        if (entity instanceof Enemy) return EntityAction.ATTACK;

        // その他 → 無視
        return EntityAction.IGNORE;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.ClientTickEvent.Phase.END) return;
        if (!ModState.STATUS.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ClickActionHandler.onClientTick(mc);

        if (!Config.isClickToMoveEnabled()) return;
        if (!ModState.CLICK_TO_MOVE.isMoving()) return;

        ModState.CLICK_TO_MOVE.updateEntityTargetPosition();
        ModState.CLICK_TO_MOVE.tickBaritone();

        if (ModState.CLICK_TO_MOVE.isAttacking()) {
            tickAttackFollow(mc);
        } else if (ModState.CLICK_TO_MOVE.isDestroying()) {
            tickDestroyFollow(mc);
        } else if (ModState.CLICK_TO_MOVE.isInteracting()) {
            tickInteractFollow(mc);
        } else if (ModState.CLICK_TO_MOVE.getTargetEntity() != null) {
            checkEntityTarget(mc);
        } else if (ModState.CLICK_TO_MOVE.useBaritone()) {
            checkBaritoneArrival(mc);
        } else {
            checkArrival(mc);
        }
    }

    private static void checkBaritoneArrival(Minecraft mc) {
        if (!ModState.CLICK_TO_MOVE.canCheckBaritoneArrival()) {
            return;
        }
        if (!BaritoneIntegration.isPathing()) {
            LOGGER.info("[ClickToMove] Baritone経路完了");
            stop();
        }
    }

    public static void setDestination(Vec3 targetPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        LOGGER.info("[ClickToMove] 目的地設定: {}", BlockPos.containing(targetPos));
        ModState.CLICK_TO_MOVE.startMoveTo(targetPos, mc.player.position());

        if (BaritoneIntegration.isBaritoneAvailable()) {
            LOGGER.info("[ClickToMove] Baritone経路探索使用");
            ModState.CLICK_TO_MOVE.setUseBaritone(true);
            BaritoneIntegration.pathTo(targetPos);
            return;
        }

        LOGGER.info("[ClickToMove] 直線移動モード");
        ModState.CLICK_TO_MOVE.setUseBaritone(false);
    }

    public static void startFollowAndAttack(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double attackRange = ModState.TARGET_HIGHLIGHT.getAttackRange(mc.player);
        double distSq = mc.player.distanceToSqr(entity.position());

        ModState.CLICK_TO_MOVE.startFollowAndAttack(entity, mc.player.position());

        if (distSq <= attackRange * attackRange) {
            mc.gameMode.attack(mc.player, entity);
            mc.player.swing(InteractionHand.MAIN_HAND);
            mc.player.sweepAttack();
        }

        if (BaritoneIntegration.isBaritoneAvailable()) {
            ModState.CLICK_TO_MOVE.setUseBaritone(true);
            BaritoneIntegration.pathToEntity(entity);
        }
    }

    public static void startInteractEntity(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        double interactRange = ClickToMoveState.DEFAULT_INTERACT_RANGE;
        double distSq = mc.player.distanceToSqr(entity.position());

        if (distSq <= interactRange * interactRange) {
            executeEntityInteract(mc, entity);
            return;
        }

        ModState.CLICK_TO_MOVE.startInteractEntity(entity, mc.player.position());

        if (BaritoneIntegration.isBaritoneAvailable()) {
            ModState.CLICK_TO_MOVE.setUseBaritone(true);
            BaritoneIntegration.pathToEntity(entity);
        }
    }

    private static void executeEntityInteract(Minecraft mc, Entity entity) {
        mc.gameMode.interact(mc.player, entity, InteractionHand.MAIN_HAND);
        mc.player.swing(InteractionHand.MAIN_HAND);
        stop();
    }

    public static void startDestroyBlock(BlockPos blockPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        double destroyRange = ClickToMoveState.DEFAULT_DESTROY_RANGE;
        Vec3 blockCenter = Vec3.atCenterOf(blockPos);
        double distSq = mc.player.distanceToSqr(blockCenter);

        if (distSq <= destroyRange * destroyRange) {
            startDestroying(mc, blockPos);
            return;
        }

        ModState.CLICK_TO_MOVE.startDestroyBlock(blockPos, mc.player.position());

        if (BaritoneIntegration.isBaritoneAvailable()) {
            ModState.CLICK_TO_MOVE.setUseBaritone(true);
            BaritoneIntegration.pathTo(blockCenter);
        }
    }

    private static void startDestroying(Minecraft mc, BlockPos blockPos) {
        ModState.CLICK_TO_MOVE.setDestroying(true);
        ModState.CLICK_TO_MOVE.setDestroyTargetBlock(blockPos);
        mc.gameMode.startDestroyBlock(blockPos, Direction.UP);
    }

    public static void startInteractBlock(BlockPos blockPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        double interactRange = ClickToMoveState.DEFAULT_INTERACT_RANGE;
        Vec3 blockCenter = Vec3.atCenterOf(blockPos);
        double distSq = mc.player.distanceToSqr(blockCenter);

        if (distSq <= interactRange * interactRange) {
            executeInteract(mc, blockPos);
            return;
        }

        ModState.CLICK_TO_MOVE.startInteractBlock(blockPos, mc.player.position());

        if (BaritoneIntegration.isBaritoneAvailable()) {
            ModState.CLICK_TO_MOVE.setUseBaritone(true);
            BaritoneIntegration.pathTo(blockCenter);
        }
    }

    private static void executeInteract(Minecraft mc, BlockPos blockPos) {
        BlockHitResult blockHit = new BlockHitResult(
                Vec3.atCenterOf(blockPos), Direction.UP, blockPos, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHit);
        mc.player.swing(InteractionHand.MAIN_HAND);
        stop();
    }

    public static void stop() {
        if (ModState.CLICK_TO_MOVE.isDestroying()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameMode != null) {
                mc.gameMode.stopDestroyBlock();
            }
        }
        if (ModState.CLICK_TO_MOVE.useBaritone()) {
            BaritoneIntegration.stop();
        }
        ModState.CLICK_TO_MOVE.stopMovement();
    }

    public static void reset() {
        BaritoneIntegration.stop();
        ModState.CLICK_TO_MOVE.reset();
    }

    public static void tickTerrainFollow(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;

        double reach = MouseRaycast.getCustomReachDistance();
        MouseRaycast.INSTANCE.update(mc, 1.0f, reach);
        HitResult hitResult = MouseRaycast.INSTANCE.getLastHitResult();

        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            Vec3 destination = blockHit.getLocation();
            ModState.CLICK_TO_MOVE.setTargetPosition(destination);

            if (BaritoneIntegration.isBaritoneAvailable() && ModState.CLICK_TO_MOVE.useBaritone()) {
                BaritoneIntegration.pathTo(destination);
            }
        }
    }

    public static float[] calculateMovementInput(Minecraft mc) {
        if (!ModState.CLICK_TO_MOVE.isMoving()) return null;
        if (mc.player == null) return null;

        if (ModState.CLICK_TO_MOVE.useBaritone()) {
            return null;
        }

        // 攻撃モードで攻撃距離内の場合は移動停止
        if (ModState.CLICK_TO_MOVE.isAttacking()) {
            Entity target = ModState.CLICK_TO_MOVE.getTargetEntity();
            if (target != null && target.isAlive()) {
                double distSq = mc.player.distanceToSqr(target.position());
                double attackRange = ModState.TARGET_HIGHLIGHT.getAttackRange(mc.player);

                if (distSq <= attackRange * attackRange) {
                    return null;
                }
            }
        }

        Vec3 destination = getEffectiveDestination(mc);
        if (destination == null) return null;

        Vec3 playerPos = mc.player.position();
        Vec3 direction = calculateDirection(playerPos, destination);

        if (direction == null) return null;

        double horizontalDist = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (horizontalDist < STOP_THRESHOLD) return null;

        double moveAngle = Math.atan2(-direction.x, direction.z);
        float playerYaw = mc.player.getYRot();
        float relativeAngle = (float) Math.toDegrees(moveAngle) - playerYaw;

        while (relativeAngle > 180) relativeAngle -= 360;
        while (relativeAngle < -180) relativeAngle += 360;

        float rad = (float) Math.toRadians(relativeAngle);
        float forward = Mth.cos(rad);
        float strafe = -Mth.sin(rad);

        float length = Mth.sqrt(forward * forward + strafe * strafe);
        if (length > 0) {
            forward /= length;
            strafe /= length;
        }

        return new float[]{forward, strafe};
    }

    private static Vec3 calculateDirection(Vec3 playerPos, Vec3 destination) {
        Vec3 direction = destination.subtract(playerPos);
        double horizontalDist = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (horizontalDist < 0.1) return null;
        return direction.normalize();
    }

    public static Vec3 getEffectiveDestination(Minecraft mc) {
        return ModState.CLICK_TO_MOVE.getTargetPosition();
    }

    private static void checkArrival(Minecraft mc) {
        if (mc.player == null) return;

        double threshold = Config.getArrivalThreshold();
        if (ModState.CLICK_TO_MOVE.hasArrived(mc.player.position(), threshold)) {
            // ホールドモードでなければ停止
            if (!ModState.CLICK_TO_MOVE.isHoldMode()) {
                stop();
            }
        }
    }

    private static void checkEntityTarget(Minecraft mc) {
        if (mc.player == null) return;

        Entity targetEntity = ModState.CLICK_TO_MOVE.getTargetEntity();
        if (targetEntity == null || !targetEntity.isAlive()) {
            stop();
            return;
        }

        ModState.CLICK_TO_MOVE.setTargetPosition(targetEntity.position());

        double attackRange = ModState.TARGET_HIGHLIGHT.getAttackRange(mc.player);
        if (ModState.CLICK_TO_MOVE.hasArrivedAtEntity(mc.player.position(), attackRange)) {
            mc.gameMode.attack(mc.player, targetEntity);
            mc.player.swing(InteractionHand.MAIN_HAND);
            // ホールドモードでなければ停止
            if (!ModState.CLICK_TO_MOVE.isHoldMode()) {
                stop();
            }
        }
    }

    public static void tickAttackFollow(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;

        Entity targetEntity = ModState.CLICK_TO_MOVE.getTargetEntity();
        if (targetEntity == null || !targetEntity.isAlive()) {
            stop();
            return;
        }

        // 発光ターゲットが変わった場合、ターゲット切り替え
        LivingEntity highlightTarget = ModState.TARGET_HIGHLIGHT.getCurrentTarget();
        if (highlightTarget != null && highlightTarget.isAlive() && highlightTarget != targetEntity) {
            EntityAction action = getEntityAction(highlightTarget);
            if (action == EntityAction.ATTACK) {
                ModState.CLICK_TO_MOVE.setTargetEntity(highlightTarget);
                targetEntity = highlightTarget;
            }
        }

        ModState.CLICK_TO_MOVE.setTargetPosition(targetEntity.position());

        double attackRange = ModState.TARGET_HIGHLIGHT.getAttackRange(mc.player);
        double distSq = mc.player.distanceToSqr(targetEntity.position());

        LOGGER.info("[ClickToMove] tickAttackFollow - distSq: {}, attackRangeSq: {}, canAttack: {}, cooldown: {}, holdMode: {}",
            distSq, attackRange * attackRange, ModState.CLICK_TO_MOVE.canAttack(), 
            ModState.CLICK_TO_MOVE.getAttackCooldown(), ModState.CLICK_TO_MOVE.isHoldMode());

        if (distSq <= attackRange * attackRange) {
            if (!ModState.CLICK_TO_MOVE.canAttack()) {
                return;
            }

            LOGGER.info("[ClickToMove] 攻撃実行!");

            mc.gameMode.attack(mc.player, targetEntity);
            mc.player.swing(InteractionHand.MAIN_HAND);
            mc.player.sweepAttack();

            double attacksPerSecond = mc.player.getAttributeValue(Attributes.ATTACK_SPEED);
            double delayInSeconds = 1.0 / attacksPerSecond;
            int cooldown = (int) (delayInSeconds * 20);
            ModState.CLICK_TO_MOVE.setAttackCooldown(Math.max(5, cooldown));

            LOGGER.info("[ClickToMove] 攻撃後クールダウン設定: {} ticks ({:.2f}秒, attacksPerSecond: {:.2f})", 
                cooldown, String.format("%.2f", delayInSeconds), String.format("%.2f", attacksPerSecond));

            // ホールドモードでなければ停止
            if (!ModState.CLICK_TO_MOVE.isHoldMode()) {
                stop();
            }
        }
    }

    public static void tickDestroyFollow(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;

        if (!ClientModBusEvents.DESTROY_KEY.isDown()) {
            stop();
            return;
        }

        double reach = MouseRaycast.getCustomReachDistance();
        MouseRaycast.INSTANCE.update(mc, 1.0f, reach);
        HitResult hitResult = MouseRaycast.INSTANCE.getLastHitResult();

        BlockPos newTargetBlock = null;
        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            newTargetBlock = blockHit.getBlockPos();
        }

        BlockPos currentTarget = ModState.CLICK_TO_MOVE.getDestroyTargetBlock();

        if (newTargetBlock == null) {
            if (currentTarget != null) {
                mc.gameMode.stopDestroyBlock();
                ModState.CLICK_TO_MOVE.setDestroying(false);
                ModState.CLICK_TO_MOVE.setDestroyTargetBlock(null);
            }
            return;
        }

        if (!newTargetBlock.equals(currentTarget)) {
            mc.gameMode.stopDestroyBlock();
            ModState.CLICK_TO_MOVE.setDestroying(false);
            currentTarget = newTargetBlock;
            ModState.CLICK_TO_MOVE.setDestroyTargetBlock(currentTarget);
        }

        BlockState state = mc.level.getBlockState(currentTarget);
        if (state.isAir()) {
            return;
        }

        double destroyRange = ClickToMoveState.DEFAULT_DESTROY_RANGE;
        Vec3 blockCenter = Vec3.atCenterOf(currentTarget);
        double distSq = mc.player.distanceToSqr(blockCenter);

        if (distSq <= destroyRange * destroyRange) {
            if (!ModState.CLICK_TO_MOVE.isDestroying()) {
                startDestroying(mc, currentTarget);
            }
            mc.gameMode.continueDestroyBlock(currentTarget, Direction.UP);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private static void tickInteractFollow(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;

        BlockPos targetBlock = ModState.CLICK_TO_MOVE.getInteractTargetBlock();
        if (targetBlock == null) {
            Entity targetEntity = ModState.CLICK_TO_MOVE.getTargetEntity();
            if (targetEntity != null && targetEntity.isAlive()) {
                double interactRange = ClickToMoveState.DEFAULT_INTERACT_RANGE;
                double distSq = mc.player.distanceToSqr(targetEntity.position());

                if (distSq <= interactRange * interactRange) {
                    executeEntityInteract(mc, targetEntity);
                }
            }
            return;
        }

        double interactRange = ClickToMoveState.DEFAULT_INTERACT_RANGE;
        Vec3 blockCenter = Vec3.atCenterOf(targetBlock);
        double distSq = mc.player.distanceToSqr(blockCenter);

        if (distSq <= interactRange * interactRange) {
            executeInteract(mc, targetBlock);
        }
    }
}