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
 * Where a table, a view, a sequence, a schema, a database or a dynamic table reads a property's name, the account reads
 * eight reserved words too - DEFAULT, CASE, CAST, CONSTRAINT, CROSS, INNER, JOIN and WHEN - only to refuse each as no
 * property of the kind, spelled as written, after the names written twice and in the order of a table hashed by name;
 * another reserved word is a syntax error at the word. Every answer is live-verified.
 */
public class ReservedPropertyNamesTest extends MeasuredCellsTestSupport {

    private static final String TABLE = "CREATE OR REPLACE TABLE tq970 (a INT) ";

    private static String invalid(final String name, final String kind) {
        return "REFUSED SQL compilation error: | invalid property '" + name + "' for '" + kind + "'";
    }

    @Test
    public void aTableRefusesAReservedWordAsNoProperty() {
        engine.execute("CREATE OR REPLACE TABLE pt970 (a INT)");
        assertCells(new String[][] {
            {TABLE + "DEFAULT = 1", invalid("DEFAULT", "TABLE")},
            {TABLE + "default = 1", invalid("default", "TABLE")},
            {TABLE + "\"DEFAULT\" = 1", invalid("\"DEFAULT\"", "TABLE")},
            {TABLE + "DEFAULT = (TYPE = CSV)", invalid("DEFAULT", "TABLE")},
            {TABLE + "DEFAULT = x", invalid("DEFAULT", "TABLE")},
            {TABLE + "DEFAULT = 1,", invalid("DEFAULT", "TABLE")},
            {TABLE + "DEFAULT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '1'."},
            {TABLE + "DEFAULT =",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '<EOF>'."},
            {TABLE + "DEFAULT = 1 x = 2 y = 3", invalid("x", "TABLE")},
            {TABLE + "DEFAULT = 1 DEFAULT = 2", "REFUSED SQL compilation error: | duplicate property 'DEFAULT';"},
            {TABLE + "x = 1 COMMENT = 'c' COMMENT = 'd'",
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {TABLE + "CHANGE_TRACKING = 'x' DEFAULT = 1", invalid("DEFAULT", "TABLE")},
            {"CREATE OR REPLACE TABLE tq970 (a NOSUCHTYPE) DEFAULT = 1", invalid("DEFAULT", "TABLE")},
            {"CREATE OR REPLACE TABLE tq970 DEFAULT = 1 (a NOSUCHTYPE)", invalid("DEFAULT", "TABLE")},
            {"CREATE OR REPLACE TABLE tq970 DEFAULT = 1 AS SELECT 1 a", invalid("DEFAULT", "TABLE")},
            {"CREATE OR REPLACE TABLE tq970 DEFAULT = 1 CLONE pt970", invalid("DEFAULT", "TABLE")},
            {"CREATE OR REPLACE TABLE tq970 DEFAULT = 1 LIKE pt970", invalid("DEFAULT", "TABLE")},
            {"CREATE OR REPLACE TRANSIENT TABLE tq970 (a INT) DEFAULT = 1", invalid("DEFAULT", "TABLE")},
        });
    }

    @Test
    public void eightReservedWordsAreNamesAndTheOthersSyntaxErrors() {
        assertCells(new String[][] {
            {TABLE + "CASE = 1", invalid("CASE", "TABLE")},
            {TABLE + "CAST = 1", invalid("CAST", "TABLE")},
            {TABLE + "CONSTRAINT = 1", invalid("CONSTRAINT", "TABLE")},
            {TABLE + "CROSS = 1", invalid("CROSS", "TABLE")},
            {TABLE + "INNER = 1", invalid("INNER", "TABLE")},
            {TABLE + "JOIN = 1", invalid("JOIN", "TABLE")},
            {TABLE + "WHEN = 1", invalid("WHEN", "TABLE")},
            {TABLE + "LEFT = 1", invalid("LEFT", "TABLE")},
            {TABLE + "SELECT = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'SELECT'."},
            {TABLE + "ORDER = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'ORDER'."},
            {TABLE + "WITH = 1", "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '='."},
            {TABLE + "DEFAULT x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'x'."},
            {TABLE + "JOIN = 1 SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'SELECT'."},
        });
    }

    @Test
    public void eachKindRefusesItInItsOwnWords() {
        engine.execute("CREATE OR REPLACE TABLE pt970 (a INT)");
        engine.execute("CREATE OR REPLACE VIEW v970 AS SELECT 1 a");
        engine.execute("CREATE OR REPLACE SEQUENCE s970");
        final String dynamic = "CREATE OR REPLACE DYNAMIC TABLE dt970 TARGET_LAG = '1 day' WAREHOUSE = nosuch_wh_w9 ";
        assertCells(new String[][] {
            {"CREATE OR REPLACE VIEW v970b DEFAULT = 1 AS SELECT 1 a", invalid("DEFAULT", "TABLE")},
            {"CREATE OR REPLACE VIEW v970b COMMENT = 'c' DEFAULT = 1 AS SELECT 1 a", invalid("DEFAULT", "TABLE")},
            {"CREATE OR REPLACE VIEW v970b DEFAULT = 1 x = 2 AS SELECT 1 a", invalid("x", "TABLE")},
            {"CREATE OR REPLACE SEQUENCE s970b DEFAULT = 1", invalid("DEFAULT", "SEQUENCE")},
            {"CREATE OR REPLACE SEQUENCE s970b DEFAULT = 1 x = 2", invalid("x", "SEQUENCE")},
            {"CREATE OR REPLACE SEQUENCE s970b START = 1 DEFAULT = 1", invalid("DEFAULT", "SEQUENCE")},
            {"CREATE OR REPLACE EVENT TABLE et970 DEFAULT = 1", invalid("DEFAULT", "TABLE")},
            {"CREATE OR REPLACE EVENT TABLE et970 x = 1 DEFAULT = 1", invalid("x", "TABLE")},
            {dynamic + "DEFAULT = 1 AS SELECT a FROM pt970", invalid("DEFAULT", "DYNAMIC_TABLE")},
            {"CREATE OR REPLACE DYNAMIC TABLE dt970 DEFAULT = 1 TARGET_LAG = '1 day' WAREHOUSE = nosuch_wh_w9 AS "
                + "SELECT a FROM pt970", invalid("DEFAULT", "DYNAMIC_TABLE")},
            {"ALTER TABLE pt970 SET DEFAULT = 1", invalid("DEFAULT", "TABLE")},
            {"ALTER TABLE pt970 SET default = 1", invalid("default", "TABLE")},
            {"ALTER TABLE pt970 SET \"DEFAULT\" = 1", invalid("\"DEFAULT\"", "TABLE")},
            {"ALTER TABLE pt970 SET COMMENT = 'c' DEFAULT = 1", invalid("DEFAULT", "TABLE")},
            {"ALTER TABLE pt970 SET DEFAULT = 1 x = 2", invalid("x", "TABLE")},
            {"ALTER TABLE pt970 UNSET DEFAULT", invalid("DEFAULT", "TABLE")},
            {"ALTER TABLE pt970 UNSET COMMENT, DEFAULT", invalid("DEFAULT", "TABLE")},
            {"ALTER TABLE pt970 SET CASE = 1", invalid("CASE", "TABLE")},
            {"ALTER TABLE nosuch_t_w9 SET DEFAULT = 1", invalid("DEFAULT", "TABLE")},
            {"ALTER VIEW v970 SET DEFAULT = 1", invalid("DEFAULT", "VIEW")},
            {"ALTER VIEW v970 UNSET DEFAULT", invalid("DEFAULT", "VIEW")},
            {"ALTER VIEW v970 SET DEFAULT = 1 x = 2", invalid("x", "VIEW")},
            {"ALTER VIEW nosuch_v_w9 SET DEFAULT = 1", invalid("DEFAULT", "VIEW")},
            {"ALTER SEQUENCE s970 SET DEFAULT = 1", invalid("DEFAULT", "SEQUENCE")},
            {"ALTER SEQUENCE s970 UNSET DEFAULT", invalid("DEFAULT", "SEQUENCE")},
            {"ALTER SEQUENCE s970 DEFAULT = 1", invalid("DEFAULT", "SEQUENCE")},
            {"ALTER SEQUENCE nosuch_s_w9 SET DEFAULT = 1", invalid("DEFAULT", "SEQUENCE")},
            {"ALTER DYNAMIC TABLE nosuch_dt_w9 SET DEFAULT = 1", invalid("DEFAULT", "DYNAMIC_TABLE")},
            {"ALTER DYNAMIC TABLE nosuch_dt_w9 UNSET DEFAULT", invalid("DEFAULT", "DYNAMIC_TABLE")},
            {"ALTER DYNAMIC TABLE nosuch_dt_w9 SET x = 1", invalid("x", "DYNAMIC_TABLE")},
            {"ALTER DYNAMIC TABLE nosuch_dt_w9 SET CASE = 1", invalid("CASE", "DYNAMIC_TABLE")},
            {"ALTER SCHEMA test_db.test_schema SET DEFAULT = 1", invalid("DEFAULT", "SCHEMA")},
            {"ALTER SCHEMA test_db.test_schema UNSET DEFAULT", invalid("DEFAULT", "SCHEMA")},
            {"ALTER DATABASE test_db SET DEFAULT = 1", invalid("DEFAULT", "DATABASE")},
            {"ALTER DATABASE test_db UNSET DEFAULT", invalid("DEFAULT", "DATABASE")},
        });
    }

    @Test
    public void aMaterializedViewIsATablesAtCreationAndItsOwnAtAlter() {
        engine.execute("CREATE OR REPLACE TABLE pt970 (a INT)");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mv970 AS SELECT a FROM pt970");
        assertCells(new String[][] {
            {"CREATE OR REPLACE MATERIALIZED VIEW mv970b DEFAULT = 1 AS SELECT a FROM pt970",
                invalid("DEFAULT", "TABLE")},
            {"CREATE OR REPLACE MATERIALIZED VIEW mv970b x = 1 AS SELECT a FROM pt970", invalid("x", "TABLE")},
            {"ALTER MATERIALIZED VIEW mv970 SET DEFAULT = 1", invalid("DEFAULT", "MATERIALIZED_VIEW")},
            {"ALTER MATERIALIZED VIEW mv970 UNSET DEFAULT", invalid("DEFAULT", "MATERIALIZED_VIEW")},
        });
    }

    @Test
    public void aPropertyBeforeTheColumnsIsATablesToo() {
        assertAnswer("Table TQ970 successfully created.", "CREATE OR REPLACE TABLE tq970 COMMENT = 'c' (a INT)");
        assertAnswer("c", "SELECT COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'TEST_SCHEMA' AND "
            + "TABLE_NAME = 'TQ970'");
    }
}
