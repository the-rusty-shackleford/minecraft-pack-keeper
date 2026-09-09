# Pack Keeper

A client-only [NeoForge](https://neoforged.net/) 1.21.1 mod that keeps a
modpack's resource packs switched on, in the modpack's order, from a list
in the modpack's config. The modpack never has to ship anyone's
`options.txt`, so an update never overwrites a player's keybinds, video
settings or sound levels.

## For modpack authors

Put the packs in `resourcepacks/` as usual and list them in
`config/packkeeper.json`, **first on top** -- the way the game's own pack
screen shows them, where the top pack wins wherever two packs paint the
same thing:

```json
{
  "resource_packs": [
    "SubWild-Pixlli-Patch.zip",
    "Pixlli V57 26.3-1.13 128x.zip",
    "3D Default 1.20+ v1.15.0.zip"
  ]
}
```

Names are file names under `resourcepacks/` (a `.zip` or a folder), not
paths. On every launch the listed packs are selected and marked as kept:
the pack screen shows them as *(kept on by the modpack)* and does not let
them be switched off or moved. Anything the player adds themselves stays
on top of them. A listed pack that says it is for another game version is
kept on all the same -- listing it is the modpack's acceptance. A listed
pack that is not there is named in the log and skipped; a file that is not
a pack (no `pack.mcmeta`) likewise.

If the config file is missing the mod writes an empty one and keeps
nothing on.

### Seeding files a new player should start with

Some files are a player's own from the moment they exist -- shader
settings, the shader pack they have picked -- and a modpack must never
ship them under `overrides/`, where every update puts the pack's copy back
over theirs. Ship them as seeds instead: anything under
`config/packkeeper/seed/` is copied to the same path under the game folder
on launch, **only where nothing exists yet**, and never touched again.

```
config/packkeeper/seed/config/iris.properties
config/packkeeper/seed/shaderpacks/ComplementaryUnbound_r5.8.1.zip.txt
```

gives a new player the pack's shader pack switched on with the pack's
settings, and leaves everyone who already has either exactly as they were.
Seeding happens at mixin bootstrap, before the game reads its options --
Iris reads its properties there too -- so it takes effect on the very
first launch. A path that would land outside the game folder is refused
and named in the log.

## For players

Nothing to do. Your other resource packs, and everything else in your
options, are yours. If the modpack seeds a file for you, it happens once,
and the log says so.

## How it works

The seeding is a mixin config with no mixins in it: its plugin is the
earliest hook a mod gets, and `Seeder` runs from its `onLoad`.

The game discovers the packs in `resourcepacks/` itself and lists them as
optional. When it asks mods for more packs, Pack Keeper hands it the
listed ones again under the same ids, marked *required* and *fixed* --
the later source wins, so each pack appears once; a required pack is
always selected; a fixed one keeps its place beneath whatever the game
adds later. Then it puts the saved pack list in the modpack's order,
which the game reads right after. The reasoning, with the alternatives,
is in `knowledge/decisions/`.

## Building and checking

Java 21. `./gradlew build` produces `build/libs/packkeeper-<version>.jar`.

`./gradlew check` runs the plain-JUnit tests against the pure layer and
the booth: a real client boots on generated fixtures -- two kept packs and
one of the player's own, a saved list in the wrong order, a seed whose
target is missing and one whose target is the player's -- and asserts
what the game selected, that the top kept pack is what paints the dirt
texture, that the player's pack stays on top, that the kept packs appear
once, and that only the missing seed was put in place. It needs a display; `-PskipBooth` leaves it out. The booth's
photo of the pack screen lands in `run/booth/screenshots/`.

`./gradlew runPackTrial` boots whatever is laid out in `run/trial` (a real
modpack's `resourcepacks/` and `config/packkeeper.json`, as a launcher
would install them) and checks that every listed pack is selected, in
order, and required. It is how a modpack's list is checked before it
ships.

## License

GNU Affero General Public License v3.0 or later. Copyright (C) 2026 Rusty
Shackleford and nfx.
