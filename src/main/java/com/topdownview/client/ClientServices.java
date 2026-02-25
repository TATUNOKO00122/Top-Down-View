package com.topdownview.client;

import net.minecraft.client.Minecraft;

public final class ClientServices {
    private static final Minecraft MINECRAFT = Minecraft.getInstance();
    
    private ClientServices() {
        throw new IllegalStateException("ユーティリティクラス");
    }
    
    public static Minecraft getMinecraft() {
        return MINECRAFT;
    }
}
