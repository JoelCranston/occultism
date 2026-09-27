/*
 * MIT License
 *
 * Copyright 2026 klikli-dev
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT
 * OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package com.klikli_dev.occultism.common.entity.spirit;

import com.klikli_dev.occultism.Occultism;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps track of tamed spirits that died, so books of calling bound to them can be marked as dead.
 * The register only lives as long as the server, and old entries are pruned to avoid unbounded growth.
 */
@EventBusSubscriber(modid = Occultism.MODID)
public class SpiritDeathRegister {

    /**
     * Entries older than this (in game ticks) are removed. Books usually pick up the death within a minute.
     */
    public static final long PRUNE_AFTER_TICKS = 20 * 60 * 60 * 24;

    private static final Map<UUID, Long> DEATHS = new HashMap<>();

    /**
     * Registers the death of a spirit.
     *
     * @param spiritId  the spirit that died.
     * @param deathTime the game time of the death.
     */
    public static void register(UUID spiritId, long deathTime) {
        DEATHS.values().removeIf(time -> time < deathTime - PRUNE_AFTER_TICKS);
        DEATHS.put(spiritId, deathTime);
    }

    /**
     * @param spiritId the spirit to check.
     * @return the game time the spirit died at, or null if it is not known to be dead.
     */
    public static Long getDeathTime(UUID spiritId) {
        return DEATHS.get(spiritId);
    }

    public static void remove(UUID spiritId) {
        DEATHS.remove(spiritId);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        //the register is not persisted, so make sure it does not leak into the next world (e.g. in single player)
        DEATHS.clear();
    }
}
