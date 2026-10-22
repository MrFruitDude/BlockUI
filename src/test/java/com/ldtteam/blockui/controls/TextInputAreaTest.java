package com.ldtteam.blockui.controls;

import org.joml.Matrix3x2f;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TextInputAreaTest
{
    @Test
    public void mapsCaretThroughBoScreenPoseToGuiBounds()
    {
        // Like BOScreen at vanilla GUI scale 2: framebuffer pixels scaled to GUI units, then the window offset.
        final Matrix3x2f pose = new Matrix3x2f().scale(0.5f).translate(100, 50);

        final TextInputArea area = TextInputArea.from(pose, 20, 40, 21, 49);

        assertEquals(60, area.left());
        assertEquals(45, area.top());
        assertEquals(61, area.right());
        assertEquals(50, area.bottom());
    }
}
