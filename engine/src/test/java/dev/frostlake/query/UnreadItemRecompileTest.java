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

package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * A derived table or a CTE whose unread items are never computed still compiles whole every time its statement
 * runs: the same text run again after a column, a function or a table its unread item names was dropped is
 * refused where the item stands, as the first run of the text would be (all live-verified).
 */
public class UnreadItemRecompileTest extends MeasuredCellsTestSupport {

    @Test
    public void theSameTextIsRefusedAgainAfterWhatAnUnreadItemNamesIsDropped() {
        assertAnswer("Table T725 successfully created.", "CREATE OR REPLACE TABLE t725 (a INT, b INT)");
        assertAnswer("1", "INSERT INTO t725 VALUES (1, 10)");
        assertAnswer("1", "WITH c AS (SELECT a, b FROM t725) SELECT a FROM c");
        assertAnswer("Statement executed successfully.", "ALTER TABLE t725 DROP COLUMN b");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'B'",
            "WITH c AS (SELECT a, b FROM t725) SELECT a FROM c");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'B'",
            "WITH c AS (SELECT a, b FROM t725)  SELECT a FROM c");
        assertAnswer("Statement executed successfully.", "ALTER TABLE t725 ADD COLUMN b INT");
        assertAnswer("1", "SELECT x FROM (SELECT a AS x, b AS y FROM t725)");
        assertAnswer("Statement executed successfully.", "ALTER TABLE t725 DROP COLUMN b");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 30 | invalid identifier 'B'",
            "SELECT x FROM (SELECT a AS x, b AS y FROM t725)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 31 | invalid identifier 'B'",
            "SELECT x  FROM (SELECT a AS x, b AS y FROM t725)");
        assertAnswer("Function F725 successfully created.",
            "CREATE OR REPLACE FUNCTION f725(x INT) RETURNS INT AS 'x + 1'");
        assertAnswer("1", "WITH c AS (SELECT a AS x, f725(a) AS y FROM t725) SELECT x FROM c");
        assertAnswer("F725 successfully dropped.", "DROP FUNCTION f725(INT)");
        assertAnswer("REFUSED SQL compilation error: | Unknown function F725.",
            "WITH c AS (SELECT a AS x, f725(a) AS y FROM t725) SELECT x FROM c");
        assertAnswer("Table H725 successfully created.", "CREATE OR REPLACE TABLE h725 (z INT)");
        assertAnswer("1", "WITH c AS (SELECT a, (SELECT MAX(z) FROM h725) h FROM t725) SELECT a FROM c");
        assertAnswer("H725 successfully dropped.", "DROP TABLE h725");
        assertAnswer("REFUSED SQL compilation error: | Object 'H725' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE H725.",
            "WITH c AS (SELECT a, (SELECT MAX(z) FROM h725) h FROM t725) SELECT a FROM c");
    }

    /** Gives a table to a role the session does not hold, which the account's test role may not do. */
    @Test
    public void theSameTextIsRefusedAgainOnceTheRoleMayNotReadATableAnUnreadItemReads() {
        if (isLiveSnowflake()) {
            return;
        }
        engine.execute("USE ROLE ACCOUNTADMIN");
        engine.execute("USE SECONDARY ROLES NONE");
        engine.execute("CREATE ROLE r726");
        engine.execute("CREATE OR REPLACE TABLE t726 (a INT)");
        engine.execute("INSERT INTO t726 VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE hidden726 (z INT)");
        final String cte = "WITH c AS (SELECT a, (SELECT MAX(z) FROM hidden726) AS h FROM t726) SELECT a FROM c";
        final String derived = "SELECT x FROM (SELECT a AS x, (SELECT MAX(z) FROM hidden726) AS y FROM t726)";
        assertAnswer("1", cte);
        assertAnswer("1", derived);
        engine.execute("GRANT OWNERSHIP ON TABLE hidden726 TO ROLE r726 COPY CURRENT GRANTS");
        final String refusal = "REFUSED SQL access control error: | Insufficient privileges to operate on table "
            + "'HIDDEN726'. Your primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.HIDDEN726.";
        assertAnswer(refusal, cte);
        assertAnswer(refusal, derived);
    }
}
