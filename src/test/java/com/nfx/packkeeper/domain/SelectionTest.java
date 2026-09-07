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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Partitions: empty / one / several names; bottom-to-top reversed; missing
 * none / some / all; imposing on an empty list, on a list with only the
 * game's packs, on one where ours sit scattered and out of order, on one
 * with the player's own file packs above and below, with some of ours
 * absent; the RI's edges -- blank, a path, a parent reference, a repeat.
 */
class SelectionTest {
    private static final Selection THREE = new Selection(List.of("top.zip", "middle", "bottom.zip"));

    @Test
    void theFirstListedIsOnTop() {
        assertEquals(List.of("bottom.zip", "middle", "top.zip"), THREE.bottomToTop());
        assertEquals(List.of(), new Selection(List.of()).bottomToTop());
        assertEquals(List.of("only.zip"), new Selection(List.of("only.zip")).bottomToTop());
    }

    @Test
    void idsAreFilePacks() {
        assertEquals("file/top.zip", Selection.id("top.zip"));
    }

    @Test
    void missingNamesAreReportedInListOrder() {
        Selection s = new Selection(List.of("a.zip", "b", "c.zip"));
        Set<String> present = Set.of("b");
        assertEquals(List.of("a.zip", "c.zip"), s.missing(present::contains));
        assertEquals(List.of(), s.missing(name -> true));
        assertEquals(List.of("a.zip", "b", "c.zip"), s.missing(name -> false));
    }

    @Test
    void imposedOnAnEmptyListIsJustOurs() {
        assertEquals(List.of("file/bottom.zip", "file/middle", "file/top.zip"), THREE.imposedOn(List.of(), name -> true));
    }

    @Test
    void oursGoAboveTheGamesOwnPacks() {
        List<String> current = List.of("vanilla", "mod_resources", "fabric");
        assertEquals(List.of("vanilla", "mod_resources", "fabric", "file/bottom.zip", "file/middle", "file/top.zip"),
                THREE.imposedOn(current, name -> true));
    }

    @Test
    void scatteredAndMisorderedEntriesAreGatheredIntoOurOrder() {
        List<String> current = List.of("file/top.zip", "vanilla", "file/bottom.zip", "mod_resources", "file/middle");
        assertEquals(List.of("vanilla", "mod_resources", "file/bottom.zip", "file/middle", "file/top.zip"),
                THREE.imposedOn(current, name -> true));
    }

    @Test
    void thePlayersOwnFilePacksStayOnTop() {
        List<String> current = List.of("vanilla", "mod_resources", "file/middle", "file/mine.zip", "fabric", "file/top.zip", "file/theirs");
        assertEquals(List.of("vanilla", "mod_resources", "file/bottom.zip", "file/middle", "file/top.zip", "file/mine.zip", "fabric", "file/theirs"),
                THREE.imposedOn(current, name -> true));
    }

    @Test
    void absentPacksAreLeftOut() {
        List<String> current = List.of("vanilla", "file/middle");
        assertEquals(List.of("vanilla", "file/bottom.zip", "file/top.zip"),
                THREE.imposedOn(current, name -> !name.equals("middle")));
    }

    @Test
    void imposingDoesNotTouchTheGivenList() {
        List<String> current = new java.util.ArrayList<>(List.of("vanilla", "file/top.zip"));
        THREE.imposedOn(current, name -> true);
        assertEquals(List.of("vanilla", "file/top.zip"), current);
    }

    @Test
    void theRepresentationInvariantIsEnforced() {
        assertThrows(IllegalArgumentException.class, () -> new Selection(List.of("")));
        assertThrows(IllegalArgumentException.class, () -> new Selection(List.of("  ")));
        assertThrows(IllegalArgumentException.class, () -> new Selection(List.of("sub/pack.zip")));
        assertThrows(IllegalArgumentException.class, () -> new Selection(List.of("..")));
        assertThrows(IllegalArgumentException.class, () -> new Selection(List.of("a.zip", "a.zip")));
    }

    @Test
    void theSelectionIsImmutable() {
        List<String> names = new java.util.ArrayList<>(List.of("a.zip"));
        Selection s = new Selection(names);
        names.add("b.zip");
        assertEquals(List.of("a.zip"), s.names());
    }
}
