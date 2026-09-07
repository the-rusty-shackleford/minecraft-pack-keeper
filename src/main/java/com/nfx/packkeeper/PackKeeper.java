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

import com.nfx.packkeeper.domain.Selection;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Keeps a modpack's resource packs switched on, in the modpack's order,
 * from a list in the modpack's config -- so the modpack never has to ship
 * anyone's {@code options.txt}.
 *
 * <p>The game finds the packs in the resource-packs folder itself and
 * lists them as optional. When it asks mods for more packs, this mod hands
 * it the listed ones again under the same ids, marked <em>required</em>:
 * the later source wins, so each pack appears once, and a required pack
 * is always selected and cannot be switched off. Then it puts the player's
 * saved pack list in order (see {@link Selection#imposedOn}), which the
 * game reads right after. Nothing else in the player's options is touched.
 *
 * <p>Client only: a server has no resource packs to keep.
 */
@Mod(value = PackKeeper.MOD_ID, dist = Dist.CLIENT)
public final class PackKeeper {
    public static final String MOD_ID = "packkeeper";
    private static final Logger LOG = LoggerFactory.getLogger("Pack Keeper");
    /** How a kept pack is labelled in the game's pack screen. */
    private static final PackSource KEPT = PackSource.create(
            name -> Component.translatable("pack.source.packkeeper", name), true);

    public PackKeeper(IEventBus modBus) {
        modBus.addListener(this::onAddPackFinders);
    }

    private void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        Selection selection = KeeperConfig.read(FMLPaths.CONFIGDIR.get().resolve(KeeperConfig.FILE_NAME));
        Minecraft minecraft = Minecraft.getInstance();
        Path folder = minecraft.getResourcePackDirectory();
        Predicate<String> present = name -> Files.exists(folder.resolve(name));
        for (String name : selection.missing(present)) {
            LOG.warn("The modpack lists a resource pack that is not in {}: {}", folder, name);
        }
        List<Pack> kept = new ArrayList<>();
        for (String name : selection.bottomToTop()) {
            if (present.test(name)) {
                Pack pack = keep(folder.resolve(name), name);
                if (pack != null) {
                    kept.add(pack);
                }
            }
        }
        event.addRepositorySource(consumer -> kept.forEach(consumer));
        List<String> ordered = selection.imposedOn(minecraft.options.resourcePacks,
                name -> kept.stream().anyMatch(pack -> pack.getId().equals(Selection.id(name))));
        minecraft.options.resourcePacks.clear();
        minecraft.options.resourcePacks.addAll(ordered);
        LOG.info("Keeping {} resource pack(s) on: {}", kept.size(), kept.stream().map(Pack::getId).toList());
    }

    /** The pack at {@code path}, required, under the game's own id for it; null if it is not a pack. */
    private static Pack keep(Path path, String name) {
        Pack.ResourcesSupplier resources = Files.isDirectory(path)
                ? new PathPackResources.PathResourcesSupplier(path)
                : new FilePackResources.FileResourcesSupplier(path);
        PackLocationInfo location = new PackLocationInfo(Selection.id(name), Component.literal(name), KEPT, Optional.empty());
        Pack pack = Pack.readMetaAndCreate(location, resources, PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (pack == null) {
            LOG.warn("The modpack lists a resource pack that is not one (no pack.mcmeta): {}", path);
        }
        return pack;
    }
}
