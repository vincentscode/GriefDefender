/*
 * This file is part of GriefDefender, licensed under the MIT License (MIT).
 *
 * Copyright (c) bloodmc
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.griefdefender.listener;

import com.griefdefender.GDBootstrap;
import com.griefdefender.GDTimings;
import com.griefdefender.GriefDefenderPlugin;
import com.griefdefender.claim.GDClaimManager;
import com.griefdefender.internal.util.NMSUtil;
import com.griefdefender.task.TaxApplyTask;
import com.griefdefender.util.TaskUtil;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.world.LoadWorldEvent;
import org.spongepowered.api.event.world.SaveWorldEvent;
import org.spongepowered.api.event.world.UnloadWorldEvent;
import org.spongepowered.api.event.world.chunk.LoadChunkEvent;
import org.spongepowered.api.event.world.chunk.UnloadChunkEvent;
import org.spongepowered.common.SpongeImpl;

import java.util.concurrent.TimeUnit;

public class WorldEventHandler {

    @Listener(order = Order.EARLY, beforeModifications = true)
    public void onWorldLoad(LoadWorldEvent event) {
        if (!SpongeImpl.getServer().isServerRunning() || !GriefDefenderPlugin.getInstance().claimsEnabledForWorld(event.getTargetWorld().getUniqueId())) {
            return;
        }

        GDTimings.WORLD_LOAD_EVENT.startTimingIfSync();
        GriefDefenderPlugin.getInstance().dataStore.registerWorld(event.getTargetWorld());
        GriefDefenderPlugin.getInstance().dataStore.loadWorldData(event.getTargetWorld());

        NMSUtil.getInstance().addEntityRemovalListener(event.getTargetWorld());
        GDTimings.WORLD_LOAD_EVENT.stopTimingIfSync();
        if (!GriefDefenderPlugin.getActiveConfig(event.getTargetWorld().getProperties()).getConfig().claim.bankTaxSystem) {
            return;
        }
        if (GriefDefenderPlugin.getInstance().economyService.isPresent()) {
            // run tax task
            TaxApplyTask taxTask = new TaxApplyTask(event.getTargetWorld().getProperties());
            int taxHour = GriefDefenderPlugin.getActiveConfig(event.getTargetWorld().getProperties()).getConfig().claim.taxApplyHour;
            long delay = TaskUtil.computeDelay(taxHour, 0, 0);
            Sponge.getScheduler().createTaskBuilder().delay(delay, TimeUnit.SECONDS).interval(1, TimeUnit.DAYS).execute(taxTask).submit(GDBootstrap.getInstance());
        }
    }

    @Listener(order = Order.FIRST, beforeModifications = true)
    public void onWorldUnload(UnloadWorldEvent event) {
        if (!SpongeImpl.getServer().isServerRunning() || !GriefDefenderPlugin.getInstance().claimsEnabledForWorld(event.getTargetWorld().getUniqueId())) {
            return;
        }

        GriefDefenderPlugin.getInstance().dataStore.removeClaimWorldManager(event.getTargetWorld().getProperties());
    }

    @Listener
    public void onWorldSave(SaveWorldEvent.Post event) {
        if (!GriefDefenderPlugin.getInstance().claimsEnabledForWorld(event.getTargetWorld().getUniqueId())) {
            return;
        }

        GDTimings.WORLD_SAVE_EVENT.startTimingIfSync();
        GDClaimManager claimWorldManager = GriefDefenderPlugin.getInstance().dataStore.getClaimWorldManager(event.getTargetWorld().getUniqueId());
        if (claimWorldManager == null) {
            GDTimings.WORLD_SAVE_EVENT.stopTimingIfSync();
            return;
        }

        claimWorldManager.save();
        GDTimings.WORLD_SAVE_EVENT.stopTimingIfSync();
    }
}
