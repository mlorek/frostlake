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
 * A statement ended before its answer, by its timeout or by a cancel: SQLSTATE 57014, with the account's vendor code —
 * 630 for the session's STATEMENT_TIMEOUT_IN_SECONDS, 604 for a driver's cancel or query timeout (live-verified).
 */
public final class StatementCanceledException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The SQLSTATE every cancellation carries. */
    public static final String SQL_STATE = "57014";

    /** The vendor code of a statement its session's timeout ended. */
    public static final int TIMEOUT_CODE = 630;

    /** The vendor code of a statement a cancel ended. */
    public static final int CANCEL_CODE = 604;

    /** The sentence a cancel ends a statement with. */
    public static final String CANCELED = "SQL execution canceled";

    private static final String TIMEOUT_PREFIX = "Statement reached its statement or warehouse timeout of ";

    private final int code;

    private StatementCanceledException(final String message, final int code) {
        super(message);
        this.code = code;
    }

    /** A statement its session's timeout of {@code seconds} ended. */
    static StatementCanceledException timedOut(final long seconds) {
        return new StatementCanceledException(TIMEOUT_PREFIX + seconds + " second(s) and was canceled.", TIMEOUT_CODE);
    }

    /** A statement a cancel ended. */
    static StatementCanceledException canceled() {
        return new StatementCanceledException(CANCELED, CANCEL_CODE);
    }

    /** The vendor code. */
    public int getCode() {
        return code;
    }

    /**
     * The vendor code a refusal's text carries when it is a cancellation's, for a driver that has only the text.
     *
     * @param message the refusal's text
     * @return 630, 604, or null for any other refusal
     */
    public static Integer codeOf(final String message) {
        if (message == null) {
            return null;
        }
        if (message.startsWith(TIMEOUT_PREFIX)) {
            return Integer.valueOf(TIMEOUT_CODE);
        }
        return CANCELED.equals(message) ? Integer.valueOf(CANCEL_CODE) : null;
    }
}
