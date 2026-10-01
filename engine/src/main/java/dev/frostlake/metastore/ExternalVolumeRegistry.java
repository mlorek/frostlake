/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.metastore;

import dev.frostlake.executor.StatementClock;
import dev.frostlake.metastore.model.ExternalVolume;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The account's external volumes, keyed by canonical name, and the dropped ones UNDROP can restore — the most
 * recently dropped volume of a name first.
 */
public final class ExternalVolumeRegistry {

    private final Map<String, ExternalVolume> volumes = new ConcurrentHashMap<>();
    private final Map<String, List<ExternalVolume>> dropped = new ConcurrentHashMap<>();

    /** The volume of that canonical name, or null. */
    public ExternalVolume find(final String name) {
        return volumes.get(name);
    }

    /** Adds or replaces a volume. */
    public void put(final ExternalVolume volume) {
        volumes.put(volume.getName(), volume);
    }

    /**
     * Drops the volume of that name, keeping it for UNDROP.
     *
     * @return the dropped volume, or null when there was none
     */
    public ExternalVolume drop(final String name) {
        final ExternalVolume volume = volumes.remove(name);
        if (volume != null) {
            volume.setDroppedOn(StatementClock.instant());
            List<ExternalVolume> history = dropped.get(name);
            if (history == null) {
                history = new ArrayList<>();
                dropped.put(name, history);
            }
            history.add(volume);
        }
        return volume;
    }

    /** Removes a volume without keeping it, as CREATE OR REPLACE does. */
    public void discard(final String name) {
        volumes.remove(name);
    }

    /**
     * Restores the most recently dropped volume of that name.
     *
     * @return the restored volume, or null when none was dropped
     */
    public ExternalVolume undrop(final String name) {
        final List<ExternalVolume> history = dropped.get(name);
        if (history == null || history.isEmpty()) {
            return null;
        }
        final ExternalVolume volume = history.remove(history.size() - 1);
        volume.setDroppedOn(null);
        volumes.put(name, volume);
        return volume;
    }

    /**
     * The dropped volumes UNDROP can still restore: each name's drops, the earliest first, as
     * {@link #restoreDropped} expects them back.
     */
    public List<ExternalVolume> droppedVolumes() {
        final List<ExternalVolume> out = new ArrayList<>();
        for (final List<ExternalVolume> history : dropped.values()) {
            out.addAll(history);
        }
        return out;
    }

    /** Puts back a dropped volume a restored snapshot recorded, after the earlier drops of its name. */
    public void restoreDropped(final ExternalVolume volume) {
        List<ExternalVolume> history = dropped.get(volume.getName());
        if (history == null) {
            history = new ArrayList<>();
            dropped.put(volume.getName(), history);
        }
        history.add(volume);
    }

    /** Every volume, ordered by name. */
    public List<ExternalVolume> all() {
        final List<ExternalVolume> out = new ArrayList<>(volumes.values());
        out.sort(new Comparator<ExternalVolume>() {
            @Override
            public int compare(final ExternalVolume a, final ExternalVolume b) {
                return a.getName().compareTo(b.getName());
            }
        });
        return out;
    }
}
