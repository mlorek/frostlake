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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code CHANGES … AT (STREAM => '<name>')} starts the change window at a stream's own offset, so it
 * answers exactly what that stream reports — the same rows, the same metadata columns — without
 * consuming it. Live-verified, including that a stream over some other table contributes nothing
 * rather than erroring.
 */
public class ChangesAtStreamTest extends BaseDatabaseTest {

    @BeforeEach
    public void seedChanges() {
        engine.execute("CREATE TABLE ct (c1 INTEGER, c2 VARCHAR)");
        engine.execute("INSERT INTO ct VALUES (1, 'a'), (2, 'b')");
        engine.execute("CREATE STREAM s1 ON TABLE ct");
        engine.execute("INSERT INTO ct VALUES (3, 'c')");
        engine.execute("UPDATE ct SET c2 = 'B' WHERE c1 = 2");
        engine.execute("DELETE FROM ct WHERE c1 = 1");
    }

    /** Each row rendered as "c1|action|isupdate", sorted, so a set of changes compares cleanly. */
    private List<String> changeSet(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final int c1 = rs.getColumnIndex("C1");
        final int action = rs.getColumnIndex("METADATA$ACTION");
        final int isUpdate = rs.getColumnIndex("METADATA$ISUPDATE");
        final List<String> rows = new ArrayList<>();
        for (int i = 0; i < rs.getRowCount(); i++) {
            rows.add(rs.getRows().get(i).getValue(c1)
                + "|" + rs.getRows().get(i).getValue(action)
                + "|" + rs.getRows().get(i).getValue(isUpdate));
        }
        rows.sort(null);
        return rows;
    }

    /** One column's values, sorted, so the image a change carries can be compared on its own. */
    private List<String> valuesOf(final String sql, final String column) {
        final ResultSet rs = engine.executeQuery(sql);
        final int index = rs.getColumnIndex(column);
        final List<String> values = new ArrayList<>();
        for (int i = 0; i < rs.getRowCount(); i++) {
            values.add(String.valueOf(rs.getRows().get(i).getValue(index)));
        }
        values.sort(null);
        return values;
    }

    /** The full delta, matching what the stream itself reports. */
    @Test
    public void defaultInformationReturnsTheWholeDelta() {
        final List<String> viaChanges = changeSet(
            "SELECT * FROM ct CHANGES (INFORMATION => DEFAULT) AT (STREAM => 's1')");
        assertEquals(List.of("1|DELETE|false", "2|DELETE|true", "2|INSERT|true", "3|INSERT|false"),
            viaChanges);
        assertEquals(changeSet("SELECT * FROM s1"), viaChanges,
            "CHANGES AT (STREAM => …) must agree with the stream itself");
    }

    /**
     * APPEND_ONLY reports the rows APPENDED in the window — not the net delta filtered to its inserts.
     * Row 2 already existed and was merely updated, so its update's insert half is not an append and
     * does not appear, even though the default delta shows it as {@code 2|INSERT|true}.
     */
    @Test
    public void appendOnlyKeepsOnlyTheRowsThatWereAppended() {
        assertEquals(List.of("3|INSERT|false"),
            changeSet("SELECT * FROM ct CHANGES (INFORMATION => APPEND_ONLY) AT (STREAM => 's1')"));
    }

