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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * CREATE VIEW compiles its body and reads no row (live-verified, pinned on both engines): a body whose
 * values would fault at row time is a view all the same — its columns known to DESCRIBE — and the fault
 * waits for whoever selects from it, with the row-time sentence the same select would raise anywhere.
 * A body that does not COMPILE is still refused at CREATE, and a table written FROM such a body (CTAS)
 * still evaluates it and fails inside the DML envelope.
 */
public class DeferredViewFaultTest extends BaseDatabaseTest {

    @BeforeEach
    public void fixture() {
        engine.execute("CREATE OR REPLACE TABLE nc (s VARCHAR)");
        engine.execute("INSERT INTO nc VALUES ('abcdefgh')");
    }

    private String refusal(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return e.getMessage();
    }

    private String viewColumnType(final String view) {
        return String.valueOf(engine.executeQuery("DESC VIEW " + view).getRows().get(0).getValue(1));
    }

    @Test
    public void aValueTimeFaultWaitsForTheSelect() {
        engine.execute("CREATE OR REPLACE VIEW nc_v AS SELECT CAST(s AS VARCHAR(5)) AS c FROM nc");
        assertEquals("VARCHAR(5)", viewColumnType("nc_v"));
        assertEquals("String 'abcdefgh' is too long and would be truncated", refusal("SELECT c FROM nc_v"));
        engine.execute("CREATE OR REPLACE VIEW v_over AS SELECT c FROM nc_v");
        assertEquals("String 'abcdefgh' is too long and would be truncated", refusal("SELECT c FROM v_over"));
        engine.execute("CREATE OR REPLACE VIEW v_lit AS SELECT 'abcdefgh'::VARCHAR(5) AS c");
        assertEquals("String 'abcdefgh' is too long and would be truncated", refusal("SELECT c FROM v_lit"));
        engine.execute("CREATE OR REPLACE VIEW v_num AS SELECT s::NUMBER AS c FROM nc");
        assertEquals("NUMBER(38,0)", viewColumnType("v_num"));
        assertEquals("Numeric value 'abcdefgh' is not recognized", refusal("SELECT c FROM v_num"));
        engine.execute("CREATE OR REPLACE VIEW v_num_lit AS SELECT 'abc'::NUMBER AS c");
        assertEquals("Numeric value 'abc' is not recognized", refusal("SELECT c FROM v_num_lit"));
        engine.execute("CREATE OR REPLACE VIEW v_range AS SELECT 12345::NUMBER(3,0) AS c");
        assertEquals("Number out of representable range: type FIXED[SB2](3,0){not null}, value 12345",
            refusal("SELECT c FROM v_range"));
        engine.execute("CREATE OR REPLACE VIEW v_div AS SELECT 1/0 AS c");
        assertEquals("Division by zero", refusal("SELECT c FROM v_div"));
        engine.execute("CREATE OR REPLACE VIEW v_div2 AS SELECT 1/n AS c FROM (SELECT 0 AS n)");
        assertEquals("Division by zero", refusal("SELECT c FROM v_div2"));
        engine.execute("CREATE OR REPLACE VIEW v_div3 AS SELECT 1/LENGTH(s) - 1/(LENGTH(s)-8) AS c FROM nc");
        assertEquals("Division by zero", refusal("SELECT c FROM v_div3"));
        engine.execute("CREATE OR REPLACE VIEW v_date AS SELECT TO_DATE('abc') AS c");
        assertEquals("Date 'abc' is not recognized", refusal("SELECT c FROM v_date"));
        engine.execute("CREATE OR REPLACE VIEW v_date2 AS SELECT TO_DATE(s) AS c FROM nc");
        assertEquals("Date 'abcdefgh' is not recognized", refusal("SELECT c FROM v_date2"));
        engine.execute("CREATE OR REPLACE VIEW v_json AS SELECT PARSE_JSON(s) AS j FROM nc");
        assertEquals("Error parsing JSON: unknown keyword \"abcdefgh\", pos 9", refusal("SELECT j FROM v_json"));
    }

    @Test
    public void aBodyThatDoesNotCompileIsStillRefusedAtCreate() {
        assertEquals("SQL compilation error: error line 1 at position 42\ninvalid identifier 'NOSUCH'",
            refusal("CREATE OR REPLACE VIEW v_badcol AS SELECT nosuch FROM nc"));
        assertEquals("SQL compilation error:\nUnknown function NOSUCHFN.",
            refusal("CREATE OR REPLACE VIEW v_badfn AS SELECT NOSUCHFN(s) AS c FROM nc"));
        assertEquals("SQL compilation error:\ninvalid type [TO_DATE(1631711999)] for parameter 'TO_DATE'",
            refusal("CREATE OR REPLACE VIEW v_badtype AS SELECT TO_DATE(1631711999) AS c"));
        assertEquals("SQL compilation error: error line 1 at position 44\nInvalid argument types for function 'SUM': (BOOLEAN)",
            refusal("CREATE OR REPLACE VIEW v_agg_bool AS SELECT SUM(TRUE) AS c"));
    }

    @Test
    public void aTableWrittenFromTheBodyStillEvaluatesIt() {
        assertEquals("DML operation to table TEST_DB.TEST_SCHEMA.T_CTAS failed on column C with error: "
            + "String 'abcdefgh' is too long and would be truncated",
            refusal("CREATE OR REPLACE TABLE t_ctas AS SELECT CAST(s AS VARCHAR(5)) AS c FROM nc"));
        assertEquals("DML operation to table TEST_DB.TEST_SCHEMA.T_CTAS2 failed on column C with error: Division by zero",
            refusal("CREATE OR REPLACE TABLE t_ctas2 AS SELECT 1/0 AS c"));
    }
}
