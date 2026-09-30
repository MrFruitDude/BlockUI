package com.ldtteam.blockui.util.texture;

import com.ldtteam.blockui.util.resloc.OutOfJarResourceLocation;
import net.minecraft.client.renderer.texture.TextureContents;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * An out-of-jar texture with animation metadata must degrade to the missing texture instead of throwing: 26.x
 * TextureManager#reload propagates any exception from loadContents and fails the whole resource reload.
 */
public class OutOfJarTextureTest
{
    /** 1x1 opaque PNG. */
    private static final byte[] PNG_1X1 = Base64.getDecoder()
        .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==");

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void animatedTextureLoadsAsMissingInsteadOfThrowing() throws Exception
    {
        final Path png = tmp.getRoot().toPath().resolve("animated.png");
        Files.write(png, PNG_1X1);
        Files.writeString(tmp.getRoot().toPath().resolve("animated.png.mcmeta"), "{\"animation\":{\"frametime\":2}}");

        final OutOfJarResourceLocation resLoc = OutOfJarResourceLocation.of("blockui_test", png);
        try (TextureContents contents = OutOfJarTexture.readContents(OutOfJarResourceLocation.getResourceHandle(resLoc, null)))
        {
            assertNotNull(contents.image());
            // missing texture is 16x16; the real image is 1x1
            assertEquals(16, contents.image().getWidth());
        }
    }

    @Test
    public void plainTextureStillLoads() throws Exception
    {
        final Path png = tmp.getRoot().toPath().resolve("plain.png");
        Files.write(png, PNG_1X1);

        final OutOfJarResourceLocation resLoc = OutOfJarResourceLocation.of("blockui_test", png);
        try (TextureContents contents = OutOfJarTexture.readContents(OutOfJarResourceLocation.getResourceHandle(resLoc, null)))
        {
            assertEquals(1, contents.image().getWidth());
        }
    }
}
