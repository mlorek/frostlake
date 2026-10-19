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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The refusal of a column default calling a Snowflake Scripting function echoes the default as the plan spells
 * it: the call as the conversion of its answer to the number it declares, TO_NUMBER(FBLK()), and a conversion to
 * FLOAT as TO_DOUBLE; a routine parameter's default refuses a subquery as an invalid default argument expression
 * (all live-verified).
 */
public class ColumnDefaultEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void aDefaultsEchoSpellsTheConversions() {
        engine.execute("CREATE OR REPLACE FUNCTION typed() RETURNS TABLE (s VARCHAR(3)) AS 'SELECT ''abcdef'''");
        engine.execute("CREATE OR REPLACE TABLE ct AS SELECT * FROM TABLE(typed())");
        engine.execute("CREATE OR REPLACE FUNCTION t_bin() RETURNS TABLE (b BINARY(1)) AS 'SELECT "
            + "TO_BINARY(''ABCD'')'");
        engine.execute("CREATE OR REPLACE FUNCTION tn() RETURNS TABLE (n NUMBER(5,2)) AS 'SELECT 1.26'");
        engine.execute("CREATE OR REPLACE FUNCTION fblk() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; END; $$");
        assertAnswer("REFUSED SQL compilation error: | Invalid column default expression [TO_NUMBER(FBLK())]",
            "CREATE OR REPLACE TABLE tdf (a NUMBER DEFAULT fblk())");
        assertAnswer("REFUSED SQL compilation error: | Invalid column default expression [(TO_NUMBER(FBLK())) + 1]",
            "CREATE OR REPLACE TABLE tdf2 (a FLOAT DEFAULT fblk() + 1)");
        assertAnswer("REFUSED SQL compilation error: | Invalid column default expression "
                + "[CASE_FLATTENED((TO_DOUBLE(TO_NUMBER(FBLK()))) > (TO_DOUBLE(0.5)), 1, 0)]",
            "CREATE OR REPLACE TABLE tdf3 (a NUMBER DEFAULT CASE WHEN fblk() > 0.5::FLOAT THEN 1 ELSE 0 END)");
        assertAnswer("Table TDF4 successfully created.", "CREATE OR REPLACE TABLE tdf4 (a FLOAT DEFAULT RANDOM() + 1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 50 | invalid default argument "
                + "expression (SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)",
            "CREATE OR REPLACE PROCEDURE p10(x NUMBER DEFAULT (SELECT 1)) RETURNS NUMBER LANGUAGE SQL AS 'BEGIN "
                + "RETURN x; END;'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 49 | invalid default argument "
                + "expression (SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)",
            "CREATE OR REPLACE FUNCTION f12(x NUMBER DEFAULT (SELECT 1)) RETURNS NUMBER AS 'x'");
        assertAnswer("Function P13 successfully created.",
            "CREATE OR REPLACE PROCEDURE p13(x NUMBER DEFAULT 1 + 1) RETURNS NUMBER LANGUAGE SQL AS 'BEGIN RETURN x; "
                + "END;'");
        assertAnswer("2", "CALL p13()");
    }
}
