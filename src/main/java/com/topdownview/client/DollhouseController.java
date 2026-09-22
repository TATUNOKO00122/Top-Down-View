package com.topdownview.client;

import com.topdownview.Config;
import com.topdownview.culling.TopDownCuller;
import com.topdownview.spatial.DollhouseMask;
import com.topdownview.spatial.RoomSegmentation;
import com.topdownview.spatial.SpaceProbe;
import com.topdownview.state.DollhouseState;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * ドールハウス表示の毎tick更新。
 *
 * <p>空間検出（{@link SpaceProbe}）のプレイヤーの部屋が成立している間だけ、その部屋セルから
 * 可視マスクを構築して {@link DollhouseState} へ公開する。部屋が変わらないtickでは再構築しない。
 * カリングのON/OFFには依存しない。
 */
public final class DollhouseController {

    /** active の 0/1 遷移速度（1tickあたり）。 */
    private static final float FADE_PER_TICK = 0.15f;

    private static long lastRoomFingerprint = Long.MIN_VALUE;

    private DollhouseController() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static void onClientTick() {
        DollhouseState state = ModState.DOLLHOUSE;
        Minecraft mc = Minecraft.getInstance();
        boolean wantDollhouse = ModState.STATUS.isEnabled() && Config.isDollhouseEnabled()
                && mc.level != null && mc.player != null;
        if (!wantDollhouse) {
            state.clearEnclosure();
            lastRoomFingerprint = Long.MIN_VALUE;
            state.setActive(approach(state.getActive(), 0.0f));
            return;
        }

        SpaceProbe.Result space = TopDownCuller.getInstance().getSpaceResult();
        RoomSegmentation.Room playerRoom = space != null ? space.getSegmentation().getPlayerRoom() : null;
        if (space != null && space.isEnclosed() && playerRoom != null) {
            long fingerprint = fingerprint(space.getRoomResult().getSeed(), playerRoom);
            if (fingerprint != lastRoomFingerprint) {
                lastRoomFingerprint = fingerprint;
                DollhouseMask.Snapshot snapshot = DollhouseMask.build(space.getRoomResult(), playerRoom);
                state.publish(snapshot.data, snapshot.anchorX, snapshot.anchorY, snapshot.anchorZ);
            }
            state.setActive(approach(state.getActive(), 1.0f));
        } else {
            state.clearEnclosure();
            lastRoomFingerprint = Long.MIN_VALUE;
            state.setActive(approach(state.getActive(), 0.0f));
        }
    }

    private static float approach(float current, float target) {
        if (current < target) {
            return Math.min(target, current + FADE_PER_TICK);
        }
        if (current > target) {
            return Math.max(target, current - FADE_PER_TICK);
        }
        return current;
    }

    private static long fingerprint(BlockPos seed, RoomSegmentation.Room room) {
        long fp = seed.asLong();
        fp = fp * 31L + room.getMinPos().asLong();
        fp = fp * 31L + room.getMaxPos().asLong();
        fp = fp * 31L + room.size();
        return fp;
    }
}
