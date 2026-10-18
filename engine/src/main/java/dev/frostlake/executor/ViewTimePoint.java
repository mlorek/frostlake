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
 * A point in a table's history a view is read at: an AT or BEFORE written after the view, whose tables are then read
 * as of it (live-verified), or one end of a CHANGES clause over a view. A time names the snapshot taken by then, a
 * statement or a stream's offset the place in the order snapshots are taken in.
 */
final class ViewTimePoint {

    /** The point's instant, in epoch milliseconds. */
    final long millis;
    /** The point's place in the order snapshots are taken in, or -1 when a time names it. */
    final long sequence;
    /** Whether the point is just before its instant. */
    final boolean before;
    /** The clause as written, for a source's description. */
    final String text;

    ViewTimePoint(final long millis, final long sequence, final boolean before, final String text) {
        this.millis = millis;
        this.sequence = sequence;
        this.before = before;
        this.text = text;
    }
}
