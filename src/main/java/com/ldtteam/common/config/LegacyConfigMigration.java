package com.ldtteam.common.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.FileNotFoundAction;
import com.electronwill.nightconfig.toml.TomlParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ValueSpec;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * port26.3: one-time migration of config files written by older NeoForge versions.
 * <p>
 * NeoForge 26.3.0.37 renamed the config types (SERVER -> SYNCED, COMMON -> LOCAL) and with them the file names
 * ({@code <mod>-server.toml} -> {@code <mod>-synced.toml}, {@code <mod>-common.toml} -> {@code <mod>-local.toml}) and the
 * per-world override folder ({@code <world>/serverconfig} -> {@code <world>/syncedconfig}), without moving existing
 * files. Without this, every setting a player changed on 1.21 is silently replaced by defaults.
 * <p>
 * Copies are only made when the new file does not exist yet; legacy files are never modified or deleted.
 * Client configs keep their name and need no migration.
 */
public final class LegacyConfigMigration
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyConfigMigration.class);

    /** 1.21 per-world server config folder. */
    private static final LevelResource LEGACY_WORLD_CONFIG = new LevelResource("serverconfig");

    /** 26.3 per-world synced config override folder (see NeoForge ServerLifecycleHooks). */
    private static final LevelResource WORLD_CONFIG = new LevelResource("syncedconfig");

    private LegacyConfigMigration()
    {
    }

    /**
     * Call right after registering a config, during mod construction (before NeoForge loads config files).
     *
     * @param modConfig the registered config
     * @param modBus    the mod event bus
     */
    public static void migrate(final ModConfig modConfig, final IEventBus modBus)
    {
        final String legacyName = legacyFileName(modConfig);
        if (legacyName == null)
        {
            return;
        }

        final Path configDir = FMLPaths.CONFIGDIR.get();
        final Path target = configDir.resolve(modConfig.getFileName());
        if (modConfig.getType() == ModConfig.Type.LOCAL)
        {
            copyIfAbsent(configDir.resolve(legacyName), target);
            return;
        }

        // SYNCED: the global base file used to be the defaultconfigs template (1.21) or config/<mod>-server.toml (26.x before .37).
        if (!copyIfAbsent(configDir.resolve(legacyName), target))
        {
            copyIfAbsent(FMLPaths.GAMEDIR.get().resolve("defaultconfigs").resolve(legacyName), target);
        }

        // A dedicated server's world is known up front, so move its per-world file before it is loaded.
        if (FMLEnvironment.getDist().isDedicatedServer())
        {
            final Path world = FMLPaths.GAMEDIR.get().resolve(dedicatedLevelName());
            copyIfAbsent(world.resolve(LEGACY_WORLD_CONFIG.id()).resolve(legacyName), world.resolve(WORLD_CONFIG.id()).resolve(modConfig.getFileName()));
        }

        // Integrated servers pick the world at runtime: there is no hook before NeoForge loads the synced config, so migrate on load.
        modBus.addListener(ModConfigEvent.Loading.class, event -> {
            if (event.getConfig() == modConfig)
            {
                migrateWorldOnLoad(modConfig, legacyName);
            }
        });
    }

    /**
     * @return the pre-.37 file name of a config registered with the default name, or null when nothing was renamed
     */
    @Nullable
    public static String legacyFileName(final ModConfig modConfig)
    {
        final String legacyExtension = switch (modConfig.getType())
        {
            case SYNCED -> "server";
            case LOCAL -> "common";
            default -> null;
        };
        final String defaultName = modConfig.getModId() + "-" + modConfig.getType().extension() + ".toml";
        if (legacyExtension == null || !modConfig.getFileName().equals(defaultName))
        {
            return null;
        }
        return modConfig.getModId() + "-" + legacyExtension + ".toml";
    }

    /**
     * Copies every legacy value the spec still defines into the target config, then corrects out-of-range values.
     *
     * @return number of values taken from the legacy config
     */
    public static int applyLegacyValues(final ModConfigSpec spec, final CommentedConfig target, final UnmodifiableConfig legacy)
    {
        final int count = applyLegacyValues(spec.getSpec(), target, legacy, new ArrayList<>());
        if (!spec.isCorrect(target))
        {
            spec.correct(target);
        }
        return count;
    }

    private static int applyLegacyValues(final UnmodifiableConfig spec, final CommentedConfig target, final UnmodifiableConfig legacy, final List<String> path)
    {
        int count = 0;
        for (final Map.Entry<String, Object> entry : legacy.valueMap().entrySet())
        {
            path.add(entry.getKey());
            final Object specValue = spec.get(path);
            if (entry.getValue() instanceof UnmodifiableConfig section && specValue instanceof UnmodifiableConfig)
            {
                count += applyLegacyValues(spec, target, section, path);
            }
            else if (specValue instanceof ValueSpec)
            {
                target.set(path, entry.getValue());
                count++;
            }
            path.removeLast();
        }
        return count;
    }

    private static void migrateWorldOnLoad(final ModConfig modConfig, final String legacyName)
    {
        final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        final IConfigSpec.ILoadedConfig loaded = modConfig.getLoadedConfig();
        if (server == null || loaded == null || !(modConfig.getSpec() instanceof ModConfigSpec spec))
        {
            return;
        }

        final Path legacy = server.getWorldPath(LEGACY_WORLD_CONFIG).resolve(legacyName);
        final Path target = server.getWorldPath(WORLD_CONFIG).resolve(modConfig.getFileName());
        if (Files.exists(target) || !Files.isRegularFile(legacy))
        {
            return;
        }

        try
        {
            final CommentedConfig legacyConfig = new TomlParser().parse(legacy, FileNotFoundAction.THROW_ERROR);
            Files.createDirectories(target.getParent());
            Files.copy(legacy, target);

            // NeoForge already loaded the global file for this session; apply the world's values in memory so they take effect now.
            final int count = applyLegacyValues(spec, loaded.config(), legacyConfig);
            spec.afterReload();
            LOGGER.info("Migrated {} legacy config values from {} to {}", count, legacy, target);
        }
        catch (final IOException | RuntimeException e)
        {
            LOGGER.error("Failed to migrate legacy config {} to {}", legacy, target, e);
        }
    }

    /**
     * @return true when the legacy file was copied to the (previously missing) target
     */
    private static boolean copyIfAbsent(final Path legacy, final Path target)
    {
        if (Files.exists(target) || !Files.isRegularFile(legacy))
        {
            return false;
        }
        try
        {
            Files.createDirectories(target.getParent());
            Files.copy(legacy, target);
            LOGGER.info("Migrated legacy config {} to {}", legacy, target);
            return true;
        }
        catch (final IOException e)
        {
            LOGGER.error("Failed to migrate legacy config {} to {}", legacy, target, e);
            return false;
        }
    }

    private static String dedicatedLevelName()
    {
        final Path properties = FMLPaths.GAMEDIR.get().resolve("server.properties");
        final Properties props = new Properties();
        if (Files.isRegularFile(properties))
        {
            try (InputStream in = Files.newInputStream(properties))
            {
                props.load(in);
            }
            catch (final IOException e)
            {
                LOGGER.warn("Could not read {} for legacy config migration", properties, e);
            }
        }
        return props.getProperty("level-name", "world");
    }
}
