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
 * The RECURSIVE view whose body the thread is planning, if any. Such a view runs as a recursive CTE of its own
 * name, which the account names after the view in what it says of it: view {@code RV} recurses in CTE
 * {@code RV_RECURSIVE_CTE} (live-verified).
 */
final class RecursiveViewBody {

    private static final ThreadLocal<String> VIEW = new ThreadLocal<String>();

    private RecursiveViewBody() {
    }

    /**
     * Note the recursive view whose body is being planned.
     *
     * @param view the view's name, or null for a view that is not recursive
     * @return the view noted before, for {@link #end}
     */
    static String begin(final String view) {
        final String outer = VIEW.get();
        if (view == null) {
            VIEW.remove();
        } else {
            VIEW.set(view);
        }
        return outer;
    }

    /**
     * Put back what {@link #begin} found.
     *
     * @param outer what it returned
     */
    static void end(final String outer) {
        if (outer == null) {
            VIEW.remove();
        } else {
            VIEW.set(outer);
        }
    }

    /**
     * The name a recursive CTE is spoken of by: its own, or the view's with the account's suffix when the CTE is a
     * recursive view's body.
     *
     * @param cteName the CTE's name
     * @return the name to print
     */
    static String spoken(final String cteName) {
        final String view = VIEW.get();
        return view != null && view.equalsIgnoreCase(cteName) ? cteName + "_RECURSIVE_CTE" : cteName;
    }
}
