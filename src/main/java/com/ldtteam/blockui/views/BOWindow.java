package com.ldtteam.blockui.views;

import com.ldtteam.blockui.BOGuiGraphics;
import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.Loader;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneParams;
import com.ldtteam.blockui.Parsers;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;

import java.util.function.ToDoubleBiFunction;

/**
 * Blockout window, high level root pane.
 */
public class BOWindow extends View
{
    /**
     * The default width.
     */
    public static final int DEFAULT_WIDTH = 420;

    /**
     * The default height.
     */
    public static final int DEFAULT_HEIGHT = 240;

    /**
     * The screen of the window.
     */
    protected BOScreen screen;

    /**
     * Defines if the window should pause the game.
     */
    protected boolean windowPausesGame = false;

    /**
     * Defines if the window should have a lightbox.
     */
    protected boolean lightbox = false;

    /**
     * Defines if the window should have blurred background.
     */
    protected boolean blurBackground = false;

    /**
     * Render using size or attemp to scale to fullscreen.
     */
    protected WindowRenderType windowRenderType = WindowRenderType.OVERSIZED_VANILLA;

    protected Identifier xmlResourceLocation;

    /**
     * Seam for a host screen (Ore UI pass S4). Null by default, so every open behaves as before.
     */
    private static OpenHook openHook = null;

    /**
     * The window's own screen and render type while it is hosted, restored by {@link #unhost()}; null while not hosted.
     */
    private BOScreen ownScreen = null;
    private WindowRenderType ownRenderType = null;

    /**
     * The render scale the host set, used by {@link WindowRenderType#HOSTED}.
     */
    private double hostScale = 1.0d;

    /**
     * Create a window from an xml file.
     *
     * @param resource Identifier to get file from.
     */
    public BOWindow(final Identifier resource)
    {
        this(resource, true);
    }

