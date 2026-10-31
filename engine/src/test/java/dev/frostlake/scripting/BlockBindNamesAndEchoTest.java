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
 * A block's bind as the account reads it (live-verified): a RESULTSET's unaliased column is named by the query as
 * the block wrote it, {@code :S} and {@code UPPER(:S)}; an argument-count refusal echoes each bind as written, a
 * variable holding NULL included; a LIMIT bound to NULL through EXECUTE IMMEDIATE's USING limits nothing; and the
 * types a bind takes under the functions, the operators and the conversions measured beside them.
 */
public class BlockBindNamesAndEchoTest extends MeasuredCellsTestSupport {

    /** Names, echoes, row counts and types over binds. */
    @Test
    public void theBindsOfABlock() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION fo(x VARCHAR) RETURNS VARCHAR AS 'x'",
                "Function FO successfully created."},
            {"EXECUTE IMMEDIATE $$ DECLARE s VARCHAR(10); res RESULTSET; BEGIN res := (SELECT fo(:s, 1) AS q30); "
                + "RETURN TABLE(res); END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : SQL compilation "
                + "error: error line 1 at position 8 | too many arguments for function [FO(:s, 1)] expected 1, got 2"},
            {"EXECUTE IMMEDIATE $$ DECLARE v INT DEFAULT NULL; r RESULTSET; BEGIN r := (EXECUTE IMMEDIATE 'SELECT "
                + "SYSTEM$TYPEOF(?) AS t' USING (v)); RETURN TABLE(r); END; $$",
                "VARCHAR[LOB]"},
            {"EXECUTE IMMEDIATE $$ DECLARE v INT DEFAULT 42; r RESULTSET; BEGIN r := (EXECUTE IMMEDIATE 'SELECT "
                + "SYSTEM$TYPEOF(?) AS t' USING (v)); RETURN TABLE(r); END; $$",
                "VARCHAR(2)[LOB]"},
            {"EXECUTE IMMEDIATE $$ DECLARE v BOOLEAN DEFAULT TRUE; r RESULTSET; BEGIN r := (EXECUTE IMMEDIATE 'SELECT "
                + "SYSTEM$TYPEOF(?) AS t' USING (v)); RETURN TABLE(r); END; $$",
                "VARCHAR(4)[LOB]"},
            {"EXECUTE IMMEDIATE $$ DECLARE v INT DEFAULT 1; r RESULTSET; BEGIN r := (EXECUTE IMMEDIATE 'SELECT 1 AS a "
                + "LIMIT ?' USING (v)); RETURN TABLE(r); END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 51 : SQL compilation "
                + "error: | Invalid row count '?' in limit clause"},
            {"EXECUTE IMMEDIATE $$ DECLARE v INT DEFAULT NULL; r RESULTSET; BEGIN r := (EXECUTE IMMEDIATE 'SELECT 1 "
                + "AS a LIMIT ?' USING (v)); RETURN TABLE(r); END; $$",
                "1"},
            {"EXECUTE IMMEDIATE $$ DECLARE s VARCHAR(10); res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(c) AS t "
                + "FROM (SELECT :s AS c UNION ALL SELECT 1.5)); RETURN TABLE(res); END; $$",
                "NUMBER(2,1)[SB1] ;; NUMBER(2,1)[SB1]"},
            {"EXECUTE IMMEDIATE $$ DECLARE s VARCHAR(10); res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(CASE WHEN "
                + "TRUE THEN :s ELSE 1 END) AS t); RETURN TABLE(res); END; $$",
                "NUMBER(1,0)[SB1]"},
            {"EXECUTE IMMEDIATE $$ DECLARE s VARCHAR(10); res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(DECODE(1, "
                + "1, :s, 2)) AS t); RETURN TABLE(res); END; $$",
                "NUMBER(1,0)[SB1]"},
            {"EXECUTE IMMEDIATE $$ DECLARE s VARCHAR(10); res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(:s + 1) "
                + "AS t); RETURN TABLE(res); END; $$",
                "NUMBER(19,0)[SB1]"},
            {"EXECUTE IMMEDIATE $$ DECLARE s DATE; d DATE DEFAULT '2024-01-02'; t VARCHAR; BEGIN SELECT "
                + "SYSTEM$TYPEOF(MONTHS_BETWEEN(:s, :d)) INTO :t; RETURN t; END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 63 : SQL compilation "
                + "error: | Function EXTRACT does not support NULL argument type"},
            {"CREATE OR REPLACE FUNCTION fd(d DATE) RETURNS DATE AS 'd'",
                "Function FD successfully created."},
            {"EXECUTE IMMEDIATE $$ DECLARE s DATE; BEGIN IF (fd(:s) IS NULL) THEN RETURN 'null'; END IF; RETURN "
                + "'not'; END; $$",
                "null"},
            {"EXECUTE IMMEDIATE $$ DECLARE s VARCHAR(10); res RESULTSET; BEGIN res := (SELECT TO_NUMBER('x' || :s) AS "
                + "q); RETURN TABLE(res); END; $$",
                "null"},
            {"EXECUTE IMMEDIATE $$ DECLARE s VARCHAR(10); res RESULTSET; BEGIN res := (SELECT nosuchfn(:s) AS q); "
                + "RETURN TABLE(res); END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : SQL compilation "
                + "error: | Unknown function NOSUCHFN."},
        });
    }

    /** Widths, row counts, names and echoes over binds of other declarations. */
    @Test
    public void moreBindsOfABlock() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE s VARCHAR; res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(:s) AS c); "
                + "RETURN TABLE(res); END; $$",
                "VARCHAR(134217728)[LOB]"},
            {"EXECUTE IMMEDIATE $$ DECLARE s STRING; res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(:s) AS c); "
                + "RETURN TABLE(res); END; $$",
                "VARCHAR(134217728)[LOB]"},
            {"EXECUTE IMMEDIATE $$ DECLARE s VARCHAR(134217728) DEFAULT 'ab'; res RESULTSET; BEGIN res := (SELECT "
                + "SYSTEM$TYPEOF(:s) AS c); RETURN TABLE(res); END; $$",
                "VARCHAR(2)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(DATEADD(day, 1, NULL))",
                "NULL[LOB]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(NULL))",
                "NULL[LOB]"},
            {"SELECT SYSTEM$TYPEOF(WEEKISO(NULL))",
                "NULL[LOB]"},
            {"SELECT SYSTEM$TYPEOF('2024-01-02'::DATE - NULL)",
                "REFUSED SQL compilation error: error line 1 at position 40 | Invalid argument types for function "
                + "'-': (DATE, NULL)"},
            {"SELECT SYSTEM$TYPEOF(NULL + 1)",
                "NUMBER(19,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(UPPER(NULL))",
                "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(ABS(NULL))",
                "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(DATEADD(day, 1, NULL::DATE))",
                "DATE[SB4]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(NULL::DATE))",
                "NUMBER(4,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(NULL::DATE + 1)",
                "DATE[SB4]"},
            {"EXECUTE IMMEDIATE $$ DECLARE s DATE DEFAULT '2024-01-02'; t VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(YEAR(:s)"
                + ") INTO :t; RETURN t; END; $$",
                "NUMBER(4,0)[SB2]"},
            {"EXECUTE IMMEDIATE $$ DECLARE s DATE; t VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(:s) INTO :t; RETURN t; END; "
                + "$$",
                "DATE[SB4]"},
            {"EXECUTE IMMEDIATE $$ DECLARE s NUMBER(10,2); t VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(ABS(:s)) INTO :t; "
                + "RETURN t; END; $$",
                "NUMBER(10,2)[SB1]"},
            {"SELECT 1 AS a LIMIT NULL",
                "1"},
            {"EXECUTE IMMEDIATE $$ DECLARE v INT DEFAULT NULL; r RESULTSET; BEGIN r := (SELECT 1 AS a LIMIT :v); "
                + "RETURN TABLE(r); END; $$",
                "1"},
            {"CREATE OR REPLACE FUNCTION fo(x VARCHAR) RETURNS VARCHAR AS 'x'",
                "Function FO successfully created."},
            {"EXECUTE IMMEDIATE $$ DECLARE s NUMBER(10,2); res RESULTSET; BEGIN res := (SELECT fo(:s, 1) AS q); "
                + "RETURN TABLE(res); END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 8 | too many arguments for function [FO(:s, 1)] expected 1, got 2"},
            {"EXECUTE IMMEDIATE $$ DECLARE s VARCHAR(10) DEFAULT 'ab'; res RESULTSET; BEGIN res := (SELECT fo(:s, 1) "
                + "AS q); RETURN TABLE(res); END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 65 : SQL compilation "
                + "error: error line 1 at position 8 | too many arguments for function [FO(:s, 1)] expected 1, got 2"},
            {"EXECUTE IMMEDIATE $$ DECLARE s VARCHAR(10); res RESULTSET; BEGIN res := (SELECT fo(UPPER(:s), :s) AS "
                + "q); RETURN TABLE(res); END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : SQL compilation "
                + "error: error line 1 at position 8 | too many arguments for function [FO(UPPER(:s), :s)] expected "
                + "1, got 2"},
        });
    }
}
