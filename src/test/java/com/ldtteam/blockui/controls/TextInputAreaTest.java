package com.ldtteam.blockui.controls;

import org.joml.Matrix3x2f;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TextInputAreaTest
{
    @Test
    public void transformsCaretToGuiScaledNativeInputBounds()
    {
        final Matrix3x2f pose = new Matrix3x2f().translation(100, 50);

        final TextInputArea area = TextInputArea.from(pose, 20, 40, 21, 49, 2.0);

        assertEquals(60, area.left());
        assertEquals(45, area.top());
        assertEquals(61, area.right());
        assertEquals(50, area.bottom());
    }
}
