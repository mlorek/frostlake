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

package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A block's handler reads the code and the state of the failure's family as the client's driver does: a missing
 * schema, sequence or stage 2003 / 02000 and a missing table 2003 / 42S02, a text no conversion reads, a number out
 * of its range, a text too long, an ambiguous column, an expression no GROUP BY takes, no XML, a statement no ID
 * names, a missing column, an unknown table function and a set operation's column counts each its own pair, and a
 * NULL in a NOT NULL column 100072 / 00000 where the client reads 22000 (all live-verified).
 */
public class HandlerErrorFamilyTest extends MeasuredCellsTestSupport {

    @Test
    public void aHandlerReadsTheFamily() {
        engine.execute("CREATE OR REPLACE TABLE hc649 (n INT)");
        engine.execute("INSERT INTO hc649 VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE hc649s (s VARCHAR(2))");
        engine.execute("CREATE OR REPLACE TABLE hc649nn (a INT NOT NULL)");
        assertAnswer("2003 02000",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT * FROM test_db.no_such_schema_649.t; RETURN 'none'; EXCEPTION WHEN "
                + "OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END $$");
        assertAnswer("2003 02000",
            "EXECUTE IMMEDIATE $$ BEGIN DROP SEQUENCE no_such_seq_649; RETURN 'none'; EXCEPTION WHEN OTHER THEN "
                + "RETURN SQLCODE || ' ' || SQLSTATE; END $$");
        assertAnswer("2003 02000",
            "EXECUTE IMMEDIATE $$ BEGIN DROP STAGE no_such_stage_649; RETURN 'none'; EXCEPTION WHEN OTHER THEN "
                + "RETURN SQLCODE || ' ' || SQLSTATE; END $$");
        assertAnswer("2003 42S02",
            "EXECUTE IMMEDIATE $$ BEGIN DROP TABLE no_such_table_649; RETURN 'none'; EXCEPTION WHEN OTHER THEN "
                + "RETURN SQLCODE || ' ' || SQLSTATE; END $$");
        assertAnswer("100035 22007",
            "EXECUTE IMMEDIATE $$ BEGIN LET x TIMESTAMP := 'x'::TIMESTAMP; RETURN 'none'; EXCEPTION WHEN OTHER THEN "
                + "RETURN SQLCODE || ' ' || SQLSTATE; END $$");
        assertAnswer("100046 22003",
            "EXECUTE IMMEDIATE $$ BEGIN LET x NUMBER := CAST(1000 AS NUMBER(2,0)); RETURN 'none'; EXCEPTION WHEN "
                + "OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END $$");
        assertAnswer("100078 22000",
            "EXECUTE IMMEDIATE $$ BEGIN INSERT INTO hc649s VALUES ('abc'); RETURN 'none'; EXCEPTION WHEN OTHER THEN "
                + "RETURN SQLCODE || ' ' || SQLSTATE; END $$");
        assertAnswer("100072 00000",
            "EXECUTE IMMEDIATE $$ BEGIN INSERT INTO hc649nn VALUES (NULL); RETURN 'none'; EXCEPTION WHEN OTHER THEN "
                + "RETURN SQLCODE || ' ' || SQLSTATE; END $$");
        assertAnswer("2028 42601",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT n FROM hc649 a, hc649 b; RETURN 'none'; EXCEPTION WHEN OTHER THEN "
                + "RETURN SQLCODE || ' ' || SQLSTATE; END $$");
        assertAnswer("979 42601",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT n, COUNT(*) FROM hc649; RETURN 'none'; EXCEPTION WHEN OTHER THEN "
                + "RETURN SQLCODE || ' ' || SQLSTATE; END $$");
        assertAnswer("100100 22P02",
            "EXECUTE IMMEDIATE $$ BEGIN LET x VARIANT := PARSE_XML('<a>'); RETURN 'none'; EXCEPTION WHEN OTHER THEN "
                + "RETURN SQLCODE || ' ' || SQLSTATE; END $$");
        assertAnswer("709 02000",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT * FROM TABLE(RESULT_SCAN('01234567-0000-0000-0000-000000000000')); "
                + "RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END $$");
        assertAnswer("1431 42601",
            "EXECUTE IMMEDIATE $$ BEGIN ALTER TABLE hc649 DROP COLUMN zz; RETURN 'none'; EXCEPTION WHEN OTHER THEN "
                + "RETURN SQLCODE || ' ' || SQLSTATE; END $$");
        assertAnswer("100037 22018",
            "EXECUTE IMMEDIATE $$ BEGIN LET x BOOLEAN := 'x'::BOOLEAN; RETURN 'none'; EXCEPTION WHEN OTHER THEN "
                + "RETURN SQLCODE || ' ' || SQLSTATE; END $$");
        assertAnswer("2142 42601",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT * FROM TABLE(no_such_udtf_649()); RETURN 'none'; EXCEPTION WHEN OTHER "
                + "THEN RETURN SQLCODE || ' ' || SQLSTATE; END $$");
        assertAnswer("1789 42601",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1 UNION SELECT 1, 2; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN "
                + "SQLCODE || ' ' || SQLSTATE; END $$");
    }
}
