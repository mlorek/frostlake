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

import java.util.concurrent.atomic.AtomicLong;

/**
 * The order the time-travel snapshots were taken in, across every table: each snapshot draws the next number. A
 * point that is a STATEMENT reads by this order rather than by the clock, which two statements inside one
 * millisecond share.
 */
public final class SnapshotSequence {

    private static final AtomicLong LAST = new AtomicLong();

    private SnapshotSequence() {
    }

    /** The next number, for a snapshot being taken. */
    static long next() {
        return LAST.incrementAndGet();
    }

    /**
     * The number the latest snapshot drew: every snapshot taken so far is at or below it.
     *
     * @return the latest number drawn
     */
    public static long current() {
        return LAST.get();
    }

    /**
     * Draw the next number for a moment that is no snapshot — a stream's offset — so it falls strictly between the
     * snapshots taken before and after it.
     *
     * @return the number
     */
    public static long mark() {
        return next();
    }
}
