/* Copyright (C) 2026 Rusty Shackleford and nfx
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package com.nfx.packkeeper.domain;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Applies a distance update before Minecraft loads options, using the real filesystem.
 * A local receipt reserves each id before writing anything: a failed or interrupted
 * attempt is never replayed over settings the player may subsequently change.
 * AF/RI: stateless service; receipts are local instance data, never pack overrides.
 */
public final class OptionsUpdates {
    private OptionsUpdates() {}

    /** Whether an update was applied, already completed, or previously interrupted. */
    public enum Result { APPLIED, ALREADY_APPLIED, INCOMPLETE }

    /**
     * requires: gameDir is this client's writable game directory; update is non-null.
     * effects: reserves update.id, backs up existing options, then atomically replaces
     * only the two requested values and marks completion. An existing reservation is
     * never replayed. Missing options are created. Unrelated files are untouched.
     * throws: IOException on filesystem failure; a reserved id remains reserved.
     */
    public static Result applyOnce(Path gameDir, DistanceUpdate update) throws IOException {
        Path receipts = gameDir.resolve("config/packkeeper/applied-options");
        Files.createDirectories(receipts);
        Path receipt = receipts.resolve(update.id());
        try {
            Files.createDirectory(receipt);
        } catch (FileAlreadyExistsException e) {
            Path complete = receipt.resolve("complete");
            return Files.isRegularFile(complete) ? Result.ALREADY_APPLIED : Result.INCOMPLETE;
        }
        Path options = gameDir.resolve("options.txt");
        boolean existed = Files.exists(options);
        String original = existed ? Files.readString(options) : "";
        if (existed) {
            Files.copy(options, receipt.resolve("options.before.txt"));
        }
        Files.writeString(receipt.resolve("update.txt"), update.toString() + "\n");
        Path temporary = Files.createTempFile(gameDir, ".packkeeper-options-", ".tmp");
        try {
            Files.writeString(temporary, update.applyTo(original));
            try {
                Files.move(temporary, options, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, options, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.createFile(receipt.resolve("complete"));
            return Result.APPLIED;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
