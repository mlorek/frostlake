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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What RESTRICT means for a table another table's foreign key references, and what a drop without it does to
 * that foreign key: the referenced table, the schema and the database that hold it stay under RESTRICT, and
 * otherwise the drop takes the referencing foreign keys with it (live-verified).
 */
public class DropRestrictForeignKeyTest extends BaseDatabaseTest {

    private static final String TABLE_HELD = "SQL compilation error:\nCannot drop the table because of dependencies";
    private static final String SCHEMA_HELD = "SQL compilation error:\nCannot drop the schema because of dependencies";
    private static final String DATABASE_HELD =
        "SQL compilation error:\nCannot drop the database because of dependencies";

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private int foreignKeys(final String table) {
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM information_schema.table_constraints"
            + " WHERE constraint_type = 'FOREIGN KEY' AND table_name = '" + table + "'");
        assertTrue(rs.next());
        return ((Number) rs.getValue(0)).intValue();
    }

    private String ddl(final String table) {
        final ResultSet rs = engine.executeQuery("SELECT GET_DDL('TABLE', '" + table + "')");
        assertTrue(rs.next());
        return String.valueOf(rs.getValue(0));
    }

    private void pair(final String parent, final String child) {
        engine.execute("CREATE OR REPLACE TABLE " + parent + " (id INT PRIMARY KEY)");
        engine.execute("CREATE OR REPLACE TABLE " + child + " (pid INT REFERENCES " + parent + "(id), x INT)");
    }

    @Test
    public void restrictKeepsAReferencedTable() {
        pair("fk_p1", "fk_c1");
        assertEquals(TABLE_HELD, refusal("DROP TABLE fk_p1 RESTRICT"));
        assertEquals(TABLE_HELD, refusal("DROP TABLE IF EXISTS fk_p1 RESTRICT"));
        engine.executeQuery("SELECT COUNT(*) FROM fk_p1");
        assertEquals(1, foreignKeys("FK_C1"));
        engine.execute("DROP TABLE fk_c1 RESTRICT");
        engine.execute("DROP TABLE fk_p1 RESTRICT");
    }

    @Test
    public void aDropWithoutRestrictTakesTheReferencingKeys() {
        pair("fk_p2", "fk_c2");
        engine.execute("DROP TABLE fk_p2 CASCADE");
        assertEquals(0, foreignKeys("FK_C2"));
        assertFalse(ddl("fk_c2").toLowerCase(Locale.ROOT).contains("references"), ddl("fk_c2"));
        assertFalse(engine.executeQuery("SHOW IMPORTED KEYS IN TABLE fk_c2").next());
        pair("fk_p3", "fk_c3");
        engine.execute("DROP TABLE fk_p3");
        assertEquals(0, foreignKeys("FK_C3"));
        engine.execute("UNDROP TABLE fk_p3");
        assertEquals(0, foreignKeys("FK_C3"));
    }

    @Test
    public void replacingTheParentOrDroppingItsKeyTakesThemToo() {
        pair("fk_p4", "fk_c4");
        engine.execute("CREATE OR REPLACE TABLE fk_p4 (id INT PRIMARY KEY, y INT)");
        assertEquals(0, foreignKeys("FK_C4"));
        pair("fk_p5", "fk_c5");
        engine.execute("ALTER TABLE fk_p5 DROP PRIMARY KEY");
        assertEquals(0, foreignKeys("FK_C5"));
        engine.execute("DROP TABLE fk_p5 RESTRICT");
    }

    @Test
    public void aNamedTableLevelKeyCountsAndASelfReferenceDoesNot() {
        engine.execute("CREATE OR REPLACE TABLE fk_n1 (id INT PRIMARY KEY)");
        engine.execute("CREATE OR REPLACE TABLE fk_n2 (pid INT, CONSTRAINT fk_n FOREIGN KEY (pid) REFERENCES fk_n1 (id))");
        assertEquals(TABLE_HELD, refusal("DROP TABLE fk_n1 RESTRICT"));
        engine.execute("DROP TABLE fk_n1 CASCADE");
        assertEquals(0, foreignKeys("FK_N2"));
        assertFalse(ddl("fk_n2").toLowerCase(Locale.ROOT).contains("foreign key"), ddl("fk_n2"));
        engine.execute("CREATE OR REPLACE TABLE fk_s1 (id INT PRIMARY KEY, pid INT REFERENCES fk_s1(id))");
        engine.execute("DROP TABLE fk_s1 RESTRICT");
    }

    @Test
    public void twoChildrenBothLoseTheirKeys() {
        pair("fk_p6", "fk_c6");
        engine.execute("CREATE OR REPLACE TABLE fk_c6b (pid INT REFERENCES fk_p6(id))");
        assertEquals(TABLE_HELD, refusal("DROP TABLE fk_p6 RESTRICT"));
        engine.execute("DROP TABLE fk_p6 CASCADE");
        assertEquals(0, foreignKeys("FK_C6"));
        assertEquals(0, foreignKeys("FK_C6B"));
    }

    @Test
    public void aViewOverTheTableHoldsNothingBack() {
        engine.execute("CREATE OR REPLACE TABLE fk_vt (a INT)");
        engine.execute("CREATE OR REPLACE VIEW fk_vv AS SELECT a FROM fk_vt");
        engine.execute("DROP TABLE fk_vt RESTRICT");
        engine.execute("DROP VIEW fk_vv RESTRICT");
    }

    @Test
    public void aSchemaHoldingAReferencedTableStaysUnderRestrict() {
        engine.execute("CREATE OR REPLACE TABLE fk_p7 (id INT PRIMARY KEY)");
        engine.execute("CREATE OR REPLACE SCHEMA fk_other");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE OR REPLACE TABLE fk_other.oc (pid INT REFERENCES test_db.test_schema.fk_p7(id))");
        assertEquals(TABLE_HELD, refusal("DROP TABLE fk_p7 RESTRICT"));
        // The schema holding only the referencing side is dropped.
        engine.execute("DROP SCHEMA fk_other RESTRICT");
        engine.execute("CREATE OR REPLACE SCHEMA fk_parent");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE OR REPLACE TABLE fk_parent.pp (id INT PRIMARY KEY)");
        engine.execute("CREATE OR REPLACE TABLE fk_c8 (pid INT REFERENCES fk_parent.pp(id))");
        assertEquals(SCHEMA_HELD, refusal("DROP SCHEMA fk_parent RESTRICT"));
        assertEquals(1, foreignKeys("FK_C8"));
        engine.execute("DROP SCHEMA fk_parent CASCADE");
        assertEquals(0, foreignKeys("FK_C8"));
        // A reference between two tables of the schema holds it back as well.
        engine.execute("CREATE OR REPLACE SCHEMA fk_inner");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE OR REPLACE TABLE fk_inner.p (id INT PRIMARY KEY)");
        engine.execute("CREATE OR REPLACE TABLE fk_inner.c (pid INT REFERENCES fk_inner.p(id))");
        assertEquals(SCHEMA_HELD, refusal("DROP SCHEMA fk_inner RESTRICT"));
        engine.execute("DROP SCHEMA fk_inner");
    }

    @Test
    public void aDatabaseHoldingAReferencedTableStaysUnderRestrict() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE fl_fk788a");
            engine.execute("CREATE OR REPLACE DATABASE fl_fk788b");
            engine.execute("CREATE OR REPLACE TABLE fl_fk788a.public.p (id INT PRIMARY KEY)");
            engine.execute("CREATE OR REPLACE TABLE fl_fk788b.public.c (pid INT REFERENCES fl_fk788a.public.p(id))");
            engine.execute("USE SCHEMA test_db.test_schema");
            assertEquals(TABLE_HELD, refusal("DROP TABLE fl_fk788a.public.p RESTRICT"));
            assertEquals(SCHEMA_HELD, refusal("DROP SCHEMA fl_fk788a.public RESTRICT"));
            assertEquals(DATABASE_HELD, refusal("DROP DATABASE fl_fk788a RESTRICT"));
            engine.execute("DROP DATABASE fl_fk788a CASCADE");
            final ResultSet keys = engine.executeQuery("SELECT COUNT(*) FROM fl_fk788b.information_schema"
                + ".table_constraints WHERE constraint_type = 'FOREIGN KEY' AND table_name = 'C'");
            assertTrue(keys.next());
            assertEquals(0, ((Number) keys.getValue(0)).intValue());
            // The database holding only the referencing side is dropped.
            engine.execute("DROP DATABASE fl_fk788b RESTRICT");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS fl_fk788a");
            engine.execute("DROP DATABASE IF EXISTS fl_fk788b");
            engine.execute("USE SCHEMA test_db.test_schema");
        }
    }
}
