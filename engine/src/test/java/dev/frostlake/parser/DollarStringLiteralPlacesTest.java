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
package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A string written between dollar signs is a string literal wherever one may stand: a comment, a tag's values, a
 * file format's options, a typed literal. Where a quoted string is refused the dollar string is, in its own
 * spelling (live-verified).
 */
public class DollarStringLiteralPlacesTest extends MeasuredCellsTestSupport {

    @Test
    public void wherePropertiesAndLiteralsTakeOne() {
        try {
            answer(
                "CREATE OR REPLACE TABLE cb (a INT)");
            answer(
                "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
            answer(
                "INSERT INTO FZ VALUES (5, TRUE), (7, FALSE)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER TABLE cb SET COMMENT = $$z$$");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER TABLE cb ALTER COLUMN a COMMENT $$z$$");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER TABLE cb MODIFY COLUMN a COMMENT $$y$$");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER TABLE cb ADD COLUMN c2 INT COMMENT $$cc$$");
            assertAnswer(
                "y ;; cc",
                "SELECT comment FROM information_schema.columns WHERE table_name = 'CB' ORDER BY ordinal_position");
            assertAnswer(
                "Table CQ2 successfully created.",
                "CREATE OR REPLACE TABLE cq2 (a INT) COMMENT = $$c$$");
            assertAnswer(
                "View VQ successfully created.",
                "CREATE OR REPLACE VIEW vq AS SELECT 1 a");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER VIEW vq SET COMMENT = $$c$$");
            assertAnswer(
                "View VQ2 successfully created.",
                "CREATE OR REPLACE VIEW vq2 COMMENT = $$c$$ AS SELECT 1 a");
            assertAnswer(
                "Sequence SQ successfully created.",
                "CREATE OR REPLACE SEQUENCE sq COMMENT = $$c$$");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SEQUENCE sq SET COMMENT = $$d$$");
            assertAnswer(
                "Tag TQ successfully created.",
                "CREATE OR REPLACE TAG tq COMMENT = $$c$$");
            assertAnswer(
                "Tag TQ2 successfully created.",
                "CREATE OR REPLACE TAG tq2 ALLOWED_VALUES $$a$$, $$b$$");
            assertAnswer(
                "Stage area STQ successfully created.",
                "CREATE OR REPLACE STAGE stq COMMENT = $$c$$");
            assertAnswer(
                "File format FFQ successfully created.",
                "CREATE OR REPLACE FILE FORMAT ffq TYPE = CSV FIELD_DELIMITER = $$|$$");
            assertAnswer(
                "Function FQ successfully created.",
                "CREATE OR REPLACE FUNCTION fq() RETURNS INT COMMENT = $$c$$ AS '1'");
            assertAnswer(
                "Function PQ successfully created.",
                "CREATE OR REPLACE PROCEDURE pq() RETURNS INT LANGUAGE SQL COMMENT = $$c$$ AS 'BEGIN RETURN 1; "
                    + "END'");
            assertAnswer(
                "Table CQ3 successfully created.",
                "CREATE OR REPLACE TABLE cq3 (a INT DEFAULT 1 COMMENT $$c$$, b VARCHAR DEFAULT $$x$$)");
            assertAnswer(
                "2020-01-01",
                "SELECT DATE $$2020-01-01$$");
            assertAnswer(
                "2020-01-01T00:00",
                "SELECT TIMESTAMP $$2020-01-01 00:00:00$$");
            assertAnswer(
                "2020-01-02",
                "SELECT DATE '2020-01-01' + INTERVAL $$1 day$$");
            assertAnswer(
                "a",
                "SELECT 'a' COLLATE $$en$$");
            assertAnswer(
                "true",
                "SELECT 'abc' ILIKE $$A%$$");
            assertAnswer(
                "true",
                "SELECT 'abc' LIKE ANY ($$a%$$, $$b%$$)");
            assertAnswer(
                "true",
                "SELECT 'abc' RLIKE $$a.*$$");
            assertAnswer(
                "true",
                "SELECT 'a_c' LIKE $$a!_c$$ ESCAPE $$!$$");
            assertAnswer(
                "Statement executed successfully.",
                "SET dq = $$x$$");
            assertAnswer(
                "x",
                "SELECT $dq");
            assertAnswer(
                "a",
                "SELECT $1 FROM VALUES ($$a$$)");
            assertAnswer(
                "2020-01-01",
                "SELECT TO_DATE($$2020-01-01$$, $$YYYY-MM-DD$$)");
            assertAnswer(
                "Table CQ5 successfully created.",
                "CREATE OR REPLACE TABLE cq5 AS SELECT $$x$$ AS a");
            assertAnswer(
                "Table CQ6 successfully created.",
                "CREATE OR REPLACE TABLE cq6 (a INT) STAGE_FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = $$|$$)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER STAGE stq SET COMMENT = $$d$$");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER FILE FORMAT ffq SET FIELD_DELIMITER = $$;$$");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER FUNCTION fq() SET COMMENT = $$d$$");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER PROCEDURE pq() SET COMMENT = $$d$$");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER TAG tq SET COMMENT = $$d$$");
            answer(
                "ALTER TABLE cb SET COMMENT = $x$z$x$");
            assertAnswer(
                "1",
                "INSERT INTO cq3 (b) VALUES ($$q$$)");
            assertAnswer(
                "Table CQ7 successfully created.",
                "CREATE OR REPLACE TABLE cq7 (a VARCHAR(10) COLLATE $$en$$)");
            assertAnswer(
                "null",
                "SELECT TRY_TO_DATE($$x$$)");
        } finally {
            answer(
                "UNSET dq");
        }
    }

    @Test
    public void commentsTagsRoutinesAndRefusals() {
        answer(
            "CREATE OR REPLACE TABLE cb (a INT)");
        answer(
            "CREATE OR REPLACE TAG tq");
        answer(
            "CREATE OR REPLACE FILE FORMAT ffq TYPE = CSV");
        answer(
            "CREATE OR REPLACE FUNCTION fq() RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE PROCEDURE pq() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        answer(
            "CREATE OR REPLACE TABLE cq2 (a INT)");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE cb SET TAG tq = $$v$$");
        assertAnswer(
            "v",
            "SELECT SYSTEM$GET_TAG('tq', 'cb', 'TABLE')");
        assertAnswer(
            "Statement executed successfully.",
            "COMMENT ON COLUMN cb.a IS $$k$$");
        assertAnswer(
            "Statement executed successfully.",
            "COMMENT ON TABLE cb IS $$t$$");
        assertAnswer(
            "t",
            "SELECT comment FROM information_schema.tables WHERE table_name = 'CB'");
        assertAnswer(
            "k",
            "SELECT comment FROM information_schema.columns WHERE table_name = 'CB'");
        assertAnswer(
            "2",
            "INSERT INTO cb VALUES (1), (3)");
        assertAnswer(
            "1",
            "SELECT * FROM cb WHERE a::VARCHAR IN ($$1$$, $$2$$)");
        assertAnswer(
            "1",
            "SELECT REGEXP_COUNT(GET_DDL('TABLE', $$cq2$$), 'CQ2')");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER FILE FORMAT ffq SET FIELD_DELIMITER = ';'");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER FILE FORMAT ffq SET FIELD_DELIMITER = $$;$$");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER FILE FORMAT ffq SET COMMENT = 'c'");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER FUNCTION fq() SET COMMENT = 'plain'");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER FUNCTION fq() SET COMMENT = $$dollar$$");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER PROCEDURE pq() SET COMMENT = $$dollar$$");
        assertAnswer(
            "dollar",
            "SELECT comment FROM information_schema.functions WHERE function_name = 'FQ'");
        assertAnswer(
            "dollar",
            "SELECT comment FROM information_schema.procedures WHERE procedure_name = 'PQ'");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE cb SET COMMENT = $$its$$");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE cb SET COMMENT = $$a'b$$");
        assertAnswer(
            "a'b",
            "SELECT comment FROM information_schema.tables WHERE table_name = 'CB'");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE cb SET COMMENT = $$a\\b$$");
        assertAnswer(
            "a\\b",
            "SELECT comment FROM information_schema.tables WHERE table_name = 'CB'");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE cb SET COMMENT = $$$$");
        assertAnswer(
            "",
            "SELECT comment FROM information_schema.tables WHERE table_name = 'CB'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '<EOF>'.",
            "ALTER TABLE cb SET COMMENT = $$z$$ x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '$$z$$'.",
            "ALTER TABLE cb SET COMMENT $$z$$");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '$$x$$'.",
            "SELECT 1 $$x$$");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '$$x$$'.",
            "SELECT 1 AS $$x$$");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid URL prefix found in: 'cb'",
            "SELECT * FROM $$cb$$");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '$$cz$$'.",
            "CREATE OR REPLACE TABLE $$cz$$ (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '$$b$$'.",
            "SELECT $$a$$ $$b$$");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'.",
            "SELECT DATE $$2020-01-01$$ x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'RENAME'.",
            "ALTER TABLE cb RENAME COLUMN a TO $$b$$");
        assertAnswer(
            "Table CQ8 successfully created.",
            "CREATE OR REPLACE TABLE cq8 (a INT COMMENT $$one$$, b INT COMMENT $$two$$) COMMENT = $$three$$");
        assertAnswer(
            "one ;; two",
            "SELECT comment FROM information_schema.columns WHERE table_name = 'CQ8' ORDER BY ordinal_position");
        assertAnswer(
            "10:00",
            "SELECT TIME $$10:00:00$$");
        answer(
            "SELECT TIMESTAMP_NTZ $$2020-01-01 00:00:00$$ = TIMESTAMP $$2020-01-01 00:00:00$$");
        assertAnswer(
            "1",
            "SELECT * FROM cb WHERE a::VARCHAR LIKE $$1%$$ ESCAPE $$!$$");
        assertAnswer(
            "2",
            "SELECT ARRAY_SIZE(SPLIT($$a,b$$, $$,$$))");
    }
}