    /**
     * The three ways an append differs from a net-delta insert, all live-verified, on one history:
     * 11 is appended then updated, 22 is appended then deleted, 33 is only appended, and 51 already
     * existed and is updated.
     *
     * <pre>
     * DEFAULT      11|INSERT(ELEVEN)  33|INSERT(thirtythree)  51|DELETE(fifty-one)  51|INSERT(FIFTY-ONE)
     * APPEND_ONLY  11|INSERT(eleven)  22|INSERT(twentytwo)    33|INSERT(thirtythree)
     * </pre>
     *
     * <p>So: an appended-then-updated row keeps the value it was appended WITH (eleven, not ELEVEN); an
     * appended-then-deleted row is still an append (22 survives, though the delta cancels it); and a
     * row that merely existed contributes nothing however it is rewritten (51 is absent).
     */
    @Test
    public void anAppendIsNotTheInsertHalfOfTheDelta() {
        engine.execute("CREATE TABLE ct2 (c1 INTEGER, c2 VARCHAR)");
        engine.execute("INSERT INTO ct2 VALUES (51, 'fifty-one')");
        engine.execute("CREATE STREAM s2 ON TABLE ct2");
        engine.execute("INSERT INTO ct2 VALUES (11, 'eleven'), (22, 'twentytwo'), (33, 'thirtythree')");
        engine.execute("UPDATE ct2 SET c2 = 'ELEVEN' WHERE c1 = 11");
        engine.execute("DELETE FROM ct2 WHERE c1 = 22");
        engine.execute("UPDATE ct2 SET c2 = 'FIFTY-ONE' WHERE c1 = 51");

        assertEquals(List.of("11|INSERT|false", "33|INSERT|false", "51|DELETE|true", "51|INSERT|true"),
            changeSet("SELECT * FROM ct2 CHANGES (INFORMATION => DEFAULT) AT (STREAM => 's2')"));
        assertEquals(List.of("11|INSERT|false", "22|INSERT|false", "33|INSERT|false"),
            changeSet("SELECT * FROM ct2 CHANGES (INFORMATION => APPEND_ONLY) AT (STREAM => 's2')"));
        assertEquals(List.of("eleven", "thirtythree", "twentytwo"), valuesOf(
            "SELECT * FROM ct2 CHANGES (INFORMATION => APPEND_ONLY) AT (STREAM => 's2')", "C2"));
    }

    /** An APPEND_ONLY stream answers exactly what CHANGES(APPEND_ONLY) does over the same history. */
    @Test
    public void anAppendOnlyStreamAgreesWithAppendOnlyChanges() {
        engine.execute("CREATE TABLE ct3 (c1 INTEGER, c2 VARCHAR)");
        engine.execute("INSERT INTO ct3 VALUES (1, 'a')");
        engine.execute("CREATE STREAM s3 ON TABLE ct3");
        engine.execute("CREATE STREAM s3ao ON TABLE ct3 APPEND_ONLY = TRUE");
        engine.execute("INSERT INTO ct3 VALUES (10, 'ten'), (20, 'twenty')");
        engine.execute("UPDATE ct3 SET c2 = 'TEN' WHERE c1 = 10");
        engine.execute("DELETE FROM ct3 WHERE c1 = 20");
        engine.execute("INSERT INTO ct3 VALUES (10, 'ten again')");
        engine.execute("UPDATE ct3 SET c2 = 'A' WHERE c1 = 1");

        final List<String> viaChanges =
            changeSet("SELECT * FROM ct3 CHANGES (INFORMATION => APPEND_ONLY) AT (STREAM => 's3')");
        assertEquals(List.of("10|INSERT|false", "10|INSERT|false", "20|INSERT|false"), viaChanges);
        assertEquals(changeSet("SELECT * FROM s3ao"), viaChanges);
        assertEquals(List.of("ten", "ten again", "twenty"),
            valuesOf("SELECT * FROM s3ao", "C2"));
    }

    /** Reading through CHANGES does not consume the stream. */
    @Test
    public void readingDoesNotAdvanceTheStream() {
        engine.executeQuery("SELECT * FROM ct CHANGES (INFORMATION => DEFAULT) AT (STREAM => 's1')");
        assertEquals(4, engine.executeQuery("SELECT * FROM s1").getRowCount(),
            "the stream still reports its whole delta afterwards");
    }

    /** A stream over a different table contributes nothing — no rows, and no error. */
    @Test
    public void aStreamOverAnotherTableContributesNothing() {
        engine.execute("CREATE TABLE other (x INTEGER)");
        engine.execute("CREATE STREAM s_other ON TABLE other");
        engine.execute("INSERT INTO other VALUES (9)");
        assertEquals(0, engine.executeQuery(
            "SELECT * FROM ct CHANGES (INFORMATION => DEFAULT) AT (STREAM => 's_other')").getRowCount());
    }

    /** An unknown stream names itself in the error exactly as it was written. */
    @Test
    public void anUnknownStreamIsReportedByName() {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(
                    "SELECT * FROM ct CHANGES (INFORMATION => DEFAULT) AT (STREAM => 'nosuch')");
            }
        });
        assertTrue(e.getMessage().contains("Stream 'nosuch' not found."), "got: " + e.getMessage());
    }

    /** A fully-qualified stream name resolves the same way. */
    @Test
    public void aQualifiedStreamNameResolves() {
        assertEquals(4, engine.executeQuery("SELECT * FROM ct CHANGES (INFORMATION => DEFAULT)"
            + " AT (STREAM => 'test_db.test_schema.s1')").getRowCount());
    }
}
