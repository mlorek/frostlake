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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IF NOT EXISTS skips only an object that exists; every other refusal still reaches the caller. Which one
 * wins depends on the kind: a TABLE's column list and a SEQUENCE's, FILE FORMAT's or STAGE's options are
 * judged before existence, so they refuse over an existing object too, while a VIEW and a SCHEMA answer
 * existence first. A refused statement creates nothing. Every cell is live-verified.
 */
public class IfNotExistsPrecedenceTest extends BaseDatabaseTest {

    private static final String INTERNAL_STAGE_ENCRYPTION =
        "Cannot set URL, credentials, or encryption key of an internal or temporary stage.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE ine_t (a INT)");
        engine.execute("CREATE VIEW ine_v AS SELECT 1 AS a");
        engine.execute("CREATE SEQUENCE ine_q");
        engine.execute("CREATE FILE FORMAT ine_ff TYPE = CSV");
        engine.execute("CREATE STAGE ine_st");
        engine.execute("CREATE SCHEMA ine_s");
        engine.execute("USE SCHEMA test_db.test_schema");
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), sql + " -> " + refused.getMessage());
    }

    private int count(final String sql) {
        return engine.executeQuery(sql).getRows().size();
    }

    @Test
    public void aTableColumnListAndSequenceOptionsAreJudgedFirst() {
        assertRefused("CREATE TABLE IF NOT EXISTS ine_t (c1 INT, c1 INT)", "duplicate column name 'C1'");
        assertRefused("CREATE TABLE IF NOT EXISTS ine_t2 (c1 INT, c1 INT)", "duplicate column name 'C1'");
        assertRefused("CREATE SEQUENCE IF NOT EXISTS ine_q INCREMENT = 0",
            "invalid value '0' for property 'SEQUENCE_INCREMENT'");
        assertRefused("CREATE SEQUENCE IF NOT EXISTS ine_q2 INCREMENT = 0",
            "invalid value '0' for property 'SEQUENCE_INCREMENT'");
        assertEquals(0, count("SHOW TABLES LIKE 'INE_T2'"));
        assertEquals(0, count("SHOW SEQUENCES LIKE 'INE_Q2'"));
    }

    @Test
    public void fileFormatAndStageOptionsAreJudgedFirst() {
        assertRefused("CREATE FILE FORMAT IF NOT EXISTS ine_ff TYPE = CSV SKIP_HEADER = -1",
            "invalid value [-1] for parameter 'SKIP_HEADER'");
        assertRefused("CREATE FILE FORMAT IF NOT EXISTS ine_ff2 TYPE = CSV SKIP_HEADER = -1",
            "invalid value [-1] for parameter 'SKIP_HEADER'");
        assertRefused("CREATE FILE FORMAT IF NOT EXISTS ine_ff TYPE = JSON SKIP_HEADER = 1",
            "Option SKIP_HEADER is not valid for file format type JSON.");
        assertRefused("CREATE FILE FORMAT IF NOT EXISTS ine_ff2 TYPE = JSON SKIP_HEADER = 1",
            "Option SKIP_HEADER is not valid for file format type JSON.");
        assertRefused("CREATE STAGE IF NOT EXISTS ine_st ENCRYPTION = (TYPE = 'AWS_SSE_S3')",
            INTERNAL_STAGE_ENCRYPTION);
        assertRefused("CREATE STAGE IF NOT EXISTS ine_st2 ENCRYPTION = (TYPE = 'AWS_SSE_S3')",
            INTERNAL_STAGE_ENCRYPTION);
        assertRefused("CREATE STAGE IF NOT EXISTS ine_st BOGUS_OPTION = 1",
            "invalid property 'BOGUS_OPTION' for 'STAGE'");
        assertRefused("CREATE STAGE IF NOT EXISTS ine_st2 BOGUS_OPTION = 1",
            "invalid property 'BOGUS_OPTION' for 'STAGE'");
        assertEquals(0, count("SHOW FILE FORMATS LIKE 'INE_FF2'"));
        assertEquals(0, count("SHOW STAGES LIKE 'INE_ST2'"));
    }

    @Test
    public void aViewAndASchemaAnswerExistenceFirst() {
        engine.execute("CREATE VIEW IF NOT EXISTS ine_v AS SELECT nosuchcol FROM ine_t");
        assertRefused("CREATE VIEW IF NOT EXISTS ine_v2 AS SELECT nosuchcol FROM ine_t",
            "error line 1 at position 43\ninvalid identifier 'NOSUCHCOL'");
        assertEquals(0, count("SHOW VIEWS LIKE 'INE_V2'"));
        engine.execute("CREATE SCHEMA IF NOT EXISTS ine_s DATA_RETENTION_TIME_IN_DAYS = -1");
        engine.execute("USE SCHEMA test_db.test_schema");
        assertRefused("CREATE SCHEMA IF NOT EXISTS ine_s2 DATA_RETENTION_TIME_IN_DAYS = -1",
            "invalid value [-1] for parameter 'DATA_RETENTION_TIME_IN_DAYS'");
        assertEquals(0, count("SHOW SCHEMAS LIKE 'INE_S2'"));
    }
}
