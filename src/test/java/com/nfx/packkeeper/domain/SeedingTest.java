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
package com.nfx.packkeeper.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Partitions. Seeds: none; one missing; one present; a mix, order kept;
 * the same seed twice. Paths: plain nested, a single segment, empty,
 * absolute, a drive, a {@code ..} or {@code .} segment, an empty segment,
 * a trailing slash, backslashes.
 */
final class SeedingTest {

    @Test
    void nothingToSeedIsAnEmptyPlan() {
        Seeding.Plan plan = Seeding.plan(List.of(), p -> false);
        assertEquals(List.of(), plan.copy());
        assertEquals(List.of(), plan.kept());
        assertEquals(List.of(), plan.refused());
    }

    @Test
    void aMissingTargetIsCopiedAndAPresentOneIsKeptInTheOrderGiven() {
        Set<String> present = Set.of("shaderpacks/Comp.zip.txt");
        Seeding.Plan plan = Seeding.plan(List.of("config/iris.properties", "shaderpacks/Comp.zip.txt", "config/other.toml"), present::contains);
        assertEquals(List.of("config/iris.properties", "config/other.toml"), plan.copy());
        assertEquals(List.of("shaderpacks/Comp.zip.txt"), plan.kept());
        assertEquals(List.of(), plan.refused());
    }

    @Test
    void aPathThatCouldLeaveTheGameFolderIsRefused() {
        Seeding.Plan plan = Seeding.plan(List.of("../outside.txt", "/etc/passwd", "C:/x.txt", "a/../b", "a/./b", "a//b", "a/", "", "a\\b", "ok/fine.txt"), p -> false);
        assertEquals(List.of("ok/fine.txt"), plan.copy());
        assertEquals(List.of("../outside.txt", "/etc/passwd", "C:/x.txt", "a/../b", "a/./b", "a//b", "a/", "", "a\\b"), plan.refused());
    }

    @Test
    void plainPathsAreNestedOrASingleName() {
        assertTrue(Seeding.isPlain("options.txt"));
        assertTrue(Seeding.isPlain("shaderpacks/ComplementaryUnbound_r5.8.1.zip.txt"));
        assertFalse(Seeding.isPlain(null));
        assertFalse(Seeding.isPlain(".."));
        assertFalse(Seeding.isPlain("."));
    }
}
