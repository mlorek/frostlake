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
 * EXECUTE IMMEDIATE ... USING binds a numbered placeholder by its place in the list, each time it is written, in
 * any order and beside a ? the list binds in turn, keeping the bound value's type; a number the list does not
 * reach, :0 and a number with a leading zero bind nothing, and a :1 inside a text is no bind (all live-verified).
 */
public class ExecuteImmediateNumberedBindsTest extends MeasuredCellsTestSupport {

    @Test
    public void aNumberedPlaceholderBindsByItsPlace() {
        engine.execute("CREATE OR REPLACE TABLE tvr (v VARIANT, n INT)");
        assertAnswer("ok",
            "DECLARE s VARCHAR(10); BEGIN EXECUTE IMMEDIATE 'INSERT INTO tvr (v) VALUES (:1)' USING (s); RETURN "
                + "'ok'; END;");
        assertAnswer("1|0", "SELECT COUNT(*), COUNT(v) FROM tvr");
        assertAnswer("xx",
            "DECLARE s VARCHAR(10) DEFAULT 'x'; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT :1 || :1 AS "
                + "r' USING (s)); RETURN TABLE(res); END;");
        assertAnswer("xy",
            "DECLARE s VARCHAR(10) DEFAULT 'x'; t VARCHAR(10) DEFAULT 'y'; res RESULTSET; BEGIN res := (EXECUTE "
                + "IMMEDIATE 'SELECT :1 || :2 AS r' USING (s, t)); RETURN TABLE(res); END;");
        assertAnswer("yx",
            "DECLARE s VARCHAR(10) DEFAULT 'x'; t VARCHAR(10) DEFAULT 'y'; res RESULTSET; BEGIN res := (EXECUTE "
                + "IMMEDIATE 'SELECT :2 || :1 AS r' USING (s, t)); RETURN TABLE(res); END;");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 64 : SQL compilation "
                + "error: error line 1 at position 7 | Bind variable :2 not set.",
            "DECLARE s VARCHAR(10) DEFAULT 'x'; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT :2 AS r' "
                + "USING (s)); RETURN TABLE(res); END;");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 64 : SQL compilation "
                + "error: error line 1 at position 7 | Bind variable :0 not set.",
            "DECLARE s VARCHAR(10) DEFAULT 'x'; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT :0 AS r' "
                + "USING (s)); RETURN TABLE(res); END;");
        assertAnswer("xx",
            "DECLARE s VARCHAR(10) DEFAULT 'x'; t VARCHAR(10) DEFAULT 'y'; res RESULTSET; BEGIN res := (EXECUTE "
                + "IMMEDIATE 'SELECT ? || :1 AS r' USING (s, t)); RETURN TABLE(res); END;");
        assertAnswer(":1",
            "DECLARE s VARCHAR(10) DEFAULT 'x'; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT '':1'' AS r' "
                + "USING (s)); RETURN TABLE(res); END;");
        assertAnswer("5|VARCHAR(1)[LOB]",
            "DECLARE n INT DEFAULT 4; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT :1 + 1 AS r, "
                + "SYSTEM$TYPEOF(:1) AS t' USING (n)); RETURN TABLE(res); END;");
        assertAnswer("ok",
            "DECLARE n INT DEFAULT 4; BEGIN EXECUTE IMMEDIATE 'INSERT INTO tvr (n) VALUES (:1)' USING (n); RETURN "
                + "'ok'; END;");
        assertAnswer("4", "SELECT n FROM tvr WHERE n IS NOT NULL");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 64 : SQL compilation "
                + "error: error line 1 at position 7 | Bind variable :01 not set.",
            "DECLARE s VARCHAR(10) DEFAULT 'x'; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT :01 AS r' "
                + "USING (s)); RETURN TABLE(res); END;");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 64 : SQL compilation "
                + "error: | syntax error line 1 at position 11 unexpected 'AS'.",
            "DECLARE s VARCHAR(10) DEFAULT 'x'; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT :1x AS r' "
                + "USING (s)); RETURN TABLE(res); END;");
    }
}
