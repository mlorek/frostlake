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
package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * CREATE EVENT TABLE … CLONE copies another event table and nothing else, an event table and a table of one
 * name refuse each other, and INFORMATION_SCHEMA tells an event table by its own kind (live-verified).
 */
public class EventTableCloneTest extends MeasuredCellsTestSupport {

    @Test
    public void aNameAnotherKindHolds() {
        answer(
            "CREATE OR REPLACE TABLE base_t (a INT)");
        answer(
            "INSERT INTO base_t VALUES (1)");
        answer(
            "CREATE OR REPLACE VIEW base_v AS SELECT a FROM base_t");
        answer(
            "CREATE OR REPLACE MATERIALIZED VIEW base_mv AS SELECT a FROM base_t");
        answer(
            "CREATE OR REPLACE STREAM base_s ON TABLE base_t");
        answer(
            "CREATE OR REPLACE DYNAMIC TABLE base_dt TARGET_LAG = '1 day' WAREHOUSE = COMPUTE_WH AS SELECT a FROM "
                + "base_t");
        answer(
            "CREATE OR REPLACE EVENT TABLE base_et");
        answer(
            "CREATE OR REPLACE TABLE plain_t (a INT)");
        answer(
            "CREATE OR REPLACE TABLE plain_u (a INT)");
        answer(
            "CREATE OR REPLACE TABLE plain_w (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'PLAIN_T' already exists as TABLE",
            "CREATE OR REPLACE EVENT TABLE plain_t");
        assertAnswer(
            "BASE TABLE",
            "SELECT table_type FROM information_schema.tables WHERE table_name = 'PLAIN_T'");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'PLAIN_U' already exists as TABLE",
            "CREATE EVENT TABLE plain_u");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'PLAIN_U' already exists as TABLE",
            "CREATE EVENT TABLE IF NOT EXISTS plain_u");
        assertAnswer(
            "BASE TABLE",
            "SELECT table_type FROM information_schema.tables WHERE table_name = 'PLAIN_U'");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'BASE_ET' already exists as EVENT_TABLE",
            "CREATE OR REPLACE TABLE base_et (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'BASE_ET' already exists as EVENT_TABLE",
            "CREATE TABLE base_et (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'BASE_ET' already exists as EVENT_TABLE",
            "CREATE TABLE IF NOT EXISTS base_et (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'BASE_ET' already exists as EVENT_TABLE",
            "CREATE OR REPLACE VIEW base_et AS SELECT 1 x");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'BASE_V' already exists as VIEW",
            "CREATE OR REPLACE EVENT TABLE base_v");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'BASE_MV' already exists as MATERIALIZED_VIEW",
            "CREATE OR REPLACE EVENT TABLE base_mv");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'BASE_S' already exists as STREAM",
            "CREATE OR REPLACE EVENT TABLE base_s");
        assertAnswer(
            "Table LK successfully created.",
            "CREATE TABLE lk LIKE base_et");
        assertAnswer(
            "BASE TABLE",
            "SELECT table_type FROM information_schema.tables WHERE table_name = 'LK'");
        assertShape(
            "TIMESTAMP:TIMESTAMP_NTZ, START_TIMESTAMP:TIMESTAMP_NTZ, OBSERVED_TIMESTAMP:TIMESTAMP_NTZ, "
                + "TRACE:OBJECT, RESOURCE:OBJECT, RESOURCE_ATTRIBUTES:OBJECT, SCOPE:OBJECT, SCOPE_ATTRIBUTES:OBJECT, "
                + "RECORD_TYPE:VARCHAR(16777216), RECORD:OBJECT, RECORD_ATTRIBUTES:OBJECT, VALUE:VARIANT, "
                + "EXEMPLARS:ARRAY",
            "SELECT * FROM lk");
        assertAnswer(
            "1",
            "INSERT INTO lk (value) VALUES (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'LIKE'.",
            "CREATE EVENT TABLE lk3 LIKE base_et");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid operation Cannot clone from an event table.",
            "CREATE TABLE lk4 CLONE base_et");
        assertAnswer(
            "",
            "SELECT table_name, table_type FROM information_schema.tables WHERE table_name IN ('LK3', 'LK4') "
                + "ORDER BY 1");
        assertAnswer(
            "Table LK5 successfully created.",
            "CREATE EVENT TABLE lk5 CLONE base_et");
        assertAnswer(
            "EVENT TABLE",
            "SELECT table_type FROM information_schema.tables WHERE table_name = 'LK5'");
        assertAnswer(
            "Table LK6 successfully created.",
            "CREATE TABLE lk6 LIKE plain_w");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'LIKE'.",
            "CREATE EVENT TABLE lk7 LIKE plain_w");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'BASE_DT' already exists as DYNAMIC_TABLE",
            "CREATE OR REPLACE TABLE base_dt (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'PLAIN_W' already exists as TABLE",
            "CREATE OR REPLACE DYNAMIC TABLE plain_w TARGET_LAG = '1 day' WAREHOUSE = COMPUTE_WH AS SELECT a FROM "
                + "base_t");
    }

    @Test
    public void aCloneOfAnEventTable() {
        answer(
            "CREATE OR REPLACE TABLE base_t (a INT)");
        answer(
            "INSERT INTO base_t VALUES (1)");
        answer(
            "CREATE OR REPLACE VIEW base_v AS SELECT a FROM base_t");
        answer(
            "CREATE OR REPLACE STREAM base_s ON TABLE base_t");
        answer(
            "CREATE OR REPLACE DYNAMIC TABLE base_dt TARGET_LAG = '1 day' WAREHOUSE = COMPUTE_WH AS SELECT a FROM "
                + "base_t");
        answer(
            "CREATE OR REPLACE EVENT TABLE base_et");
        answer(
            "CREATE OR REPLACE TABLE plain_w (a INT)");
        assertAnswer(
            "Table C1 successfully created.",
            "CREATE EVENT TABLE c1 CLONE base_et");
        assertAnswer(
            "EVENT TABLE",
            "SELECT table_type FROM information_schema.tables WHERE table_name = 'C1'");
        assertShape(
            "TIMESTAMP:TIMESTAMP_NTZ, START_TIMESTAMP:TIMESTAMP_NTZ, OBSERVED_TIMESTAMP:TIMESTAMP_NTZ, "
                + "TRACE:OBJECT, RESOURCE:OBJECT, RESOURCE_ATTRIBUTES:OBJECT, SCOPE:OBJECT, SCOPE_ATTRIBUTES:OBJECT, "
                + "RECORD_TYPE:VARCHAR(16777216), RECORD:OBJECT, RECORD_ATTRIBUTES:OBJECT, VALUE:VARIANT, "
                + "EXEMPLARS:ARRAY",
            "SELECT * FROM c1");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'C1' already exists.",
            "CREATE EVENT TABLE c1 CLONE base_et");
        assertAnswer(
            "C1 already exists, statement succeeded.",
            "CREATE EVENT TABLE IF NOT EXISTS c1 CLONE base_et");
        assertAnswer(
            "Table C1 successfully created.",
            "CREATE OR REPLACE EVENT TABLE c1 CLONE base_et");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid operation Cannot clone an Event table from a table.",
            "CREATE EVENT TABLE c2 CLONE plain_w");
        assertAnswer(
            "",
            "SELECT table_type FROM information_schema.tables WHERE table_name = 'C2'");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'NOSUCH' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE NOSUCH.",
            "CREATE EVENT TABLE c3 CLONE nosuch");
        assertAnswer(
            "REFUSED SQL compilation error: Cannot clone from a view object.",
            "CREATE EVENT TABLE c4 CLONE base_v");
        answer(
            "CREATE EVENT TABLE c5 CLONE base_et COMMENT = 'x'");
        assertAnswer(
            "",
            "SELECT comment FROM information_schema.tables WHERE table_name = 'C5'");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid operation Cannot clone an Event table from a dynamic table.",
            "CREATE EVENT TABLE c6 CLONE base_dt");
        assertAnswer(
            "Table C7 successfully created.",
            "CREATE EVENT TABLE c7 CLONE test_db.test_schema.base_et");
        assertAnswer(
            "EVENT TABLE",
            "SELECT table_type FROM information_schema.tables WHERE table_name = 'C7'");
        assertAnswer(
            "REFUSED SQL compilation error: A clone operation cannot be performed on a EVENT_TABLE with a STREAM "
                + "source. Please refer to documentation for details.",
            "CREATE EVENT TABLE c8 CLONE base_s");
        answer(
            "CREATE EVENT TABLE c9 CLONE base_et CLONE base_et");
        answer(
            "CREATE EVENT TABLE c10 CLONE");
    }

    @Test
    public void whichRefusalComesFirst() {
        answer(
            "CREATE OR REPLACE TABLE base_t (a INT)");
        answer(
            "CREATE OR REPLACE EVENT TABLE base_et");
        answer(
            "CREATE OR REPLACE TABLE plain_w (a INT)");
        answer(
            "CREATE OR REPLACE EVENT TABLE c1");
        assertAnswer(
            "EVENT TABLE",
            "SELECT table_type FROM information_schema.tables WHERE table_name = 'BASE_ET'");
        assertAnswer(
            "BASE TABLE|1 ;; EVENT TABLE|2",
            "SELECT table_type, COUNT(*) FROM information_schema.tables WHERE table_name IN ('BASE_ET', 'PLAIN_W',"
                + " 'C1') GROUP BY 1 ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'C1' already exists.",
            "CREATE EVENT TABLE c1 CLONE nosuch");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'NOSUCH' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE NOSUCH.",
            "CREATE EVENT TABLE IF NOT EXISTS c1 CLONE nosuch");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid operation Cannot clone an Event table from a table.",
            "CREATE OR REPLACE EVENT TABLE c1 CLONE plain_w");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'C1' already exists.",
            "CREATE EVENT TABLE c1 CLONE plain_w");
        assertAnswer(
            "C1 already exists, statement succeeded.",
            "CREATE EVENT TABLE IF NOT EXISTS c1 CLONE plain_w");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'PLAIN_W' already exists as TABLE",
            "CREATE EVENT TABLE plain_w CLONE base_et");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'PLAIN_W' already exists as TABLE",
            "CREATE OR REPLACE EVENT TABLE plain_w CLONE base_et");
        assertAnswer(
            "Statement executed successfully.",
            "COMMENT ON TABLE base_et IS 'the source'");
        assertAnswer(
            "Table C2 successfully created.",
            "CREATE EVENT TABLE c2 CLONE base_et");
        assertAnswer(
            "the source",
            "SELECT comment FROM information_schema.tables WHERE table_name = 'C2'");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'C2' already exists.",
            "CREATE OR REPLACE EVENT TABLE c2 CLONE c2");
        assertAnswer(
            "EVENT TABLE",
            "SELECT table_type FROM information_schema.tables WHERE table_name = 'C2'");
    }
}
