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

package dev.frostlake.security;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One session's ALTER SESSION parameters and SET variables. A session starts from a new session's settings —
 * every parameter at its default, MULTI_STATEMENT_COUNT at 1, no variable — and nothing another session sets
 * reaches it: live, a TIMEZONE, a QUERY_TAG, a TIMESTAMP_TYPE_MAPPING or a variable one connection sets is
 * invisible to another, which still reads the default and refuses {@code $v} as a variable that does not exist.
 */
public final class SessionSettings {

    /**
     * The smallest and the largest SIXTEEN-digit number. Live numbers every session with a 16-digit id —
     * {@code CURRENT_SESSION()} answers e.g. 1169029968573758, and two sessions opened moments apart get ids a
     * few thousand apart — so the engine hands out consecutive 16-digit numbers from a random start.
     */
    private static final long SMALLEST_SESSION_NUMBER = 1_000_000_000_000_000L;
    private static final long LARGEST_FIRST_SESSION_NUMBER = 9_000_000_000_000_000L;
    private static final AtomicLong NEXT_SESSION_NUMBER = new AtomicLong(
        ThreadLocalRandom.current().nextLong(SMALLEST_SESSION_NUMBER, LARGEST_FIRST_SESSION_NUMBER));

    private final Map<String, Object> parameters = new HashMap<String, Object>();
    private final Map<String, Object> variables = new LinkedHashMap<String, Object>();
    /** The session's number: what CURRENT_SESSION() answers and every listing that names a session shows. */
    private final long sessionNumber = NEXT_SESSION_NUMBER.getAndIncrement();
    /** The SQL text of the statement this session is running, what CURRENT_STATEMENT() answers. */
    private volatile String currentStatement;
    /** The public id of this session's last committed or rolled-back transaction, for LAST_TRANSACTION(). */
    private volatile String lastTransactionId;

    /** A new session's settings. */
    public SessionSettings() {
        reset();
    }

    /**
     * The number of the session these settings belong to, fixed for the session's life: a {@code reset()}
     * returns the parameters and variables to a new session's, but the session stays the same session.
     *
     * @return the session's 16-digit number
     */
    public long getSessionNumber() {
        return sessionNumber;
    }

    /** The statement this session is running now; each session has its own. */
    public String getCurrentStatement() {
        return currentStatement;
    }

    public void setCurrentStatement(final String currentStatement) {
        this.currentStatement = currentStatement;
    }

    /**
     * The id of THIS session's last completed transaction — never another session's, so a session that has
     * not yet ended one answers NULL however many other sessions have.
     */
    public String getLastTransactionId() {
        return lastTransactionId;
    }

    public void setLastTransactionId(final String lastTransactionId) {
        this.lastTransactionId = lastTransactionId;
    }

    /** The parameters ALTER SESSION set, by upper-case name. */
    Map<String, Object> parameters() {
        return parameters;
    }

    /** The variables SET defined, by upper-case name, in the order they were defined. */
    Map<String, Object> variables() {
        return variables;
    }

    /** Back to a new session's settings. */
    void reset() {
        parameters.clear();
        parameters.put("MULTI_STATEMENT_COUNT", 1);
        variables.clear();
    }
}
