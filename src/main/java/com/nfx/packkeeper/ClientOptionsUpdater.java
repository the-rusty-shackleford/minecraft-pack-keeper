/* Copyright (C) 2026 Rusty Shackleford and nfx
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package com.nfx.packkeeper;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.nfx.packkeeper.domain.DistanceUpdate;
import com.nfx.packkeeper.domain.OptionsUpdates;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reads the optional, narrowly scoped options update before Minecraft loads options. */
public final class ClientOptionsUpdater {
    private static final Logger LOG = LoggerFactory.getLogger("Pack Keeper");
    private ClientOptionsUpdater() {}

    private record Update(String id, int render_distance, double entity_distance_scaling) {}
    private record Config(Update options_update) {}

    /**
     * requires: gameDir is the client instance directory.
     * effects: reads packkeeper.json and applies its optional update at most once;
     * logs malformed configuration or I/O failures without aborting the game.
     */
    public static void apply(Path gameDir) {
        Path config = gameDir.resolve("config/packkeeper.json");
        if (!Files.isRegularFile(config)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(config)) {
            Config parsed = new Gson().fromJson(reader, Config.class);
            if (parsed == null || parsed.options_update() == null) {
                return;
            }
            Update value = parsed.options_update();
            DistanceUpdate update = new DistanceUpdate(value.id(), value.render_distance(), value.entity_distance_scaling());
            OptionsUpdates.Result result = OptionsUpdates.applyOnce(gameDir, update);
            if (result == OptionsUpdates.Result.INCOMPLETE) {
                LOG.warn("Options update {} has an incomplete local receipt; preserving current preferences. See config/packkeeper/applied-options/{}",
                        update.id(), update.id());
            } else {
                LOG.info("Options update {}: {} (render distance {}, entity scaling {}); other preferences preserved",
                        update.id(), result, update.renderDistance(), update.entityDistanceScaling());
            }
        } catch (IOException | JsonParseException | IllegalArgumentException | NullPointerException e) {
            LOG.error("Cannot apply the modpack's options update; keeping current preferences: {}", e.getMessage());
        }
    }
}
