package com.topdownview.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * 頂点カラーのアルファ値を強制的に上書きする {@link VertexConsumer} ラッパー。
 * {@link #setAlpha(float)} で値を更新して再利用できる。
 */
public final class AlphaVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private float alpha = 1.0f;

    public AlphaVertexConsumer(VertexConsumer delegate) {
        this.delegate = delegate;
    }

    public void setAlpha(float alpha) {
        this.alpha = Math.max(0.0f, Math.min(1.0f, alpha));
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        return delegate.vertex(x, y, z);
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        return delegate.color(r, g, b, (int) (this.alpha * 255));
    }

    @Override
    public VertexConsumer color(float r, float g, float b, float a) {
        return delegate.color(r, g, b, this.alpha);
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        return delegate.uv(u, v);
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        return delegate.overlayCoords(u, v);
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        return delegate.uv2(u, v);
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        return delegate.normal(x, y, z);
    }

    @Override
    public void endVertex() {
        delegate.endVertex();
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {
        delegate.defaultColor(r, g, b, a);
    }

    @Override
    public void unsetDefaultColor() {
        delegate.unsetDefaultColor();
    }
}
