/*
 * Pack Keeper - keeps a modpack's resource packs on without anyone's options.txt.
 * Copyright (C) 2026 Rusty Shackleford and nfx
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.nfx.packkeeper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.nfx.packkeeper.domain.Selection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The modpack's list, {@code config/packkeeper.json}:
 * <pre>{ "resource_packs": [ "on-top.zip", "under-it", "at-the-bottom.zip" ] }</pre>
 * -- file names under the resource-packs folder, first on top. A missing
 * file is written out empty so a pack author finds the shape; a broken one
 * is reported and treated as empty, never rewritten.
 */
final class KeeperConfig {
    static final String FILE_NAME = "packkeeper.json";
    private static final Logger LOG = LoggerFactory.getLogger("Pack Keeper");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private KeeperConfig() {}

    /** The file's shape, as Gson sees it. */
    private static final class Shape {
        List<String> resource_packs = List.of();
    }

    /** effects: reads {@code file}; writes an empty one if it does not exist */
    static Selection read(Path file) {
        if (!Files.exists(file)) {
            write(file);
            return new Selection(List.of());
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            Shape shape = GSON.fromJson(reader, Shape.class);
            List<String> names = shape == null || shape.resource_packs == null ? List.of() : shape.resource_packs;
            return new Selection(names);
        } catch (IOException | JsonParseException | IllegalArgumentException e) {
            LOG.error("Cannot read {}; keeping no packs on: {}", file, e.getMessage());
            return new Selection(List.of());
        }
    }

    private static void write(Path file) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(new Shape(), writer);
            }
        } catch (IOException e) {
            LOG.warn("Cannot write an empty {}: {}", file, e.getMessage());
        }
    }
}
