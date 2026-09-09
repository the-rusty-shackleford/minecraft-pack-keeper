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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Which of a modpack's seed files to put in place: a seed is a file the
 * pack wants a player to <em>start</em> with -- shader settings, a shader
 * pack selection -- but never to have overwritten once it is theirs. So a
 * seed is copied only where nothing exists yet; a file the player already
 * has, whatever is in it, is kept.
 *
 * <p>Seeds are named by their path under the game folder, with forward
 * slashes. A path that would land outside the game folder is refused.
 */
public final class Seeding {
    private Seeding() {}

    /**
     * What to do with each seed.
     *
     * @param copy    seeds whose target does not exist: to be copied, in the order given
     * @param kept    seeds whose target exists: left alone
     * @param refused seeds whose path is not a plain path under the game folder
     */
    public record Plan(List<String> copy, List<String> kept, List<String> refused) {}

    /**
     * effects: returns the plan for {@code seeds}, {@code exists} saying
     * whether the target of a path is already there: a seed is copied only
     * if it is not; a path that is empty, absolute, has a {@code ..} or an
     * empty segment, or uses backslashes is refused
     */
    public static Plan plan(List<String> seeds, Predicate<String> exists) {
        List<String> copy = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        List<String> refused = new ArrayList<>();
        for (String seed : seeds) {
            if (!isPlain(seed)) {
                refused.add(seed);
            } else if (exists.test(seed)) {
                kept.add(seed);
            } else {
                copy.add(seed);
            }
        }
        return new Plan(List.copyOf(copy), List.copyOf(kept), List.copyOf(refused));
    }

    /** effects: returns whether {@code path} is a plain relative path: no drive, no root, no {@code .} or {@code ..}, no empty segment, forward slashes only */
    public static boolean isPlain(String path) {
        if (path == null || path.isEmpty() || path.contains("\\") || path.startsWith("/") || path.contains(":")) {
            return false;
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }
}
