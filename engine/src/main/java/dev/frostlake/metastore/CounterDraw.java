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

/** One value a statement drew from a counter: a table's IDENTITY or a sequence (see {@link StatementDraws}). */
public final class CounterDraw {

    private final String counter;
    private final long value;

    /**
     * @param counter the counter, {@code IDENTITY:T} or {@code SEQUENCE:S}
     * @param value   the value drawn
     */
    public CounterDraw(final String counter, final long value) {
        this.counter = counter;
        this.value = value;
    }

    /** The counter drawn from. */
    public String getCounter() {
        return counter;
    }

    /** The value drawn. */
    public long getValue() {
        return value;
    }
}
