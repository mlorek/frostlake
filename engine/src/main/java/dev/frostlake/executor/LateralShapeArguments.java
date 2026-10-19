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
 * Marks the reading of a table function's arguments for a lateral item's shape. The item's columns are settled
 * while the statement is planned, with every column to its left bound to NULL; no row is behind those NULLs, so
 * while the mark is set a handler is not called - it answers NULL - and a fault a value raises is no fault of
 * the statement. What a row makes of the arguments is found when the row is read.
 */
public final class LateralShapeArguments {

    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<Boolean>();

    private LateralShapeArguments() {
    }

    /** Whether the current thread reads arguments for a shape alone. */
    public static boolean isActive() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    /** Sets the mark; hands back the state to put back with {@link #end}. */
    public static boolean begin() {
        final boolean previous = isActive();
        ACTIVE.set(Boolean.TRUE);
        return previous;
    }

    public static void end(final boolean previous) {
        if (previous) {
            ACTIVE.set(Boolean.TRUE);
        } else {
            ACTIVE.remove();
        }
    }
}
