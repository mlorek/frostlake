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
 * A thread-local mode in which a relation is read for its SHAPE and never for its rows: every base
 * source resolves to zero rows and a FROM-less item that cannot be computed is left NULL, so the
 * columns come out typed by the static channel while nothing value-dependent runs.
 *
 * <p>CREATE VIEW derives its column list this way (live-verified): a body whose values would fault at
 * row time — a cast to a narrower VARCHAR, 1/0, TO_DATE of text that is no date, a number past its
 * declared width — is a view all the same, DESCRIBE knows its columns, and the fault waits for whoever
 * selects from it. A body that does not COMPILE — an unknown column or function, an argument family a
 * function refuses — is still refused at CREATE, because the static checks run over zero rows exactly
 * as they run over any.
 */
public final class RelationShapeOnly {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<Boolean>();

    private RelationShapeOnly() {
    }

    /** Whether the current thread is reading shapes only. */
    public static boolean isActive() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    /** Enter the mode; hands back the state to put back with {@link #end}. */
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
