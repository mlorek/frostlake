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
 * What a USING list hands the statement it binds: the TEXT of each value, whatever its variable's type — a
 * cursor's OPEN … USING and a dynamic text's EXECUTE IMMEDIATE … USING alike. SYSTEM$TYPEOF(?) over a FLOAT
 * holding 6.5 is VARCHAR(3); a NUMBER is its digits at the declared scale, a FLOAT fifteen significant digits
 * with an exponent where its magnitude calls for one, a time its declared fractional digits. The statement
 * converts the text where it needs another type, so an infinity bound into a comparison with a NUMBER is
 * refused as no number, and a placeholder standing as a row count is refused (live-verified).
 */
public class UsingBindTextTest extends MeasuredCellsTestSupport {

    @Test
    public void aCursorsInfinityAndOtherFloats() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 106 : Numeric value 'inf' "
                + "is not recognized",
            "DECLARE x FLOAT DEFAULT 'inf'::FLOAT; c CURSOR FOR SELECT id FROM fz WHERE id < ?; r INT DEFAULT 0; "
                + "BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 107 : Numeric value "
                + "'-inf' is not recognized",
            "DECLARE x FLOAT DEFAULT '-inf'::FLOAT; c CURSOR FOR SELECT id FROM fz WHERE id > ?; r INT DEFAULT 0; "
                + "BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 106 : Numeric value 'NaN' "
                + "is not recognized",
            "DECLARE x FLOAT DEFAULT 'NaN'::FLOAT; c CURSOR FOR SELECT id FROM fz WHERE id < ?; r INT DEFAULT 0; "
                + "BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "5",
            "DECLARE x FLOAT DEFAULT 6.5; c CURSOR FOR SELECT id FROM fz WHERE id < ?; r INT DEFAULT 0; BEGIN "
                + "OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 107 : SQL compilation "
                + "error: error line 1 at position 29 | Bind variable :x not set.",
            "DECLARE x FLOAT DEFAULT 'inf'::FLOAT; c CURSOR FOR SELECT id FROM fz WHERE id < :x; r INT DEFAULT 0; "
                + "BEGIN OPEN c; FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 100 : Numeric value 'inf' "
                + "is not recognized",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; LET c CURSOR FOR SELECT id FROM fz WHERE id < ?; LET r INT := 0; "
                + "OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 136 : Numeric value 'inf' "
                + "is not recognized",
            "DECLARE x FLOAT DEFAULT 'inf'::FLOAT; y FLOAT DEFAULT 1; c CURSOR FOR SELECT id FROM fz WHERE id < ? "
                + "AND id > ?; r INT DEFAULT 0; BEGIN OPEN c USING (x, y); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "100038 22018 Numeric value 'inf' is not recognized",
            "DECLARE x FLOAT DEFAULT 'inf'::FLOAT; c CURSOR FOR SELECT id FROM fz WHERE id < ?; r INT DEFAULT 0; "
                + "BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' "
                + "|| SQLSTATE || ' ' || SQLERRM; END;");
        assertAnswer(
            "100038 22018 Numeric value 'NaN' is not recognized",
            "DECLARE x FLOAT DEFAULT 'NaN'::FLOAT; c CURSOR FOR SELECT id FROM fz WHERE id < ?; r INT DEFAULT 0; "
                + "BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' "
                + "|| SQLSTATE || ' ' || SQLERRM; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 101 : Numeric value 'inf' "
                + "is not recognized",
            "DECLARE x VARCHAR DEFAULT 'inf'; c CURSOR FOR SELECT id FROM fz WHERE id < ?; r INT DEFAULT 0; BEGIN "
                + "OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 101 : Numeric value 'abc' "
                + "is not recognized",
            "DECLARE x VARCHAR DEFAULT 'abc'; c CURSOR FOR SELECT id FROM fz WHERE id < ?; r INT DEFAULT 0; BEGIN "
                + "OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "inf",
            "DECLARE x FLOAT DEFAULT 'inf'::FLOAT; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); "
                + "FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "NaN",
            "DECLARE x FLOAT DEFAULT 'NaN'::FLOAT; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); "
                + "FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "6.5",
            "DECLARE x FLOAT DEFAULT 6.5; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); FETCH c "
                + "INTO r; RETURN r; END;");
        assertAnswer(
            "inf",
            "DECLARE x FLOAT DEFAULT 'inf'::FLOAT; c CURSOR FOR SELECT ?::FLOAT AS v; r VARCHAR; BEGIN OPEN c "
                + "USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 87 : Numeric value 'inf' "
                + "is not recognized",
            "DECLARE x FLOAT DEFAULT 'inf'::FLOAT; c CURSOR FOR SELECT ? + 1 AS v; r VARCHAR; BEGIN OPEN c USING "
                + "(x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "7.5",
            "DECLARE x FLOAT DEFAULT 6.5; c CURSOR FOR SELECT ? + 1 AS v; r VARCHAR; BEGIN OPEN c USING (x); "
                + "FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "VARCHAR(3)[LOB]",
            "DECLARE x FLOAT DEFAULT 'inf'::FLOAT; c CURSOR FOR SELECT SYSTEM$TYPEOF(?) AS v; r VARCHAR; BEGIN "
                + "OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "VARCHAR(3)[LOB]",
            "DECLARE x FLOAT DEFAULT 6.5; c CURSOR FOR SELECT SYSTEM$TYPEOF(?) AS v; r VARCHAR; BEGIN OPEN c "
                + "USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "VARCHAR(1)[LOB]",
            "DECLARE x INT DEFAULT 6; c CURSOR FOR SELECT SYSTEM$TYPEOF(?) AS v; r VARCHAR; BEGIN OPEN c USING "
                + "(x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "VARCHAR(1)[LOB]",
            "DECLARE x VARCHAR DEFAULT 'a'; c CURSOR FOR SELECT SYSTEM$TYPEOF(?) AS v; r VARCHAR; BEGIN OPEN c "
                + "USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "5",
            "DECLARE x FLOAT DEFAULT 'inf'::FLOAT; c CURSOR FOR SELECT id FROM fz WHERE id::FLOAT < ?; r INT "
                + "DEFAULT 0; BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "5",
            "DECLARE x FLOAT DEFAULT 'NaN'::FLOAT; c CURSOR FOR SELECT id FROM fz WHERE id::FLOAT < ?; r INT "
                + "DEFAULT 0; BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "5",
            "DECLARE x FLOAT DEFAULT '-inf'::FLOAT; c CURSOR FOR SELECT id FROM fz WHERE id::FLOAT > ?; r INT "
                + "DEFAULT 0; BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 106 : Numeric value 'inf' "
                + "is not recognized",
            "DECLARE x FLOAT DEFAULT 'inf'::FLOAT; c CURSOR FOR SELECT id FROM fz WHERE ? > id; r INT DEFAULT 0; "
                + "BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 123 : Numeric value 'inf' "
                + "is not recognized",
            "DECLARE x FLOAT DEFAULT 'inf'::FLOAT; c CURSOR FOR SELECT id FROM fz WHERE id < ? ORDER BY id DESC; "
                + "r INT DEFAULT 0; BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        answer(
            "DECLARE x FLOAT DEFAULT 1e308; c CURSOR FOR SELECT id FROM fz WHERE id < ?; r INT DEFAULT 0; BEGIN "
                + "OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "null",
            "DECLARE x FLOAT; c CURSOR FOR SELECT id FROM fz WHERE id < ?; r INT DEFAULT 0; BEGIN OPEN c USING "
                + "(x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "2",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; LET r INT := (SELECT COUNT(*) FROM fz WHERE id < :x); RETURN r; "
                + "END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 56 : SQL compilation "
                + "error: | Invalid bind value (inf) for type (REAL).",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; LET rs RESULTSET := (SELECT id FROM fz WHERE id < :x); RETURN "
                + "TABLE(rs); END;");
    }

    @Test
    public void theTextOfEachType() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        assertAnswer(
            "VARCHAR(4)[LOB]",
            "DECLARE x BOOLEAN DEFAULT TRUE; c CURSOR FOR SELECT SYSTEM$TYPEOF(?) AS v; r VARCHAR; BEGIN OPEN c "
                + "USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "true",
            "DECLARE x BOOLEAN DEFAULT TRUE; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); FETCH "
                + "c INTO r; RETURN r; END;");
        assertAnswer(
            "VARCHAR(10)[LOB]",
            "DECLARE x DATE DEFAULT '2024-01-15'; c CURSOR FOR SELECT SYSTEM$TYPEOF(?) AS v; r VARCHAR; BEGIN "
                + "OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "2024-01-15",
            "DECLARE x DATE DEFAULT '2024-01-15'; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); "
                + "FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "VARCHAR(29)[LOB]",
            "DECLARE x TIMESTAMP_NTZ DEFAULT '2024-01-15 10:20:30.5'; c CURSOR FOR SELECT SYSTEM$TYPEOF(?) AS v; "
                + "r VARCHAR; BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        answer(
            "DECLARE x TIMESTAMP_NTZ DEFAULT '2024-01-15 10:20:30.5'; c CURSOR FOR SELECT ? AS v; r VARCHAR; "
                + "BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "VARCHAR(18)[LOB]",
            "DECLARE x TIME DEFAULT '10:20:30'; c CURSOR FOR SELECT SYSTEM$TYPEOF(?) AS v; r VARCHAR; BEGIN OPEN "
                + "c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "10:20:30.000000000",
            "DECLARE x TIME DEFAULT '10:20:30'; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); "
                + "FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "VARCHAR(4)[LOB]",
            "DECLARE x NUMBER(5,2) DEFAULT 1.5; c CURSOR FOR SELECT SYSTEM$TYPEOF(?) AS v; r VARCHAR; BEGIN OPEN "
                + "c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "1.50",
            "DECLARE x NUMBER(5,2) DEFAULT 1.5; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); "
                + "FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "VARCHAR(1)[LOB]",
            "DECLARE x FLOAT DEFAULT 2; c CURSOR FOR SELECT SYSTEM$TYPEOF(?) AS v; r VARCHAR; BEGIN OPEN c USING "
                + "(x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "2",
            "DECLARE x FLOAT DEFAULT 2; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); FETCH c "
                + "INTO r; RETURN r; END;");
        assertAnswer(
            "VARCHAR(20)[LOB]",
            "DECLARE x NUMBER(38,0) DEFAULT 12345678901234567890; c CURSOR FOR SELECT SYSTEM$TYPEOF(?) AS v; r "
                + "VARCHAR; BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "12345678901234567890",
            "DECLARE x NUMBER(38,0) DEFAULT 12345678901234567890; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN "
                + "OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        answer(
            "DECLARE x VARIANT DEFAULT PARSE_JSON('{\"a\": 1}'); c CURSOR FOR SELECT SYSTEM$TYPEOF(?) AS v; r "
                + "VARCHAR; BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        answer(
            "DECLARE x VARIANT DEFAULT PARSE_JSON('{\"a\": 1}'); c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN "
                + "OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        answer(
            "DECLARE x ARRAY DEFAULT ARRAY_CONSTRUCT(1, 2); c CURSOR FOR SELECT SYSTEM$TYPEOF(?) AS v; r VARCHAR; "
                + "BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        answer(
            "DECLARE x ARRAY DEFAULT ARRAY_CONSTRUCT(1, 2); c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c "
                + "USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "VARCHAR(4)[LOB]",
            "DECLARE x BINARY DEFAULT TO_BINARY('6162'); c CURSOR FOR SELECT SYSTEM$TYPEOF(?) AS v; r VARCHAR; "
                + "BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "6162",
            "DECLARE x BINARY DEFAULT TO_BINARY('6162'); c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c "
                + "USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "VARCHAR(5)[LOB]",
            "DECLARE x FLOAT DEFAULT 1e20; c CURSOR FOR SELECT SYSTEM$TYPEOF(?) AS v; r VARCHAR; BEGIN OPEN c "
                + "USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "1e+20",
            "DECLARE x FLOAT DEFAULT 1e20; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); FETCH c "
                + "INTO r; RETURN r; END;");
        assertAnswer(
            "VARCHAR(3)[LOB]",
            "DECLARE x FLOAT DEFAULT 0.1; c CURSOR FOR SELECT SYSTEM$TYPEOF(?) AS v; r VARCHAR; BEGIN OPEN c "
                + "USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "0.1",
            "DECLARE x FLOAT DEFAULT 0.1; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); FETCH c "
                + "INTO r; RETURN r; END;");
        assertAnswer(
            "VARCHAR(1)[LOB]",
            "DECLARE x FLOAT DEFAULT -0.0; c CURSOR FOR SELECT SYSTEM$TYPEOF(?) AS v; r VARCHAR; BEGIN OPEN c "
                + "USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "0",
            "DECLARE x FLOAT DEFAULT -0.0; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); FETCH c "
                + "INTO r; RETURN r; END;");
        assertAnswer(
            "VARCHAR(4)[LOB]",
            "DECLARE x VARCHAR DEFAULT 'it''s'; c CURSOR FOR SELECT SYSTEM$TYPEOF(?) AS v; r VARCHAR; BEGIN OPEN "
                + "c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "it's",
            "DECLARE x VARCHAR DEFAULT 'it''s'; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); "
                + "FETCH c INTO r; RETURN r; END;");
        answer(
            "DECLARE x INT; c CURSOR FOR SELECT SYSTEM$TYPEOF(?) AS v; r VARCHAR; BEGIN OPEN c USING (x); FETCH c "
                + "INTO r; RETURN r; END;");
        assertAnswer(
            "null",
            "DECLARE x INT; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); FETCH c INTO r; RETURN "
                + "r; END;");
        assertAnswer(
            "VARCHAR(36)[LOB]",
            "DECLARE x TIMESTAMP_TZ DEFAULT '2024-01-15 10:20:30 +02:00'; c CURSOR FOR SELECT SYSTEM$TYPEOF(?) AS "
                + "v; r VARCHAR; BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        answer(
            "DECLARE x TIMESTAMP_TZ DEFAULT '2024-01-15 10:20:30 +02:00'; c CURSOR FOR SELECT ? AS v; r VARCHAR; "
                + "BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "VARCHAR(20)[LOB]",
            "DECLARE x FLOAT DEFAULT 123456789012345678; c CURSOR FOR SELECT SYSTEM$TYPEOF(?) AS v; r VARCHAR; "
                + "BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "1.23456789012346e+17",
            "DECLARE x FLOAT DEFAULT 123456789012345678; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c "
                + "USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "VARCHAR(7)[LOB]",
            "DECLARE x FLOAT DEFAULT 1.5e-7; c CURSOR FOR SELECT SYSTEM$TYPEOF(?) AS v; r VARCHAR; BEGIN OPEN c "
                + "USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "1.5e-07",
            "DECLARE x FLOAT DEFAULT 1.5e-7; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); FETCH "
                + "c INTO r; RETURN r; END;");
        assertAnswer(
            "VARCHAR(6)[LOB]",
            "DECLARE x NUMBER(10,4) DEFAULT 2; c CURSOR FOR SELECT SYSTEM$TYPEOF(?) AS v; r VARCHAR; BEGIN OPEN c "
                + "USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "2.0000",
            "DECLARE x NUMBER(10,4) DEFAULT 2; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); "
                + "FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "5",
            "DECLARE x FLOAT DEFAULT 6.5; c CURSOR FOR SELECT id FROM fz WHERE id < ?; r INT DEFAULT 0; BEGIN "
                + "OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "5",
            "DECLARE x BOOLEAN DEFAULT TRUE; c CURSOR FOR SELECT id FROM fz WHERE b = ?; r INT DEFAULT 0; BEGIN "
                + "OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        answer(
            "DECLARE x DATE DEFAULT '2024-01-15'; c CURSOR FOR SELECT DATEADD(day, 1, ?) AS v; r VARCHAR; BEGIN "
                + "OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "false",
            "DECLARE x DATE DEFAULT '2024-01-15'; c CURSOR FOR SELECT ? = CURRENT_DATE() AS v; r VARCHAR; BEGIN "
                + "OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "13",
            "DECLARE x FLOAT DEFAULT 6.5; c CURSOR FOR SELECT :1 + :1 AS v; r VARCHAR; BEGIN OPEN c USING (x); "
                + "FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "VARCHAR(1)[LOB]",
            "DECLARE x FLOAT DEFAULT 6.5; y INT DEFAULT 2; c CURSOR FOR SELECT SYSTEM$TYPEOF(:2) AS v; r VARCHAR; "
                + "BEGIN OPEN c USING (x, y); FETCH c INTO r; RETURN r; END;");
    }

    @Test
    public void aDynamicTextsUsing() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        assertAnswer(
            "VARCHAR(3)[LOB]",
            "BEGIN LET x FLOAT := 6.5; LET rs RESULTSET := (EXECUTE IMMEDIATE 'SELECT SYSTEM$TYPEOF(?) AS v' "
                + "USING (x)); RETURN TABLE(rs); END;");
        assertAnswer(
            "VARCHAR(1)[LOB]",
            "BEGIN LET x INT := 6; LET rs RESULTSET := (EXECUTE IMMEDIATE 'SELECT SYSTEM$TYPEOF(?) AS v' USING "
                + "(x)); RETURN TABLE(rs); END;");
        assertAnswer(
            "VARCHAR(1)[LOB]",
            "BEGIN LET x VARCHAR := 'a'; LET rs RESULTSET := (EXECUTE IMMEDIATE 'SELECT SYSTEM$TYPEOF(?) AS v' "
                + "USING (x)); RETURN TABLE(rs); END;");
        assertAnswer(
            "VARCHAR(4)[LOB]",
            "BEGIN LET x BOOLEAN := TRUE; LET rs RESULTSET := (EXECUTE IMMEDIATE 'SELECT SYSTEM$TYPEOF(?) AS v' "
                + "USING (x)); RETURN TABLE(rs); END;");
        assertAnswer(
            "VARCHAR(10)[LOB]",
            "BEGIN LET x DATE := '2024-01-15'; LET rs RESULTSET := (EXECUTE IMMEDIATE 'SELECT SYSTEM$TYPEOF(?) AS "
                + "v' USING (x)); RETURN TABLE(rs); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 56 : Numeric value 'inf' "
                + "is not recognized",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; LET rs RESULTSET := (EXECUTE IMMEDIATE 'SELECT ? + 1 AS v' USING "
                + "(x)); RETURN TABLE(rs); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 56 : Numeric value 'inf' "
                + "is not recognized",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; LET rs RESULTSET := (EXECUTE IMMEDIATE 'SELECT id FROM fz WHERE "
                + "id < ?' USING (x)); RETURN TABLE(rs); END;");
        assertAnswer(
            "6.5",
            "BEGIN LET x FLOAT := 6.5; LET rs RESULTSET := (EXECUTE IMMEDIATE 'SELECT ? AS v' USING (x)); RETURN "
                + "TABLE(rs); END;");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "BEGIN LET x FLOAT := 6.5; LET rs RESULTSET := (SELECT SYSTEM$TYPEOF(:x) AS v); RETURN TABLE(rs); END;");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "BEGIN LET x FLOAT := 6.5; LET r VARCHAR := (SELECT SYSTEM$TYPEOF(:x)); RETURN r; END;");
    }

    @Test
    public void whereAStatementWantsMoreThanAText() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE tn (n NUMBER(10,2), d DATE, s VARCHAR)");
        answer(
            "DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT id FROM fz ORDER BY id LIMIT ?; r VARCHAR; BEGIN OPEN c "
                + "USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : SQL compilation "
                + "error: | Invalid row count '?' in limit clause",
            "DECLARE x INT DEFAULT 1; BEGIN LET rs RESULTSET := (EXECUTE IMMEDIATE 'SELECT id FROM fz ORDER BY id "
                + "LIMIT ?' USING (x)); RETURN TABLE(rs); END;");
        answer(
            "DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT TOP ? id FROM fz ORDER BY id; r VARCHAR; BEGIN OPEN c "
                + "USING (x); FETCH c INTO r; RETURN r; END;");
        answer(
            "DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT id FROM fz ORDER BY id LIMIT 2 OFFSET ?; r VARCHAR; "
                + "BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "2",
            "DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT ?::INT + 1 AS v; r VARCHAR; BEGIN OPEN c USING (x); "
                + "FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "[\"1\"]",
            "DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT ARRAY_CONSTRUCT(?) AS v; r VARCHAR; BEGIN OPEN c USING "
                + "(x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT SYSTEM$TYPEOF(? + 0) AS v; r VARCHAR; BEGIN OPEN c "
                + "USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "5",
            "DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT id FROM fz WHERE id IN (?, 5) ORDER BY id; r VARCHAR; "
                + "BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "2024-01-16",
            "DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT DATEADD(day, ?, DATE '2024-01-15') AS v; r VARCHAR; "
                + "BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "5",
            "DECLARE x INT DEFAULT 1; y INT DEFAULT 6; c CURSOR FOR SELECT id FROM fz WHERE id BETWEEN ? AND ?; r "
                + "VARCHAR; BEGIN OPEN c USING (x, y); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "a",
            "DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT REPEAT('a', ?) AS v; r VARCHAR; BEGIN OPEN c USING (x); "
                + "FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "he",
            "DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT SUBSTR('hello', ?, 2) AS v; r VARCHAR; BEGIN OPEN c "
                + "USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "1.2000",
            "DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT ROUND(1.2345, ?) AS v; r VARCHAR; BEGIN OPEN c USING "
                + "(x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "5",
            "DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT id FROM fz ORDER BY ?; r VARCHAR; BEGIN OPEN c USING "
                + "(x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "true",
            "DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT ? = 1 AS v; r VARCHAR; BEGIN OPEN c USING (x); FETCH c "
                + "INTO r; RETURN r; END;");
        assertAnswer(
            "1a",
            "DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT ? || 'a' AS v; r VARCHAR; BEGIN OPEN c USING (x); FETCH "
                + "c INTO r; RETURN r; END;");
        assertAnswer(
            "2",
            "DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT ? * 2 AS v; r VARCHAR; BEGIN OPEN c USING (x); FETCH c "
                + "INTO r; RETURN r; END;");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT SYSTEM$TYPEOF(? * 2) AS v; r VARCHAR; BEGIN OPEN c "
                + "USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 85 : SQL compilation "
                + "error: error line 1 at position 7 | Invalid argument types for function 'IFF': (VARCHAR(1), "
                + "VARCHAR(1), VARCHAR(1))",
            "DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT IFF(?, 't', 'f') AS v; r VARCHAR; BEGIN OPEN c USING "
                + "(x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 92 : SQL compilation "
                + "error: error line 1 at position 7 | Invalid argument types for function 'IFF': (VARCHAR(4), "
                + "VARCHAR(1), VARCHAR(1))",
            "DECLARE x BOOLEAN DEFAULT TRUE; c CURSOR FOR SELECT IFF(?, 't', 'f') AS v; r VARCHAR; BEGIN OPEN c "
                + "USING (x); FETCH c INTO r; RETURN r; END;");
        answer(
            "DECLARE x BOOLEAN DEFAULT TRUE; c CURSOR FOR SELECT id FROM fz WHERE ? ORDER BY id; r VARCHAR; BEGIN "
                + "OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "false",
            "DECLARE x BOOLEAN DEFAULT TRUE; c CURSOR FOR SELECT NOT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); "
                + "FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "2",
            "DECLARE x VARCHAR DEFAULT 'fz'; c CURSOR FOR SELECT COUNT(*) FROM IDENTIFIER(?); r VARCHAR; BEGIN "
                + "OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "5",
            "DECLARE x VARCHAR DEFAULT 'id'; c CURSOR FOR SELECT IDENTIFIER(?) FROM fz ORDER BY 1; r VARCHAR; "
                + "BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 88 : SQL compilation "
                + "error: | Function EXTRACT does not support VARCHAR(10) argument type",
            "DECLARE x DATE DEFAULT '2024-01-15'; c CURSOR FOR SELECT YEAR(?) AS v; r VARCHAR; BEGIN OPEN c USING "
                + "(x); FETCH c INTO r; RETURN r; END;");
        answer(
            "DECLARE x DATE DEFAULT '2024-01-15'; c CURSOR FOR SELECT ? + 1 AS v; r VARCHAR; BEGIN OPEN c USING "
                + "(x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "DATE[SB4]",
            "DECLARE x DATE DEFAULT '2024-01-15'; c CURSOR FOR SELECT SYSTEM$TYPEOF(?::DATE) AS v; r VARCHAR; "
                + "BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 120 : SQL compilation "
                + "error: | Function DATE_TRUNC does not support VARCHAR(29) argument type",
            "DECLARE x TIMESTAMP_NTZ DEFAULT '2024-01-15 10:20:30'; c CURSOR FOR SELECT DATE_TRUNC('hour', ?) AS "
                + "v; r VARCHAR; BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "29",
            "DECLARE x TIMESTAMP_LTZ DEFAULT '2024-01-15 10:20:30'; c CURSOR FOR SELECT LENGTH(?) AS v; r "
                + "VARCHAR; BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        answer(
            "DECLARE x TIMESTAMP_NTZ(3) DEFAULT '2024-01-15 10:20:30.5'; c CURSOR FOR SELECT ? AS v; r VARCHAR; "
                + "BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "10:20:30.500",
            "DECLARE x TIME(3) DEFAULT '10:20:30.5'; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING "
                + "(x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "-1.50",
            "DECLARE x NUMBER(5,2) DEFAULT -1.5; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); "
                + "FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "9.5",
            "DECLARE x FLOAT DEFAULT -6.5; c CURSOR FOR SELECT 3-? AS v; r VARCHAR; BEGIN OPEN c USING (x); FETCH "
                + "c INTO r; RETURN r; END;");
        assertAnswer(
            "1e+15",
            "DECLARE x FLOAT DEFAULT 1e15; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); FETCH c "
                + "INTO r; RETURN r; END;");
        assertAnswer(
            "100000000000000",
            "DECLARE x FLOAT DEFAULT 1e14; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); FETCH c "
                + "INTO r; RETURN r; END;");
        assertAnswer(
            "0.0001",
            "DECLARE x FLOAT DEFAULT 0.0001; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); FETCH "
                + "c INTO r; RETURN r; END;");
        assertAnswer(
            "1e-05",
            "DECLARE x FLOAT DEFAULT 0.00001; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); "
                + "FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "0.333333",
            "DECLARE x FLOAT DEFAULT 1.0/3; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); FETCH "
                + "c INTO r; RETURN r; END;");
        assertAnswer(
            "100",
            "DECLARE x FLOAT DEFAULT 100; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); FETCH c "
                + "INTO r; RETURN r; END;");
        assertAnswer(
            "123456.789",
            "DECLARE x FLOAT DEFAULT 123456.789; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); "
                + "FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "2.5e-05",
            "DECLARE x DOUBLE DEFAULT 2.5e-5; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); "
                + "FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "6162",
            "DECLARE x BINARY DEFAULT TO_BINARY('ab', 'UTF-8'); c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN "
                + "c USING (x); FETCH c INTO r; RETURN r; END;");
        answer(
            "DECLARE x OBJECT DEFAULT OBJECT_CONSTRUCT('a', 1); c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN "
                + "c USING (x); FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "false",
            "DECLARE x BOOLEAN DEFAULT FALSE; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); "
                + "FETCH c INTO r; RETURN r; END;");
        assertAnswer(
            "8",
            "DECLARE x INT DEFAULT -5; c CURSOR FOR SELECT 3-? AS v; r VARCHAR; BEGIN OPEN c USING (x); FETCH c "
                + "INTO r; RETURN r; END;");
        assertAnswer(
            "-5",
            "DECLARE x INT DEFAULT -5; c CURSOR FOR SELECT ? AS v; r VARCHAR; BEGIN OPEN c USING (x); FETCH c "
                + "INTO r; RETURN r; END;");
    }

    @Test
    public void aWriteThroughADynamicText() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE tn (n NUMBER(10,2), d DATE, s VARCHAR)");
        assertAnswer(
            "done",
            "DECLARE x NUMBER(5,2) DEFAULT 1.5; d DATE DEFAULT '2024-01-15'; s VARCHAR DEFAULT 'a'; BEGIN EXECUTE "
                + "IMMEDIATE 'INSERT INTO tn VALUES (?, ?, ?)' USING (x, d, s); RETURN 'done'; END;");
        assertAnswer(
            "1.50|2024-01-15|a",
            "SELECT n, d, s FROM tn");
        assertAnswer(
            "done",
            "DECLARE x FLOAT DEFAULT 2.5; BEGIN EXECUTE IMMEDIATE 'UPDATE tn SET n = n + ? WHERE s = ''a''' USING "
                + "(x); RETURN 'done'; END;");
        assertAnswer(
            "4.00|2024-01-15|a",
            "SELECT n, d, s FROM tn");
        assertAnswer(
            "5 ;; 7",
            "DECLARE x INT DEFAULT 1; BEGIN LET rs RESULTSET := (EXECUTE IMMEDIATE 'SELECT id FROM fz WHERE id > "
                + "? ORDER BY id' USING (x)); RETURN TABLE(rs); END;");
        assertAnswer(
            "7",
            "DECLARE x INT DEFAULT 6; BEGIN LET rs RESULTSET := (EXECUTE IMMEDIATE 'SELECT ? + 1 AS v' USING "
                + "(x)); RETURN TABLE(rs); END;");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "DECLARE x INT DEFAULT 6; BEGIN LET rs RESULTSET := (EXECUTE IMMEDIATE 'SELECT SYSTEM$TYPEOF(? + 1) "
                + "AS v' USING (x)); RETURN TABLE(rs); END;");
        assertAnswer(
            "2",
            "DECLARE x VARCHAR DEFAULT 'fz'; BEGIN LET rs RESULTSET := (EXECUTE IMMEDIATE 'SELECT COUNT(*) AS v "
                + "FROM IDENTIFIER(?)' USING (x)); RETURN TABLE(rs); END;");
        assertAnswer(
            "6|6",
            "DECLARE x INT DEFAULT 6; BEGIN LET rs RESULTSET := (EXECUTE IMMEDIATE 'SELECT :1 AS a, :1 AS b' "
                + "USING (x)); RETURN TABLE(rs); END;");
    }
}
