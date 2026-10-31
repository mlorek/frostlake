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

package dev.frostlake.http;

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.ExecutionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A session's temporary table keeps its table stage to itself: another session's temporary table of the same name
 * starts with an empty stage and lists none of the first one's files, and what one session stages there goes with
 * the session.
 */
public class SessionTemporaryTableStageTest {

    private ConcurrentDatabaseEngine engine;
    private SessionContext first;
    private SessionContext second;

    @BeforeEach
    public void startEngine() {
        engine = new ConcurrentDatabaseEngine();
        first = engine.createSession();
        second = engine.createSession();
        run(first, "CREATE DATABASE tmp_stage");
        run(first, "USE SCHEMA tmp_stage.public");
        run(second, "USE SCHEMA tmp_stage.public");
    }

    @AfterEach
    public void stopEngine() {
        engine.shutdown();
    }

    /** A statement's rows, the first cell of each, or its refusal on one line. */
    private String answer(final SessionContext session, final String sql) {
        final ExecutionResult result;
        try {
            result = engine.execute(sql, session);
        } catch (final RuntimeException refused) {
            return "REFUSED " + String.valueOf(refused.getMessage()).replace("\n", " | ");
        }
        if (!result.isSuccess()) {
            return "REFUSED " + String.valueOf(result.getErrorMessage()).replace("\n", " | ");
        }
        final StringBuilder out = new StringBuilder();
        if (!result.getResultSets().isEmpty()) {
            for (int i = 0; i < result.getResultSets().get(0).getRows().size(); i++) {
                out.append(i == 0 ? "" : " ;; ")
                    .append(result.getResultSets().get(0).getRows().get(i).getValue(0));
            }
        }
        return out.toString();
    }

    private void run(final SessionContext session, final String sql) {
        final String answered = answer(session, sql);
        assertTrue(!answered.startsWith("REFUSED"), sql + " ==> " + answered);
    }

    @Test
    public void anotherSessionsTemporaryTableOfTheNameListsNoneOfItsFiles() {
        run(first, "CREATE TEMPORARY TABLE tt (a INT)");
        run(first, "COPY INTO @%tt/one.csv FROM (SELECT 1) SINGLE = TRUE");
        assertEquals("one.csv", answer(first, "LIST @%tt"));
        run(second, "CREATE TEMPORARY TABLE tt (a INT)");
        assertEquals("", answer(second, "LIST @%tt"));
        run(second, "COPY INTO @%tt/two.csv FROM (SELECT 2) SINGLE = TRUE");
        assertEquals("two.csv", answer(second, "LIST @%tt"));
        assertEquals("one.csv", answer(first, "LIST @%tt"));
        engine.endSession(first);
        assertEquals("two.csv", answer(second, "LIST @%tt"));
    }
}
