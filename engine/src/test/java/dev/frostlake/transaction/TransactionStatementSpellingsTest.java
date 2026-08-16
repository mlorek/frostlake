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

package dev.frostlake.transaction;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WORK is the SQL-standard synonym of TRANSACTION and Snowflake accepts it on all three transaction
 * statements (live-verified: {@code BEGIN WORK}, {@code COMMIT WORK}, {@code ROLLBACK WORK}) — the
 * same transaction the plain spellings start and end, not a separate construct.
 *
 * <p>The keyword stays an ordinary name: live accepts a COLUMN and a TABLE called {@code work}, so
 * admitting the spelling must not reserve the word.
 */
public class TransactionStatementSpellingsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE tx_rows (n INT)");
    }

    private String rowCount() {
        return engine.executeQuery("SELECT COUNT(*) FROM tx_rows").getRows().get(0).getValue(0).toString();
    }

    @Test
    public void beginWorkStartsATransactionThatCommits() {
        engine.execute("BEGIN WORK");
        engine.execute("INSERT INTO tx_rows VALUES (1)");
        engine.execute("COMMIT");
        assertEquals("1", rowCount());
    }

    @Test
    public void commitWorkEndsIt() {
        engine.execute("BEGIN");
        engine.execute("INSERT INTO tx_rows VALUES (1)");
        engine.execute("COMMIT WORK");
        assertEquals("1", rowCount());
    }

    @Test
    public void rollbackWorkDiscardsIt() {
        engine.execute("BEGIN WORK");
        engine.execute("INSERT INTO tx_rows VALUES (1)");
        engine.execute("ROLLBACK WORK");
        assertEquals("0", rowCount());
    }

    @Test
    public void workRemainsUsableAsAColumnName() {
        engine.execute("CREATE TABLE kw_column (work INT)");
        engine.execute("INSERT INTO kw_column VALUES (7)");
        assertEquals("7", engine.executeQuery("SELECT work FROM kw_column WHERE work = 7")
            .getRows().get(0).getValue(0).toString());
    }

    @Test
    public void workRemainsUsableAsATableName() {
        engine.execute("CREATE TABLE work (n INT)");
        engine.execute("INSERT INTO work VALUES (3)");
        assertEquals("3", engine.executeQuery("SELECT n FROM work").getRows().get(0).getValue(0).toString());
    }
}
