package com.ldtteam.blockui.controls;

import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

/** Bounds for Minecraft's native SDL text input caret, in vanilla GUI coordinates (what TextInputManager expects). */
record TextInputArea(int left, int top, int right, int bottom)
{
    static TextInputArea from(final Matrix3x2fc pose,
                              final float caretX,
                              final float topY,
                              final float rightX,
                              final float bottomY)
    {
        // BOScreen's pose already maps BlockUI pixels to vanilla GUI coordinates.
        final Vector2f topLeft = pose.transformPosition(caretX, topY, new Vector2f());
        final Vector2f bottomRight = pose.transformPosition(rightX, bottomY, new Vector2f());
        final int left = (int) Math.floor(topLeft.x);
        final int top = (int) Math.floor(topLeft.y);
        final int right = Math.max(left + 1, (int) Math.ceil(bottomRight.x));
        final int bottom = Math.max(top + 1, (int) Math.ceil(bottomRight.y));
        return new TextInputArea(left, top, right, bottom);
    }
}
