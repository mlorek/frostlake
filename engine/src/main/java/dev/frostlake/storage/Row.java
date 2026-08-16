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

package dev.frostlake.storage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Row {

    private final List<Object> values;

    public Row(final List<Object> values) {
        this.values = new ArrayList<>(values);
    }

    public Row(final Object... values) {
        this.values = new ArrayList<>(Arrays.asList(values));
    }

    private Row(final List<Object> values, final boolean adopt) {
        this.values = adopt ? values : new ArrayList<>(values);
    }

    /**
     * Create a Row backed directly by the given list, WITHOUT a defensive copy. Use only when the
     * caller has freshly built the list and will not mutate or reuse it (e.g. join/projection output)
     * — avoids a redundant per-row copy on the hot path.
     */
    public static Row of(final List<Object> values) {
        return new Row(values, true);
    }

    public List<Object> getValues() {
        return values;
    }

    public Object getValue(final int index) {
        return values.get(index);
    }

    public void setValue(final int index, final Object value) {
        values.set(index, value);
    }

    public int size() {
        return values.size();
    }

    @Override
    public String toString() {
        return values.toString();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final Row row = (Row) obj;
        return Objects.equals(values, row.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    public Row copy() {
        return new Row(new ArrayList<>(values));
    }
}
