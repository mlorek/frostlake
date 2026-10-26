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
 * The select list of a scalar subquery's body, whose ROW item is refused at the body's own SELECT:
 * {@code SELECT (SELECT (1, 2))} is refused at 8, the subquery's SELECT, alias or no alias, where
 * {@code SELECT (1, 2)} standing alone is refused at its row (live-verified). The list is named by its text, which
 * no SELECT nested inside it can share.
 */
public final class ScalarBodyRowItems {

    private static final ThreadLocal<String> BODY = new ThreadLocal<String>();

    private ScalarBodyRowItems() {
    }

    /**
     * Names the select list of the scalar subquery body being planned.
     *
     * @param selectList the select list's text
     * @return the name to put back with {@link #end}
     */
    public static String begin(final String selectList) {
        final String previous = BODY.get();
        BODY.set(selectList);
        return previous;
    }

    /**
     * Whether a select list is a scalar subquery body's.
     *
     * @param selectList the select list's text
     * @return true for the body's list
     */
    public static boolean names(final String selectList) {
        return selectList != null && selectList.equals(BODY.get());
    }

    /**
     * Puts back the name {@link #begin} found.
     *
     * @param previous what {@link #begin} handed back
     */
    public static void end(final String previous) {
        if (previous == null) {
            BODY.remove();
        } else {
            BODY.set(previous);
        }
    }
}
