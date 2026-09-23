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

import dev.frostlake.security.SecurityManager;

/**
 * The statement-count gate the text of an EXECUTE IMMEDIATE passes.
 *
 * <p>The text is counted as a request is: after it parses, so a syntax error in any of its statements speaks first,
 * and before any of it runs — an INSERT ahead of a second statement inserts nothing (live-verified). A ->> chain is
 * one statement. The count it must match is the request's own when the EXECUTE IMMEDIATE is a statement of the
 * request — the count the client's statement declares, else the session's MULTI_STATEMENT_COUNT — and the session's
 * inside a Snowflake Scripting block (live-verified: a JDBC statement declaring 0 runs a text of two under a session
 * count of 1, and a block under that statement is still held to the session's 1). Under 1 a text of two statements is
 * refused with the request gate's own sentence; under 0 any number runs, as a multi-statement request (see
 * MultiStatementAnswer). A script EXECUTE IMMEDIATE FROM reads from a stage is not counted.
 */
public final class DynamicStatementCount {

    /** The count the running request declares, while a client's request runs; null otherwise. */
    private static final ThreadLocal<Integer> REQUEST_COUNT = new ThreadLocal<Integer>();

    private DynamicStatementCount() {
    }

    /**
     * Note the count the request about to run on this thread asks for: the client statement's own, else its session's.
     *
     * @param desired the count, 0 meaning any number
     * @return the count it displaced, for {@link #endRequest}
     */
    public static Integer beginRequest(final int desired) {
        final Integer previous = REQUEST_COUNT.get();
        REQUEST_COUNT.set(Integer.valueOf(desired));
        return previous;
    }

    /**
     * Put back what {@link #beginRequest} displaced; null clears.
     *
     * @param previous the displaced count
     */
    public static void endRequest(final Integer previous) {
        if (previous == null) {
            REQUEST_COUNT.remove();
        } else {
            REQUEST_COUNT.set(previous);
        }
    }

    /**
     * Refuse a text of {@code actual} statements unless the count in force asks for that many, or for any number.
     *
     * @param actual          how many statements the text holds
     * @param securityManager the session's security manager, or null when there is no session
     * @param inBlock         whether the EXECUTE IMMEDIATE is a statement of a Snowflake Scripting block
     */
    public static void require(final int actual, final SecurityManager securityManager, final boolean inBlock) {
        final Integer requested = inBlock ? null : REQUEST_COUNT.get();
        final int desired = requested != null ? requested.intValue() : sessionCount(securityManager);
        if (desired != 0 && actual != desired) {
            throw new StatementCountMismatch(actual, desired);
        }
    }

    /** The session's MULTI_STATEMENT_COUNT: 1 until something sets it, 0 meaning any number. */
    private static int sessionCount(final SecurityManager securityManager) {
        final Object value = securityManager == null || securityManager.getSessionContext() == null ? null
            : securityManager.getSessionContext().getSessionParameter("MULTI_STATEMENT_COUNT");
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString().trim());
            } catch (final NumberFormatException notANumber) {
                return 1;
            }
        }
        return 1;
    }
}
