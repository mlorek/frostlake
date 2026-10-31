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
 * EXPLAIN of an INSERT, an UPDATE, a DELETE or a MERGE compiles the whole statement before it plans it, and is
 * refused as the statement is: an unreachable merge case, a column or a relation that is not there, a value list
 * or a source query of another width than its columns, a column set or listed twice, each at the statement's own
 * place (all live-verified).
 */
public class ExplainDmlCompileTest extends MeasuredCellsTestSupport {

    @Test
    public void dmlRefusalsAreThePlansToo() {
        engine.execute("CREATE OR REPLACE TABLE et (a INT, b VARCHAR)");
        engine.execute("CREATE OR REPLACE TABLE eu (a INT, c VARCHAR)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 90 | Unreachable merge case.",
            "EXPLAIN MERGE INTO et t USING eu s ON t.a = s.a WHEN MATCHED THEN UPDATE SET b = s.c WHEN MATCHED THEN "
                + "DELETE");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 78 | Unreachable merge case.",
            "EXPLAIN MERGE INTO et t USING eu s ON t.a = s.a WHEN MATCHED THEN DELETE WHEN MATCHED THEN UPDATE SET b "
                + "= s.c");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 99 | Unreachable merge case.",
            "EXPLAIN MERGE INTO et t USING eu s ON t.a = s.a WHEN NOT MATCHED THEN INSERT (a) VALUES (s.a) WHEN NOT "
                + "MATCHED THEN INSERT (a) VALUES (s.a)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 127 | Unreachable merge case.",
            "EXPLAIN MERGE INTO et t USING eu s ON t.a = s.a WHEN MATCHED AND s.a > 1 THEN DELETE WHEN MATCHED THEN "
                + "UPDATE SET b = s.c WHEN MATCHED THEN DELETE");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 90 | Unreachable merge case.",
            "EXPLAIN MERGE INTO et t USING eu s ON t.a = s.a WHEN MATCHED THEN UPDATE SET b = s.c WHEN MATCHED AND "
                + "s.a > 1 THEN DELETE");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 22 | invalid identifier 'NOSUCH'",
            "EXPLAIN UPDATE et SET nosuch = 1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 26 | invalid identifier 'NOSUCH'",
            "EXPLAIN UPDATE et SET b = nosuch");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 29 | invalid identifier 'NOSUCH'",
            "EXPLAIN DELETE FROM et WHERE nosuch = 1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 24 | invalid identifier 'NOSUCH'",
            "EXPLAIN INSERT INTO et (nosuch) VALUES (1)");
        assertAnswer("REFUSED SQL compilation error: | Insert value list does not match column list expecting 2 but "
                + "got 3",
            "EXPLAIN INSERT INTO et VALUES (1, 'x', 3)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 30 | invalid identifier 'NOSUCH'",
            "EXPLAIN INSERT INTO et SELECT nosuch FROM eu");
        assertAnswer("REFUSED SQL compilation error: | Insert value list does not match column list expecting 2 but "
                + "got 1",
            "EXPLAIN INSERT INTO et SELECT a FROM eu");
        assertAnswer("REFUSED SQL compilation error: | Object 'NOSUCH_SRC' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_SRC.",
            "EXPLAIN MERGE INTO et t USING nosuch_src s ON t.a = s.a WHEN MATCHED THEN DELETE");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 44 | invalid identifier 'S.NOSUCH'",
            "EXPLAIN MERGE INTO et t USING eu s ON t.a = s.nosuch WHEN MATCHED THEN DELETE");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 77 | invalid identifier 'NOSUCH'",
            "EXPLAIN MERGE INTO et t USING eu s ON t.a = s.a WHEN MATCHED THEN UPDATE SET nosuch = 1");
        assertAnswer("REFUSED SQL compilation error: | Object 'NOSUCH_U' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_U.",
            "EXPLAIN DELETE FROM et USING nosuch_u WHERE et.a = nosuch_u.a");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'EU.NOSUCH'",
            "EXPLAIN UPDATE et SET b = eu.c FROM eu WHERE et.a = eu.nosuch");
        assertAnswer("REFUSED SQL compilation error: | Object 'NOSUCH_T' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "EXPLAIN UPDATE nosuch_t SET a = 1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 42 | invalid identifier 'NOSUCH'",
            "EXPLAIN DELETE FROM et WHERE a IN (SELECT nosuch FROM eu)");
        assertAnswer("REFUSED SQL compilation error: | duplicate column name 'A'",
            "EXPLAIN INSERT INTO et (a, a) VALUES (1, 2)");
        assertAnswer("REFUSED SQL compilation error: | duplicate column name 'A'",
            "EXPLAIN UPDATE et SET a = 1, a = 2");
    }
}
