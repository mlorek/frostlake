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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An Iceberg table and an ordinary table are altered, commented and cloned each by the statement of its kind, and
 * an Iceberg table never stops tracking its changes.
 */
public class IcebergTableKindTest extends BaseDatabaseTest {

    private static final String USE_ALTER_ICEBERG = "The table ICE_KIND is an Iceberg table. Iceberg tables should use"
        + " ALTER ICEBERG TABLE commands. Please ensure the appropriate command format is used.";
    private static final String USE_ALTER_TABLE = "The table PLAIN_KIND is a legacy Iceberg table. Legacy Iceberg tables"
        + " should use the ALTER TABLE commands. Please ensure the appropriate command format is used.";
    private static final String SWAP_REFUSED = "SWAP WITH is not supported for ICEBERG TABLE.";
    private static final String TRACKING_KEPT = "invalid value 'false' for property 'CHANGE_TRACKING', Reason: Change"
        + " Tracking cannot be turned off for Iceberg tables";

    private List<String> described(final String relation) {
        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE " + relation);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            out.add(row.getValue(rs.getColumnIndex("name")) + " " + row.getValue(rs.getColumnIndex("type")));
        }
        return out;
    }

    private List<String> column(final String sql, final String column) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            out.add(String.valueOf(row.getValue(rs.getColumnIndex(column))));
        }
        return out;
    }

    private List<String> rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            final StringBuilder line = new StringBuilder();
            for (int i = 0; i < rs.getColumns().size(); i++) {
                line.append(i == 0 ? "" : "|").append(row.getValue(i));
            }
            out.add(line.toString());
        }
        return out;
    }

    private void refused(final String sql, final String fragment) {
        final RuntimeException refusal = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        assertTrue(refusal.getMessage().contains(fragment), sql + " => " + refusal.getMessage());
    }

    @Override
    protected void setupTest() {
        engine.executeQuery("CREATE ICEBERG TABLE ice_kind (a INT, b VARCHAR)");
        engine.executeQuery("CREATE TABLE plain_kind (a INT, v VARCHAR(10))");
    }

    @Test
    public void anIcebergTableIsNotAlteredAsATable() {
        for (final String statement : new String[] {"ALTER TABLE ice_kind RENAME TO ice_kind2",
            "ALTER TABLE ice_kind SET COMMENT = 'x'", "ALTER TABLE ice_kind SET DATA_RETENTION_TIME_IN_DAYS = 1",
            "ALTER TABLE ice_kind SET CHANGE_TRACKING = TRUE", "ALTER TABLE ice_kind SET CHANGE_TRACKING = FALSE",
            "ALTER TABLE ice_kind UNSET COMMENT", "ALTER TABLE ice_kind UNSET CHANGE_TRACKING",
            "ALTER TABLE ice_kind DROP COLUMN b", "ALTER TABLE ice_kind RENAME COLUMN b TO b2",
            "ALTER TABLE ice_kind CLUSTER BY (a)", "ALTER TABLE ice_kind SUSPEND RECLUSTER",
            "ALTER TABLE ice_kind ADD COLUMN v VARCHAR(10)", "ALTER TABLE ice_kind ADD COLUMN j VARCHAR",
            "ALTER TABLE ice_kind ADD CONSTRAINT pk PRIMARY KEY (a)", "ALTER TABLE ice_kind DROP PRIMARY KEY",
            "ALTER TABLE ice_kind ALTER COLUMN b SET DATA TYPE VARCHAR(100)",
            "ALTER TABLE ice_kind ALTER COLUMN a COMMENT 'c'", "ALTER TABLE ice_kind MODIFY COLUMN a SET NOT NULL",
            "ALTER TABLE IF EXISTS ice_kind SET COMMENT = 'x'", "ALTER TABLE test_schema.ice_kind RENAME TO ice_kind9",
            "ALTER TABLE \"ICE_KIND\" SET COMMENT = 'x'", "COMMENT ON TABLE ice_kind IS 'x'",
            "COMMENT ON COLUMN ice_kind.a IS 'c'"}) {
            refused(statement, USE_ALTER_ICEBERG);
        }
        // What the statement names beside the table is looked for first, and a syntax error is one for any table.
        refused("ALTER TABLE ice_kind SET TAG nosuch_tag = 'x'", "Tag 'NOSUCH_TAG' does not exist or not authorized.");
        refused("ALTER TABLE ice_kind ADD ROW ACCESS POLICY nosuch ON (a)",
            "Row access policy 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized.");
        refused("ALTER TABLE ice_kind nonsense", "syntax error line 1 at position 21 unexpected 'nonsense'.");
        assertEquals("[A NUMBER(10,0), B VARCHAR(134217728)]", described("ice_kind").toString());
        assertEquals("[]", column("SHOW TABLES LIKE 'ICE_KIND'", "comment").toString().replace("null", ""));
        // The statements that are not an ALTER take an Iceberg table as a table.
        engine.executeQuery("INSERT INTO ice_kind VALUES (1, 'x')");
        engine.executeQuery("TRUNCATE TABLE ice_kind");
        assertEquals("[0]", column("SELECT COUNT(*) AS n FROM ice_kind", "N").toString());
        engine.executeQuery("DROP TABLE ice_kind");
        engine.executeQuery("UNDROP TABLE ice_kind");
        assertEquals("[Y]", column("SHOW TABLES LIKE 'ICE_KIND'", "is_iceberg").toString());
    }

    @Test
    public void aSwapAsksForTablesOfOneKind() {
        engine.executeQuery("CREATE ICEBERG TABLE ice_two (c INT) COMMENT = 'two'");
        engine.executeQuery("INSERT INTO ice_two VALUES (2), (3)");
        engine.executeQuery("INSERT INTO ice_kind VALUES (1, 'x')");
        engine.executeQuery("CREATE TABLE plain_two (a INT)");
        engine.executeQuery("CREATE VIEW view_kind AS SELECT 1 AS a");
        // ALTER TABLE swaps no Iceberg table, whatever stands beside it.
        for (final String statement : new String[] {"ALTER TABLE ice_kind SWAP WITH plain_kind",
            "ALTER TABLE plain_kind SWAP WITH ice_kind", "ALTER TABLE ice_kind SWAP WITH ice_two",
            "ALTER TABLE ice_kind SWAP WITH ice_kind", "ALTER TABLE ice_kind SWAP WITH view_kind",
            "ALTER TABLE IF EXISTS ice_nosuch SWAP WITH ice_kind"}) {
            refused(statement, SWAP_REFUSED);
        }
        // ALTER ICEBERG TABLE swaps two Iceberg tables and nothing else.
        for (final String statement : new String[] {"ALTER ICEBERG TABLE ice_kind SWAP WITH plain_kind",
            "ALTER ICEBERG TABLE plain_kind SWAP WITH ice_kind", "ALTER ICEBERG TABLE ice_kind SWAP WITH view_kind",
            "ALTER ICEBERG TABLE IF EXISTS ice_kind SWAP WITH plain_kind",
            "ALTER ICEBERG TABLE IF EXISTS ice_nosuch SWAP WITH ice_kind"}) {
            refused(statement, SWAP_REFUSED);
        }
        refused("ALTER ICEBERG TABLE plain_kind SWAP WITH plain_two", USE_ALTER_TABLE);
        refused("ALTER ICEBERG TABLE plain_kind SWAP WITH view_kind", USE_ALTER_TABLE);
        refused("ALTER ICEBERG TABLE IF EXISTS plain_kind SWAP WITH plain_two", USE_ALTER_TABLE);
        // The other table is looked for first, and the table the statement alters before it.
        final String noTable = "Table 'TEST_DB.TEST_SCHEMA.ICE_NOSUCH' does not exist or not authorized.";
        for (final String statement : new String[] {"ALTER ICEBERG TABLE ice_kind SWAP WITH ice_nosuch",
            "ALTER ICEBERG TABLE plain_kind SWAP WITH ice_nosuch", "ALTER TABLE ice_kind SWAP WITH ice_nosuch",
            "ALTER ICEBERG TABLE ice_nosuch SWAP WITH plain_kind", "ALTER ICEBERG TABLE ice_nosuch SWAP WITH ice_kind",
            "ALTER ICEBERG TABLE ice_nosuch SWAP WITH ice_other",
            "ALTER ICEBERG TABLE IF EXISTS ice_other SWAP WITH ice_nosuch"}) {
            refused(statement, noTable);
        }
        final String noSchema = "Schema 'TEST_DB.NO_SCHEMA' does not exist or not authorized.";
        refused("ALTER ICEBERG TABLE no_schema.ice_kind SWAP WITH plain_kind", noSchema);
        refused("ALTER ICEBERG TABLE ice_kind SWAP WITH no_schema.plain_kind", noSchema);
        final String aView = "Object found is of type 'VIEW', not specified type 'TABLE'.";
        refused("ALTER ICEBERG TABLE view_kind SWAP WITH plain_kind", aView);
        refused("ALTER ICEBERG TABLE view_kind SWAP WITH ice_kind", aView);
        refused("ALTER ICEBERG TABLE IF EXISTS view_kind SWAP WITH plain_kind", aView);
        refused("ALTER ICEBERG TABLE IF EXISTS ice_nosuch SWAP WITH view_kind", aView);
        refused("ALTER ICEBERG TABLE information_schema.tables SWAP WITH plain_kind",
            "Insufficient privileges to operate on view 'TABLES'.");
        // A table that is not there is passed over when the other one could take its place.
        engine.executeQuery("ALTER ICEBERG TABLE IF EXISTS ice_nosuch SWAP WITH plain_kind");
        // Two Iceberg tables trade everything, their files too; one traded with itself stays as it is.
        final List<String> locationOfTwo = column("SHOW ICEBERG TABLES LIKE 'ICE_TWO'", "base_location");
        engine.executeQuery("ALTER ICEBERG TABLE ice_kind SWAP WITH ice_two");
        assertEquals("[C NUMBER(10,0)]", described("ice_kind").toString());
        assertEquals("[A NUMBER(10,0), B VARCHAR(134217728)]", described("ice_two").toString());
        assertEquals("[2, 3]", rows("SELECT * FROM ice_kind ORDER BY 1").toString());
        assertEquals("[1|x]", rows("SELECT * FROM ice_two").toString());
        assertEquals("[two]", column("SHOW ICEBERG TABLES LIKE 'ICE_KIND'", "comment").toString());
        assertEquals(locationOfTwo, column("SHOW ICEBERG TABLES LIKE 'ICE_KIND'", "base_location"));
        engine.executeQuery("ALTER ICEBERG TABLE IF EXISTS ice_kind SWAP WITH ice_two");
        assertEquals("[C NUMBER(10,0)]", described("ice_two").toString());
        engine.executeQuery("ALTER ICEBERG TABLE ice_two SWAP WITH ice_two");
        assertEquals("[C NUMBER(10,0)]", described("ice_two").toString());
        assertEquals("[2, 3]", rows("SELECT * FROM ice_two ORDER BY 1").toString());
    }

    @Test
    public void aTableIsNotAlteredAsAnIcebergTable() {
        for (final String statement : new String[] {"ALTER ICEBERG TABLE plain_kind ADD COLUMN x INT",
            "ALTER ICEBERG TABLE plain_kind SET COMMENT = 'x'", "ALTER ICEBERG TABLE plain_kind SET CHANGE_TRACKING = FALSE",
            "ALTER ICEBERG TABLE plain_kind RENAME TO plain_kind2", "ALTER ICEBERG TABLE plain_kind DROP COLUMN v",
            "ALTER ICEBERG TABLE plain_kind CLUSTER BY (a)", "ALTER ICEBERG TABLE IF EXISTS plain_kind SET COMMENT = 'x'"}) {
            refused(statement, USE_ALTER_TABLE);
        }
        // A column's type is held to the format before the table is looked at.
        refused("ALTER ICEBERG TABLE plain_kind ADD COLUMN v2 VARCHAR(10)", "For Iceberg tables, only max length");
        refused("ALTER ICEBERG TABLE plain_kind ADD COLUMN v2 CHAR(3)",
            "Unsupported data type 'CHAR(3)' for iceberg tables.");
        refused("ALTER ICEBERG TABLE plain_kind ALTER COLUMN v SET DATA TYPE VARCHAR(20)",
            "For Iceberg tables, only max length");
        refused("ALTER ICEBERG TABLE plain_kind REFRESH", "The type of table PLAIN_KIND is NOT_ICEBERG.");
        assertEquals("[A NUMBER(38,0), V VARCHAR(10)]", described("plain_kind").toString());
    }

    @Test
    public void anIcebergTableKeepsTrackingItsChanges() {
        refused("ALTER ICEBERG TABLE ice_kind SET CHANGE_TRACKING = FALSE", TRACKING_KEPT);
        refused("ALTER ICEBERG TABLE ice_kind SET CHANGE_TRACKING = FALSE, COMMENT = 'c'", TRACKING_KEPT);
        refused("ALTER ICEBERG TABLE ice_kind SET COMMENT = 'c2' CHANGE_TRACKING = FALSE", TRACKING_KEPT);
        refused("ALTER ICEBERG TABLE ice_kind UNSET CHANGE_TRACKING", TRACKING_KEPT);
        // Nothing of a refused SET is applied.
        assertEquals("[ON]", column("SHOW TABLES LIKE 'ICE_KIND'", "change_tracking").toString());
        assertEquals("[]", column("SHOW TABLES LIKE 'ICE_KIND'", "comment").toString().replace("null", ""));
        engine.executeQuery("ALTER ICEBERG TABLE ice_kind SET CHANGE_TRACKING = TRUE COMMENT = 'y'");
        assertEquals("[y]", column("SHOW TABLES LIKE 'ICE_KIND'", "comment").toString());
        // A table that is not there is passed over before its properties are read.
        engine.executeQuery("ALTER ICEBERG TABLE IF EXISTS ice_nosuch SET CHANGE_TRACKING = FALSE");
        refused("ALTER ICEBERG TABLE ice_nosuch SET CHANGE_TRACKING = FALSE",
            "Table 'TEST_DB.TEST_SCHEMA.ICE_NOSUCH' does not exist or not authorized.");
    }

    @Test
    public void aCloneIsOfItsSourcesKindAndACopyOfItsStatements() {
        refused("CREATE TABLE plain_cl CLONE ice_kind", "Invalid operation The table ICE_KIND is an Iceberg table. Clone"
            + " Iceberg table should use CREATE ICEBERG TABLE CLONE command. Please ensure the appropriate command"
            + " format is used.");
        refused("CREATE ICEBERG TABLE ice_cl CLONE plain_kind", "Invalid operation The table PLAIN_KIND is not Iceberg"
            + " table. Clone from Non-Iceberg table to Iceberg table is not supported. Please use Iceberg table with"
            + " Snowflake catalog to clone.");
        engine.executeQuery("CREATE ICEBERG TABLE ice_cl2 CLONE ice_kind");
        assertEquals("[A NUMBER(10,0), B VARCHAR(134217728)]", described("ice_cl2").toString());
        assertEquals("[Y]", column("SHOW TABLES LIKE 'ICE_CL2'", "is_iceberg").toString());
        // LIKE copies the columns as they are, whichever kind either table is.
        engine.executeQuery("CREATE TABLE plain_lk LIKE ice_kind");
        assertEquals("[A NUMBER(10,0), B VARCHAR(134217728)]", described("plain_lk").toString());
        assertEquals("[N]", column("SHOW TABLES LIKE 'PLAIN_LK'", "is_iceberg").toString());
        engine.executeQuery("CREATE ICEBERG TABLE ice_lk LIKE plain_kind");
        assertEquals("[A NUMBER(38,0), V VARCHAR(10)]", described("ice_lk").toString());
        assertEquals("[Y]", column("SHOW TABLES LIKE 'ICE_LK'", "is_iceberg").toString());
    }
}
