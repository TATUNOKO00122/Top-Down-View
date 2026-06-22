package com.topdownview.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * 看板（SignBlockEntity）から表示すべきテキストを抽出するヘルパークラス
 */
public final class SignTextHelper {

    private SignTextHelper() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /**
     * 指定された BlockEntity が看板であれば、プレイヤーの向きに応じて前面または背面の有効なテキストを取得します。
     * 
     * @param blockEntity 対象の BlockEntity
     * @return テキスト行のリスト（看板でない場合、またはテキストがない場合は空のリスト）
     */
    public static List<Component> getSignText(BlockEntity blockEntity) {
        List<Component> lines = new ArrayList<>();
        if (!(blockEntity instanceof SignBlockEntity signEntity)) {
            return lines;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return lines;
        }

        // プレイヤーが対面している面（前面か背面か）を手動で計算して判定
        boolean facingFront = isFacingFront(signEntity.getBlockState(), mc.player.getViewVector(1.0F));
        SignText signText = facingFront ? signEntity.getFrontText() : signEntity.getBackText();

        // SignText からメッセージを取得（バニラの1.20.1のシグネチャ）
        Component[] messages = signText.getMessages(false);
        if (messages != null) {
            for (Component component : messages) {
                // 空行は詰めて表示する（文字が入っている場合のみ追加）
                if (component != null && !component.getString().trim().isEmpty()) {
                    lines.add(component);
                }
            }
        }

        // もし選択された面が空で、逆側の面にテキストが書かれている場合は、逆側を表示するフォールバック
        if (lines.isEmpty()) {
            SignText fallbackText = facingFront ? signEntity.getBackText() : signEntity.getFrontText();
            Component[] fallbackMessages = fallbackText.getMessages(false);
            if (fallbackMessages != null) {
                for (Component component : fallbackMessages) {
                    if (component != null && !component.getString().trim().isEmpty()) {
                        lines.add(component);
                    }
                }
            }
        }

        return lines;
    }

    /**
     * プレイヤーの視線ベクトルと看板の向きから、プレイヤーが看板の前面に対面しているかを判定します。
     */
    private static boolean isFacingFront(BlockState state, Vec3 playerLookDir) {
        Vec3 signFrontDir = getSignFrontDirection(state);
        if (signFrontDir == null) {
            return true; // 判定できない場合は前面とする
        }
        // 看板の前面方向ベクトルと、プレイヤーの視線ベクトルのドット積を計算
        // ドット積が負であれば、対面している（向かい合っている）ので前面と判定
        return playerLookDir.dot(signFrontDir) < 0;
    }

    /**
     * 看板のブロックステートから前面の方向ベクトル（法線）を取得します。
     */
    private static Vec3 getSignFrontDirection(BlockState state) {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            // 壁掛け看板など
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            return new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        } else if (state.hasProperty(BlockStateProperties.ROTATION_16)) {
            // 自立看板
            int rotation = state.getValue(BlockStateProperties.ROTATION_16);
            double angle = Math.toRadians(rotation * 22.5);
            // rotation の向きに対応する法線ベクトルを計算
            // rotation=0(南:+Z), 4(西:-X), 8(北:-Z), 12(東:+X)
            return new Vec3(-Math.sin(angle), 0.0, Math.cos(angle));
        }
        return null;
    }
}
