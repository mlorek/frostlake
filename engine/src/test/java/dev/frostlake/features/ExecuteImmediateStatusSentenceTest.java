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

package dev.frostlake.features;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

/**
 * A statement run through EXECUTE IMMEDIATE answers with the status sentence of the statement its text ran:
 * {@code EXECUTE IMMEDIATE 'CREATE TABLE t (a INT)'} says "Table T successfully created." where Frostlake
 * said "Statement executed successfully.", and a RESULTSET filled from it holds that sentence too, whether
 * a LET or a DECLARE … DEFAULT fills it. An EXECUTE IMMEDIATE FROM a staged script answers for the script's
 * last statement, the conditional sentence included, which once named the stage instead of the table.
 */
public class ExecuteImmediateStatusSentenceTest extends BaseDatabaseTest {

    /** The first column's name and the first row's value, from whichever engine is under test. */
    private String answerOf(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getColumns().get(0).getName() + ": " + String.valueOf(rs.getRows().get(0).getValue(0));
    }

    @Test
    public void aTopLevelExecuteImmediateAnswersForTheStatementItRan() {
        assertEquals("status: Table EIS_T1 successfully created.",
            answerOf("EXECUTE IMMEDIATE 'CREATE OR REPLACE TABLE eis_t1 (a INT)'"));
        assertEquals("status: EIS_T1 already exists, statement succeeded.",
            answerOf("EXECUTE IMMEDIATE 'CREATE TABLE IF NOT EXISTS eis_t1 (a INT)'"));
        assertEquals("status: Drop statement executed successfully (EIS_NOTHERE already dropped).",
            answerOf("EXECUTE IMMEDIATE 'DROP TABLE IF EXISTS eis_nothere'"));
        assertEquals("status: Statement executed successfully.",
            answerOf("EXECUTE IMMEDIATE 'ALTER TABLE eis_t1 ADD COLUMN b INT'"));
        assertEquals("status: Table EIS_T2 successfully created.",
            answerOf("EXECUTE IMMEDIATE 'EXECUTE IMMEDIATE ''CREATE OR REPLACE TABLE eis_t2 (a INT)'''"));
        assertEquals("status: EIS_T2 successfully dropped.",
            answerOf("EXECUTE IMMEDIATE 'DROP TABLE eis_t2'"));
    }

    @Test
    public void aResultSetFilledFromExecuteImmediateHoldsTheStatementsSentence() {
        answerOf("CREATE OR REPLACE TABLE eis_base (a INT)");
        assertEquals("status: View EIS_V successfully created.", answerOf("""
            EXECUTE IMMEDIATE $$
            BEGIN
                LET r RESULTSET := (EXECUTE IMMEDIATE 'CREATE OR REPLACE VIEW eis_v AS SELECT a FROM eis_base');
                RETURN TABLE(r);
            END;
            $$"""));
        assertEquals("status: EIS_BASE already exists, statement succeeded.", answerOf("""
            EXECUTE IMMEDIATE $$
            BEGIN
                LET r RESULTSET := (EXECUTE IMMEDIATE 'CREATE TABLE IF NOT EXISTS eis_base (a INT)');
                RETURN TABLE(r);
            END;
            $$"""));
        assertEquals("status: EIS_V successfully dropped.", answerOf("""
            EXECUTE IMMEDIATE $$
            DECLARE
                r RESULTSET DEFAULT (EXECUTE IMMEDIATE 'DROP VIEW eis_v');
            BEGIN
                RETURN TABLE(r);
            END;
            $$"""));
        assertEquals("status: Drop statement executed successfully (EIS_GONE already dropped).", answerOf("""
            EXECUTE IMMEDIATE $$
            DECLARE
                r RESULTSET DEFAULT (EXECUTE IMMEDIATE 'DROP TABLE IF EXISTS eis_gone');
            BEGIN
                RETURN TABLE(r);
            END;
            $$"""));
        assertEquals("status: Sequence EIS_SEQ successfully created.", answerOf("""
            EXECUTE IMMEDIATE $$
            BEGIN
                LET r RESULTSET := (EXECUTE IMMEDIATE 'EXECUTE IMMEDIATE ''CREATE OR REPLACE SEQUENCE eis_seq''');
                RETURN TABLE(r);
            END;
            $$"""));
        assertEquals("anonymous block: Table EIS_T3 successfully created.", answerOf("""
            EXECUTE IMMEDIATE $$
            BEGIN
                LET r RESULTSET := (EXECUTE IMMEDIATE 'CREATE OR REPLACE TABLE eis_t3 (a INT)');
                LET s VARCHAR := '';
                FOR x IN r DO
                    s := s || x."status";
                END FOR;
                RETURN s;
            END;
            $$"""));
    }

    @Test
    public void aStagedScriptAnswersForItsLastStatement() {
        answerOf("CREATE OR REPLACE STAGE eis_st");
        answerOf("CREATE OR REPLACE TABLE eis_kept (a INT)");
        stageLocalFile("eis_st", "two.sql", """
            CREATE OR REPLACE TABLE eis_f1 (a INT);
            CREATE OR REPLACE TABLE eis_f2 (a INT);
            """);
        stageLocalFile("eis_st", "kept.sql", """
            CREATE TABLE IF NOT EXISTS eis_kept (a INT);
            """);
        stageLocalFile("eis_st", "kept_then_new.sql", """
            CREATE TABLE IF NOT EXISTS eis_kept (a INT);
            CREATE OR REPLACE TABLE eis_f3 (a INT);
            """);
        assertEquals("status: Table EIS_F2 successfully created.",
            answerOf("EXECUTE IMMEDIATE FROM @eis_st/two.sql"));
        assertEquals("status: EIS_KEPT already exists, statement succeeded.",
            answerOf("EXECUTE IMMEDIATE FROM @eis_st/kept.sql"));
        assertEquals("status: Table EIS_F3 successfully created.",
            answerOf("EXECUTE IMMEDIATE FROM @eis_st/kept_then_new.sql"));
        assertEquals("status: EIS_KEPT already exists, statement succeeded.", answerOf("""
            EXECUTE IMMEDIATE $$
            BEGIN
                LET r RESULTSET := (EXECUTE IMMEDIATE FROM @eis_st/kept.sql);
                RETURN TABLE(r);
            END;
            $$"""));
    }
}
