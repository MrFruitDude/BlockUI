package com.ldtteam.blockui.util.texture;

import com.ldtteam.blockui.util.resloc.OutOfJarResourceLocation;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inspired by {@link SimpleTexture}
 */
public class OutOfJarTexture extends ReloadableTexture
{
    private static final Logger LOGGER = LoggerFactory.getLogger(OutOfJarTexture.class);
    private static final Set<String> WARNED_ANIMATED = ConcurrentHashMap.newKeySet();

    protected final OutOfJarResourceLocation resourceLocation;

    public OutOfJarTexture(final OutOfJarResourceLocation resourceLocation)
    {
        super(resourceLocation);
        this.resourceLocation = resourceLocation;
    }

    @Override
    public TextureContents loadContents(final ResourceManager resourceManager) throws IOException
    {
        return readContents(OutOfJarResourceLocation.getResourceHandle(resourceLocation, resourceManager));
    }

    static TextureContents readContents(final Resource resource) throws IOException
    {
        // Since 26.1 an animated sprite can't be loaded without a texture atlas. Degrade to the missing texture instead of
        // throwing: TextureManager#reload propagates any exception from here and fails the whole resource reload.
        if (resource.metadata().getSection(AnimationMetadataSection.TYPE).isPresent())
        {
            if (WARNED_ANIMATED.add(resource.sourcePackId()))
            {
                LOGGER.warn("Animated out-of-jar textures are not supported since 26.1, using missing texture: {}", resource.sourcePackId());
            }
            return TextureContents.createMissing();
        }

        final TextureMetadataSection textureMeta = resource.metadata().getSection(TextureMetadataSection.TYPE).orElse(null);
        final NativeImage nativeImage;

        try (var is = resource.open())
        {
            nativeImage = NativeImage.read(is);
        }
        catch (final NoSuchFileException e)
        {
            // rethrow to java.io since vanilla stupid
            throw new FileNotFoundException(e.getMessage());
        }
        return new TextureContents(nativeImage, textureMeta);
    }

    public static AbstractTexture assertLoadedDefaultManagers(final Identifier resLoc)
    {
        return assertLoaded(resLoc, Minecraft.getInstance().getTextureManager(), Minecraft.getInstance().getResourceManager());
    }

    /**
     * Checks whether given resLoc should be loaded into given textureManager as outOfJar or sprite texture
     *
     * @return valid texture instance (including missing texture)
     */
    public static AbstractTexture assertLoaded(final Identifier resLoc, final TextureManager textureManager, final ResourceManager resourceManager)
    {
        if (!(resLoc instanceof final OutOfJarResourceLocation outOfJarResLoc))
        {
            // if not out-of-jar use normal vanilla systems
            return textureManager.getTexture(resLoc);
        }

        final AbstractTexture current = textureManager.getTexture(resLoc);
        if (IsOurTexture.isOur(current))
        {
            return current;
        }

        final OutOfJarTexture outOfJarTexture = new OutOfJarTexture(outOfJarResLoc);
        textureManager.registerAndLoad(outOfJarResLoc, outOfJarTexture); // this causes texture to load

        return outOfJarTexture;
    }
}
