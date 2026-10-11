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
 * A bind standing where a SAMPLE's size, its seed or a PIVOT's value is written is never bound: a question mark
 * and a block's :name alike are refused while the statement compiles, whatever the variable holds - the size and
 * the seed with the range their parameter has, a PIVOT's value as a bind nothing sets. A size and a seed written
 * outside their range are refused the same way, the seed first. IDENTIFIER() over a variable that holds a text
 * names what the text names; over NULL, or a value that is no text, it names the bind itself as written, and
 * over a number it is an unsupported feature. Every answer is live-verified.
 */
public class NeverBoundSlotsTest extends MeasuredCellsTestSupport {

    @Test
    public void sampleSeedPivotAndIdentifier() {
        engine.execute(
            "CREATE OR REPLACE TABLE tp (k INT, v INT)");
        engine.execute(
            "INSERT INTO tp VALUES (1, 10), (2, 20), (3, 30)");
        try {
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : SQL compilation "
                    + "error:  | parameter value out of range: sample percentage. Must be between 0 and 100.",
                "DECLARE s NUMBER(10,2); res RESULTSET; BEGIN res := (SELECT v AS d1 FROM tp SAMPLE BERNOULLI "
                    + "(:s)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 64 : SQL compilation "
                    + "error:  | parameter value out of range: sample percentage. Must be between 0 and 100.",
                "DECLARE s NUMBER(10,2) DEFAULT 100; res RESULTSET; BEGIN res := (SELECT v AS d2 FROM tp SAMPLE "
                    + "BERNOULLI (:s)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : SQL compilation "
                    + "error:  | parameter value out of range: seed. Must be between 0 and 2,147,483,647.",
                "DECLARE s NUMBER(10,2); res RESULTSET; BEGIN res := (SELECT v AS d3 FROM tp SAMPLE (50) SEED "
                    + "(:s)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                    + "error:  | parameter value out of range: seed. Must be between 0 and 2,147,483,647.",
                "DECLARE s INT DEFAULT 7; res RESULTSET; BEGIN res := (SELECT v AS d4 FROM tp SAMPLE (100) SEED "
                    + "(:s)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 49 : SQL compilation "
                    + "error:  | parameter value out of range: sample percentage. Must be between 0 and 100.",
                "DECLARE s NUMBER(10,2) DEFAULT 100; t INT; BEGIN SELECT COUNT(*) INTO :t FROM tp SAMPLE "
                    + "BERNOULLI (:s); RETURN t; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 51 : SQL compilation "
                    + "error: | Object '\"s\"' does not exist or not authorized. Your primary role <ROLE> must have "
                    + "at least one privilege granted on TABLE \"s\".",
                "DECLARE s VARCHAR(10); res RESULTSET; BEGIN res := (SELECT COUNT(*) AS d8 FROM IDENTIFIER(:s)); "
                    + "RETURN TABLE(res); END;");
            assertAnswer(
                "3",
                "DECLARE s VARCHAR DEFAULT 'tp'; res RESULTSET; BEGIN res := (SELECT COUNT(*) AS d9 FROM "
                    + "IDENTIFIER(:s)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 46 : SQL compilation "
                    + "error: | Object '\"s\"' does not exist or not authorized. Your primary role <ROLE> must have "
                    + "at least one privilege granted on TABLE \"s\".",
                "DECLARE s NUMBER; res RESULTSET; BEGIN res := (SELECT COUNT(*) AS d10 FROM IDENTIFIER(:s)); "
                    + "RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 56 : Unsupported "
                    + "feature 'Bind by Object ID'.",
                "DECLARE s NUMBER DEFAULT 1; res RESULTSET; BEGIN res := (SELECT COUNT(*) AS d11 FROM "
                    + "IDENTIFIER(:s)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 64 : SQL compilation "
                    + "error: | Object 'NOSUCH' does not exist or not authorized. Your primary role <ROLE> must have "
                    + "at least one privilege granted on TABLE NOSUCH.",
                "DECLARE s VARCHAR DEFAULT 'nosuch'; res RESULTSET; BEGIN res := (SELECT COUNT(*) AS d12 FROM "
                    + "IDENTIFIER(:s)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 54 : SQL compilation "
                    + "error:  | parameter value out of range: fixed row size. Must be between 0 and 1,000,000.",
                "DECLARE s INT DEFAULT 50; res RESULTSET; BEGIN res := (SELECT COUNT(*) AS d14 FROM tp SAMPLE (:s "
                    + "ROWS)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 54 : SQL compilation "
                    + "error:  | parameter value out of range: sample percentage. Must be between 0 and 100.",
                "DECLARE s INT DEFAULT 50; res RESULTSET; BEGIN res := (SELECT COUNT(*) AS d15 FROM tp "
                    + "TABLESAMPLE SYSTEM (:s)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED SQL compilation error:  | parameter value out of range: sample percentage. Must be "
                    + "between 0 and 100.",
                "SELECT COUNT(*) FROM tp SAMPLE BERNOULLI (:s)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 41 | Bind variable :s not set.",
                "SELECT * FROM tp PIVOT (SUM(v) FOR k IN (:s, 1))");
            assertAnswer(
                "REFUSED SQL compilation error:  | parameter value out of range: seed. Must be between 0 and 2,"
                    + "147,483,647.",
                "SELECT COUNT(*) FROM tp SAMPLE (50) SEED (:s)");
            assertAnswer(
                "REFUSED SQL compilation error:  | parameter value out of range: sample percentage. Must be "
                    + "between 0 and 100.",
                "SELECT COUNT(*) FROM tp SAMPLE BERNOULLI (?)");
            assertAnswer(
                "REFUSED SQL compilation error:  | parameter value out of range: sample percentage. Must be "
                    + "between 0 and 100.",
                "SELECT COUNT(*) FROM tp SAMPLE (101)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected '-'.",
                "SELECT COUNT(*) FROM tp SAMPLE (50) SEED (-1)");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void rangesAndOtherValues() {
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
                "3",
                "SELECT COUNT(*) FROM tp SAMPLE (100)");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tp SAMPLE (0)");
            assertAnswer(
                "REFUSED SQL compilation error:  | parameter value out of range: sample percentage. Must be "
                    + "between 0 and 100.",
                "SELECT COUNT(*) FROM tp SAMPLE (100.5)");
            assertAnswer(
                "REFUSED SQL compilation error:  | parameter value out of range: size of fixed sample. Must be "
                    + "between 0 and 1,000,000.",
                "SELECT COUNT(*) FROM tp SAMPLE (1000001 ROWS)");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tp SAMPLE (1000000 ROWS)");
            assertAnswer(
                "REFUSED SQL compilation error:  | parameter value out of range: seed. Must be between 0 and 2,"
                    + "147,483,647.",
                "SELECT COUNT(*) FROM tp SAMPLE (100) SEED (2147483648)");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tp SAMPLE (100) SEED (2147483647)");
            assertAnswer(
                "REFUSED SQL compilation error:  | parameter value out of range: sample percentage. Must be "
                    + "between 0 and 100.",
                "SELECT COUNT(*) FROM tp SAMPLE BLOCK (101)");
            assertAnswer(
                "REFUSED SQL compilation error:  | parameter value out of range: sample percentage. Must be "
                    + "between 0 and 100.",
                "SELECT COUNT(*) FROM tp TABLESAMPLE (101)");
            assertAnswer(
                "REFUSED SQL compilation error:  | parameter value out of range: sample percentage. Must be "
                    + "between 0 and 100.",
                "SELECT COUNT(*) FROM tp SAMPLE ROW (101)");
            assertAnswer(
                "REFUSED SQL compilation error:  | parameter value out of range: seed. Must be between 0 and 2,"
                    + "147,483,647.",
                "SELECT COUNT(*) FROM tp SAMPLE (100) REPEATABLE (:s)");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tp SAMPLE (100.0)");
            assertAnswer(
                "REFUSED SQL compilation error:  | parameter value out of range: seed. Must be between 0 and 2,"
                    + "147,483,647.",
                "SELECT COUNT(*) FROM tp SAMPLE (101) SEED (:s)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 44 | Bind variable :s not set.",
                "SELECT * FROM tp PIVOT (SUM(v) FOR k IN (1, :s))");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 41 | Bind variable :a not set.",
                "SELECT * FROM tp PIVOT (SUM(v) FOR k IN (:a, :b))");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 47 : SQL compilation "
                    + "error: | Object '\"s\"' does not exist or not authorized. Your primary role <ROLE> must have "
                    + "at least one privilege granted on TABLE \"s\".",
                "DECLARE s BOOLEAN; res RESULTSET; BEGIN res := (SELECT COUNT(*) AS i1 FROM IDENTIFIER(:s)); "
                    + "RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 44 : SQL compilation "
                    + "error: | Object '\"s\"' does not exist or not authorized. Your primary role <ROLE> must have "
                    + "at least one privilege granted on TABLE \"s\".",
                "DECLARE s DATE; res RESULTSET; BEGIN res := (SELECT COUNT(*) AS i3 FROM IDENTIFIER(:s)); RETURN "
                    + "TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 56 : Unsupported "
                    + "feature 'Bind by Object ID'.",
                "DECLARE s NUMBER DEFAULT 1; res RESULTSET; BEGIN res := (SELECT IDENTIFIER(:s) AS i4 FROM tp); "
                    + "RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 47 : SQL compilation "
                    + "error: | Object 'S' does not exist or not authorized. Your primary role <ROLE> must have at "
                    + "least one privilege granted on TABLE S.",
                "DECLARE S VARCHAR; res RESULTSET; BEGIN res := (SELECT COUNT(*) AS i5 FROM IDENTIFIER(:S)); "
                    + "RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 51 : SQL compilation "
                    + "error: | Object '\"MyVar\"' does not exist or not authorized. Your primary role <ROLE> must "
                    + "have at least one privilege granted on TABLE \"MyVar\".",
                "DECLARE MyVar VARCHAR; res RESULTSET; BEGIN res := (SELECT COUNT(*) AS i6 FROM "
                    + "IDENTIFIER(:MyVar)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : SQL compilation "
                    + "error: | Object '\"s\"' does not exist or not authorized. Your primary role <ROLE> must have "
                    + "at least one privilege granted on TABLE \"s\".",
                "DECLARE s BOOLEAN DEFAULT TRUE; res RESULTSET; BEGIN res := (SELECT COUNT(*) AS i8 FROM "
                    + "IDENTIFIER(:s)); RETURN TABLE(res); END;");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
