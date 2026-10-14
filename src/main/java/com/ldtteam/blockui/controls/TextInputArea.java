package com.ldtteam.blockui.controls;

import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

/** Screen-space bounds for Minecraft's native SDL text input caret. */
record TextInputArea(int left, int top, int right, int bottom)
{
    static TextInputArea from(final Matrix3x2fc pose,
                              final float caretX,
                              final float topY,
                              final float rightX,
                              final float bottomY,
                              final double guiScale)
    {
        final Vector2f topLeft = pose.transformPosition(caretX, topY, new Vector2f());
        final Vector2f bottomRight = pose.transformPosition(rightX, bottomY, new Vector2f());
        final int left = (int) Math.floor(topLeft.x / guiScale);
        final int top = (int) Math.floor(topLeft.y / guiScale);
        final int right = Math.max(left + 1, (int) Math.ceil(bottomRight.x / guiScale));
        final int bottom = Math.max(top + 1, (int) Math.ceil(bottomRight.y / guiScale));
        return new TextInputArea(left, top, right, bottom);
    }
}
