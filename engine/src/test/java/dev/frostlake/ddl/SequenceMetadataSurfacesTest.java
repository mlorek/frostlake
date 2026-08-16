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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A sequence's three metadata surfaces agree with each other and with the account: SHOW SEQUENCES,
 * DESCRIBE SEQUENCE and INFORMATION_SCHEMA.SEQUENCES all carry the ORDER flag (Y/N on the first two,
 * YES/NO on the view — the spelling split every other object keeps), the creation instant and the
 * comment. Live-verified:
 *
 * <ul>
 *   <li>DESCRIBE SEQUENCE prints the same ten cells as SHOW SEQUENCES for the one sequence — the
 *       creation instant filled, an unset comment the EMPTY STRING, owner_role_type ROLE;</li>
 *   <li>INFORMATION_SCHEMA.SEQUENCES spells the flag YES/NO, fills CREATED and LAST_ALTERED (equal for
 *       a sequence never altered), keeps an unset COMMENT NULL, and types the five bounds — START_VALUE,
 *       MINIMUM_VALUE, MAXIMUM_VALUE, NEXT_VALUE, INCREMENT — as TEXT;</li>
 *   <li>every {@code created_on} is a TIMESTAMP_LTZ at the TRUE instant, to the millisecond: read back
 *       through RESULT_SCAN it renders at the session zone with its offset ({@code Z} under UTC,
 *       {@code +0900} under Asia/Tokyo), and its age against CURRENT_TIMESTAMP is real. SHOW TABLES,
 *       VIEWS, STAGES, STREAMS (stale_after too) and SCHEMAS render the same way.</li>
 * </ul>
 *
 * <p>Frostlake used to drop the flag on the view, leave DESCRIBE's instant and comment NULL, and stamp
 * every SHOW with a host-local wall clock that printed no offset.
 *
 * <p>NOT COVERED: the owner cell (the account's role versus SYSADMIN), and the declared TYPE of a
 * RESULT_SCAN or INFORMATION_SCHEMA column, which Frostlake does not carry.
 */
public class SequenceMetadataSurfacesTest extends BaseDatabaseTest {

    private static final String RENDERED_LTZ = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} (Z|[+-]\\d{4})";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE SEQUENCE seq_ord START WITH 1 INCREMENT BY 1 ORDER");
        engine.execute("CREATE OR REPLACE SEQUENCE seq_noord START WITH 5 INCREMENT BY 2 NOORDER COMMENT = 'c1'");
    }

    private Row firstRow(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertTrue(rs.getRowCount() >= 1, "no rows from " + sql);
        return rs.getRows().get(0);
    }

    private Object cell(final String sql, final String column) {
        final ResultSet rs = engine.executeQuery(sql);
        assertTrue(rs.getRowCount() >= 1, "no rows from " + sql);
        return rs.getRows().get(0).getValue(rs.getColumnIndex(column));
    }

    /** The rendered {@code created_on} of the LAST listing, read back through RESULT_SCAN. */
    private String renderedCreatedOn() {
        return String.valueOf(cell("SELECT TO_VARCHAR(\"created_on\") AS t FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "t"));
    }

    private long ageInSeconds() {
        return ((Number) cell("SELECT TIMESTAMPDIFF(SECOND, \"created_on\", CURRENT_TIMESTAMP()) AS d"
            + " FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "d")).longValue();
    }

    /** ★ The view spells the flag YES/NO, and keeps it. */
    @Test
    public void theViewCarriesTheOrderFlag() {
        final ResultSet rs = engine.executeQuery("SELECT sequence_name, ordered, comment, cycle_option"
            + " FROM information_schema.sequences WHERE sequence_name IN ('SEQ_ORD', 'SEQ_NOORD')"
            + " ORDER BY sequence_name");
        assertEquals(2, rs.getRowCount());
        assertEquals("SEQ_NOORD", rs.getRows().get(0).getValue(0));
        assertEquals("NO", rs.getRows().get(0).getValue(1));
        assertEquals("c1", rs.getRows().get(0).getValue(2));
        assertEquals("NO", rs.getRows().get(0).getValue(3));
        assertEquals("SEQ_ORD", rs.getRows().get(1).getValue(0));
        assertEquals("YES", rs.getRows().get(1).getValue(1));
        assertEquals(null, rs.getRows().get(1).getValue(2), "an unset comment is NULL on the view");
        assertEquals("Y", cell("SHOW SEQUENCES LIKE 'SEQ_ORD'", "ordered"));
        assertEquals("N", cell("SHOW SEQUENCES LIKE 'SEQ_NOORD'", "ordered"));
        assertEquals("Y", cell("DESCRIBE SEQUENCE seq_ord", "ordered"));
        assertEquals("N", cell("DESCRIBE SEQUENCE seq_noord", "ordered"));
    }

    /** ★ The view's bounds are text, and its two instants are filled and equal. */
    @Test
    public void theViewTypesTheBoundsAsTextAndFillsItsInstants() {
        final Row row = firstRow("SELECT start_value, minimum_value, maximum_value, next_value, \"INCREMENT\","
            + " numeric_precision, numeric_precision_radix, numeric_scale, data_type"
            + " FROM information_schema.sequences WHERE sequence_name = 'SEQ_NOORD'");
        assertEquals("5", row.getValue(0));
        assertEquals("-9223372036854775808", row.getValue(1));
        assertEquals("9223372036854775807", row.getValue(2));
        assertEquals("5", row.getValue(3));
        assertEquals("2", row.getValue(4));
        assertEquals(38L, ((Number) row.getValue(5)).longValue(), "the NUMERIC_* cells stay numbers");
        assertEquals(10L, ((Number) row.getValue(6)).longValue());
        assertEquals(0L, ((Number) row.getValue(7)).longValue());
        assertEquals("NUMBER", row.getValue(8));
        final Row instants = firstRow("SELECT TO_VARCHAR(created) AS c, TO_VARCHAR(last_altered) AS l,"
            + " TIMESTAMPDIFF(SECOND, created, CURRENT_TIMESTAMP()) AS age"
            + " FROM information_schema.sequences WHERE sequence_name = 'SEQ_ORD'");
        assertNotNull(instants.getValue(0));
        assertTrue(String.valueOf(instants.getValue(0)).matches(RENDERED_LTZ), String.valueOf(instants.getValue(0)));
        assertEquals(instants.getValue(0), instants.getValue(1), "never altered: LAST_ALTERED is CREATED");
        final long age = ((Number) instants.getValue(2)).longValue();
        assertTrue(age >= 0 && age <= 3600, "a real instant: " + age + "s old");
    }

    /** ★ DESCRIBE prints SHOW's ten cells, the instant and the empty-string comment included. */
    @Test
    public void describeCarriesTheInstantAndTheComment() {
        final ResultSet describe = engine.executeQuery("DESCRIBE SEQUENCE seq_ord");
        assertEquals(1, describe.getRowCount());
        final Row row = describe.getRows().get(0);
        assertEquals("SEQ_ORD", row.getValue(describe.getColumnIndex("name")));
        assertEquals("", row.getValue(describe.getColumnIndex("comment")), "an unset comment is the empty string");
        assertEquals("ROLE", row.getValue(describe.getColumnIndex("owner_role_type")));
        assertNotNull(row.getValue(describe.getColumnIndex("created_on")));
        assertTrue(renderedCreatedOn().matches(RENDERED_LTZ));
        engine.executeQuery("DESCRIBE SEQUENCE seq_ord");
        final long age = ageInSeconds();
        assertTrue(age >= 0 && age <= 3600, "a real instant: " + age + "s old");
        assertEquals("c1", cell("DESCRIBE SEQUENCE seq_noord", "comment"));
        final ResultSet show = engine.executeQuery("SHOW SEQUENCES LIKE 'SEQ_ORD'");
        assertEquals("", show.getRows().get(0).getValue(show.getColumnIndex("comment")));
        assertEquals(String.valueOf(row.getValue(describe.getColumnIndex("created_on"))),
            String.valueOf(show.getRows().get(0).getValue(show.getColumnIndex("created_on"))),
            "DESCRIBE and SHOW carry the one instant");
    }

    /** ★ Every listing's created_on renders at the session zone with its offset, and ages for real. */
    @Test
    public void everyListingRendersItsInstantAtTheSessionZone() {
        engine.execute("CREATE OR REPLACE TABLE pt (a INT)");
        engine.execute("CREATE OR REPLACE VIEW pv AS SELECT a FROM pt");
        engine.execute("CREATE OR REPLACE STAGE pstg");
        engine.execute("CREATE OR REPLACE STREAM pstrm ON TABLE pt");
        for (final String listing : new String[] {"SHOW SEQUENCES LIKE 'SEQ_ORD'", "SHOW TABLES LIKE 'PT'",
                "SHOW VIEWS LIKE 'PV'", "SHOW STAGES LIKE 'PSTG'", "SHOW STREAMS LIKE 'PSTRM'",
                "SHOW SCHEMAS LIKE 'TEST_SCHEMA'"}) {
            engine.executeQuery(listing);
            final String rendered = renderedCreatedOn();
            assertTrue(rendered.matches(RENDERED_LTZ), listing + ": " + rendered);
            engine.executeQuery(listing);
            final long age = ageInSeconds();
            assertTrue(age >= 0 && age <= 3600, listing + ": " + age + "s old");
        }
        engine.executeQuery("SHOW STREAMS LIKE 'PSTRM'");
        final String staleAfter = String.valueOf(cell(
            "SELECT TO_VARCHAR(\"stale_after\") AS t FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "t"));
        assertTrue(staleAfter.matches(RENDERED_LTZ), staleAfter);
        try {
            engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
            engine.executeQuery("SHOW SEQUENCES LIKE 'SEQ_ORD'");
            assertTrue(renderedCreatedOn().endsWith(" Z"), "a zero offset is spelled Z");
            engine.executeQuery("DESCRIBE SEQUENCE seq_ord");
            assertTrue(renderedCreatedOn().endsWith(" Z"));
            engine.execute("ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'");
            engine.executeQuery("SHOW SEQUENCES LIKE 'SEQ_ORD'");
            assertTrue(renderedCreatedOn().endsWith(" +0900"), "the same instant, nine hours on");
            assertTrue(String.valueOf(cell("SELECT TO_VARCHAR(created) AS c FROM information_schema.sequences"
                + " WHERE sequence_name = 'SEQ_ORD'", "c")).endsWith(" +0900"));
        } finally {
            engine.execute("ALTER SESSION UNSET TIMEZONE");
        }
    }
}
