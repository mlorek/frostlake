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
 * A LIMIT, an OFFSET or a FETCH count written with a fraction keeps its whole part, and so does an exact number
 * a block's variable binds there, whatever its scale; a variable that holds NULL limits nothing. A negative
 * number, a text, a FLOAT and a BOOLEAN bound there are refused while the statement compiles, with the bind as
 * written - an invalid row count in the limit clause, or in the result offset clause for an OFFSET - and so is
 * a count written with more digits than 64 bits hold. The count binds the same way in a RESULTSET's query, a
 * block's own SELECT ... INTO, inside a construct and under a subquery. Every answer is live-verified.
 */
public class RowCountBindTest extends MeasuredCellsTestSupport {

    @Test
    public void aBindWhereARowCountStands() {
        engine.execute(
            "CREATE OR REPLACE TABLE tp (k INT, v INT)");
        engine.execute(
            "INSERT INTO tp VALUES (1, 10), (2, 20), (3, 30)");
        try {
            assertAnswer(
                "7",
                "DECLARE s NUMBER(10,2) DEFAULT 1; res RESULTSET; BEGIN res := (SELECT 7 AS c1 LIMIT :s); RETURN "
                    + "TABLE(res); END;");
            assertAnswer(
                "10",
                "DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT v AS c2 FROM tp ORDER BY v LIMIT "
                    + ":s); RETURN TABLE(res); END;");
            assertAnswer(
                "10",
                "DECLARE s NUMBER(10,2) DEFAULT 1.5; res RESULTSET; BEGIN res := (SELECT v AS c3 FROM tp ORDER BY "
                    + "v LIMIT :s); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 63 : SQL compilation "
                    + "error: | Invalid row count ':s' in limit clause",
                "DECLARE s NUMBER(10,2) DEFAULT -1; res RESULTSET; BEGIN res := (SELECT v AS c4 FROM tp ORDER BY "
                    + "v LIMIT :s); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 59 : SQL compilation "
                    + "error: | Invalid row count ':s' in limit clause",
                "DECLARE s VARCHAR DEFAULT '1'; res RESULTSET; BEGIN res := (SELECT v AS c5 FROM tp ORDER BY v "
                    + "LIMIT :s); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 57 : SQL compilation "
                    + "error: | Invalid row count ':s' in limit clause",
                "DECLARE s FLOAT DEFAULT 2.0; res RESULTSET; BEGIN res := (SELECT v AS c7 FROM tp ORDER BY v "
                    + "LIMIT :s); RETURN TABLE(res); END;");
            assertAnswer(
                "",
                "DECLARE s INT DEFAULT 0; res RESULTSET; BEGIN res := (SELECT v AS c8 FROM tp ORDER BY v LIMIT "
                    + ":s); RETURN TABLE(res); END;");
            assertAnswer(
                "20",
                "DECLARE s NUMBER(10,2) DEFAULT 1; res RESULTSET; BEGIN res := (SELECT v AS c9 FROM tp ORDER BY v "
                    + "LIMIT 1 OFFSET :s); RETURN TABLE(res); END;");
            assertAnswer(
                "30",
                "DECLARE s NUMBER(10,2) DEFAULT 2; res RESULTSET; BEGIN res := (SELECT v AS c10 FROM tp ORDER BY "
                    + "v LIMIT :s OFFSET :s); RETURN TABLE(res); END;");
            assertAnswer(
                "20",
                "DECLARE s NUMBER(10,2) DEFAULT 1; res RESULTSET; BEGIN res := (SELECT v AS c13 FROM tp ORDER BY "
                    + "v OFFSET :s ROWS FETCH NEXT :s ROWS ONLY); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : SQL compilation "
                    + "error: | Invalid row count ':s' in limit clause",
                "DECLARE s BOOLEAN DEFAULT TRUE; res RESULTSET; BEGIN res := (SELECT v AS c15 FROM tp ORDER BY v "
                    + "LIMIT :s); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 59 : SQL compilation "
                    + "error: | Invalid row count ':s' in limit clause",
                "DECLARE s VARCHAR DEFAULT 'x'; res RESULTSET; BEGIN res := (SELECT v AS c18 FROM tp ORDER BY v "
                    + "LIMIT :s); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 57 : SQL compilation "
                    + "error: | Invalid row count ':s' in limit clause",
                "DECLARE s FLOAT DEFAULT 1.5; res RESULTSET; BEGIN res := (SELECT v AS c19 FROM tp ORDER BY v "
                    + "LIMIT :s); RETURN TABLE(res); END;");
            assertAnswer(
                "2",
                "DECLARE s NUMBER(10,2) DEFAULT 2; t INT; BEGIN SELECT COUNT(*) INTO :t FROM (SELECT v FROM tp "
                    + "LIMIT :s); RETURN t; END;");
            assertAnswer(
                "2",
                "DECLARE s NUMBER(10,2) DEFAULT 2; t INT; BEGIN IF (TRUE) THEN SELECT COUNT(*) INTO :t FROM "
                    + "(SELECT v FROM tp LIMIT :s); END IF; RETURN t; END;");
            assertAnswer(
                "10",
                "SELECT v FROM tp ORDER BY v LIMIT 1.5");
            assertAnswer(
                "10 ;; 20",
                "SELECT v FROM tp ORDER BY v LIMIT 2.0");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ''1''.",
                "SELECT v FROM tp ORDER BY v LIMIT '1'");
            assertAnswer(
                "10 ;; 20 ;; 30",
                "SELECT v FROM tp ORDER BY v LIMIT NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '-'.",
                "SELECT v FROM tp ORDER BY v LIMIT -1");
            assertAnswer(
                "10",
                "SELECT v FROM tp ORDER BY v LIMIT 1 OFFSET NULL");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void fractionsOffsetsAndConstructs() {
        engine.execute(
            "CREATE OR REPLACE TABLE tp (k INT, v INT)");
        engine.execute(
            "INSERT INTO tp VALUES (1, 10), (2, 20), (3, 30)");
        engine.execute(
            "CREATE OR REPLACE TABLE tb (b BOOLEAN, n NUMBER(10,2), i INT)");
        engine.execute(
            "INSERT INTO tb VALUES (TRUE, 1.5, 0)");
        engine.execute(
            "CREATE OR REPLACE TABLE tv (n NUMBER(10,2))");
        try {
            assertAnswer(
                "20",
                "SELECT v FROM tp ORDER BY v LIMIT 1.9 OFFSET 1.2");
            assertAnswer(
                "10",
                "SELECT v FROM tp ORDER BY v FETCH FIRST 1.5 ROWS ONLY");
            assertAnswer(
                "20",
                "SELECT v FROM tp ORDER BY v OFFSET 1.5 ROWS FETCH NEXT 1 ROWS ONLY");
            assertAnswer(
                "10",
                "SELECT v FROM tp ORDER BY v LIMIT 1e0");
            assertAnswer(
                "",
                "SELECT v FROM tp ORDER BY v LIMIT .5");
            assertAnswer(
                "",
                "SELECT v FROM tp ORDER BY v LIMIT 0.9");
            assertAnswer(
                "10 ;; 20",
                "SELECT v FROM tp ORDER BY v LIMIT 2.");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM (SELECT v FROM tp LIMIT 2.0)");
            assertAnswer(
                "10 ;; 20 ;; 30",
                "SELECT v FROM tp ORDER BY v LIMIT 3000000000");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid row count '99999999999999999999' in limit clause",
                "SELECT v FROM tp ORDER BY v LIMIT 99999999999999999999");
            assertAnswer(
                "20",
                "SELECT v FROM tp ORDER BY v LIMIT 1 OFFSET 1.9");
            assertAnswer(
                "10",
                "SELECT v FROM tp ORDER BY v LIMIT 1.5e0");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM (SELECT v FROM tp LIMIT 1.9 OFFSET 0.9)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 63 : SQL compilation "
                    + "error: | Invalid row count ':s' in result offset clause",
                "DECLARE s NUMBER(10,2) DEFAULT -1; res RESULTSET; BEGIN res := (SELECT v AS h1 FROM tp ORDER BY "
                    + "v LIMIT 1 OFFSET :s); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 59 : SQL compilation "
                    + "error: | Invalid row count ':s' in limit clause",
                "DECLARE s VARCHAR DEFAULT '1'; res RESULTSET; BEGIN res := (SELECT v AS h2 FROM tp ORDER BY v "
                    + "FETCH FIRST :s ROWS ONLY); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 55 : SQL compilation "
                    + "error: | Invalid row count ':s' in result offset clause",
                "DECLARE s FLOAT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT v AS h3 FROM tp ORDER BY v LIMIT "
                    + "2 OFFSET :s); RETURN TABLE(res); END;");
            assertAnswer(
                "20",
                "DECLARE s NUMBER(10,2) DEFAULT 1.9; res RESULTSET; BEGIN res := (SELECT v AS h4 FROM tp ORDER BY "
                    + "v LIMIT 1 OFFSET :s); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 63 : SQL compilation "
                    + "error: | Invalid row count ':s' in limit clause",
                "DECLARE s NUMBER(10,2) DEFAULT -1; res RESULTSET; BEGIN res := (SELECT v AS h5 FROM tp ORDER BY "
                    + "v FETCH FIRST :s ROWS ONLY); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : SQL compilation "
                    + "error: | Invalid row count ':s' in result offset clause",
                "DECLARE s BOOLEAN DEFAULT TRUE; res RESULTSET; BEGIN res := (SELECT v AS h6 FROM tp ORDER BY v "
                    + "LIMIT 1 OFFSET :s); RETURN TABLE(res); END;");
            assertAnswer(
                "10",
                "DECLARE s NUMBER(10,2); res RESULTSET; BEGIN res := (SELECT v AS h7 FROM tp ORDER BY v LIMIT 1 "
                    + "OFFSET :s); RETURN TABLE(res); END;");
            assertAnswer(
                "10",
                "DECLARE s NUMBER(10,2) DEFAULT 1.5; res RESULTSET; BEGIN IF (TRUE) THEN res := (SELECT v AS h9 "
                    + "FROM tp ORDER BY v LIMIT :s); END IF; RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 74 : SQL compilation "
                    + "error: | Invalid row count ':s' in limit clause",
                "DECLARE s VARCHAR DEFAULT '1'; res RESULTSET; BEGIN IF (TRUE) THEN res := (SELECT v AS h10 FROM "
                    + "tp ORDER BY v LIMIT :s); END IF; RETURN TABLE(res); END;");
            assertAnswer(
                "2",
                "DECLARE s NUMBER(10,2) DEFAULT 2; t INT; BEGIN SELECT COUNT(*) INTO :t FROM (SELECT v FROM tp "
                    + "LIMIT :s); RETURN t; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 40 : SQL compilation "
                    + "error: | Invalid row count ':s' in limit clause",
                "DECLARE s FLOAT DEFAULT 2; t INT; BEGIN SELECT COUNT(*) INTO :t FROM (SELECT v FROM tp LIMIT "
                    + ":s); RETURN t; END;");
            assertAnswer(
                "1",
                "DECLARE s NUMBER(10,2) DEFAULT 2; t INT DEFAULT 0; BEGIN FOR i IN 1 TO 2 DO SELECT COUNT(*) INTO "
                    + ":t FROM (SELECT v FROM tp LIMIT :s OFFSET :i); END FOR; RETURN t; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 34 | Bind variable :s not set.",
                "SELECT v FROM tp ORDER BY v LIMIT :s");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 43 | Bind variable :s not set.",
                "SELECT v FROM tp ORDER BY v LIMIT 1 OFFSET :s");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
