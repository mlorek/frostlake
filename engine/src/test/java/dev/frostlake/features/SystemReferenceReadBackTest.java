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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A token SYSTEM$REFERENCE issued for a table or a view reads as that object where IDENTIFIER() or TABLE() names
 * it in a procedure, and a row change through a reference made without the privilege it takes is refused
 * (all live-verified).
 */
public class SystemReferenceReadBackTest extends MeasuredCellsTestSupport {

    @Test
    public void referencesReadBack() {
        engine.execute("CREATE OR REPLACE TABLE tsr (a INT)");
        engine.execute("INSERT INTO tsr VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE VIEW vsr AS SELECT a FROM tsr");
        engine.execute("CREATE OR REPLACE STREAM ssr2 ON TABLE tsr");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_id(r VARCHAR) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN LET n "
            + "NUMBER := (SELECT COUNT(*) FROM IDENTIFIER(:r)); RETURN n; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_tb(r VARCHAR) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN LET n "
            + "NUMBER := (SELECT COUNT(*) FROM TABLE(:r)); RETURN n; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_ins(r VARCHAR) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN INSERT "
            + "INTO IDENTIFIER(:r) VALUES (9); RETURN 1; END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected "
                + "'SYSTEM$REFERENCE'. | syntax error line 1 at position 63 unexpected ')'.",
            "SELECT COUNT(*) FROM IDENTIFIER(SYSTEM$REFERENCE('TABLE', 'tsr'))");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function SYSTEM$REFERENCE",
            "SELECT COUNT(*) FROM TABLE(SYSTEM$REFERENCE('TABLE', 'tsr'))");
        assertAnswer("2", "CALL pr_id(SYSTEM$REFERENCE('TABLE', 'tsr'))");
        assertAnswer("2", "CALL pr_tb(SYSTEM$REFERENCE('TABLE', 'tsr'))");
        assertAnswer("2", "CALL pr_id(SYSTEM$REFERENCE('TABLE', 'tsr', 'CALL', 'SELECT'))");
        assertAnswer("2", "CALL pr_id(SYSTEM$REFERENCE('TABLE', 'tsr', 'SESSION', 'SELECT'))");
        assertAnswer("2", "CALL pr_id(SYSTEM$REFERENCE('VIEW', 'vsr'))");
        assertAnswer("2", "CALL pr_id('tsr')");
        assertAnswer("REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "CALL pr_id(SYSTEM$REFERENCE('TABLE', 'nosuch_t'))");
        assertAnswer("1", "CALL pr_ins(SYSTEM$REFERENCE('TABLE', 'tsr', 'CALL', 'INSERT'))");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Specified object "
                + "does not exist or not authorized for the reference with allowed privileges: SELECT.",
            "CALL pr_ins(SYSTEM$REFERENCE('TABLE', 'tsr', 'CALL', 'SELECT'))");
        assertAnswer("3", "CALL pr_id(SYSTEM$REFERENCE('TABLE', 'tsr', 'PERSISTENT', 'SELECT'))");
        assertAnswer("3", "CALL pr_tb(SYSTEM$REFERENCE('VIEW', 'vsr'))");
        assertAnswer("3", "SELECT COUNT(*) FROM tsr");
    }
}
