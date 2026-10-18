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

package dev.frostlake.executor.expressions;

/**
 * The grouping set whose super-group row the thread is computing, while its items are evaluated: GROUPING and
 * GROUPING_ID answer from it wherever they stand in an item, inside IFF, CASE or arithmetic as well as alone
 * (live-verified).
 */
public final class GroupingSetScope {

    private static final ThreadLocal<GroupingKeys> CURRENT = new ThreadLocal<>();

    private GroupingSetScope() {
    }

    /** The grouping set being computed, or null outside a super-group row. */
    public static GroupingKeys current() {
        return CURRENT.get();
    }

    /**
     * Enter a grouping set; the caller leaves it in a finally block with what this answers.
     *
     * @param keys the set's keys
     * @return the set entered before, or null
     */
    public static GroupingKeys enter(final GroupingKeys keys) {
        final GroupingKeys outer = CURRENT.get();
        CURRENT.set(keys);
        return outer;
    }

    /**
     * Leave the grouping set {@link #enter} entered.
     *
     * @param outer what {@link #enter} answered
     */
    public static void leave(final GroupingKeys outer) {
        if (outer == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(outer);
        }
    }
}
