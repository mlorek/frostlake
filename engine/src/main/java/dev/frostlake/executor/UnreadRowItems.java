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
 * The select list whose ROW items stand unread: an EXISTS's body that reads no relation and filters nothing answers
 * its one row whatever that list holds, so {@code EXISTS (SELECT (1, 2))} is TRUE where the same SELECT standing alone
 * is refused, {@code Unsupported: SELECT list element with multiple columns.} (live-verified). The list is named by its
 * text, which no SELECT nested inside it can share; every other select list is judged as ever.
 */
public final class UnreadRowItems {

    private static final ThreadLocal<String> WAIVED = new ThreadLocal<String>();

    private UnreadRowItems() {
    }

    /**
     * Leaves a select list's ROW items unread while the EXISTS body holding it is planned.
     *
     * @param selectList the select list's text
     * @return the waiver to put back with {@link #end}
     */
    public static String begin(final String selectList) {
        final String previous = WAIVED.get();
        WAIVED.set(selectList);
        return previous;
    }

    /**
     * Whether a select list is the one whose ROW items stand unread.
     *
     * @param selectList the select list's text
     * @return true for the waived list
     */
    public static boolean waives(final String selectList) {
        return selectList != null && selectList.equals(WAIVED.get());
    }

    /**
     * Puts back the waiver {@link #begin} found.
     *
     * @param previous what {@link #begin} handed back
     */
    public static void end(final String previous) {
        if (previous == null) {
            WAIVED.remove();
        } else {
            WAIVED.set(previous);
        }
    }
}
