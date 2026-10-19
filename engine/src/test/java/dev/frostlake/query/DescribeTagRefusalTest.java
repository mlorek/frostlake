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
 * DESCRIBE TAG is refused whether or not the tag exists, whatever name and property it carries, in a block
 * and through EXECUTE IMMEDIATE: the account describes no tag, and SHOW TAGS reads one back. A property left
 * without its value is the syntax error it is after any DESCRIBE. Every answer is live-verified.
 */
public class DescribeTagRefusalTest extends MeasuredCellsTestSupport {

    @Test
    public void aTagIsNotDescribed() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        engine.execute(
            "CREATE OR REPLACE VIEW vw AS SELECT a FROM t");
        engine.execute(
            "CREATE OR REPLACE TAG tg");
        assertAnswer(
            "REFUSED Unsupported feature 'TAG'.",
            "DESCRIBE TAG tg");
        assertAnswer(
            "REFUSED Unsupported feature 'TAG'.",
            "DESC TAG tg");
        assertAnswer(
            "REFUSED Unsupported feature 'TAG'.",
            "DESCRIBE TAG nosuch_tg");
        assertAnswer(
            "REFUSED Unsupported feature 'TAG'.",
            "DESCRIBE TAG test_schema.tg");
        assertAnswer(
            "REFUSED Unsupported feature 'TAG'.",
            "DESCRIBE TAG nosuch_s.tg");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '<EOF>'.",
            "DESCRIBE TAG");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '<EOF>'.",
            "DESCRIBE TAG tg tg");
        assertAnswer(
            "REFUSED Unsupported feature 'TAG'.",
            "BEGIN DESCRIBE TAG tg; RETURN 1; END;");
        assertAnswer(
            "REFUSED Unsupported feature 'TAG'.",
            "BEGIN DESCRIBE TAG tg; RETURN 1; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' "
                + "|| SQLERRM; END;");
        assertAnswer(
            "REFUSED Unsupported feature 'TAG'.",
            "BEGIN DESCRIBE TAG nosuch_tg; RETURN 1; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE "
                + "|| ' ' || SQLERRM; END;");
        assertAnswer(
            "REFUSED Unsupported feature 'TAG'.",
            "EXECUTE IMMEDIATE 'DESCRIBE TAG tg'");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), owner:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "allowed_values:VARCHAR(16777216), owner_role_type:VARCHAR(16777216), propagate:VARCHAR(16777216), "
                + "on_conflict:VARCHAR(16777216), multi_value:VARCHAR(16777216)",
            "SHOW TAGS LIKE 'TG'");
        assertAnswer(
            "null",
            "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('tg')");
        assertAnswer(
            "REFUSED Unsupported feature 'TAG'.",
            "DESCRIBE TAG tg TYPE = x");
    }

    @Test
    public void aPropertyAfterTheTagChangesNothing() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        engine.execute(
            "CREATE OR REPLACE VIEW vw AS SELECT a FROM t");
        engine.execute(
            "CREATE OR REPLACE TAG tg");
        assertAnswer(
            "REFUSED Unsupported feature 'TAG'.",
            "DESCRIBE TAG tg nosuch = 1");
        assertAnswer(
            "REFUSED Unsupported feature 'TAG'.",
            "DESCRIBE TAG tg TYPE = STAGE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '<EOF>'.",
            "DESCRIBE TAG tg TYPE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '1'.",
            "DESCRIBE TAG tg(1)");
    }
}
