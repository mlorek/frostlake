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
 * A refusal placed in a function's own body: a BREAK or a CONTINUE of a scripting function's block that stands in
 * no loop. Its place is the body's, wherever the call stands — {@code x := (SELECT fbreak())} in a block reports
 * {@code error line 1 at position 7} inside the expression's wrapper, as {@code SELECT fbreak()} does from the
 * session — and the place a block gives a query's own refusals in its expression is not applied to it
 * (live-verified).
 */
public final class BodyPlacedError extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** How far a failure's causes are searched for one. */
    private static final int DEPTH = 8;

    /**
     * @param refused the body's refusal
     */
    public BodyPlacedError(final RuntimeException refused) {
        super(refused.getMessage(), refused);
    }

    /**
     * @param failure a failure
     * @return whether it is, or was raised for, a refusal placed in a function's body
     */
    public static boolean isIn(final Throwable failure) {
        Throwable at = failure;
        for (int i = 0; i < DEPTH && at != null; i++) {
            if (at instanceof BodyPlacedError) {
                return true;
            }
            at = at.getCause();
        }
        return false;
    }
}
