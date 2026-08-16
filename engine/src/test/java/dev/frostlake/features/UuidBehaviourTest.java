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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A UUID is its own type, not a VARCHAR that happens to hold one. A comparison READS the other side as
 * a UUID, so letter case does not matter and text that is no UUID is refused; a conditional over a UUID
 * and a text folds to UUID; the ordering aggregates refuse it outright; a cast to VARCHAR is not held to
 * the cast's width; and a cast to BINARY is refused at compile time. Live-verified.
 */
public class UuidBehaviourTest extends BaseDatabaseTest {

    /** The canonical text, and the same UUID written in upper case. */
    private static final String UPPER = "1B4E28BA-2FA1-11D2-883F-0016D3CCA427";
    private static final String LOWER = "1b4e28ba-2fa1-11d2-883f-0016d3cca427";

    private void createUuidRow() {
        engine.execute("CREATE OR REPLACE TABLE uuid_row (u UUID, v VARCHAR(40))");
        engine.execute("INSERT INTO uuid_row VALUES ('" + UPPER + "', '" + UPPER + "')");
    }

    /** The one cell of a single-row query over the table, as text. */
    private String scalar(final String select) {
        final ResultSet rs = engine.executeQuery("SELECT " + select + " FROM uuid_row");
        assertEquals(1, rs.getRowCount(), select);
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? "NULL" : value.toString().toUpperCase();
    }

    /** Asserts a statement is refused with a message carrying {@code fragment}. */
    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        assertTrue(refused.getMessage() != null && refused.getMessage().contains(fragment),
            sql + " should be refused with \"" + fragment + "\" but read: " + refused.getMessage());
    }

    /** A comparison reads the other side as a UUID: the case it was written in does not matter. */
    @Test
    public void aComparisonReadsTheOtherSideAsAUuid() {
        createUuidRow();
        assertEquals("TRUE", scalar("u = '" + UPPER + "'"));
        assertEquals("TRUE", scalar("u = '" + LOWER + "'"));
        assertEquals("TRUE", scalar("u = v"));
        assertEquals("TRUE", scalar("u IN ('" + UPPER + "')"));
        assertEquals("1", scalar("COUNT(*)").equals("1") ? "1" : "0");
        final ResultSet matched = engine.executeQuery(
            "SELECT COUNT(*) FROM uuid_row WHERE u = '" + UPPER + "'");
        assertEquals("1", matched.getRows().get(0).getValue(0).toString());
    }

    /** Text that is no UUID is refused where the comparison reads it. */
    @Test
    public void textThatIsNoUuidIsRefused() {
        createUuidRow();
        assertRefused("SELECT COUNT(*) FROM uuid_row WHERE u < 'z'",
            "UUID 'z' is invalid, expected format is");
        assertRefused("SELECT u > 'a' FROM uuid_row", "UUID 'a' is invalid, expected format is");
    }

    /** A conditional over a UUID and a text folds to UUID. */
    @Test
    public void aConditionalFoldsToUuid() {
        createUuidRow();
        assertEquals("UUID[SB16]", scalar("SYSTEM$TYPEOF(u)"));
        assertEquals("UUID[SB16]", scalar("SYSTEM$TYPEOF(COALESCE(u, v))"));
        assertEquals("UUID[SB16]", scalar("SYSTEM$TYPEOF(NVL(u, v))"));
        assertEquals("UUID[SB16]", scalar("SYSTEM$TYPEOF(CASE WHEN TRUE THEN u ELSE v END)"));
        assertEquals("UUID[SB16]", scalar("SYSTEM$TYPEOF(GREATEST(u, v))"));
    }

    /** The ordering aggregates refuse a UUID; the counting and collecting ones take it. */
    @Test
    public void theOrderingAggregatesRefuseAUuid() {
        createUuidRow();
        assertRefused("SELECT MAX(u) FROM uuid_row", "Function MAX does not support UUID argument type");
        assertRefused("SELECT MIN(u) FROM uuid_row", "Function MIN does not support UUID argument type");
        assertRefused("SELECT MODE(u) FROM uuid_row", "Function MODE does not support UUID argument type");
        assertEquals("1", scalar("COUNT(u)"));
        assertEquals("1", scalar("COUNT(DISTINCT u)"));
        assertEquals(LOWER.toUpperCase(), scalar("ANY_VALUE(u)"));
        assertEquals(LOWER.toUpperCase(), scalar("LISTAGG(u, ',')"));
    }

    /** A cast to VARCHAR keeps the whole text; a cast to BINARY is refused. */
    @Test
    public void theCastsFollowTheUuidRules() {
        createUuidRow();
        assertEquals(LOWER.toUpperCase(), scalar("CAST(u AS VARCHAR(10))"));
        assertRefused("SELECT u::BINARY FROM uuid_row", "for parameter 'TO_BINARY'");
        assertEquals("36", scalar("LENGTH(u)"));
        assertEquals(UPPER, scalar("UPPER(u)"));
    }
}
