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
package com.nfx.packkeeper.mixin;

import com.nfx.packkeeper.Seeder;
import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * The earliest hook a mod gets: a mixin config plugin is loaded when
 * mixins are bootstrapped, before the game's own classes exist. This one
 * applies no mixins; it is here so the seeds are in place before the
 * game reads its options -- Iris reads its properties file from a mixin
 * into them, so a seed made any later would take effect only on the next
 * launch.
 */
public final class SeedPlugin implements IMixinConfigPlugin {
    private static final Logger LOG = LoggerFactory.getLogger("Pack Keeper");

    @Override
    public void onLoad(String mixinPackage) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        try {
            Seeder.seed(FMLPaths.GAMEDIR.get());
        } catch (RuntimeException e) {
            LOG.error("Seeding the modpack's files failed; going on without", e);
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
