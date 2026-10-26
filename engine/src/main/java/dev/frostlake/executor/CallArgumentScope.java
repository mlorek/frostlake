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
 * Whether this thread is evaluating a CALL's arguments: a SYSTEM$REFERENCE made there with no scope written is the
 * call's, and names no object once the call is done, where one a query makes with no scope lasts the session
 * (live-verified; see ObjectReferenceTokens).
 */
final class CallArgumentScope {

    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<Boolean>();

    private CallArgumentScope() {
    }

    /**
     * Note that a CALL's arguments are about to be evaluated.
     *
     * @return what it displaced, for {@link #end}
     */
    static Boolean begin() {
        final Boolean previous = ACTIVE.get();
        ACTIVE.set(Boolean.TRUE);
        return previous;
    }

    /** Put back whatever {@link #begin} displaced; null clears. */
    static void end(final Boolean previous) {
        if (previous == null) {
            ACTIVE.remove();
        } else {
            ACTIVE.set(previous);
        }
    }

    /** @return whether a CALL's arguments are being evaluated */
    static boolean active() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }
}
