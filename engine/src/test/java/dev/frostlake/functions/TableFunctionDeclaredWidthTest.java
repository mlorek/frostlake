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

package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A table function's text column is not held to the width it declares, and an INSERT ... SELECT writes it into a
 * column of that width: the write is judged by the declared width, not by the value (all live-verified).
 */
public class TableFunctionDeclaredWidthTest extends MeasuredCellsTestSupport {

    @Test
    public void aDeclaredWidthJudgesTheWriteNotTheValue() {
        engine.execute("CREATE OR REPLACE FUNCTION typed() RETURNS TABLE (s VARCHAR(3)) AS 'SELECT ''abcdef'''");
        engine.execute("CREATE OR REPLACE TABLE ct AS SELECT * FROM TABLE(typed())");
        engine.execute("CREATE OR REPLACE FUNCTION t_bin() RETURNS TABLE (b BINARY(1)) AS 'SELECT "
            + "TO_BINARY(''ABCD'')'");
        engine.execute("CREATE OR REPLACE FUNCTION tn() RETURNS TABLE (n NUMBER(5,2)) AS 'SELECT 1.26'");
        engine.execute("CREATE OR REPLACE FUNCTION fblk() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; END; $$");
        assertAnswer("abcdef|6|VARCHAR(3)[LOB]", "SELECT s, LENGTH(s), SYSTEM$TYPEOF(s) FROM ct");
        assertAnswer("1", "INSERT INTO ct SELECT * FROM TABLE(typed())");
        assertAnswer("abcdef|6 ;; abcdef|6", "SELECT s, LENGTH(s) FROM ct ORDER BY 1");
        assertAnswer("abcdef|6|VARCHAR(3)[LOB]", "SELECT s, LENGTH(s), SYSTEM$TYPEOF(s) FROM TABLE(typed())");
        assertAnswer("2", "SELECT LENGTH(b) FROM TABLE(t_bin())");
        assertAnswer("ABCD", "SELECT b FROM TABLE(t_bin())");
        assertAnswer("NUMBER(5,2)[SB1]|1.26", "SELECT SYSTEM$TYPEOF(n), n FROM TABLE(tn())");
    }
}
