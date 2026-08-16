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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The INFORMATION_SCHEMA table functions, and the two ways Frostlake used to be more permissive than
 * the account.
 *
 * <p>They exist ONLY under INFORMATION_SCHEMA, so the bare name resolves to nothing — Frostlake
 * resolved a qualified name by its last part and so never noticed the qualifier was missing. And their
 * leading positional parameter is a TIME-RANGE BOUND, not a result limit: {@code QUERY_HISTORY(2)}
 * blames {@code 'END_TIME_RANGE_START'}, where Frostlake read the 2 as a row cap and answered rows.
 */
public class HistoryTableFunctionArgumentTest extends BaseDatabaseTest {

    /** The bare name is not a function, however it is spelled. */
    @Test
    public void theBareNameIsAnInvalidIdentifier() {
        assertEquals("SQL compilation error:\nInvalid identifier QUERY_HISTORY",
            messageOf("SELECT * FROM TABLE(QUERY_HISTORY(RESULT_LIMIT => 2))"));
        assertEquals("SQL compilation error:\nInvalid identifier TASK_HISTORY",
            messageOf("SELECT * FROM TABLE(TASK_HISTORY(RESULT_LIMIT => 2))"));
        assertEquals("SQL compilation error:\nInvalid identifier TAG_REFERENCES",
            messageOf("SELECT * FROM TABLE(TAG_REFERENCES('t', 'TABLE'))"));
    }

    /** Both qualified spellings run — the schema alone, and database-qualified. */
    @Test
    public void theQualifiedSpellingsRun() {
        assertNotNull(engine.executeQuery(
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY(RESULT_LIMIT => 2))"));
        assertNotNull(engine.executeQuery(
            "SELECT * FROM TABLE(test_db.INFORMATION_SCHEMA.QUERY_HISTORY(RESULT_LIMIT => 2))"));
        assertNotNull(engine.executeQuery(
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY(RESULT_LIMIT => 2))"));
    }

    /**
     * The leading positional parameter is a timestamp, and it is named in the refusal rather than
     * reported by position — unlike FLATTEN or SPLIT_TO_TABLE, whose positional parameters are '1'
     * and '2'. A date-shaped STRING is refused too: there is no implicit conversion here.
     */
    @Test
    public void theLeadingPositionalParameterIsATimeRangeBound() {
        assertEquals("SQL compilation error:\n"
            + "invalid type [NUMBER(1,0)] for parameter 'END_TIME_RANGE_START'",
            messageOf("SELECT * FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY(2))"));
        assertEquals("SQL compilation error:\n"
            + "invalid type [NUMBER(1,0)] for parameter 'SCHEDULED_TIME_RANGE_START'",
            messageOf("SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY(2))"));
        assertEquals("SQL compilation error:\n"
            + "invalid type [VARCHAR(10)] for parameter 'END_TIME_RANGE_START'",
            messageOf("SELECT * FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY('2026-01-01'))"));
    }

    private String messageOf(final String sql) {
        final RuntimeException thrown = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, "Snowflake refuses this statement: " + sql);
        Throwable root = thrown;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return String.valueOf(root.getMessage());
    }
}