    /**
     * Make default sized window.
     */
    public BOWindow(final Identifier resource, final boolean shouldloadXml)
    {
        this(resource, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        if (shouldloadXml)
        {
            Loader.createFromXMLFile(resource, this);
        }
    }

    /**
     * Window constructor when there is a fixed Width and Height.
     *
     * @param w Width of the window, in pixels.
     * @param h Height of the window, in pixels.
     */
    public BOWindow(final Identifier resource, final int w, final int h)
    {
        super();
        xmlResourceLocation = resource;
        width = w;
        height = h;

        screen = new BOScreen(this);
        window = this;
    }

    /**
     * Load the xml parameters.
     *
     * @param params xml parameters.
     */
    public void loadParams(final PaneParams params)
    {
        params.getResource("inherit", r -> Loader.createFromXMLFile(r, this));

        params.applyShorthand("size", Parsers.INT, 2, a -> {
            width = a.get(0);
            height = a.get(1);
        });

        lightbox = params.getBoolean("lightbox", lightbox);
        blurBackground = params.getBoolean("blurBackground", blurBackground);
        windowPausesGame = params.getBoolean("pause", windowPausesGame);
        windowRenderType = params.getEnum("type", WindowRenderType.class, windowRenderType);
    }

    @Override
    public void drawSelf(final BOGuiGraphics ms, final double mx, final double my)
    {
        debugging = mc.hasShiftDown() && mc.hasAltDown() && mc.hasControlDown();

        super.drawSelf(ms, mx, my);
    }

    public void setLightbox(final boolean lightbox)
    {
        this.lightbox = lightbox;
    }

    /**
     * Return {@code true} if the 'lightbox' (default dark background) should be displayed.
     *
     * @return {@code true} if the 'lightbox' should be displayed.
     */
    public boolean hasLightbox()
    {
        return lightbox;
    }

    public void setBlurBackground(final boolean blurBackground)
    {
        this.blurBackground = blurBackground;
    }

    public boolean hasBlurredBackground()
    {
        return blurBackground;
    }

    public void setWindowPausesGame(final boolean windowPausesGame)
    {
        this.windowPausesGame = windowPausesGame;
    }

    /**
     * Return {@code true} if the game should be paused when the Window is displayed.
     *
     * @return {@code true} if the game should be paused when the Window is displayed.
     */
    public boolean doesWindowPauseGame()
    {
        return windowPausesGame;
    }

    /**
     * @return {@link WindowRenderType}
     */
    public WindowRenderType getRenderType()
    {
        return windowRenderType;
    }

    /**
     * @return xml defining this window
     */
    public Identifier getXmlResourceLocation()
    {
        return xmlResourceLocation;
    }

    /**
     * Open the window.
     */
    public void open()
    {
        if (openHook != null && openHook.open(this, false))
        {
            return;
        }
        mc.submit(() -> mc.gui.setScreen(screen));
    }

    /**
     * Open the window.
     */
    public void openAsLayer()
    {
        if (openHook != null && openHook.open(this, true))
        {
            return;
        }
        mc.submit(() -> mc.gui.pushScreenLayer(screen));
    }

    /**
     * Set the open hook (Ore UI pass S4). It is asked before every {@link #open()} and {@link #openAsLayer()}; null (the default) passes every
     * open through unchanged.
     *
     * @param hook the hook, or null to remove it.
     */
    public static void setOpenHook(final OpenHook hook)
    {
        openHook = hook;
    }

    /**
     * @return the open hook, or null when none is set.
     */
    public static OpenHook getOpenHook()
    {
        return openHook;
    }

    /**
     * Host this window in another screen: the host screen becomes the window's screen (text fields, tooltips and ticks read it), the render type
     * becomes {@link WindowRenderType#HOSTED} with the given scale, and the window is moved by the offset (BlockUI pixels) from the centred position
     * the screen gives it. {@link #unhost()} restores the window's own screen, render type and position.
     *
     * @param host    the host screen.
     * @param scale   render scale, framebuffer pixels per BlockUI pixel.
     * @param offsetX x offset from the centred position, in BlockUI pixels.
     * @param offsetY y offset from the centred position, in BlockUI pixels.
     */
    public void host(final BOScreen host, final double scale, final int offsetX, final int offsetY)
    {
        if (ownScreen == null)
        {
            ownScreen = screen;
            ownRenderType = windowRenderType;
        }
        screen = host;
        windowRenderType = WindowRenderType.HOSTED;
        setHostPlacement(scale, offsetX, offsetY);
    }

    /**
     * Move a hosted window: new scale and offset (see {@link #host}).
     */
    public void setHostPlacement(final double scale, final int offsetX, final int offsetY)
    {
        hostScale = scale;
        setPosition(offsetX, offsetY);
    }

    /**
     * Undo {@link #host}: the window's own screen, render type and an offset of zero. Nothing happens when the window is not hosted.
     */
    public void unhost()
    {
        if (ownScreen == null)
        {
            return;
        }
        screen = ownScreen;
        windowRenderType = ownRenderType;
        ownScreen = null;
        ownRenderType = null;
        hostScale = 1.0d;
        setPosition(0, 0);
    }

    /**
     * @return true while the window is hosted by another screen.
     */
    public boolean isHosted()
    {
        return ownScreen != null;
    }

    /**
     * @return the render scale the host set (1 when not hosted).
     */
    public double hostScale(final Window mcWindow)
    {
        return hostScale;
    }

    /**
     * Asked before a window opens (Ore UI pass S4).
     */
    @FunctionalInterface
    public interface OpenHook
    {
        /**
         * @param window  the window that is opening.
         * @param asLayer true for {@link #openAsLayer()}, false for {@link #open()}.
         * @return true when the hook opened the window itself; false lets the window open as usual.
         */
        boolean open(BOWindow window, boolean asLayer);
    }

    /**
     * Windows wrap a GuiScreen.
     *
     * @return The current GuiScreen.
     */
    public BOScreen getScreen()
    {
        return screen;
    }

    /**
     * Mouse click released handler.
     * <p>
     * Currently does nothing.
     *
     * @param mx Mouse X position
     * @param my Mouse Y position
     */
    public boolean onMouseReleased(final double mx, final double my)
    {
        // Can be overridden
        return false;
    }

    /**
     * Characted input handler. Directs text to focused Pane.
     * <p>
     * It is advised not to override this method.
     *
     * @return {@code true} if the key was handled by a Pane.
     */
    @Override
    public boolean onCharactedEvent(final CharacterEvent event)
    {
        final Pane focus = getOwnFocus();
        return focus != null && focus.onCharactedEvent(event);
    }

    /**
     * The focused pane, if it belongs to this window. Focus is shared by all windows, so a layer
     * opened over a window with a focused text field must not route input to that hidden field.
     *
     * @return the focused pane of this window, or null.
     */
    protected Pane getOwnFocus()
    {
        final Pane focus = getFocus();
        return focus != null && focus.getWindow() == this ? focus : null;
    }

    /**
     * Key input handler. Directs keystrokes to focused Pane, or to onUnhandledKeyTyped() if no
     * Pane handles the keystroke.
     * <p>
     * It is advised not to override this method.
     *
     * @return {@code true} if the key was handled by a Pane.
     */
    @Override
    public boolean onKeyEvent(final KeyEvent event)
    {
        final Pane focus = getOwnFocus();
        if (focus != null && focus.onKeyEvent(event))
        {
            return true;
        }

        return onUnhandledKeyTyped(event);
    }

    /**
     * Key input handler when a focused pane did not handle the key.
     * <p>
     * Override this to handle key input at the Window level.
     *
     */
    public boolean onUnhandledKeyTyped(final KeyEvent event)
    {
        if (event.isEscape())
        {
            if (getOwnFocus() != null)
            {
                clearFocus();
            }
            else
            {
                close();
            }
            return true;
        }
        return false;
    }

    /**
     * Close the Window.
     */
    public void close()
    {
        Minecraft.getInstance().gui.popScreenLayer();
    }

    /**
     * Called when the Window is displayed.
     */
    public void onOpened()
    {
        // Can be overridden
    }

    /**
     * Called when the Window is closed.
     */
    public void onClosed()
    {
        // Can be overridden
    }

    /**
     * Defines how gui should be rendered.
     */
    public enum WindowRenderType
    {
        /**
         * upscaling according to minecraft gui scale settings, no downscaling, max gui resolution is 320*240 px, anything above might not be rendered
         */
        VANILLA((mcWindow, window) -> Math.max(mcWindow.getGuiScale(), 1.0d)),
        /**
         * scaling to size of framebuffer with no lower limit, max gui resolution is unlimited
         */
        FULLSCREEN((mcWindow, window) -> {
            final double widthScale = ((double) mcWindow.getScreenWidth()) / window.getWidth();
            final double heightScale = ((double) mcWindow.getScreenHeight()) / window.getHeight();

            return Math.min(widthScale, heightScale);
        }),
        /**
         * scaling to size of framebuffer with lower limit of 320*240 px, max gui resolution is unlimited
         */
        FULLSCREEN_VANILLA((mcWindow, window) -> {
            final double widthScale = Math.max(mcWindow.getScreenWidth(), 320.0d) / window.getWidth();
            final double heightScale = Math.max(mcWindow.getScreenHeight(), 240.0d) / window.getHeight();

            return Math.min(widthScale, heightScale);
        }),
        /**
         * no upscaling, no downscalling, max gui resolution is unlimited
         */
        FIXED((mcWindow, window) -> 1.0d),
        /**
         * no upscaling, downscaling down to 320*240 px according to size of framebuffer, max gui resolution is unlimited
         */
        FIXED_VANILLA((mcWindow, window) -> Math.min(FULLSCREEN_VANILLA.calcRenderScale(mcWindow, window), 1.0d)),
        /**
         * integer upscaling up to mc gui scale, downscaling down to size of framebuffer, max gui resolution is unlimited
         */
        OVERSIZED((mcWindow, window) -> {
            final double fs = FULLSCREEN.calcRenderScale(mcWindow, window);
            final int userScale = window.mc.options.guiScale().get();
            return fs < 1.0d ? fs : Math.min(Math.floor(fs), userScale == 0 ? Double.MAX_VALUE : userScale);
        }),
        /**
         * integer upscaling up to mc gui scale, downscaling down to 320*240 px according to size of framebuffer, max gui resolution is unlimited
         */
        OVERSIZED_VANILLA((mcWindow, window) -> {
            final double fs_vanilla = FULLSCREEN_VANILLA.calcRenderScale(mcWindow, window);
            final int userScale = window.mc.options.guiScale().get();
            return fs_vanilla < 1.0d ? fs_vanilla : Math.min(Math.floor(fs_vanilla), userScale == 0 ? Double.MAX_VALUE : userScale);
        }),
        /**
         * set by a host screen through {@link BOWindow#host}, Ore UI pass S4
         */
        HOSTED((mcWindow, window) -> window.hostScale(mcWindow));

        private final ToDoubleBiFunction<Window, BOWindow> renderScaleCalculator;

        private WindowRenderType(final ToDoubleBiFunction<Window, BOWindow> renderScaleCalculator)
        {
            this.renderScaleCalculator = renderScaleCalculator;
        }

        public double calcRenderScale(final Window mcWindow, final BOWindow window)
        {
            return renderScaleCalculator.applyAsDouble(mcWindow, window);
        }
    }
}
