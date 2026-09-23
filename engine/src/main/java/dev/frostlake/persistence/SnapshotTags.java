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

package dev.frostlake.persistence;

import dev.frostlake.metastore.Taggable;

import java.util.HashMap;
import java.util.Map;

/**
 * An object's tag associations, written into a snapshot and read back out of one.
 *
 * <p>A tag set by {@code ALTER … SET TAG} belongs to the object for its lifetime, and an account never
 * restarts, so a persistent database has to carry its tags across one. The WAL replays the ALTER until a
 * checkpoint truncates it, which is what made the loss show only in snapshot-only mode and after a
 * checkpoint.
 *
 * <p>A snapshot written before tags were recorded has none, and reads back as an object carrying none —
 * that is what makes an older {@code catalog.dat} still readable.
 */
final class SnapshotTags {

    private SnapshotTags() {
    }

    /** The object's tags as a snapshot records them, or null when it carries none. */
    static Map<String, String> of(final Taggable object) {
        final Map<String, String> tags = object.getTagValues();
        return tags == null || tags.isEmpty() ? null : new HashMap<>(tags);
    }

    /** Put {@code tags} back on {@code object}; null and empty both leave it untouched. */
    static void restore(final Taggable object, final Map<String, String> tags) {
        if (tags == null) {
            return;
        }
        for (final Map.Entry<String, String> tag : tags.entrySet()) {
            object.setTag(tag.getKey(), tag.getValue());
        }
    }
}
