package com.example.examplemod.client;

import net.minecraft.client.Minecraft;

public final class ClientServices {
    private static final Minecraft MINECRAFT = Minecraft.getInstance();
    
    private ClientServices() {
        throw new AssertionError("Utility class");
    }
    
    public static Minecraft getMinecraft() {
        return MINECRAFT;
    }
}
