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
 * A SYSTEM$REFERENCE token reads and writes as its privileges allow: one made with none takes SELECT alone, an
 * UPDATE or a DELETE that reads the rows it changes takes SELECT beside its own privilege, each refusal naming the
 * privileges the reference carries, a SESSION token reads its table in later statements, and a function's token
 * calls the function in IDENTIFIER(:r)(...) (all live-verified).
 */
public class ObjectReferencePrivilegeTest extends MeasuredCellsTestSupport {

    @Test
    public void privilegesAndDomains() {
        engine.execute("CREATE OR REPLACE TABLE tsr (a INT)");
        engine.execute("INSERT INTO tsr VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE tsr2 (a INT)");
        engine.execute("INSERT INTO tsr2 VALUES (1)");
        engine.execute("CREATE OR REPLACE FUNCTION fr(x NUMBER) RETURNS NUMBER AS 'x + 1'");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_id(r VARCHAR) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN LET n "
            + "NUMBER := (SELECT COUNT(*) FROM IDENTIFIER(:r)); RETURN n; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_ins(r VARCHAR) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN INSERT "
            + "INTO IDENTIFIER(:r) VALUES (9); RETURN 1; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_upd(r VARCHAR) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN UPDATE "
            + "IDENTIFIER(:r) SET a = a + 100 WHERE a = 9; RETURN 1; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_del(r VARCHAR) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN DELETE "
            + "FROM IDENTIFIER(:r) WHERE a > 100; RETURN 1; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_fn(r VARCHAR) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN LET n "
            + "NUMBER := (SELECT IDENTIFIER(:r)(1)); RETURN n; END $$");
        assertAnswer("Statement executed successfully.",
            "SET tok = (SELECT SYSTEM$REFERENCE('TABLE', 'tsr', 'CALL', 'SELECT'))");
        assertAnswer("Statement executed successfully.",
            "SET stok = (SELECT SYSTEM$REFERENCE('TABLE', 'tsr', 'SESSION', 'SELECT'))");
        assertAnswer("2", "SELECT COUNT(*) FROM IDENTIFIER($stok)");
        assertAnswer("2", "CALL pr_id($stok)");
        assertAnswer("Statement executed successfully.",
            "SET ptok = (SELECT SYSTEM$REFERENCE('TABLE', 'tsr2', 'PERSISTENT', 'SELECT'))");
        assertAnswer("Table TSR2 successfully created.", "CREATE OR REPLACE TABLE tsr2 (a INT)");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Specified object "
                + "does not exist or not authorized for the reference with allowed privileges: SELECT.",
            "CALL pr_ins(SYSTEM$REFERENCE('TABLE', 'tsr'))");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Specified object "
                + "does not exist or not authorized for the reference with allowed privileges: INSERT.",
            "CALL pr_upd(SYSTEM$REFERENCE('TABLE', 'tsr', 'CALL', 'INSERT'))");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Specified object "
                + "does not exist or not authorized for the reference with allowed privileges: INSERT.",
            "CALL pr_del(SYSTEM$REFERENCE('TABLE', 'tsr', 'CALL', 'INSERT'))");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Specified object "
                + "does not exist or not authorized for the reference with allowed privileges: UPDATE.",
            "CALL pr_upd(SYSTEM$REFERENCE('TABLE', 'tsr', 'CALL', 'UPDATE'))");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Specified object "
                + "does not exist or not authorized for the reference with allowed privileges: DELETE.",
            "CALL pr_del(SYSTEM$REFERENCE('TABLE', 'tsr', 'CALL', 'DELETE'))");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Specified object "
                + "does not exist or not authorized for the reference with allowed privileges: SELECT.",
            "CALL pr_upd(SYSTEM$REFERENCE('TABLE', 'tsr'))");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Specified object "
                + "does not exist or not authorized for the reference with allowed privileges: INSERT, UPDATE.",
            "CALL pr_del(SYSTEM$REFERENCE('TABLE', 'tsr', 'CALL', 'INSERT', 'UPDATE'))");
        assertAnswer("2", "CALL pr_fn(SYSTEM$REFERENCE('FUNCTION', 'fr(NUMBER)'))");
        assertAnswer("2", "CALL pr_fn(SYSTEM$REFERENCE('FUNCTION', 'fr(NUMBER)', 'CALL', 'USAGE'))");
        assertAnswer("true", "SELECT SYSTEM$REFERENCE('TABLE', 'tsr') IS NOT NULL");
        assertAnswer("true", "SELECT LENGTH(SYSTEM$REFERENCE('TABLE', 'tsr')) > 10");
        assertAnswer("2", "SELECT COUNT(*) FROM tsr");
    }
}
