package com.ldtteam.blockui.controls;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.TextPalette;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The parts of the {@link TextPalette} hook that need no client: which window a text control asks for, and the Style recolouring of formatted text.
 * {@link AbstractTextElement} uses them; they are public so they can be tested without a client.
 */
public final class TextPaletteSupport
{
    private TextPaletteSupport()
    {
    }

    /**
     * The window a text control asks the palette for. A tooltip draws on the vanilla tooltip background, not on its window's page, so it keeps its colours
     * (no window, which a palette maps as identity).
     */
    @Nullable
    public static <W> W root(final Class<?> type, @Nullable final W window)
    {
        return Tooltip.class.isAssignableFrom(type) ? null : window;
    }

    /**
     * Rebuilds the text with the palette's Style colours; returns the same component, without building anything, when no colour changes.
     * Legacy section-sign colour codes are decoded later by the font and are not seen here.
     */
    public static MutableComponent recolor(final MutableComponent textBlock, final TextPalette palette, @Nullable final Pane root)
    {
        final Optional<Boolean> changes = textBlock.visit((style, part) -> {
            final TextColor color = style.getColor();
            return color != null && map(color, palette, root) != (color.getValue() & 0xFFFFFF) ? Optional.of(Boolean.TRUE) : Optional.empty();
        }, Style.EMPTY);
        if (changes.isEmpty())
        {
            return textBlock;
        }
        final MutableComponent recolored = Component.empty();
        textBlock.visit((style, part) -> {
            final TextColor color = style.getColor();
            final int to = color == null ? 0 : map(color, palette, root);
            recolored.append(Component.literal(part).setStyle(color == null || to == (color.getValue() & 0xFFFFFF) ? style : style.withColor(to)));
            return Optional.empty();
        }, Style.EMPTY);
        return recolored;
    }

    private static int map(final TextColor color, final TextPalette palette, @Nullable final Pane root)
    {
        return palette.map(0xFF000000 | (color.getValue() & 0xFFFFFF), root) & 0xFFFFFF;
    }
}
