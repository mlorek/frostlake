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
 * Two readings of a USING list (live-verified): a NULL is bound as a text with no value, a widthless VARCHAR,
 * whatever the variable's type; and a bound value that stands as a whole predicate is named in the type
 * refusal by its placeholder as written, {@code [?]} or {@code [:1]}, in a cursor's query and in an EXECUTE
 * IMMEDIATE text alike.
 */
public class UsingBindTypedNullTest extends MeasuredCellsTestSupport {

    @Test
    public void aNullIsBoundAsAText() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        assertCells(new String[][] {
            {"DECLARE x INT; c CURSOR FOR SELECT SYSTEM$TYPEOF(?); r VARCHAR; BEGIN OPEN c USING (x); FETCH c INTO r; "
                + "RETURN r; END;",
                "VARCHAR[LOB]"},
            {"DECLARE x VARCHAR; c CURSOR FOR SELECT SYSTEM$TYPEOF(?); r VARCHAR; BEGIN OPEN c USING (x); FETCH c "
                + "INTO r; RETURN r; END;",
                "VARCHAR[LOB]"},
            {"DECLARE x INT DEFAULT NULL; c CURSOR FOR SELECT SYSTEM$TYPEOF(?); r VARCHAR; BEGIN OPEN c USING (x); "
                + "FETCH c INTO r; RETURN r; END;",
                "VARCHAR[LOB]"},
            {"DECLARE x INT; c CURSOR FOR SELECT ? IS NULL; r VARCHAR; BEGIN OPEN c USING (x); FETCH c INTO r; RETURN "
                + "r; END;",
                "true"},
            {"DECLARE x INT; c CURSOR FOR SELECT COALESCE(?, 'z'); r VARCHAR; BEGIN OPEN c USING (x); FETCH c INTO r; "
                + "RETURN r; END;",
                "z"},
            {"DECLARE x DATE; c CURSOR FOR SELECT SYSTEM$TYPEOF(?); r VARCHAR; BEGIN OPEN c USING (x); FETCH c INTO "
                + "r; RETURN r; END;",
                "VARCHAR[LOB]"},
            {"DECLARE x VARIANT; c CURSOR FOR SELECT SYSTEM$TYPEOF(?); r VARCHAR; BEGIN OPEN c USING (x); FETCH c "
                + "INTO r; RETURN r; END;",
                "VARCHAR[LOB]"},
            {"DECLARE x INT; c CURSOR FOR SELECT SYSTEM$TYPEOF(? + 1); r VARCHAR; BEGIN OPEN c USING (x); FETCH c "
                + "INTO r; RETURN r; END;",
                "NUMBER(19,0)[SB1]"},
            {"DECLARE x BOOLEAN DEFAULT TRUE; c CURSOR FOR SELECT id FROM fz WHERE ? ORDER BY id; r VARCHAR; BEGIN "
                + "OPEN c USING (x); FETCH c INTO r; RETURN r; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 101 : SQL compilation "
                + "error: | Invalid data type [VARCHAR(4)] for predicate [?]"},
            {"DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT id FROM fz WHERE ? ORDER BY id; r VARCHAR; BEGIN OPEN c "
                + "USING (x); FETCH c INTO r; RETURN r; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 94 : SQL compilation "
                + "error: | Invalid data type [VARCHAR(1)] for predicate [?]"},
            {"DECLARE x VARCHAR DEFAULT 'abc'; c CURSOR FOR SELECT id FROM fz WHERE ? ORDER BY id; r VARCHAR; BEGIN "
                + "OPEN c USING (x); FETCH c INTO r; RETURN r; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 102 : SQL compilation "
                + "error: | Invalid data type [VARCHAR(3)] for predicate [?]"},
            {"DECLARE x BOOLEAN DEFAULT TRUE; c CURSOR FOR SELECT id FROM fz WHERE NOT ? ORDER BY id; r VARCHAR; "
                + "BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;",
                "null"},
            {"DECLARE x BOOLEAN DEFAULT TRUE; c CURSOR FOR SELECT id FROM fz WHERE b AND ? ORDER BY id; r VARCHAR; "
                + "BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;",
                "5"},
        });
    }

    @Test
    public void aBoundPredicateIsNamedByItsPlaceholder() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        assertCells(new String[][] {
            {"DECLARE x INT; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT SYSTEM$TYPEOF(?) t' USING (x)); "
                + "RETURN TABLE(res); END;",
                "VARCHAR[LOB]"},
            {"DECLARE x INT; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT ? + 1 v' USING (x)); RETURN "
                + "TABLE(res); END;",
                "null"},
            {"DECLARE x BOOLEAN DEFAULT TRUE; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT id FROM fz WHERE "
                + "?' USING (x)); RETURN TABLE(res); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 61 : SQL compilation "
                + "error: | Invalid data type [VARCHAR(4)] for predicate [?]"},
            {"DECLARE x BOOLEAN DEFAULT TRUE; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT id FROM fz WHERE "
                + ":1' USING (x)); RETURN TABLE(res); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 61 : SQL compilation "
                + "error: | Invalid data type [VARCHAR(4)] for predicate [:1]"},
            {"DECLARE x BOOLEAN DEFAULT TRUE; c CURSOR FOR SELECT id FROM fz WHERE ?; r VARCHAR; BEGIN OPEN c USING "
                + "(x); FETCH c INTO r; RETURN r; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 89 : SQL compilation "
                + "error: | Invalid data type [VARCHAR(4)] for predicate [?]"},
            {"DECLARE x INT; c CURSOR FOR SELECT id FROM fz WHERE ?; r VARCHAR; BEGIN OPEN c USING (x); FETCH c INTO "
                + "r; RETURN r; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 72 : SQL compilation "
                + "error: | Invalid data type [VARCHAR(134217728)] for predicate [?]"},
            {"DECLARE x INT; c CURSOR FOR SELECT ? + 1; r VARCHAR; BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; "
                + "END;",
                "null"},
            {"DECLARE x INT; c CURSOR FOR SELECT SYSTEM$TYPEOF(? + 1); r VARCHAR; BEGIN OPEN c USING (x); FETCH c "
                + "INTO r; RETURN r; END;",
                "NUMBER(19,0)[SB1]"},
            {"DECLARE x INT; c CURSOR FOR SELECT SYSTEM$TYPEOF(COALESCE(?, 1)); r VARCHAR; BEGIN OPEN c USING (x); "
                + "FETCH c INTO r; RETURN r; END;",
                "NUMBER(1,0)[SB1]"},
            {"DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT id FROM fz WHERE ? AND b; r VARCHAR; BEGIN OPEN c USING "
                + "(x); FETCH c INTO r; RETURN r; END;",
                "5"},
        });
    }
}
