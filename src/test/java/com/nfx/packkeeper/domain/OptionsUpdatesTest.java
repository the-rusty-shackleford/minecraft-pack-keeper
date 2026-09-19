/* Copyright (C) 2026 Rusty Shackleford and nfx
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package com.nfx.packkeeper.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Partitions: existing/missing options; existing/missing/duplicate target keys;
 * LF/CRLF/no final newline; unrelated vanilla/mod keys; fresh/repeated/new id;
 * player changes after application; invalid ids/ranges; interrupted/failed writes.
 * Filesystem checks use real temporary directories, without mock backends.
 */
final class OptionsUpdatesTest {
    @TempDir Path game;
    private static final DistanceUpdate UPDATE = new DistanceUpdate("visibility-1", 24, 2.0);

    @Test void preservesEveryUnrelatedCharacterAndWindowsNewlines() {
        String before = "soundCategory_master:0.0\r\nrenderDistance:12\r\nkey_key.jump:key.keyboard.j\r\n"
                + "entityDistanceScaling:1.0\r\nmod:custom:value\r\ngamma:0.73";
        assertEquals(before.replace("renderDistance:12", "renderDistance:24")
                .replace("entityDistanceScaling:1.0", "entityDistanceScaling:2.0"), UPDATE.applyTo(before));
    }

    @Test void appendsMissingKeysAndReplacesDuplicates() {
        assertEquals("renderDistance:24\nentityDistanceScaling:2.0\n", UPDATE.applyTo(""));
        assertEquals("gamma:0.73\nrenderDistance:24\nentityDistanceScaling:2.0\n", UPDATE.applyTo("gamma:0.73"));
        assertEquals("renderDistance:24\nrenderDistance:24\nentityDistanceScaling:2.0\n",
                UPDATE.applyTo("renderDistance:8\nrenderDistance:16\n"));
        assertEquals("gamma:0.73\r\nrenderDistance:24\r\nentityDistanceScaling:2.0\r\n",
                UPDATE.applyTo("gamma:0.73\r\n"));
    }

    @Test void validatesIdsAndMinecraftRanges() {
        for (String id : List.of("", ".", "..", "../other", "/outside", "a/b", "a\\b", "a".repeat(81))) {
            assertThrows(IllegalArgumentException.class, () -> new DistanceUpdate(id, 24, 2.0));
        }
        for (int distance : List.of(1, 33)) {
            assertThrows(IllegalArgumentException.class, () -> new DistanceUpdate("ok", distance, 2.0));
        }
        for (double scale : List.of(0.49, 5.01, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException.class, () -> new DistanceUpdate("ok", 24, scale));
        }
        assertDoesNotThrow(() -> new DistanceUpdate("boundary-min", 2, 0.5));
        assertDoesNotThrow(() -> new DistanceUpdate("boundary-max", 32, 5.0));
    }

    @Test void backsUpExistingOptionsAndNeverReplaysOverPlayerChanges() throws IOException {
        Path options = game.resolve("options.txt");
        String original = "renderDistance:12\nentityDistanceScaling:1.0\nsoundCategory_master:0.0\n";
        Files.writeString(options, original);
        assertEquals(OptionsUpdates.Result.APPLIED, OptionsUpdates.applyOnce(game, UPDATE));
        assertEquals(UPDATE.applyTo(original), Files.readString(options));
        assertEquals(original, Files.readString(game.resolve("config/packkeeper/applied-options/visibility-1/options.before.txt")));
        String personal = "renderDistance:10\nentityDistanceScaling:0.75\nsoundCategory_master:0.0\n";
        Files.writeString(options, personal);
        assertEquals(OptionsUpdates.Result.ALREADY_APPLIED, OptionsUpdates.applyOnce(game, UPDATE));
        // Even editing the shipped values under an existing id does not replay it.
        assertEquals(OptionsUpdates.Result.ALREADY_APPLIED,
                OptionsUpdates.applyOnce(game, new DistanceUpdate("visibility-1", 32, 5.0)));
        assertEquals(personal, Files.readString(options));
    }

    @Test void newInstallsAndAnExplicitlyNewUpdateIdAreSupported() throws IOException {
        assertEquals(OptionsUpdates.Result.APPLIED, OptionsUpdates.applyOnce(game, UPDATE));
        assertEquals("renderDistance:24\nentityDistanceScaling:2.0\n", Files.readString(game.resolve("options.txt")));
        assertFalse(Files.exists(game.resolve("config/packkeeper/applied-options/visibility-1/options.before.txt")));
        DistanceUpdate next = new DistanceUpdate("visibility-2", 16, 1.0);
        assertEquals(OptionsUpdates.Result.APPLIED, OptionsUpdates.applyOnce(game, next));
        assertEquals("renderDistance:16\nentityDistanceScaling:1.0\n", Files.readString(game.resolve("options.txt")));
    }

    @Test void interruptedAttemptCannotOverwriteLaterPreferences() throws IOException {
        Files.createDirectories(game.resolve("config/packkeeper/applied-options/visibility-1"));
        Files.writeString(game.resolve("options.txt"), "renderDistance:10\n");
        assertEquals(OptionsUpdates.Result.INCOMPLETE, OptionsUpdates.applyOnce(game, UPDATE));
        assertEquals("renderDistance:10\n", Files.readString(game.resolve("options.txt")));
    }

    @Test void actualFilesystemFailureLeavesAnIncompleteReservation() throws IOException {
        Files.createDirectory(game.resolve("options.txt"));
        assertThrows(IOException.class, () -> OptionsUpdates.applyOnce(game, UPDATE));
        Files.delete(game.resolve("options.txt"));
        Files.writeString(game.resolve("options.txt"), "renderDistance:10\n");
        assertEquals(OptionsUpdates.Result.INCOMPLETE, OptionsUpdates.applyOnce(game, UPDATE));
        assertEquals("renderDistance:10\n", Files.readString(game.resolve("options.txt")));
    }
}
