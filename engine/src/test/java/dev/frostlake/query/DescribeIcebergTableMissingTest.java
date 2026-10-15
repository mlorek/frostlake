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
 * DESCRIBE ICEBERG TABLE over a name that reaches nothing: the object is refused as an Iceberg table, by its
 * full path, whatever way the name is written and whatever TYPE follows it (live-verified).
 */
public class DescribeIcebergTableMissingTest extends MeasuredCellsTestSupport {

    @Test
    public void aMissingIcebergTable() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_ICE' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ICE.",
            "DESCRIBE ICEBERG TABLE nosuch_ice");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_ICE' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ICE.",
            "DESC ICEBERG TABLE nosuch_ice");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_ICE' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ICE.",
            "DESCRIBE ICEBERG TABLE test_db.test_schema.nosuch_ice");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_ICE' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ICE.",
            "DESCRIBE ICEBERG TABLE test_schema.nosuch_ice");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.\"nosuch ice\"' does not exist "
                + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.\"nosuch ice\".",
            "DESCRIBE ICEBERG TABLE \"nosuch ice\"");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_ICE' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ICE.",
            "DESCRIBE ICEBERG TABLE nosuch_ice TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_ICE' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ICE.",
            "DESCRIBE ICEBERG TABLE nosuch_ice TYPE = STAGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_ICE' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ICE.",
            "DESCRIBE ICEBERG TABLE IDENTIFIER('nosuch_ice')");
    }

    @Test
    public void otherKindsAndStatements() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_ICE' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ICE.",
            "DROP ICEBERG TABLE nosuch_ice");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_ICE already dropped).",
            "DROP ICEBERG TABLE IF EXISTS nosuch_ice");
        assertAnswer(
            "REFUSED SQL Compilation error: The object T1 is not an Iceberg table. Only Iceberg tables can use "
                + "DESCRIBE ICEBERG TABLE command.",
            "DESCRIBE ICEBERG TABLE t1");
        assertAnswer(
            "REFUSED SQL Compilation error: The object T1 is not an Iceberg table. Only Iceberg tables can use "
                + "DESCRIBE ICEBERG TABLE command.",
            "DESCRIBE ICEBERG TABLE t1 TYPE = STAGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'NOSUCH_ICE' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_ICE.",
            "DESCRIBE TABLE nosuch_ice");
        assertAnswer(
            "REFUSED SQL compilation error: | Event table 'TEST_DB.TEST_SCHEMA.NOSUCH_ICE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ICE.",
            "DESCRIBE EVENT TABLE nosuch_ice");
        assertAnswer(
            "REFUSED SQL compilation error: | Dynamic table 'TEST_DB.TEST_SCHEMA.NOSUCH_ICE' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ICE.",
            "DESCRIBE DYNAMIC TABLE nosuch_ice");
        assertAnswer(
            "REFUSED SQL compilation error: | External table 'TEST_DB.TEST_SCHEMA.NOSUCH_ICE' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ICE.",
            "DESCRIBE EXTERNAL TABLE nosuch_ice");
        assertAnswer(
            "REFUSED SQL compilation error: | Materialized view 'NOSUCH_ICE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_ICE.",
            "DESCRIBE MATERIALIZED VIEW nosuch_ice");
        assertAnswer(
            "REFUSED SQL compilation error: | View 'NOSUCH_ICE' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_ICE.",
            "DESCRIBE VIEW nosuch_ice");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'TABLE'.",
            "SHOW COLUMNS IN ICEBERG TABLE nosuch_ice");
    }
}
