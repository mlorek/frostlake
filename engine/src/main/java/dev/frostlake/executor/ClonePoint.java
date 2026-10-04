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

package dev.frostlake.executor;

/**
 * The point a CLONE's {@code AT | BEFORE} clause names, resolved once, so that every table a clone of a schema or
 * database copies is read as of the same moment. Resolved by {@link QueryExecutor#clonePoint}.
 */
public final class ClonePoint {

    private final long millis;
    private final long sequence;
    private final boolean before;

    ClonePoint(final long millis, final long sequence, final boolean before) {
        this.millis = millis;
        this.sequence = sequence;
        this.before = before;
    }

    /** The instant, in epoch milliseconds. */
    public long millis() {
        return millis;
    }

    /** The snapshot sequence a STATEMENT point reads by, or -1 for a TIMESTAMP or OFFSET point. */
    public long sequence() {
        return sequence;
    }

    /** Whether the point is just BEFORE the instant rather than AT it. */
    public boolean isBefore() {
        return before;
    }
}
