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
package com.nfx.packkeeper.gametest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * The check through the real path, on the fixtures the build wrote into
 * {@code run/booth}: the game boots with an {@code options.txt} that has
 * the kept packs in the wrong order and the player's own pack beneath
 * them. At the title screen the booth reads what the game selected, reads
 * the dirt and stone textures back through the resource manager to see
 * which pack painted them, photographs the pack screen, and writes one
 * {@code booth: PASS} or {@code booth: FAIL} line per check to the log,
 * which the build's gate reads. Active only under
 * {@code packkeeper.photobooth}.
 */
@EventBusSubscriber(modid = BoothMod.MOD_ID, value = Dist.CLIENT)
public final class PackBooth {
    private PackBooth() {}

    private static final Logger LOG = LoggerFactory.getLogger("Pack Keeper booth");
    private static final boolean ACTIVE = Boolean.getBoolean("packkeeper.photobooth");
    private static final List<String> EXPECTED = List.of(
            "vanilla", "mod_resources", "file/keeper-base", "file/keeper-top.zip", "file/mine");
    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;

    private static int tick = 0;
    private static boolean checked = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ACTIVE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!checked) {
            if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) {
                return;
            }
            checked = true;
            check(mc);
            PackRepository repository = mc.getResourcePackRepository();
            mc.setScreen(new PackSelectionScreen(repository, r -> {}, mc.getResourcePackDirectory(),
                    Component.literal("Pack Keeper booth")));
            return;
        }
        tick++;
        if (tick == 20) {
            Screenshot.grab(mc.gameDirectory, "booth-pack-screen.png", mc.getMainRenderTarget(),
                    message -> LOG.info("booth: {}", message.getString()));
        }
        if (tick == 30) {
            mc.stop();
        }
    }

    private static void check(Minecraft mc) {
        PackRepository repository = mc.getResourcePackRepository();
        List<String> selected = List.copyOf(repository.getSelectedIds());
        verdict("the selection is the kept packs in the modpack's order with the player's own on top",
                EXPECTED.equals(selected), "selected " + selected);
        Pack top = repository.getPack("file/keeper-top.zip");
        Pack base = repository.getPack("file/keeper-base");
        Pack mine = repository.getPack("file/mine");
        verdict("the kept packs are required and the player's own is not",
                top != null && top.isRequired() && base != null && base.isRequired() && mine != null && !mine.isRequired(),
                "top=" + describe(top) + " base=" + describe(base) + " mine=" + describe(mine));
        verdict("the kept packs are listed once each",
                repository.getAvailableIds().stream().filter(id -> id.startsWith("file/keeper")).count() == 2,
                "available " + repository.getAvailableIds());
        verdict("the absent pack is not selected and not invented",
                !selected.contains("file/keeper-absent.zip") && repository.getPack("file/keeper-absent.zip") == null,
                "available " + repository.getAvailableIds());
        verdict("the top kept pack paints dirt", pixel(mc, "textures/block/dirt.png") == RED,
                String.format("dirt pixel %08x", pixel(mc, "textures/block/dirt.png")));
        verdict("the player's own pack paints stone", pixel(mc, "textures/block/stone.png") == GREEN,
                String.format("stone pixel %08x", pixel(mc, "textures/block/stone.png")));
        verdict("the saved options carry the same order", EXPECTED.equals(mc.options.resourcePacks),
                "options " + mc.options.resourcePacks);
    }

    private static String describe(Pack pack) {
        return pack == null ? "absent" : (pack.isRequired() ? "required" : "optional");
    }

    /** The top-left pixel of the texture as the game now resolves it, or 0 if it cannot be read. */
    private static int pixel(Minecraft mc, String texture) {
        ResourceLocation location = ResourceLocation.withDefaultNamespace(texture);
        try (InputStream in = mc.getResourceManager().getResourceOrThrow(location).open()) {
            BufferedImage image = ImageIO.read(in);
            return image.getRGB(0, 0);
        } catch (IOException e) {
            LOG.error("booth: cannot read {}", texture, e);
            return 0;
        }
    }

    private static void verdict(String check, boolean passed, String detail) {
        if (passed) {
            LOG.info("booth: PASS {} ({})", check, detail);
        } else {
            LOG.error("booth: FAIL {} ({})", check, detail);
        }
    }
}
