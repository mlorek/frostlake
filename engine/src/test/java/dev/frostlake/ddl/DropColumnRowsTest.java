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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ALTER TABLE … DROP COLUMN takes the column's VALUE out of every row, not only its name out of the
 * catalog. Rows are positional, so a value left behind surfaced under whichever column later held its
 * slot: after {@code DROP COLUMN b} over (1, 2, 3) the table read (1, 2), and an ADD COLUMN after that
 * read the stale 3 as the new column's value. Everything that holds the table's rows by position loses
 * the slot with it — the stored rows, their time-travel history, and the change records of the streams
 * over the table — because the account answers all three through the table's CURRENT columns.
 *
 * <p>The statement is also judged whole before anything goes: a name the table lacks is refused unless
 * its own IF EXISTS forgives it (the IF EXISTS after DROP covers the first name only; a later name
 * carries its own), and a list that would leave the table with no column is refused outright.
 */
public class DropColumnRowsTest extends BaseDatabaseTest {

    private RuntimeException refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
    }

    /** Every row of the query, each as its cells' text joined by "|". */
    private List<String> rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            final StringBuilder text = new StringBuilder();
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    text.append('|');
                }
                text.append(row.getValue(i));
            }
            out.add(text.toString());
        }
        return out;
    }

    /** The query's column names, joined by "|". */
    private String columns(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < rs.getColumns().size(); i++) {
            if (i > 0) {
                text.append('|');
            }
            text.append(rs.getColumns().get(i).getName());
        }
        return text.toString();
    }

    private static List<String> list(final String... rows) {
        final List<String> out = new ArrayList<>();
        for (final String row : rows) {
            out.add(row);
        }
        return out;
    }

    private static final String ALL_COLUMNS = "SQL compilation error:\ncannot drop all the columns of a table";

    /** A middle, a first and a last column, and a list of them: the rest of each row stays readable. */
    @Test
    public void theRowsLoseTheDroppedValue() {
        engine.execute("CREATE TABLE dr_mid (a INT, b INT, c INT)");
        engine.execute("INSERT INTO dr_mid VALUES (1, 2, 3), (4, 5, 6)");
        engine.execute("ALTER TABLE dr_mid DROP COLUMN b");
        assertEquals("A|C", columns("SELECT * FROM dr_mid"));
        assertEquals(list("1|3", "4|6"), rows("SELECT * FROM dr_mid ORDER BY a"));
        assertEquals(list("1|3", "4|6"), rows("SELECT a, c FROM dr_mid ORDER BY a"));

        engine.execute("CREATE TABLE dr_first (a INT, b VARCHAR, c INT)");
        engine.execute("INSERT INTO dr_first VALUES (1, 'x', 3)");
        engine.execute("ALTER TABLE dr_first DROP COLUMN a");
        assertEquals(list("x|3"), rows("SELECT * FROM dr_first"));

        engine.execute("CREATE TABLE dr_last (a INT, b VARCHAR, c INT)");
        engine.execute("INSERT INTO dr_last VALUES (1, 'x', 3)");
        engine.execute("ALTER TABLE dr_last DROP COLUMN c");
        engine.execute("INSERT INTO dr_last VALUES (2, 'y')");
        assertEquals(list("1|x", "2|y"), rows("SELECT * FROM dr_last ORDER BY a"));

        engine.execute("CREATE TABLE dr_list (a INT, b INT, c INT, d INT)");
        engine.execute("INSERT INTO dr_list VALUES (1, 2, 3, 4)");
        engine.execute("ALTER TABLE dr_list DROP COLUMN b, d");
        assertEquals(list("1|3"), rows("SELECT * FROM dr_list"));
    }

    /** The slot a dropped value vacated is not reused: a column added later reads its own value. */
    @Test
    public void anAddedColumnReadsItsOwnValue() {
        engine.execute("CREATE TABLE dr_add (a INT, b INT, c INT)");
        engine.execute("INSERT INTO dr_add VALUES (1, 2, 3), (4, 5, 6)");
        engine.execute("ALTER TABLE dr_add DROP COLUMN b");
        engine.execute("ALTER TABLE dr_add ADD COLUMN d INT");
        assertEquals(list("1|3|null", "4|6|null"), rows("SELECT * FROM dr_add ORDER BY a"));
        engine.execute("INSERT INTO dr_add VALUES (7, 9, 10)");
        engine.execute("UPDATE dr_add SET c = 99 WHERE a = 1");
        assertEquals(list("1|99|null", "4|6|null", "7|9|10"), rows("SELECT * FROM dr_add ORDER BY a"));
        engine.execute("CREATE TABLE dr_copy (a INT, c INT, d INT)");
        engine.execute("INSERT INTO dr_copy SELECT * FROM dr_add");
        assertEquals(list("1|99|null", "4|6|null", "7|9|10"), rows("SELECT * FROM dr_copy ORDER BY a"));
    }

    /** Identity, default, key and unique columns after the dropped one keep their values and their work. */
    @Test
    public void theColumnsAfterTheDropKeepTheirValuesAndTheirWork() {
        engine.execute("CREATE TABLE dr_keys (x INT, id INT IDENTITY, v VARCHAR DEFAULT 'dflt',"
            + " k INT PRIMARY KEY, u INT UNIQUE)");
        engine.execute("INSERT INTO dr_keys (x, k, u) VALUES (10, 100, 1000), (20, 200, 2000)");
        final List<String> identities = rows("SELECT id FROM dr_keys ORDER BY k");
        engine.execute("ALTER TABLE dr_keys DROP COLUMN x");
        assertEquals("ID|V|K|U", columns("SELECT * FROM dr_keys"));
        assertEquals(identities, rows("SELECT id FROM dr_keys ORDER BY k"), "each row keeps its identity");
        assertEquals(list("dflt|100|1000", "dflt|200|2000"), rows("SELECT v, k, u FROM dr_keys ORDER BY k"));
        // The identity is only promised to grow, not to stay gap-free, so a new row's value is not pinned.
        engine.execute("INSERT INTO dr_keys (k, u) VALUES (300, 3000)");
        assertEquals(list("dflt|100|1000", "dflt|200|2000", "dflt|300|3000"),
            rows("SELECT v, k, u FROM dr_keys ORDER BY k"));
        assertEquals(list("0"), rows("SELECT COUNT(*) FROM dr_keys WHERE id IS NULL"));
        engine.execute("ALTER TABLE dr_keys DROP COLUMN k");
        engine.execute("ALTER TABLE dr_keys DROP COLUMN id");
        engine.execute("INSERT INTO dr_keys (u) VALUES (4000)");
        assertEquals(list("dflt|1000", "dflt|2000", "dflt|3000", "dflt|4000"),
            rows("SELECT * FROM dr_keys ORDER BY u"));
    }

    /**
     * A primary key that spans the dropped column goes with it, whole — the column left behind is no
     * longer a key, but keeps the NOT NULL the key gave it.
     */
    @Test
    public void aCompositePrimaryKeyGoesWholeWithItsColumn() {
        engine.execute("CREATE TABLE dr_pk (a INT, b INT, c INT, PRIMARY KEY (a, b))");
        engine.execute("ALTER TABLE dr_pk DROP COLUMN b");
        assertEquals(0, engine.executeQuery("SHOW PRIMARY KEYS IN TABLE dr_pk").getRowCount());
        assertEquals("N", describeCell("dr_pk", "A", "primary key"));
        assertEquals("N", describeCell("dr_pk", "A", "null?"));
        assertEquals("DML operation to table DR_PK failed on column A with error: NULL result in a"
            + " non-nullable column", refusal("INSERT INTO dr_pk VALUES (NULL, 1)").getMessage());
    }

    /** A DROP COLUMN commits the transaction it finds open, so the rows written before it narrow too. */
    @Test
    public void theDropCommitsTheOpenTransactionFirst() {
        engine.execute("CREATE TABLE dr_txn (a INT, b INT, c INT)");
        engine.execute("BEGIN");
        engine.execute("INSERT INTO dr_txn VALUES (1, 2, 3)");
        engine.execute("ALTER TABLE dr_txn DROP COLUMN b");
        engine.execute("INSERT INTO dr_txn VALUES (4, 6)");
        engine.execute("ROLLBACK");
        assertEquals(list("1|3", "4|6"), rows("SELECT * FROM dr_txn ORDER BY a"));
    }

    /** A stream answers the changes it captured before the drop through the table's current columns. */
    @Test
    public void aStreamReadsItsChangesThroughTheCurrentColumns() {
        engine.execute("CREATE TABLE dr_src (a INT, b INT, c INT)");
        engine.execute("CREATE STREAM dr_s ON TABLE dr_src");
        engine.execute("INSERT INTO dr_src VALUES (1, 2, 3)");
        engine.execute("ALTER TABLE dr_src DROP COLUMN b");
        engine.execute("INSERT INTO dr_src VALUES (4, 6)");
        assertEquals(list("1|3|INSERT", "4|6|INSERT"),
            rows("SELECT a, c, METADATA$ACTION FROM dr_s ORDER BY a"));

        engine.execute("CREATE TABLE dr_up (a INT, b INT, c INT)");
        engine.execute("INSERT INTO dr_up VALUES (1, 2, 3)");
        engine.execute("CREATE STREAM dr_us ON TABLE dr_up");
        engine.execute("UPDATE dr_up SET c = 30 WHERE a = 1");
        engine.execute("ALTER TABLE dr_up DROP COLUMN b");
        assertEquals(list("1|3|DELETE|true", "1|30|INSERT|true"), rows(
            "SELECT a, c, METADATA$ACTION, METADATA$ISUPDATE FROM dr_us ORDER BY METADATA$ACTION"));

        engine.execute("CREATE TABLE dr_vb (a INT, b INT, c INT)");
        engine.execute("CREATE VIEW dr_vv AS SELECT a, c FROM dr_vb");
        engine.execute("CREATE STREAM dr_vs ON VIEW dr_vv");
        engine.execute("INSERT INTO dr_vb VALUES (1, 2, 3)");
        engine.execute("ALTER TABLE dr_vb DROP COLUMN b");
        engine.execute("INSERT INTO dr_vb VALUES (4, 6)");
        assertEquals(list("1|3|INSERT", "4|6|INSERT"),
            rows("SELECT a, c, METADATA$ACTION FROM dr_vs ORDER BY a"));
    }

    /** CREATE OR ALTER TABLE drops a column through the same step, streams included. */
    @Test
    public void createOrAlterDropsThroughTheSameStep() {
        engine.execute("CREATE TABLE dr_coa (a INT, b INT, c INT)");
        engine.execute("CREATE STREAM dr_coas ON TABLE dr_coa");
        engine.execute("INSERT INTO dr_coa VALUES (1, 2, 3)");
        engine.execute("CREATE OR ALTER TABLE dr_coa (a INT, c INT)");
        assertEquals(list("1|3"), rows("SELECT * FROM dr_coa"));
        assertEquals(list("1|3|INSERT"), rows("SELECT a, c, METADATA$ACTION FROM dr_coas"));
    }

    /** Time travel reads the history through the current columns: the dropped one is gone from it too. */
    @Test
    public void timeTravelReadsThroughTheCurrentColumns() {
        engine.execute("CREATE TABLE dr_tt (a INT, b INT, c INT)");
        engine.execute("INSERT INTO dr_tt VALUES (1, 2, 3)");
        engine.execute("SET dr_ts = CURRENT_TIMESTAMP()");
        engine.execute("ALTER TABLE dr_tt DROP COLUMN b");
        assertEquals("A|C", columns("SELECT * FROM dr_tt AT(TIMESTAMP => $dr_ts)"));
        assertEquals(list("1|3"), rows("SELECT * FROM dr_tt AT(TIMESTAMP => $dr_ts)"));
        assertEquals("SQL compilation error: error line 1 at position 7\ninvalid identifier 'B'",
            refusal("SELECT b FROM dr_tt AT(TIMESTAMP => $dr_ts)").getMessage());
    }

    /**
     * The mirror image: a column ADDED later reaches the same three holders. A row from before the add
     * reads the column's default — or NULL — in the table, in its history and in a stream's changes, and
     * a scaled default is stored in the column's own scale everywhere.
     */
    @Test
    public void anAddedColumnReachesHistoryAndStreams() {
        engine.execute("CREATE TABLE dr_ad (a INT, b INT)");
        engine.execute("CREATE STREAM dr_ads ON TABLE dr_ad");
        engine.execute("INSERT INTO dr_ad VALUES (1, 2)");
        engine.execute("SET dr_adts = CURRENT_TIMESTAMP()");
        engine.execute("ALTER TABLE dr_ad ADD COLUMN d INT DEFAULT 7");
        assertEquals(list("1|2|7"), rows("SELECT * FROM dr_ad"));
        assertEquals(list("1|2|7"), rows("SELECT * FROM dr_ad AT(TIMESTAMP => $dr_adts)"));
        assertEquals(list("1|2|7|INSERT"), rows("SELECT a, b, d, METADATA$ACTION FROM dr_ads"));
        engine.execute("ALTER TABLE dr_ad ADD COLUMN f VARCHAR");
        assertEquals(list("1|2|7|null|INSERT"), rows("SELECT a, b, d, f, METADATA$ACTION FROM dr_ads"));

        engine.execute("CREATE TABLE dr_adn (a NUMBER(10,2))");
        engine.execute("CREATE STREAM dr_adns ON TABLE dr_adn");
        engine.execute("INSERT INTO dr_adn VALUES (2.5)");
        engine.execute("ALTER TABLE dr_adn ADD COLUMN e NUMBER(10,2) DEFAULT 3");
        assertEquals(list("2.50|3.00"), rows("SELECT TO_VARCHAR(a), TO_VARCHAR(e) FROM dr_adn"));
        assertEquals(list("2.50|3.00|INSERT"),
            rows("SELECT TO_VARCHAR(a), TO_VARCHAR(e), METADATA$ACTION FROM dr_adns"));
    }

    /** CREATE OR ALTER TABLE adds a column the same way: its default, not NULL, fills the rows there. */
    @Test
    public void createOrAlterAddsThroughTheSameStep() {
        engine.execute("CREATE TABLE dr_coad (a INT)");
        engine.execute("CREATE STREAM dr_coads ON TABLE dr_coad");
        engine.execute("INSERT INTO dr_coad VALUES (1)");
        engine.execute("SET dr_coats = CURRENT_TIMESTAMP()");
        engine.execute("CREATE OR ALTER TABLE dr_coad (a INT, d INT DEFAULT 7)");
        assertEquals(list("1|7"), rows("SELECT * FROM dr_coad"));
        assertEquals(list("1|7|INSERT"), rows("SELECT a, d, METADATA$ACTION FROM dr_coads"));
        assertEquals(list("1|7"), rows("SELECT * FROM dr_coad AT(TIMESTAMP => $dr_coats)"));
    }

    /** A clone taken after the drop copies the narrowed rows; one taken before keeps the old width. */
    @Test
    public void aCloneCopiesTheRowsAsTheyAre() {
        engine.execute("CREATE TABLE dr_cl (a INT, b INT, c INT)");
        engine.execute("INSERT INTO dr_cl VALUES (1, 2, 3)");
        engine.execute("CREATE TABLE dr_cl_before CLONE dr_cl");
        engine.execute("ALTER TABLE dr_cl DROP COLUMN b");
        engine.execute("CREATE TABLE dr_cl_after CLONE dr_cl");
        assertEquals(list("1|2|3"), rows("SELECT * FROM dr_cl_before"));
        assertEquals(list("1|3"), rows("SELECT * FROM dr_cl_after"));
    }

    /** A temporary table narrows its own rows and leaves the permanent table it hides as it was. */
    @Test
    public void aTemporaryTableNarrowsOnlyItself() {
        engine.execute("CREATE TABLE dr_sh (a INT, b INT, c INT)");
        engine.execute("INSERT INTO dr_sh VALUES (1, 2, 3)");
        engine.execute("CREATE TEMPORARY TABLE dr_sh (a INT, b INT, c INT)");
        engine.execute("INSERT INTO dr_sh VALUES (10, 20, 30)");
        engine.execute("ALTER TABLE dr_sh DROP COLUMN b");
        assertEquals(list("10|30"), rows("SELECT * FROM dr_sh"));
        engine.execute("DROP TABLE dr_sh");
        assertEquals(list("1|2|3"), rows("SELECT * FROM dr_sh"));
    }

    /** No column may be left: one at a time, several at once, and under either IF EXISTS. */
    @Test
    public void theLastColumnCannotBeDropped() {
        engine.execute("CREATE TABLE dr_one (x INT)");
        assertEquals(ALL_COLUMNS, refusal("ALTER TABLE dr_one DROP COLUMN x").getMessage());
        assertEquals(ALL_COLUMNS, refusal("ALTER TABLE dr_one DROP x").getMessage());
        assertEquals(ALL_COLUMNS, refusal("ALTER TABLE dr_one DROP COLUMN IF EXISTS x").getMessage());
        engine.execute("ALTER TABLE dr_one DROP COLUMN IF EXISTS nosuch");
        assertEquals("X", columns("SELECT * FROM dr_one"));

        engine.execute("CREATE TABLE dr_two (x INT, y INT)");
        engine.execute("ALTER TABLE dr_two DROP COLUMN x");
        assertEquals(ALL_COLUMNS, refusal("ALTER TABLE dr_two DROP COLUMN y").getMessage());

        engine.execute("CREATE TABLE dr_three (x INT, y INT, z INT)");
        engine.execute("INSERT INTO dr_three VALUES (1, 2, 3)");
        assertEquals(ALL_COLUMNS, refusal("ALTER TABLE dr_three DROP COLUMN x, y, z").getMessage());
        assertEquals(ALL_COLUMNS, refusal("ALTER TABLE dr_three DROP COLUMN z, y, x").getMessage());
        assertEquals(ALL_COLUMNS, refusal("ALTER TABLE IF EXISTS dr_three DROP COLUMN x, y, z").getMessage());
        assertEquals(list("1|2|3"), rows("SELECT * FROM dr_three"));
    }

    /**
     * The whole list is judged before anything goes: a missing name drops nothing, in written order and
     * ahead of the no-column check; a name given twice goes once.
     */
    @Test
    public void theListIsJudgedWholeBeforeAnythingGoes() {
        engine.execute("CREATE TABLE dr_atom (x INT, y INT, z INT)");
        engine.execute("INSERT INTO dr_atom VALUES (1, 2, 3)");
        assertEquals("SQL compilation error:\ncolumn 'NOSUCH' does not exist",
            refusal("ALTER TABLE dr_atom DROP COLUMN x, nosuch").getMessage());
        assertEquals(list("1|2|3"), rows("SELECT * FROM dr_atom"));
        assertEquals("SQL compilation error:\ncolumn 'NOSUCH' does not exist",
            refusal("ALTER TABLE dr_atom DROP COLUMN nosuch, x, y, z").getMessage());
        assertEquals("SQL compilation error:\ncolumn 'NOSUCH1' does not exist",
            refusal("ALTER TABLE dr_atom DROP COLUMN nosuch1, nosuch2").getMessage());
        engine.execute("ALTER TABLE dr_atom DROP COLUMN x, x");
        assertEquals(list("2|3"), rows("SELECT * FROM dr_atom"));
    }

    /**
     * Each name carries its own IF EXISTS: the one after DROP covers the first name only, and a later
     * name may be written with its own COLUMN and IF EXISTS.
     */
    @Test
    public void eachNameCarriesItsOwnIfExists() {
        engine.execute("CREATE TABLE dr_ife (x INT, y INT, z INT)");
        engine.execute("ALTER TABLE dr_ife DROP COLUMN IF EXISTS nosuch, y");
        assertEquals("X|Z", columns("SELECT * FROM dr_ife"));
        assertEquals("SQL compilation error:\ncolumn 'NOSUCH' does not exist",
            refusal("ALTER TABLE dr_ife DROP COLUMN IF EXISTS x, nosuch").getMessage());
        assertEquals("SQL compilation error:\ncolumn 'Q' does not exist",
            refusal("ALTER TABLE dr_ife DROP COLUMN q, IF EXISTS nosuch").getMessage());
        engine.execute("ALTER TABLE dr_ife DROP COLUMN IF EXISTS nosuch, COLUMN IF EXISTS nosuch2");
        assertEquals("X|Z", columns("SELECT * FROM dr_ife"));

        engine.execute("CREATE TABLE dr_items (a INT, b INT, c INT, d INT)");
        engine.execute("ALTER TABLE dr_items DROP COLUMN a, COLUMN b");
        engine.execute("ALTER TABLE dr_items DROP c, IF EXISTS nosuch");
        assertEquals("D", columns("SELECT * FROM dr_items"));
        assertEquals(ALL_COLUMNS,
            refusal("ALTER TABLE dr_items DROP COLUMN IF EXISTS nosuch, COLUMN d").getMessage());
    }
}
