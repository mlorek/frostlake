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

    private static final ThreadLocal<Boolean> VIEW_BODY = new ThreadLocal<Boolean>();

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
        return selectList != null && (selectList.equals(WAIVED.get()) || Boolean.TRUE.equals(VIEW_BODY.get()));
    }

    /**
     * Leaves every ROW item unread while CREATE VIEW compiles the view's body: the account creates a view whose body
     * selects a named ROW, or holds one in an EXISTS's body, and refuses the item where the view is read, placed in
     * the view's CREATE text (live-verified). A view the body reads is judged as ever (see {@link #suspendViewBody}).
     *
     * @return the state to put back with {@link #endViewBody}
     */
    public static Boolean beginViewBody() {
        final Boolean previous = VIEW_BODY.get();
        VIEW_BODY.set(Boolean.TRUE);
        return previous;
    }

    /**
     * Judges ROW items again while a view is expanded, inside a body being created or not.
     *
     * @return the state to put back with {@link #endViewBody}
     */
    public static Boolean suspendViewBody() {
        final Boolean previous = VIEW_BODY.get();
        VIEW_BODY.remove();
        return previous;
    }

    /**
     * Whether a view's body is being created, so that its ROW items stand unread.
     *
     * @return true while {@link #beginViewBody} is in force
     */
    public static boolean inViewBody() {
        return Boolean.TRUE.equals(VIEW_BODY.get());
    }

    /**
     * Puts back what {@link #beginViewBody} or {@link #suspendViewBody} found.
     *
     * @param previous what either handed back
     */
    public static void endViewBody(final Boolean previous) {
        if (previous == null) {
            VIEW_BODY.remove();
        } else {
            VIEW_BODY.set(previous);
        }
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
