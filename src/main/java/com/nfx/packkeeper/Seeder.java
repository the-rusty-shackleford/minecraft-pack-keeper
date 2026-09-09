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

import com.nfx.packkeeper.domain.Seeding;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Puts the modpack's seed files in place: everything under
 * {@code config/packkeeper/seed/} is copied to the same path under the
 * game folder, but only where nothing exists yet. A modpack ships its
 * shader settings and its shader-pack selection there, so a new player
 * starts with them and an update never overwrites anyone's own.
 *
 * <p>Runs before the game reads any of it: from the mixin plugin, at
 * mixin bootstrap, which is before the game's options -- and Iris's
 * properties, read alongside them -- exist. Never throws: a modpack that
 * cannot be seeded is logged and the game goes on.
 */
public final class Seeder {
    private Seeder() {}

    private static final Logger LOG = LoggerFactory.getLogger("Pack Keeper");
    /** Where the seeds live, under the game folder. */
    public static final String SEED_FOLDER = "config/packkeeper/seed";

    /** What was done: how many seeds were copied, kept and refused. */
    public record Report(int copied, int kept, int refused) {}

    /**
     * effects: copies every seed under {@code gameDir/config/packkeeper/seed}
     * to its place under {@code gameDir} where nothing is there yet, logs
     * what it did, and returns the counts; nothing at all if there is no
     * seed folder
     */
    public static Report seed(Path gameDir) {
        Path root = gameDir.resolve(SEED_FOLDER);
        if (!Files.isDirectory(root)) {
            return new Report(0, 0, 0);
        }
        List<String> seeds = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .map(file -> root.relativize(file).toString().replace(java.io.File.separatorChar, '/'))
                    .sorted()
                    .forEach(seeds::add);
        } catch (IOException e) {
            LOG.error("Cannot read the seed folder {}: {}", root, e.getMessage());
            return new Report(0, 0, 0);
        }
        Seeding.Plan plan = Seeding.plan(seeds, rel -> Files.exists(gameDir.resolve(rel)));
        int copied = 0;
        for (String rel : plan.copy()) {
            Path target = gameDir.resolve(rel);
            try {
                Files.createDirectories(target.getParent());
                Files.copy(root.resolve(rel), target);
                copied++;
                LOG.info("Seeded {} from the modpack; it is yours now and will not be touched again", rel);
            } catch (IOException e) {
                LOG.error("Cannot seed {}: {}", rel, e.getMessage());
            }
        }
        for (String rel : plan.refused()) {
            LOG.warn("Refusing to seed {}: not a plain path under the game folder", rel);
        }
        if (!plan.kept().isEmpty()) {
            LOG.info("Kept {} file(s) the player already has: {}", plan.kept().size(), plan.kept());
        }
        return new Report(copied, plan.kept().size(), plan.refused().size());
    }
}
