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
 * Whether the thread is planning the body of a stored view for a statement that reads it. A relation the body
 * names and nothing answers to is then reported by its full path and under the view's name
 * (see {@link ViewExpansionException}), where a statement's own miss names it as written.
 */
final class ViewExpansion {

    private static final ThreadLocal<Boolean> IN_FORCE = new ThreadLocal<Boolean>();

    private ViewExpansion() {
    }

    /**
     * Note that a view's body is being planned.
     *
     * @return whether one already was, for {@link #end}
     */
    static boolean begin() {
        final boolean outer = inForce();
        IN_FORCE.set(Boolean.TRUE);
        return outer;
    }

    /**
     * Put back what {@link #begin} found.
     *
     * @param outer what it returned
     */
    static void end(final boolean outer) {
        if (outer) {
            IN_FORCE.set(Boolean.TRUE);
        } else {
            IN_FORCE.remove();
        }
    }

    /**
     * Whether a view's body is being planned.
     *
     * @return true inside a view's expansion
     */
    static boolean inForce() {
        return Boolean.TRUE.equals(IN_FORCE.get());
    }
}
