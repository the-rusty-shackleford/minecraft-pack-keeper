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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The packs the modpack keeps on, as written in its config: file names
 * under the resource-packs folder, listed the way the game's own screen
 * shows them -- <em>first is on top</em>, and the top pack wins where two
 * packs paint the same thing.
 *
 * <p>Immutable. RI: every name is non-blank, has no path separators, and
 * appears once; order is as given.
 *
 * @param names the pack file names, first on top
 */
public record Selection(List<String> names) {

    /** The game's id for a pack found in the resource-packs folder. */
    public static final String FILE_PREFIX = "file/";

    /**
     * @throws IllegalArgumentException if a name is blank, contains a path
     *         separator or a parent reference, or repeats
     */
    public Selection {
        Set<String> seen = new LinkedHashSet<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("a pack name must not be blank");
            }
            if (name.contains("/") || name.contains("\\") || name.equals("..") || name.equals(".")) {
                throw new IllegalArgumentException("a pack name is a file name under the resource-packs folder, not a path: " + name);
            }
            if (!seen.add(name)) {
                throw new IllegalArgumentException("a pack is listed twice: " + name);
            }
        }
        names = List.copyOf(names);
    }

    /** effects: returns the game's id for {@code name}: {@code file/} plus the name */
    public static String id(String name) {
        return FILE_PREFIX + name;
    }

    /**
     * effects: returns the names bottom to top -- the order the game's
     * options list them in, where the last entry is the one on top
     */
    public List<String> bottomToTop() {
        List<String> order = new ArrayList<>(names);
        Collections.reverse(order);
        return order;
    }

    /**
     * effects: returns the names that {@code exists} says are not there, in
     * list order -- the ones a log line should name so a pack author sees
     * a typo or a pack that never made it into the modpack
     */
    public List<String> missing(Predicate<String> exists) {
        return names.stream().filter(name -> !exists.test(name)).toList();
    }

    /**
     * The player's pack list with this selection imposed on it.
     *
     * <p>The game keeps the selected packs as a list of ids, bottom to top.
     * This selection's packs are taken out of wherever the list had them
     * and put back as one block in this selection's order, sitting above
     * everything that is not a file pack (the game's own, the mods') and
     * below any file pack the player chose for themselves -- a pack the
     * player added stays on top, as they put it.
     *
     * @param current the player's list, bottom to top; not modified
     * @param present the names of this selection that are actually there;
     *        the others are left out of the result altogether
     * @return the new list, bottom to top
     */
    public List<String> imposedOn(List<String> current, Predicate<String> present) {
        List<String> ours = bottomToTop().stream().filter(present).map(Selection::id).toList();
        List<String> rest = new ArrayList<>(current);
        rest.removeAll(names.stream().map(Selection::id).toList());
        int at = rest.size();
        for (int i = 0; i < rest.size(); i++) {
            if (rest.get(i).startsWith(FILE_PREFIX)) {
                at = i;
                break;
            }
        }
        rest.addAll(at, ours);
        return List.copyOf(rest);
    }
}
