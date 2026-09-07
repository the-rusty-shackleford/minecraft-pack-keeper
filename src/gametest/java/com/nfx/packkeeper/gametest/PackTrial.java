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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A real modpack's trial (the {@code packTrial} run): the game boots on
 * whatever is in {@code run/trial}, and at the title screen this checks
 * that every pack {@code config/packkeeper.json} lists is selected, in the
 * listed order, and required. One {@code trial: PASS} or {@code trial:
 * FAIL} line per check; a photo of the pack screen; then it quits.
 */
@EventBusSubscriber(modid = BoothMod.MOD_ID, value = Dist.CLIENT)
public final class PackTrial {
    private PackTrial() {}

    private static final Logger LOG = LoggerFactory.getLogger("Pack Keeper trial");
    private static final boolean ACTIVE = Boolean.getBoolean("packkeeper.trial");

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
            mc.setScreen(new PackSelectionScreen(mc.getResourcePackRepository(), r -> {},
                    mc.getResourcePackDirectory(), Component.literal("Pack Keeper trial")));
            return;
        }
        tick++;
        if (tick == 30) {
            Screenshot.grab(mc.gameDirectory, "trial-pack-screen.png", mc.getMainRenderTarget(),
                    message -> LOG.info("trial: {}", message.getString()));
        }
        if (tick == 40) {
            mc.stop();
        }
    }

    private static void check(Minecraft mc) {
        List<String> listed = listed(mc);
        List<String> expected = new ArrayList<>(listed);
        Collections.reverse(expected);
        expected.replaceAll(name -> "file/" + name);
        PackRepository repository = mc.getResourcePackRepository();
        List<String> selected = List.copyOf(repository.getSelectedIds());
        List<String> ours = selected.stream().filter(expected::contains).toList();
        verdict("every listed pack is selected", ours.size() == expected.size(),
                "listed " + expected.size() + ", selected " + ours.size() + "; selected " + selected);
        verdict("the listed packs are in the listed order", ours.equals(expected), "found " + ours);
        List<String> optional = expected.stream().filter(id -> {
            Pack pack = repository.getPack(id);
            return pack == null || !pack.isRequired();
        }).toList();
        verdict("every listed pack is required", optional.isEmpty(), "not required: " + optional);
        LOG.info("trial: {} packs available, {} selected: {}", repository.getAvailableIds().size(),
                selected.size(), selected);
    }

    private static List<String> listed(Minecraft mc) {
        try (Reader reader = Files.newBufferedReader(mc.gameDirectory.toPath().resolve("config/packkeeper.json"))) {
            JsonObject json = new Gson().fromJson(reader, JsonObject.class);
            List<String> names = new ArrayList<>();
            json.getAsJsonArray("resource_packs").forEach(e -> names.add(e.getAsString()));
            return names;
        } catch (IOException | RuntimeException e) {
            LOG.error("trial: FAIL cannot read config/packkeeper.json", e);
            return List.of();
        }
    }

    private static void verdict(String check, boolean passed, String detail) {
        if (passed) {
            LOG.info("trial: PASS {} ({})", check, detail);
        } else {
            LOG.error("trial: FAIL {} ({})", check, detail);
        }
    }
}
