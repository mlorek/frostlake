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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Where a CREATE TABLE … AS query takes the table's options: before the AS, after a list of column names as
 * after a typed one or none, and never after the query.
 */
public class CreateTableAsOptionsTest extends BaseDatabaseTest {

    private static final String SYNTAX = "SQL compilation error:\nsyntax error line 1 at position ";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE plain_t (a INT)");
    }

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private String shown(final String column) {
        final ResultSet rs = engine.executeQuery("SHOW TABLES LIKE 'TQ'");
        return cell(rs, soleRowWhere(rs, "name", "TQ"), column);
    }

    @Test
    public void optionsStandBetweenAListOfColumnNamesAndTheQuery() {
        engine.execute("CREATE OR REPLACE TABLE tq (x) COMMENT = 'c' AS SELECT 1");
        assertEquals("c", shown("comment"));
        assertEquals("1", shown("rows"));
        engine.execute("CREATE OR REPLACE TABLE tq (x) CLUSTER BY (x) COMMENT = 'cm' AS SELECT 1");
        assertEquals("cm", shown("comment"));
        engine.execute("CREATE OR REPLACE TABLE tq (x) COPY GRANTS AS SELECT 1");
        engine.execute("CREATE OR REPLACE TABLE tq (x) DATA_RETENTION_TIME_IN_DAYS = 1 AS SELECT 1");
        assertEquals("1", shown("retention_time"));
        engine.execute("CREATE OR REPLACE TABLE tq (x) CHANGE_TRACKING = TRUE AS SELECT 1");
        assertEquals("ON", shown("change_tracking"));
        // Before the list as well, and before the query of a table with typed columns or with none.
        engine.execute("CREATE OR REPLACE TABLE tq COMMENT = 'before' (x) AS SELECT 1");
        assertEquals("before", shown("comment"));
        engine.execute("CREATE OR REPLACE TABLE tq (x INT) COMMENT = 'typed' AS SELECT 1 a");
        assertEquals("typed", shown("comment"));
        engine.execute("CREATE OR REPLACE TABLE tq COMMENT = 'none' AS SELECT 1 x");
        assertEquals("none", shown("comment"));
    }

    @Test
    public void aListOfColumnNamesIsStillJudgedWithOptionsAfterIt() {
        assertEquals("SQL compilation error:\nInvalid column definition list",
            refusalOf("CREATE OR REPLACE TABLE tq (x, y) COMMENT = 'c' AS SELECT 1"));
        assertEquals("SQL compilation error: error line 1 at position 43\ninvalid identifier 'NOSUCH'",
            refusalOf("CREATE OR REPLACE TABLE tq (x) CLUSTER BY (nosuch) AS SELECT 1"));
        // A list of names without a query is no table.
        assertEquals(SYNTAX + "29 unexpected ')'.", refusalOf("CREATE OR REPLACE TABLE tq (x) COMMENT = 'c'"));
        assertEquals(SYNTAX + "29 unexpected ')'.", refusalOf("CREATE OR REPLACE TABLE tq (x)"));
    }

    @Test
    public void noOptionFollowsTheQuery() {
        assertEquals(SYNTAX + "45 unexpected 'COMMENT'.",
            refusalOf("CREATE OR REPLACE TABLE tq (x) AS SELECT 1 a COMMENT = 'c'"));
        assertEquals(SYNTAX + "41 unexpected 'COMMENT'.",
            refusalOf("CREATE OR REPLACE TABLE tq AS SELECT 1 a COMMENT = 'c'"));
        assertEquals(SYNTAX + "49 unexpected 'COMMENT'.",
            refusalOf("CREATE OR REPLACE TABLE tq (x INT) AS SELECT 1 a COMMENT = 'c'"));
        assertEquals(SYNTAX + "41 unexpected 'CLUSTER'.",
            refusalOf("CREATE OR REPLACE TABLE tq AS SELECT 1 a CLUSTER BY (a)"));
        assertEquals(SYNTAX + "43 unexpected 'COMMENT'.",
            refusalOf("CREATE OR REPLACE TABLE tq AS (SELECT 1 a) COMMENT = 'c'"));
        assertEquals(SYNTAX + "64 unexpected 'COMMENT'.",
            refusalOf("CREATE OR REPLACE TABLE tq AS SELECT a FROM plain_t WHERE a = 1 COMMENT = 'c'"));
        assertEquals(SYNTAX + "64 unexpected 'COPY'.",
            refusalOf("CREATE OR REPLACE TABLE tq AS SELECT a FROM plain_t WHERE a = 1 COPY GRANTS"));
        // Where the word reads as an alias, the fault is what follows it.
        assertEquals(SYNTAX + "51 unexpected '='.",
            refusalOf("CREATE OR REPLACE TABLE tq (x) AS SELECT 1 COMMENT = 'c'"));
        assertEquals(SYNTAX + "60 unexpected '='.",
            refusalOf("CREATE OR REPLACE TABLE tq AS SELECT a FROM plain_t COMMENT = 'c'"));
    }
}
