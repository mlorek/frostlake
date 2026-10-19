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
 * SYSTEM$GET_TAG is answered while the statement compiles. Its domain is judged first and as written - an
 * unknown one, a blank about one, a tag as the object - then its tag and its object, each a text of no more
 * parts than its kind has; a number is no string and a column no constant. Its value is a text of no width.
 * Every answer is live-verified.
 */
public class SystemGetTagArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void theTagAnObjectCarries() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        engine.execute(
            "INSERT INTO t VALUES (1)");
        engine.execute(
            "CREATE OR REPLACE VIEW vw AS SELECT a FROM t");
        engine.execute(
            "CREATE OR REPLACE TAG tg");
        engine.execute(
            "CREATE OR REPLACE TAG tg2");
        engine.execute(
            "ALTER TABLE t SET TAG tg2 = 'v1'");
        engine.execute(
            "ALTER TABLE t MODIFY COLUMN a SET TAG tg2 = 'c1'");
        engine.execute(
            "CREATE OR REPLACE TABLE ts (a VARCHAR)");
        engine.execute(
            "INSERT INTO ts VALUES ('x y'), ('X-Y z.w'), ('hello world'), (NULL)");
        engine.execute(
            "CREATE OR REPLACE TABLE te (a VARCHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.",
            "SELECT SYSTEM$GET_TAG('nosuch_tag', 't', 'table')");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'TEST_DB.TEST_SCHEMA.NOSUCH_TAG' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TAG "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_TAG.",
            "SELECT SYSTEM$GET_TAG('test_schema.nosuch_tag', 't', 'table')");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.",
            "SELECT SYSTEM$GET_TAG('nosuch_tag', 't.a', 'column')");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "SELECT SYSTEM$GET_TAG('tg', 'nosuch_t', 'table')");
        assertAnswer(
            "null",
            "SELECT SYSTEM$GET_TAG('tg', 't', 'table')");
        assertShape(
            "SYSTEM$GET_TAG('TG', 'T', 'TABLE'):VARCHAR(134217728)",
            "SELECT SYSTEM$GET_TAG('tg', 't', 'table')");
        assertAnswer(
            "v1",
            "SELECT SYSTEM$GET_TAG('tg2', 't', 'table')");
        assertShape(
            "SYSTEM$GET_TAG('TG2', 'T', 'TABLE'):VARCHAR(134217728)",
            "SELECT SYSTEM$GET_TAG('tg2', 't', 'table')");
        assertAnswer(
            "c1",
            "SELECT SYSTEM$GET_TAG('tg2', 't.a', 'column')");
        assertAnswer(
            "null",
            "SELECT SYSTEM$GET_TAG('tg', 't.a', 'column')");
        assertAnswer(
            "REFUSED SQL compilation error: | Column 'TEST_DB.TEST_SCHEMA.T.NOSUCH' does not exist or not "
                + "authorized.",
            "SELECT SYSTEM$GET_TAG('tg', 't.nosuch', 'column')");
        assertAnswer(
            "REFUSED SQL compilation error: | Column 'TEST_DB.TEST_SCHEMA.T' does not exist or not authorized.",
            "SELECT SYSTEM$GET_TAG('tg', 't', 'column')");
        assertAnswer(
            "REFUSED Unknown domain: nosuch_domain.",
            "SELECT SYSTEM$GET_TAG('tg', 't', 'nosuch_domain')");
        assertAnswer(
            "v1",
            "SELECT SYSTEM$GET_TAG('tg2', 't', 'TABLE')");
        assertAnswer(
            "v1",
            "SELECT SYSTEM$GET_TAG('TG2', 'T', 'table')");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag '\"tg2\"' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TAG \"tg2\".",
            "SELECT SYSTEM$GET_TAG('\"tg2\"', 't', 'table')");
        assertAnswer(
            "v1",
            "SELECT SYSTEM$GET_TAG('tg2', 'test_schema.t', 'table')");
        assertAnswer(
            "v1",
            "SELECT SYSTEM$GET_TAG('test_db.test_schema.tg2', 'test_db.test_schema.t', 'table')");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA.",
            "SELECT SYSTEM$GET_TAG('tg', 'nosuch_schema.t', 'table')");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA.",
            "SELECT SYSTEM$GET_TAG('nosuch_schema.tg', 't', 'table')");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB' does not exist or not authorized. Your primary "
                + "role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB.",
            "SELECT SYSTEM$GET_TAG('nosuch_db.s.tg', 't', 'table')");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB' does not exist or not authorized. Your primary "
                + "role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB.",
            "SELECT SYSTEM$GET_TAG('tg', 'nosuch_db.s.t', 'table')");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function SYSTEM$GET_TAG needs to be constant, found "
                + "'null'",
            "SELECT SYSTEM$GET_TAG(NULL, 't', 'table')");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$GET_TAG needs to be constant, found "
                + "'null'",
            "SELECT SYSTEM$GET_TAG('tg', NULL, 'table')");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function SYSTEM$GET_TAG needs to be constant, found "
                + "'null'",
            "SELECT SYSTEM$GET_TAG('tg', 't', NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[SYSTEM$GET_TAG('tg', 't')], expected 3, got 2",
            "SELECT SYSTEM$GET_TAG('tg', 't')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[SYSTEM$GET_TAG('tg')], expected 3, got 1",
            "SELECT SYSTEM$GET_TAG('tg')");
        assertAnswer(
            "null",
            "SELECT SYSTEM$GET_TAG('tg', 'test_schema', 'schema')");
        assertAnswer(
            "null",
            "SELECT SYSTEM$GET_TAG('tg', 'test_db', 'database')");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB' does not exist or not authorized. Your primary "
                + "role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB.",
            "SELECT SYSTEM$GET_TAG('tg', 'nosuch_db', 'database')");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SELECT SYSTEM$GET_TAG('tg', 'nosuch_s', 'schema')");
        assertAnswer(
            "null",
            "SELECT SYSTEM$GET_TAG('tg', 'vw', 'table')");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value VIEW for argument OBJECT_TYPE. Please use object type "
                + "TABLE for all kinds of table-like objects.",
            "SELECT SYSTEM$GET_TAG('tg', 'vw', 'view')");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.",
            "SELECT SYSTEM$GET_TAG('nosuch_tag', 'nosuch_t', 'table')");
        assertAnswer(
            "REFUSED SQL compilation error: | Column 'TEST_DB.TEST_SCHEMA.NOSUCH_T.A' does not exist or not "
                + "authorized.",
            "SELECT SYSTEM$GET_TAG('tg', 'nosuch_t.a', 'column')");
        assertAnswer(
            "REFUSED Unknown domain: nosuch_domain.",
            "SELECT SYSTEM$GET_TAG('nosuch_tag', 't', 'nosuch_domain')");
        assertAnswer(
            "REFUSED SQL compilation error: Object tagging not supported for object type TAG.",
            "SELECT SYSTEM$GET_TAG('tg', 'tg2', 'tag')");
        assertAnswer(
            "REFUSED Unknown domain: TABLE .",
            "SELECT SYSTEM$GET_TAG('tg', 't', 'TABLE ')");
        assertAnswer(
            "REFUSED SQL compilation error: | Table '\"\"' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE \"\".",
            "SELECT SYSTEM$GET_TAG('tg', '', 'table')");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag '\"\"' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TAG \"\".",
            "SELECT SYSTEM$GET_TAG('', 't', 'table')");
        assertAnswer(
            "REFUSED Unknown domain: .",
            "SELECT SYSTEM$GET_TAG('tg', 't', '')");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT SYSTEM$GET_TAG('a.b.c.d', 't', 'table')");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT SYSTEM$GET_TAG('tg', 'a.b.c.d', 'table')");
        assertAnswer(
            "REFUSED SQL compilation error: | argument needs to be a string: 'SYSTEM$GET_TAG'",
            "SELECT SYSTEM$GET_TAG(1, 't', 'table')");
        assertAnswer(
            "REFUSED SQL compilation error: | argument needs to be a string: 'SYSTEM$GET_TAG'",
            "SELECT SYSTEM$GET_TAG('tg', 1, 'table')");
        assertAnswer(
            "REFUSED SQL compilation error: | argument needs to be a string: 'SYSTEM$GET_TAG'",
            "SELECT SYSTEM$GET_TAG('tg', 't', 1)");
        assertAnswer(
            "v1",
            "SELECT SYSTEM$GET_TAG('tg2', 't', 'table') FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.",
            "SELECT SYSTEM$GET_TAG('nosuch_tag', 't', 'table') FROM t WHERE FALSE");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "SELECT SYSTEM$GET_TAG('tg', 'nosuch_t', 'table') FROM te");
        assertAnswer(
            "v1",
            "BEGIN RETURN SYSTEM$GET_TAG('tg2', 't', 'table'); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [A] for function 'SYSTEM$GET_TAG', parameter 0: "
                + "constant arguments expected",
            "SELECT SYSTEM$GET_TAG(a, 't', 'table') FROM ts");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [A] for function 'SYSTEM$GET_TAG', parameter 1: "
                + "constant arguments expected",
            "SELECT SYSTEM$GET_TAG('tg2', a, 'table') FROM ts");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [A] for function 'SYSTEM$GET_TAG', parameter 2: "
                + "constant arguments expected",
            "SELECT SYSTEM$GET_TAG('tg2', 't', a) FROM ts");
        assertAnswer(
            "v1",
            "SELECT SYSTEM$GET_TAG('tg' || '2', 't', 'table')");
        assertAnswer(
            "v1",
            "SELECT SYSTEM$GET_TAG('tg2', 't', LOWER('TABLE'))");
        assertAnswer(
            "VARCHAR[LOB]",
            "SELECT SYSTEM$TYPEOF(SYSTEM$GET_TAG('tg2', 't', 'table'))");
    }
}
