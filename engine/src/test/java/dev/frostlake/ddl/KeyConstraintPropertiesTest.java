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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The properties a PRIMARY KEY, UNIQUE or FOREIGN KEY constraint takes. The engine read RELY and NORELY alone and
 * refused every other property as a syntax error.
 *
 * <p>On the account a constraint takes, in any order and repeated, ENFORCED or NOT ENFORCED, DEFERRABLE or NOT
 * DEFERRABLE, INITIALLY DEFERRED or IMMEDIATE, ENABLE or DISABLE with VALIDATE or NOVALIDATE after either, and RELY
 * or NORELY, alike for the three kinds, in a column definition and at table level. Two that contradict each other
 * are refused, and a constraint the account cannot keep as written — DEFERRABLE, INITIALLY IMMEDIATE, ENABLE or
 * VALIDATE — is left out without a word while the statement succeeds. Every cell is live-verified.
 */
public class KeyConstraintPropertiesTest extends BaseDatabaseTest {

    private static final String CONFLICT = "SQL compilation error:|Conflicting constraint properties";
    private static final String NOT_DEFERRABLE = "SQL compilation error:|Constraint is not deferrable";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE p1 (id INT PRIMARY KEY)");
    }

    /** The statement's refusal on one line, or "ok". */
    private String outcome(final String sql) {
        try {
            engine.execute(sql);
            return "ok";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The table's constraints as {@code TYPE rely=YES|NO}, in order of type. */
    private List<String> kept(final String table) {
        final List<String> out = new ArrayList<>();
        for (final Row row : engine.executeQuery("SELECT constraint_type, rely, enforced, is_deferrable,"
                + " initially_deferred FROM information_schema.table_constraints WHERE table_name = '" + table
                + "' AND table_schema = 'TEST_SCHEMA' ORDER BY 1").getRows()) {
            assertEquals("NO", String.valueOf(row.getValue(2)), "no constraint is enforced");
            assertEquals("NO", String.valueOf(row.getValue(3)));
            assertEquals("YES", String.valueOf(row.getValue(4)));
            out.add(row.getValue(0) + " rely=" + row.getValue(1));
        }
        return out;
    }

    private static List<String> rows(final String... rows) {
        final List<String> out = new ArrayList<>();
        for (final String row : rows) {
            out.add(row);
        }
        return out;
    }

    private static String syntax(final int position, final String token) {
        return "SQL compilation error:|syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void aKeptConstraintTakesTheseProperties() {
        final String[] kept = {"", "NOT ENFORCED", "ENFORCED", "NOT DEFERRABLE", "INITIALLY DEFERRED", "DISABLE",
            "NORELY", "DISABLE NOVALIDATE", "NOT ENFORCED NOT ENFORCED", "ENFORCED NOT ENFORCED", "DISABLE DISABLE",
            "NORELY NORELY"};
        for (final String properties : kept) {
            assertEquals("ok", outcome("CREATE OR REPLACE TABLE k (a INT, PRIMARY KEY (a) " + properties + ")"),
                properties);
            assertEquals(rows("PRIMARY KEY rely=NO"), kept("K"), properties);
        }
        final String[] relied = {"RELY", "NOT ENFORCED RELY", "RELY NOT ENFORCED", "RELY RELY", "ENFORCED RELY",
            "NOT ENFORCED DISABLE NOVALIDATE RELY"};
        for (final String properties : relied) {
            assertEquals("ok", outcome("CREATE OR REPLACE TABLE k (a INT, PRIMARY KEY (a) " + properties + ")"),
                properties);
            assertEquals(rows("PRIMARY KEY rely=YES"), kept("K"), properties);
        }
    }

    @Test
    public void aConstraintTheAccountCannotKeepIsLeftOut() {
        final String[] leftOut = {"DEFERRABLE", "INITIALLY IMMEDIATE", "ENABLE", "ENABLE NOVALIDATE", "ENABLE VALIDATE",
            "DISABLE VALIDATE", "ENABLE ENABLE", "DEFERRABLE INITIALLY DEFERRED", "DEFERRABLE INITIALLY IMMEDIATE",
            "NOT DEFERRABLE INITIALLY IMMEDIATE", "INITIALLY IMMEDIATE NOT DEFERRABLE",
            "NOT ENFORCED NOT DEFERRABLE INITIALLY IMMEDIATE"};
        for (final String properties : leftOut) {
            assertEquals("ok", outcome("CREATE OR REPLACE TABLE k (a INT, PRIMARY KEY (a) " + properties + ")"),
                properties);
            assertEquals(rows(), kept("K"), properties);
        }
    }

    @Test
    public void contradictingPropertiesAreRefused() {
        for (final String properties : new String[] {"RELY NORELY", "NORELY RELY", "ENABLE DISABLE", "DISABLE ENABLE",
            "DEFERRABLE NOT DEFERRABLE", "NOT DEFERRABLE DEFERRABLE", "INITIALLY DEFERRED INITIALLY IMMEDIATE",
            "INITIALLY IMMEDIATE INITIALLY DEFERRED", "ENABLE DISABLE NOVALIDATE", "DISABLE NOVALIDATE ENABLE VALIDATE",
            "DEFERRABLE INITIALLY DEFERRED NOT DEFERRABLE"}) {
            assertEquals(CONFLICT, outcome("CREATE OR REPLACE TABLE k (a INT, PRIMARY KEY (a) " + properties + ")"),
                properties);
        }
        assertEquals(NOT_DEFERRABLE,
            outcome("CREATE OR REPLACE TABLE k (a INT, PRIMARY KEY (a) NOT DEFERRABLE INITIALLY DEFERRED)"));
        assertEquals(NOT_DEFERRABLE,
            outcome("CREATE OR REPLACE TABLE k (a INT, PRIMARY KEY (a) INITIALLY DEFERRED NOT DEFERRABLE)"));
    }

    @Test
    public void theWordsAreReadWhereTheyStand() {
        assertEquals(syntax(50, "VALIDATE"), outcome("CREATE OR REPLACE TABLE k (a INT, PRIMARY KEY (a) VALIDATE)"));
        assertEquals(syntax(50, "NOVALIDATE"), outcome("CREATE OR REPLACE TABLE k (a INT, PRIMARY KEY (a) NOVALIDATE)"));
        assertEquals(syntax(50, "FOO"), outcome("CREATE OR REPLACE TABLE k (a INT, PRIMARY KEY (a) FOO)"));
        assertEquals(syntax(59, ")"), outcome("CREATE OR REPLACE TABLE k (a INT, PRIMARY KEY (a) INITIALLY)"));
        assertEquals(syntax(53, ")"), outcome("CREATE OR REPLACE TABLE k (a INT, PRIMARY KEY (a) NOT)"));
        assertEquals(syntax(69, "VALIDATE"),
            outcome("CREATE OR REPLACE TABLE k (a INT, PRIMARY KEY (a) DISABLE NOVALIDATE VALIDATE)"));
        assertEquals(syntax(49, ")"), outcome("CREATE OR REPLACE TABLE k (y INT UNIQUE INITIALLY)"));
    }

    @Test
    public void theThreeKindsReadThemAlikeInEveryPlace() {
        final String[][] forms = {
            {"UNIQUE", "(a INT, UNIQUE (a) %s)"},
            {"FOREIGN KEY", "(a INT, FOREIGN KEY (a) REFERENCES p1 (id) %s)"},
            {"PRIMARY KEY", "(a INT PRIMARY KEY %s)"},
            {"UNIQUE", "(a INT UNIQUE %s)"},
            {"FOREIGN KEY", "(a INT REFERENCES p1 (id) %s)"},
        };
        for (final String[] form : forms) {
            assertEquals("ok", outcome("CREATE OR REPLACE TABLE k " + String.format(form[1], "NOT ENFORCED RELY")), form[1]);
            assertEquals(rows(form[0] + " rely=YES"), kept("K"), form[1]);
            assertEquals("ok", outcome("CREATE OR REPLACE TABLE k " + String.format(form[1], "DISABLE NOVALIDATE")), form[1]);
            assertEquals(rows(form[0] + " rely=NO"), kept("K"), form[1]);
            assertEquals("ok", outcome("CREATE OR REPLACE TABLE k " + String.format(form[1], "ENABLE")), form[1]);
            assertEquals(rows(), kept("K"), form[1]);
            assertEquals("ok", outcome("CREATE OR REPLACE TABLE k " + String.format(form[1], "DEFERRABLE")), form[1]);
            assertEquals(rows(), kept("K"), form[1]);
            assertEquals(CONFLICT, outcome("CREATE OR REPLACE TABLE k " + String.format(form[1], "RELY NORELY")), form[1]);
            assertEquals(NOT_DEFERRABLE,
                outcome("CREATE OR REPLACE TABLE k " + String.format(form[1], "NOT DEFERRABLE INITIALLY DEFERRED")), form[1]);
        }
    }

    @Test
    public void aKeyLeftOutStillMakesItsColumnNotNull() {
        engine.execute("CREATE OR REPLACE TABLE sk1 (a INT PRIMARY KEY ENABLE, b INT UNIQUE DEFERRABLE)");
        assertEquals("N N N", describeCell("sk1", "A", "null?") + " " + describeCell("sk1", "A", "primary key") + " "
            + describeCell("sk1", "A", "unique key"));
        assertEquals("Y N N", describeCell("sk1", "B", "null?") + " " + describeCell("sk1", "B", "primary key") + " "
            + describeCell("sk1", "B", "unique key"));
        engine.execute("CREATE OR REPLACE TABLE sk2 (a INT, b INT, PRIMARY KEY (a) ENABLE, UNIQUE (b) INITIALLY IMMEDIATE)");
        assertEquals("N N", describeCell("sk2", "A", "null?") + " " + describeCell("sk2", "A", "primary key"));
        assertEquals("Y N", describeCell("sk2", "B", "null?") + " " + describeCell("sk2", "B", "unique key"));
        engine.execute("CREATE OR REPLACE TABLE sk3 (a INT PRIMARY KEY DISABLE, b INT UNIQUE NOT DEFERRABLE)");
        assertEquals("N Y", describeCell("sk3", "A", "null?") + " " + describeCell("sk3", "A", "primary key"));
        assertEquals("Y Y", describeCell("sk3", "B", "null?") + " " + describeCell("sk3", "B", "unique key"));
    }

    @Test
    public void aKeyLeftOutIsNoSecondKeyAndTakesNoName() {
        assertEquals("ok", outcome("CREATE OR REPLACE TABLE sk6 (a INT PRIMARY KEY ENABLE, b INT PRIMARY KEY)"));
        assertEquals("N Y", describeCell("sk6", "A", "primary key") + " " + describeCell("sk6", "B", "primary key"));
        assertEquals("SQL compilation error:|primary key already exists for table 'SK7'",
            outcome("CREATE OR REPLACE TABLE sk7 (a INT PRIMARY KEY, b INT PRIMARY KEY)"));
        assertEquals("ok", outcome("CREATE OR REPLACE TABLE sk4 (a INT, b INT, CONSTRAINT c PRIMARY KEY (a) ENABLE,"
            + " CONSTRAINT c UNIQUE (b))"));
        assertEquals("SQL compilation error:|constraint 'C' already exists for table 'SK5'",
            outcome("CREATE OR REPLACE TABLE sk5 (a INT, b INT, CONSTRAINT c PRIMARY KEY (a), CONSTRAINT c UNIQUE (b))"));
    }

    @Test
    public void anAlterAddReadsThemTheSameWay() {
        engine.execute("CREATE OR REPLACE TABLE s1 (y INT, z INT)");
        assertEquals("ok", outcome("ALTER TABLE s1 ADD CONSTRAINT fk_alt FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED RELY"));
        assertEquals("ok", outcome("ALTER TABLE s1 ADD CONSTRAINT uq_alt UNIQUE (z) DEFERRABLE"));
        assertEquals("ok", outcome("ALTER TABLE s1 ADD PRIMARY KEY (y) ENABLE"));
        assertEquals(rows("FOREIGN KEY rely=YES"), kept("S1"));
        assertEquals("ok", outcome("ALTER TABLE s1 ADD CONSTRAINT pk_alt PRIMARY KEY (y) NOT ENFORCED"));
        assertEquals("SQL compilation error:|primary key already exists for table 'S1'",
            outcome("ALTER TABLE s1 ADD PRIMARY KEY (z)"));
        assertEquals(rows("FOREIGN KEY rely=YES", "PRIMARY KEY rely=NO"), kept("S1"));
        assertEquals(CONFLICT, outcome("ALTER TABLE s1 ADD UNIQUE (z) RELY NORELY"));
    }
}
