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

package dev.frostlake.metastore.model;

import java.util.List;

/**
 * One logical row's accumulated (net) change while consolidating a stream window: {@code oldValues}
 * is the row's image at the stream offset (null when the row was born inside the window) and
 * {@code newValues} its current image (null when the row no longer exists). Both null means the
 * change cancelled out entirely.
 */
public class StreamNetChange {

    private final List<Object> oldValues;
    private List<Object> newValues;
    private final long rowId;

    public StreamNetChange(final List<Object> oldValues, final List<Object> newValues, final long rowId) {
        this.oldValues = oldValues;
        this.newValues = newValues;
        this.rowId = rowId;
    }

    public List<Object> getOldValues() {
        return oldValues;
    }

    public List<Object> getNewValues() {
        return newValues;
    }

    /** Advance the row's current image (a later update in the window), or null it out (a delete). */
    public void setNewValues(final List<Object> newValues) {
        this.newValues = newValues;
    }

    public long getRowId() {
        return rowId;
    }
}
