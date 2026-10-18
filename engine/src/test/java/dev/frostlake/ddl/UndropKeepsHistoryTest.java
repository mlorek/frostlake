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
 * An UNDROP brings a table back with its time-travel history (live-verified): a point before the drop reads the rows
 * the table held then, as an AT, a BEFORE, a CHANGES clause and a CLONE read it, and so does a point while it was
 * dropped. That holds for a table undropped under a name another table took meanwhile, for one dropped and undropped
 * twice, and for the tables of an undropped schema; a point before the table was created is still refused.
 */
public class UndropKeepsHistoryTest extends MeasuredCellsTestSupport {

    private static final String NOT_AVAILABLE = "REFUSED Time travel data is not available for table %s. The"
        + " requested time is either beyond the allowed time travel period or before the object creation time.";

    /** Keeps the statements around a point apart, so each falls in a moment of its own on either side. */
    private static void pause() {
        try {
            Thread.sleep(isLiveSnowflake() ? 2500L : 40L);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The clock now, into a session variable, kept apart from the statements before and after it. */
    private void point(final String name) {
        pause();
        engine.execute("SET " + name + " = DATE_PART(EPOCH_MILLISECOND, CURRENT_TIMESTAMP())");
        pause();
    }

    private static String at(final String kind, final String name) {
        return kind + "(TIMESTAMP => TO_TIMESTAMP_LTZ($" + name + ", 3))";
    }

    @Test
    public void anUndroppedTableKeepsItsHistory() {
        point("p0");
        engine.execute("CREATE OR REPLACE TABLE ud_t (a INT) CHANGE_TRACKING = TRUE");
        pause();
        engine.execute("INSERT INTO ud_t VALUES (1)");
        point("p1");
        engine.execute("INSERT INTO ud_t VALUES (2)");
        engine.execute("SET q2 = LAST_QUERY_ID()");
        point("p2");
        engine.execute("DROP TABLE ud_t");
        point("p3");
        engine.execute("UNDROP TABLE ud_t");
        point("p4");
        engine.execute("INSERT INTO ud_t VALUES (3)");
        assertCells(new String[][] {
            {"SELECT a FROM ud_t ORDER BY 1", "1 ;; 2 ;; 3"},
            {"SELECT a FROM ud_t " + at("AT", "p1") + " ORDER BY 1", "1"},
            {"SELECT a FROM ud_t " + at("AT", "p2") + " ORDER BY 1", "1 ;; 2"},
            {"SELECT a FROM ud_t " + at("AT", "p3") + " ORDER BY 1", "1 ;; 2"},
            {"SELECT a FROM ud_t " + at("AT", "p4") + " ORDER BY 1", "1 ;; 2"},
            {"SELECT a FROM ud_t " + at("BEFORE", "p2") + " ORDER BY 1", "1 ;; 2"},
            {"SELECT a FROM ud_t BEFORE(STATEMENT => $q2) ORDER BY 1", "1"},
            {"SELECT a FROM ud_t " + at("AT", "p0") + " ORDER BY 1", String.format(NOT_AVAILABLE, "UD_T")},
            {"SELECT a, METADATA$ACTION, METADATA$ISUPDATE FROM ud_t CHANGES(INFORMATION => DEFAULT) "
                + at("AT", "p1") + " ORDER BY 1", "2|INSERT|false ;; 3|INSERT|false"},
            {"SELECT a, METADATA$ACTION FROM ud_t CHANGES(INFORMATION => APPEND_ONLY) " + at("AT", "p1")
                + " ORDER BY 1", "2|INSERT ;; 3|INSERT"},
            {"SELECT a, METADATA$ACTION FROM ud_t CHANGES(INFORMATION => DEFAULT) " + at("AT", "p1") + " "
                + at("END", "p2") + " ORDER BY 1", "2|INSERT"},
            {"SELECT a, METADATA$ACTION FROM ud_t CHANGES(INFORMATION => DEFAULT) " + at("AT", "p3")
                + " ORDER BY 1", "3|INSERT"},
            {"CREATE TABLE ud_k CLONE ud_t " + at("AT", "p1"), "Table UD_K successfully created."},
            {"SELECT a FROM ud_k ORDER BY 1", "1"},
            {"CREATE TABLE ud_k2 CLONE ud_t " + at("AT", "p3"), "Table UD_K2 successfully created."},
            {"SELECT a FROM ud_k2 ORDER BY 1", "1 ;; 2"},
            {"CREATE TABLE ud_k3 CLONE ud_t " + at("AT", "p0"), String.format(NOT_AVAILABLE, "UD_T")},
        });
    }

    @Test
    public void aTableUndroppedUnderATakenNameKeepsItsOwnHistory() {
        engine.execute("CREATE OR REPLACE TABLE ud_r (a INT)");
        pause();
        engine.execute("INSERT INTO ud_r VALUES (1)");
        point("r1");
        engine.execute("DROP TABLE ud_r");
        pause();
        engine.execute("CREATE TABLE ud_r (a INT)");
        engine.execute("INSERT INTO ud_r VALUES (9)");
        point("r2");
        engine.execute("ALTER TABLE ud_r RENAME TO ud_r2");
        pause();
        engine.execute("UNDROP TABLE ud_r");
        pause();
        assertCells(new String[][] {
            {"SELECT a FROM ud_r ORDER BY 1", "1"},
            {"SELECT a FROM ud_r " + at("AT", "r1") + " ORDER BY 1", "1"},
            {"SELECT a FROM ud_r " + at("AT", "r2") + " ORDER BY 1", "1"},
            {"SELECT a FROM ud_r2 " + at("AT", "r2") + " ORDER BY 1", "9"},
            {"SELECT a FROM ud_r2 " + at("AT", "r1") + " ORDER BY 1", String.format(NOT_AVAILABLE, "UD_R2")},
            {"DROP TABLE ud_r", "UD_R successfully dropped."},
            {"UNDROP TABLE ud_r", "Table UD_R successfully restored."},
            {"SELECT a FROM ud_r " + at("AT", "r1") + " ORDER BY 1", "1"},
        });
    }

    @Test
    public void anUndroppedSchemasTablesKeepTheirHistory() {
        engine.execute("CREATE OR REPLACE SCHEMA ud_s");
        engine.execute("USE SCHEMA test_db.test_schema");
        pause();
        engine.execute("CREATE TABLE ud_s.t (a INT)");
        pause();
        engine.execute("INSERT INTO ud_s.t VALUES (1)");
        point("s1");
        engine.execute("INSERT INTO ud_s.t VALUES (2)");
        point("s2");
        engine.execute("DROP SCHEMA ud_s");
        point("s3");
        engine.execute("UNDROP SCHEMA ud_s");
        engine.execute("USE SCHEMA test_db.test_schema");
        pause();
        assertCells(new String[][] {
            {"SELECT a FROM ud_s.t ORDER BY 1", "1 ;; 2"},
            {"SELECT a FROM ud_s.t " + at("AT", "s1") + " ORDER BY 1", "1"},
            {"SELECT a FROM ud_s.t " + at("AT", "s2") + " ORDER BY 1", "1 ;; 2"},
            {"SELECT a FROM ud_s.t " + at("AT", "s3") + " ORDER BY 1", "1 ;; 2"},
            {"CREATE TABLE ud_s.k CLONE ud_s.t " + at("AT", "s1"), "Table K successfully created."},
            {"SELECT a FROM ud_s.k ORDER BY 1", "1"},
        });
    }
}
