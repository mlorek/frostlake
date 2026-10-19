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
 * SYSTEM$GET_TAG names its tag and its object as identifiers are named: bare in the session's schema, or by a
 * schema or a full path, each argument on its own. An unquoted name folds to upper case and a quoted one is
 * matched exactly, so a quoted lower-case name finds no tag, table or column created unquoted — and the refusal
 * names what is missing as it was written. Every answer is live-verified.
 */
public class SystemGetTagNamesTest extends MeasuredCellsTestSupport {

    @Test
    public void aTagAndItsObjectByAnyPath() {
        engine.execute("CREATE OR REPLACE TAG tg");
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("CREATE SCHEMA IF NOT EXISTS s2");
        engine.execute("USE SCHEMA test_schema");
        engine.execute("CREATE OR REPLACE TAG s2.tg2");
        engine.execute("CREATE OR REPLACE TABLE s2.t2 (a INT)");
        engine.execute("ALTER TABLE t1 SET TAG tg = 'on-table'");
        engine.execute("ALTER TABLE t1 SET TAG s2.tg2 = 'other'");
        engine.execute("ALTER TABLE s2.t2 SET TAG tg = 'far'");
        engine.execute("ALTER TABLE t1 MODIFY COLUMN a SET TAG tg = 'on-col'");
        assertAnswer(
            "on-table",
            "SELECT SYSTEM$GET_TAG('tg', 't1', 'TABLE')");
        assertAnswer(
            "on-table",
            "SELECT SYSTEM$GET_TAG('test_schema.tg', 't1', 'TABLE')");
        assertAnswer(
            "on-table",
            "SELECT SYSTEM$GET_TAG('test_db.test_schema.tg', 't1', 'TABLE')");
        assertAnswer(
            "on-table",
            "SELECT SYSTEM$GET_TAG('tg', 'test_schema.t1', 'TABLE')");
        assertAnswer(
            "on-table",
            "SELECT SYSTEM$GET_TAG('tg', 'test_db.test_schema.t1', 'TABLE')");
        assertAnswer(
            "on-table",
            "SELECT SYSTEM$GET_TAG('test_db.test_schema.tg', 'test_db.test_schema.t1', 'TABLE')");
        assertAnswer(
            "on-table",
            "SELECT SYSTEM$GET_TAG('\"TG\"', '\"T1\"', 'TABLE')");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag '\"tg\"' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TAG \"tg\".",
            "SELECT SYSTEM$GET_TAG('\"tg\"', 't1', 'TABLE')");
        assertAnswer(
            "on-table",
            "SELECT SYSTEM$GET_TAG('TG', 'T1', 'TABLE')");
        assertAnswer(
            "on-table",
            "SELECT SYSTEM$GET_TAG('tg', 't1', 'table')");
        assertAnswer(
            "on-col",
            "SELECT SYSTEM$GET_TAG('tg', 't1.a', 'COLUMN')");
        assertAnswer(
            "on-col",
            "SELECT SYSTEM$GET_TAG('test_db.test_schema.tg', 'test_db.test_schema.t1.a', 'COLUMN')");
        assertAnswer(
            "on-col",
            "SELECT SYSTEM$GET_TAG('test_schema.tg', 'test_schema.t1.a', 'COLUMN')");
        assertAnswer(
            "other",
            "SELECT SYSTEM$GET_TAG('s2.tg2', 't1', 'TABLE')");
        assertAnswer(
            "other",
            "SELECT SYSTEM$GET_TAG('test_db.s2.tg2', 't1', 'TABLE')");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'TG2' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TAG TG2.",
            "SELECT SYSTEM$GET_TAG('tg2', 't1', 'TABLE')");
        assertAnswer(
            "far",
            "SELECT SYSTEM$GET_TAG('tg', 's2.t2', 'TABLE')");
        assertAnswer(
            "far",
            "SELECT SYSTEM$GET_TAG('test_db.test_schema.tg', 'test_db.s2.t2', 'TABLE')");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'T2' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE T2.",
            "SELECT SYSTEM$GET_TAG('tg', 't2', 'TABLE')");
        assertAnswer(
            "null",
            "SELECT SYSTEM$GET_TAG('s2.tg2', 's2.t2', 'TABLE')");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOPE' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TAG NOPE.",
            "SELECT SYSTEM$GET_TAG('nope', 't1', 'TABLE')");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'TEST_DB.TEST_SCHEMA.NOPE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TAG "
                + "TEST_DB.TEST_SCHEMA.NOPE.",
            "SELECT SYSTEM$GET_TAG('test_db.test_schema.nope', 't1', 'TABLE')");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'NOPE' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOPE.",
            "SELECT SYSTEM$GET_TAG('tg', 'nope', 'TABLE')");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOS' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOS.",
            "SELECT SYSTEM$GET_TAG('nos.tg', 't1', 'TABLE')");
        assertAnswer(
            "REFUSED Unknown domain: NOPE.",
            "SELECT SYSTEM$GET_TAG('tg', 't1', 'NOPE')");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value VIEW for argument OBJECT_TYPE. Please use object "
                + "type TABLE for all kinds of table-like objects.",
            "SELECT SYSTEM$GET_TAG('tg', 't1', 'VIEW')");
        assertAnswer(
            "null",
            "SELECT SYSTEM$GET_TAG('tg', 'test_db.test_schema', 'SCHEMA')");
    }

    @Test
    public void bareNamesAreTheSessionsSchemas() {
        engine.execute("CREATE OR REPLACE TAG tg");
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("CREATE SCHEMA IF NOT EXISTS s2");
        engine.execute("USE SCHEMA test_schema");
        engine.execute("CREATE OR REPLACE TAG s2.tg2");
        engine.execute("CREATE OR REPLACE TABLE s2.t2 (a INT)");
        engine.execute("ALTER TABLE t1 SET TAG tg = 'on-table'");
        engine.execute("ALTER TABLE t1 SET TAG s2.tg2 = 'other'");
        engine.execute("ALTER TABLE s2.t2 SET TAG tg = 'far'");
        engine.execute("ALTER TABLE t1 MODIFY COLUMN a SET TAG tg = 'on-col'");
        engine.execute("USE SCHEMA s2");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'TG' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TAG TG.",
            "SELECT SYSTEM$GET_TAG('tg', 't1', 'TABLE')");
        assertAnswer(
            "on-table",
            "SELECT SYSTEM$GET_TAG('test_schema.tg', 'test_schema.t1', 'TABLE')");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'T1' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE T1.",
            "SELECT SYSTEM$GET_TAG('test_schema.tg', 't1', 'TABLE')");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'TG' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TAG TG.",
            "SELECT SYSTEM$GET_TAG('tg', 'test_schema.t1', 'TABLE')");
        assertAnswer(
            "far",
            "SELECT SYSTEM$GET_TAG('test_schema.tg', 't2', 'TABLE')");
        assertAnswer(
            "other",
            "SELECT SYSTEM$GET_TAG('tg2', 'test_schema.t1', 'TABLE')");
    }

    @Test
    public void aQuotedNameIsMatchedExactly() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE h (id INT, v INT)");
        engine.execute("INSERT INTO h VALUES (5, 1), (7, 2)");
        engine.execute("CREATE OR REPLACE TAG tg ALLOWED_VALUES 'a', 'b'");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag '\"tg\"' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TAG \"tg\".",
            "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('\"tg\"')");
        assertAnswer(
            "[\"a\",\"b\"]",
            "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('tg')");
        assertAnswer(
            "REFUSED SQL compilation error: | Table '\"fz\"' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE \"fz\".",
            "SELECT SYSTEM$GET_TAG('tg', '\"fz\"', 'TABLE')");
        assertAnswer(
            "REFUSED SQL compilation error: | Column 'TEST_DB.TEST_SCHEMA.FZ.\"id\"' does not exist or not "
                + "authorized.",
            "SELECT SYSTEM$GET_TAG('tg', 'fz.\"id\"', 'COLUMN')");
    }
}
