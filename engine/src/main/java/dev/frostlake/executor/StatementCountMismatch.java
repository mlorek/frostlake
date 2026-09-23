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
 * A text holding another number of statements than the session's MULTI_STATEMENT_COUNT asks for, refused with the
 * request gate's own sentence. A handler reads it as error 8, SQLSTATE 0A000 (live-verified).
 */
public class StatementCountMismatch extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The error code a handler's SQLCODE reads. */
    public static final int CODE = 8;

    /** The SQLSTATE a handler reads. */
    public static final String STATE = "0A000";

    /**
     * @param actual  how many statements the text holds
     * @param desired how many the session asks for
     */
    public StatementCountMismatch(final int actual, final int desired) {
        super("Actual statement count " + actual + " did not match the desired statement count " + desired + ".");
    }
}
