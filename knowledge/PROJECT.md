---
title: Pack Keeper — project
type: overview
layer: store
tags: [overview]
---

# Pack Keeper

Version 1.2.0 adds an optional, named one-time update of render distance and
entity distance for existing and new installs. Rusty approved applying it once
to everyone, then corrected the requested client scale to 200%. Other settings
and players' subsequent adjustments remain untouched. See [D-0004](decisions/D-0004.md).

## What this is

A client-only NeoForge 1.21.1 mod that keeps a modpack's resource packs
switched on, in the modpack's order, from a list in the modpack's config
(`config/packkeeper.json`). It exists so the modpack never has to ship
anyone's `options.txt`: the Modrinth-format pack we hand out through the
Mod Hub used to carry one to get the resource packs enabled, and every
update of the pack overwrote every player's keybinds, video settings and
sound levels with it. Rusty, 2026-09-06.

## Shape

Three source sets, one direction of dependency:

- `domain` -- `Selection`: the listed packs, their order, which are
  missing, and how to impose them on the player's saved pack list. JDK
  only, plain JUnit.
- `main` -- `PackKeeper` hands the listed packs to the game as *required*
  at pack discovery and puts the player's list in order right before the
  game reads it; `KeeperConfig` reads the JSON.
- `gametest` -- a mod of its own carrying `PackBooth`, the check through
  the real path: a client boots on fixtures with the wrong order saved and
  asserts what the game selected and rendered. There is no gametest
  server run: a client-only mod has nothing for it to do. `PackTrial`
  (`runPackTrial`) does the same on a real modpack laid out in `run/trial`.
- `domain.DistanceUpdate` preserves unrelated options text; `OptionsUpdates`
  applies it through real filesystem operations with a backup and local receipt.
  `ClientOptionsUpdater` reads the optional config at the existing bootstrap hook.

Decisions: the packs are re-registered under the game's own `file/` ids,
required, and the order is imposed on the options list (`D-0001`); they
are fixed in position, and a listed pack for another game version is
accepted on the player's behalf (`D-0002`).

## How it is verified

`./gradlew check`: the JUnit tests against `domain` and the booth
(`runPhotoBooth`, needs a display; `-PskipBooth` leaves it out). The booth
writes `booth: PASS`/`FAIL` lines to `run/booth/logs/latest.log` and a
photo of the pack screen to `run/booth/screenshots/`; `runOptionsRelaunch`
then boots the same instance to verify later player changes survive. The gate fails on
any FAIL or no PASS.
