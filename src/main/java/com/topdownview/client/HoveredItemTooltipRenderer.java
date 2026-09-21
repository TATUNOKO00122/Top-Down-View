package com.topdownview.client;

import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderGuiEvent;

/**
 * カーソルが合っているドロップアイテム（ラベル＝ネームタグ上）のツールチップを画面上に表示する。
 * バニラのツールチップ描画をそのまま利用するため、色などの見た目は他MOD（LegendaryTooltips等）に委ねる。
 */
public final class HoveredItemTooltipRenderer {

    private HoveredItemTooltipRenderer() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        if (!ModState.STATUS.isEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.level == null || mc.player == null) {
            return;
        }

        ItemEntity item = DroppedItemLabelRenderer.findItemUnderCursor(mc);
        if (item == null) {
            return;
        }
        ItemStack stack = item.getItem();
        if (stack.isEmpty()) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        double mouseX = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth()
                / (double) mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight()
                / (double) mc.getWindow().getScreenHeight();

        guiGraphics.renderTooltip(mc.font, stack, (int) mouseX, (int) mouseY);
    }
}
