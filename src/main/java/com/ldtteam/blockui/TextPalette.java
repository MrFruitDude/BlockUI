package com.ldtteam.blockui;

import org.jetbrains.annotations.Nullable;

/**
 * Maps the colours BlockUI text controls draw: the text, hover and disabled colours of a control and the colours inside formatted text (Style colours).
 * One palette is installed for the whole client; the default is {@link #IDENTITY}, which leaves every control exactly as before.
 * Callers keep the alpha of the colour they asked for, so a palette compares the RGB part.
 */
@FunctionalInterface
public interface TextPalette
{
    /**
     * Draws every colour unchanged.
     */
    TextPalette IDENTITY = (argb, root) -> argb;

    /**
     * @param argb the colour the control would draw (Style colours are passed with alpha 0xFF)
     * @param root the window the text belongs to, null while the control is not in a window
     * @return the colour to draw
     */
    int map(int argb, @Nullable Pane root);

    /**
     * @return the installed palette, {@link #IDENTITY} when none is
     */
    static TextPalette get()
    {
        return Holder.palette;
    }

    /**
     * Install a palette for every BlockUI text control; null restores {@link #IDENTITY}. Text already laid out is recoloured at its next draw.
     */
    static void set(@Nullable final TextPalette palette)
    {
        Holder.palette = palette == null ? IDENTITY : palette;
    }

    final class Holder
    {
        private static volatile TextPalette palette = IDENTITY;

        private Holder()
        {
        }
    }
}
