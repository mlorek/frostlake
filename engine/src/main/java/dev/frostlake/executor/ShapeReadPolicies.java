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
 * A thread-local mode in which a relation's columns are projected for a description, not handed out: a projection
 * policy restricts what a query shows, and DESCRIBE of a dynamic table whose column one restricts still lists the
 * column (live-verified), so the projection it reads its shape from is not refused for the policy.
 */
public final class ShapeReadPolicies {
    private static final ThreadLocal<Boolean> DESCRIBING = new ThreadLocal<Boolean>();

    private ShapeReadPolicies() {
    }

    /**
     * Whether the current thread projects a relation for its description.
     *
     * @return whether it does
     */
    public static boolean describing() {
        return Boolean.TRUE.equals(DESCRIBING.get());
    }

    /**
     * Enter the mode.
     *
     * @return the state to put back with {@link #end}
     */
    public static boolean begin() {
        final boolean previous = describing();
        DESCRIBING.set(Boolean.TRUE);
        return previous;
    }

    /**
     * Leave the mode, putting back the state {@link #begin} handed out.
     *
     * @param previous that state
     */
    public static void end(final boolean previous) {
        if (previous) {
            DESCRIBING.set(Boolean.TRUE);
        } else {
            DESCRIBING.remove();
        }
    }
}
