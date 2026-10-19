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
 * ALTER EXTERNAL TABLE over a table that is not there. The action is judged first - a property an external
 * table has not, a value AUTO_REFRESH does not take - then the name: a missing schema is refused with or
 * without IF EXISTS, a table or a view of that name is an invalid object type, and a missing table is refused
 * as an external table by its full path, which IF EXISTS forgives. Every answer is live-verified.
 */
public class ExternalTableAlterTest extends MeasuredCellsTestSupport {

    @Test
    public void anExternalTableThatIsNotThere() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | External table 'TEST_DB.TEST_SCHEMA.NOSUCH_EXT' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_EXT.",
            "ALTER EXTERNAL TABLE nosuch_ext REFRESH");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext REFRESH");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "ALTER EXTERNAL TABLE nosuch_s.nosuch_ext REFRESH");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "ALTER EXTERNAL TABLE nosuch_db_w7.s.nosuch_ext REFRESH");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid object type: 'TABLE'",
            "ALTER EXTERNAL TABLE t REFRESH");
        assertAnswer(
            "REFUSED SQL compilation error: | External table 'TEST_DB.TEST_SCHEMA.NOSUCH_EXT' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_EXT.",
            "ALTER EXTERNAL TABLE nosuch_ext REFRESH 'p'");
        assertAnswer(
            "REFUSED SQL compilation error: | External table 'TEST_DB.TEST_SCHEMA.NOSUCH_EXT' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_EXT.",
            "ALTER EXTERNAL TABLE nosuch_ext SET AUTO_REFRESH = TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: | External table 'TEST_DB.TEST_SCHEMA.NOSUCH_EXT' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_EXT.",
            "ALTER EXTERNAL TABLE nosuch_ext ADD FILES ('a')");
        assertAnswer(
            "REFUSED SQL compilation error: | External table 'TEST_DB.TEST_SCHEMA.NOSUCH_EXT' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_EXT.",
            "ALTER EXTERNAL TABLE nosuch_ext REMOVE FILES ('a')");
        assertAnswer(
            "REFUSED SQL compilation error: | External table 'TEST_DB.TEST_SCHEMA.NOSUCH_EXT' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_EXT.",
            "ALTER EXTERNAL TABLE nosuch_ext RENAME TO x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '<EOF>'.",
            "ALTER EXTERNAL TABLE nosuch_ext");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '<EOF>'.",
            "ALTER EXTERNAL TABLE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'nosuch_word'.",
            "ALTER EXTERNAL TABLE nosuch_ext nosuch_word");
    }

    @Test
    public void theActionIsJudgedBeforeTheName() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        engine.execute(
            "CREATE OR REPLACE VIEW vw AS SELECT a FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid object type: 'VIEW'",
            "ALTER EXTERNAL TABLE vw REFRESH");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'nosuch_word'.",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext nosuch_word");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [1] for parameter 'AUTO_REFRESH'",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext SET AUTO_REFRESH = 1");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext SET AUTO_REFRESH = TRUE");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext ADD PARTITION(a = 'b') LOCATION 'x'");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext DROP PARTITION LOCATION 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'COMMENT' for 'EXTERNAL_TABLE'",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext SET COMMENT = 'x'");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext ADD FILES ('a', 'b')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected ')'.",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext ADD FILES ()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'x'.",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext REFRESH x");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext RENAME TO x");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_s.nosuch_ext REFRESH");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid object type: 'TABLE'",
            "ALTER EXTERNAL TABLE IF EXISTS t REFRESH");
    }
}
