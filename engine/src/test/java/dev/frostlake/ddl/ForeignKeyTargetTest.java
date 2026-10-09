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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a foreign key may point at. The engine took any parent, any columns of it and any number of them.
 *
 * <p>On the account the parent has to exist, the columns a key names have to be a primary or unique key of the
 * parent in that key's own order, and as many as the foreign key's own. A key that names none takes the parent's
 * primary key when that has as many columns, else a unique key that has; with no key that fits, or pointed at a
 * view, it is refused. A table may point at itself. Every cell is live-verified.
 */
public class ForeignKeyTargetTest extends BaseDatabaseTest {

    private static final String NO_KEY = "SQL compilation error:|primary or unique key does not exist for the columns"
        + " referenced by the foreign key";
    private static final String NO_MATCH = "SQL compilation error:|could not find matching constraints for the foreign"
        + " key";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE p1 (id INT PRIMARY KEY)");
        engine.execute("CREATE TABLE p2 (a INT, b INT, PRIMARY KEY (a, b))");
        engine.execute("CREATE TABLE p3 (u INT UNIQUE, v INT, w INT, UNIQUE (v, w))");
        engine.execute("CREATE TABLE nokey (y INT)");
    }

    /** The statement's refusal with its lines joined, or "ok". */
    private String outcome(final String sql) {
        try {
            engine.execute(sql);
            return "ok";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).trim().replace('\n', '|');
        }
    }

    /** The parent's columns the table's foreign keys point at, a key's joined by commas. */
    private List<String> targets(final String table) {
        final List<String> out = new ArrayList<>();
        final ResultSet rs = engine.executeQuery("SHOW IMPORTED KEYS IN TABLE " + table);
        for (final Row row : rs.getRows()) {
            out.add(cell(rs, row, "pk_table_name") + "." + cell(rs, row, "pk_column_name") + "<-"
                + cell(rs, row, "fk_column_name"));
        }
        Collections.sort(out);
        return out;
    }

    private static List<String> rows(final String... rows) {
        final List<String> out = new ArrayList<>();
        for (final String row : rows) {
            out.add(row);
        }
        return out;
    }

    @Test
    public void aKeyWithoutColumnsTakesTheParentsKey() {
        assertEquals("ok", outcome("CREATE TABLE s1 (y INT, FOREIGN KEY (y) REFERENCES p1)"));
        assertEquals(rows("P1.ID<-Y"), targets("s1"));
        assertEquals("ok", outcome("CREATE TABLE s2 (a INT, b INT, FOREIGN KEY (a, b) REFERENCES p2)"));
        assertEquals(rows("P2.A<-A", "P2.B<-B"), targets("s2"));
        assertEquals("ok", outcome("CREATE TABLE s3 (y INT REFERENCES p1)"));
        assertEquals(rows("P1.ID<-Y"), targets("s3"));
        assertEquals("ok", outcome("CREATE TABLE s4 (y INT, FOREIGN KEY (y) REFERENCES p3)"), "a unique key fits too");
        assertEquals(rows("P3.U<-Y"), targets("s4"));
        assertEquals(NO_MATCH, outcome("CREATE TABLE s5 (a INT REFERENCES p2)"), "one column against a key of two");
        assertEquals(NO_MATCH, outcome("CREATE TABLE s5 (y INT, FOREIGN KEY (y) REFERENCES nokey)"));
    }

    @Test
    public void theNamedColumnsHaveToBeAKeyOfTheParent() {
        assertEquals("ok", outcome("CREATE TABLE k1 (y INT REFERENCES p3 (u))"));
        assertEquals("ok", outcome("CREATE TABLE k2 (y INT, z INT, FOREIGN KEY (y, z) REFERENCES p3 (v, w))"));
        assertEquals(NO_KEY, outcome("CREATE TABLE k3 (y INT, z INT, FOREIGN KEY (y, z) REFERENCES p3 (w, v))"),
            "a key's columns in the key's own order");
        assertEquals(NO_KEY, outcome("CREATE TABLE k3 (y INT, FOREIGN KEY (y) REFERENCES p3 (v))"), "half a key is none");
        assertEquals(NO_KEY, outcome("CREATE TABLE k3 (y INT, FOREIGN KEY (y) REFERENCES nokey (y))"));
        assertEquals("SQL compilation error:|Number of columns in foreign key specification does not match number of"
            + " columns in foreign key reference", outcome("CREATE TABLE k3 (y INT, FOREIGN KEY (y) REFERENCES p2 (a, b))"));
    }

    @Test
    public void aPrimaryKeysOrderIsItsDeclarationsNotItsColumns() {
        engine.execute("CREATE TABLE po (b INT, a INT, c INT, PRIMARY KEY (a, b))");
        assertEquals("ok", outcome("CREATE TABLE po1 (x INT, y INT, FOREIGN KEY (x, y) REFERENCES po (a, b))"));
        assertEquals(NO_KEY, outcome("CREATE TABLE po2 (x INT, y INT, FOREIGN KEY (x, y) REFERENCES po (b, a))"));
        assertEquals("ok", outcome("CREATE TABLE po3 (x INT, y INT, FOREIGN KEY (x, y) REFERENCES po)"));
        assertEquals(rows("PO.A<-X", "PO.B<-Y"), targets("po3"));
        final ResultSet keys = engine.executeQuery("SHOW PRIMARY KEYS IN TABLE po");
        assertEquals("1", cell(keys, soleRowWhere(keys, "column_name", "A"), "key_sequence"));
        assertEquals("2", cell(keys, soleRowWhere(keys, "column_name", "B"), "key_sequence"));
    }

    @Test
    public void aTableMayPointAtItselfAndNotAtAView() {
        assertEquals("ok", outcome("CREATE TABLE tree (id INT PRIMARY KEY, parent INT REFERENCES tree (id))"));
        assertEquals(rows("TREE.ID<-PARENT"), targets("tree"));
        engine.execute("CREATE VIEW pv AS SELECT id FROM p1");
        assertEquals(NO_KEY, outcome("CREATE TABLE v1 (y INT REFERENCES pv (id))"));
        engine.execute("CREATE TABLE lost (a INT PRIMARY KEY ENABLE)");
        assertEquals(NO_KEY, outcome("CREATE TABLE v1 (y INT REFERENCES lost (a))"), "a key left out is no key");
    }

    @Test
    public void theParentHasToExist() {
        final String missing = hinted("SQL compilation error:|Table 'TEST_DB.TEST_SCHEMA.NOSUCH_PARENT' does not exist"
            + " or not authorized.");
        assertEquals(missing, outcome("CREATE TABLE m1 (y INT, FOREIGN KEY (y) REFERENCES nosuch_parent (id))"));
        assertEquals(missing, outcome("CREATE TABLE m1 (y INT REFERENCES nosuch_parent (id))"));
        assertEquals(missing, outcome("CREATE TABLE m1 (y INT REFERENCES nosuch_parent (id) ON DELETE CASCADE)"));
    }

    @Test
    public void anAlterAddIsJudgedTheSameWay() {
        engine.execute("CREATE TABLE a1 (y INT, z INT)");
        assertEquals(NO_KEY, outcome("ALTER TABLE a1 ADD FOREIGN KEY (z) REFERENCES p3 (w)"));
        assertEquals("ok", outcome("ALTER TABLE a1 ADD FOREIGN KEY (y) REFERENCES p1"));
        assertEquals(rows("P1.ID<-Y"), targets("a1"));
        assertEquals("SQL compilation error: error line 1 at position 32|invalid identifier 'NOSUCH'",
            outcome("ALTER TABLE a1 ADD FOREIGN KEY (nosuch) REFERENCES p1 (id)"));
        assertEquals("SQL compilation error: error line 1 at position 32|invalid identifier 'NOSUCH'",
            outcome("ALTER TABLE a1 ADD FOREIGN KEY (nosuch) REFERENCES p1 (id) MATCH SIMPLE"));
    }

    @Test
    public void aKeysOwnColumnsHaveToBeTheTables() {
        assertEquals("SQL compilation error: error line 1 at position 37|invalid identifier 'NOSUCH'",
            outcome("CREATE TABLE n3 (y INT, FOREIGN KEY (nosuch) REFERENCES p1 (id))"));
        assertEquals("SQL compilation error: error line 1 at position 37|invalid identifier 'NOSUCH'",
            outcome("CREATE TABLE n3 (y INT, PRIMARY KEY (nosuch))"));
        assertEquals("SQL compilation error: error line 1 at position 35|invalid identifier 'NOSUCH'",
            outcome("CREATE TABLE n3 (y INT, UNIQUE (y, nosuch))"));
        assertEquals("SQL compilation error: error line 1 at position 37|invalid identifier 'NOSUCH'",
            outcome("CREATE TABLE n3 (y INT, PRIMARY KEY (nosuch) ENABLE)"), "a key left out is judged as well");
    }
}
