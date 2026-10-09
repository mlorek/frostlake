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
 * A constraint written in a column definition may carry a name. The engine refused the CONSTRAINT word there, and
 * let two constraints of a table share a name.
 *
 * <p>On the account {@code CONSTRAINT name} may stand before a column's PRIMARY KEY, UNIQUE, REFERENCES or CHECK,
 * and not before NOT NULL or DEFAULT. No two constraints a table keeps carry one name: the second is refused naming
 * the table, and a foreign key in the words of any object that already exists. Every cell is live-verified.
 */
public class InlineNamedConstraintTest extends BaseDatabaseTest {

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

    /** The table's constraints as {@code NAME TYPE}, without the generated names, by name. */
    private List<String> named(final String table) {
        final List<String> out = new ArrayList<>();
        for (final Row row : engine.executeQuery("SELECT constraint_name || ' ' || constraint_type"
                + " FROM information_schema.table_constraints WHERE table_name = '" + table
                + "' AND table_schema = 'TEST_SCHEMA' AND constraint_name NOT LIKE 'SYS%' ORDER BY 1").getRows()) {
            out.add(String.valueOf(row.getValue(0)));
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

    private static String taken(final String name, final String table) {
        return "SQL compilation error:|constraint '" + name + "' already exists for table '" + table + "'";
    }

    @Test
    public void aColumnsKeyMayBeNamed() {
        assertEquals("ok", outcome("CREATE TABLE i2 (y INT CONSTRAINT fk_i2 FOREIGN KEY REFERENCES p1 (id))"));
        assertEquals(rows("FK_I2 FOREIGN KEY"), named("I2"));
        assertEquals("ok", outcome("CREATE TABLE i3 (y INT CONSTRAINT fk_i3 REFERENCES p1 (id))"));
        assertEquals(rows("FK_I3 FOREIGN KEY"), named("I3"));
        assertEquals("ok", outcome("CREATE TABLE i5 (y INT CONSTRAINT uq_i5 UNIQUE)"));
        assertEquals(rows("UQ_I5 UNIQUE"), named("I5"));
        assertEquals("ok", outcome("CREATE TABLE i6 (y INT CONSTRAINT pk_i6 PRIMARY KEY)"));
        assertEquals(rows("PK_I6 PRIMARY KEY"), named("I6"));
        assertEquals("ok", outcome("CREATE TABLE i8 (y INT CONSTRAINT c10 UNIQUE NOT ENFORCED RELY)"));
        assertEquals(rows("C10 UNIQUE"), named("I8"));
        assertEquals("ok", outcome("CREATE TABLE i9 (y INT CONSTRAINT c11a UNIQUE CONSTRAINT c11b REFERENCES p1 (id))"));
        assertEquals(rows("C11A UNIQUE", "C11B FOREIGN KEY"), named("I9"));
        assertEquals("ok", outcome("CREATE TABLE i10 (y INT CONSTRAINT c12 CHECK (y > 0))"));
    }

    @Test
    public void theNameNeedsAConstraintAfterIt() {
        assertEquals(syntax(40, "NOT"), outcome("CREATE TABLE i7 (y INT CONSTRAINT nn_i7 NOT NULL)"));
        assertEquals(syntax(38, ")"), outcome("CREATE TABLE s12 (y INT CONSTRAINT c12)"));
        assertEquals(syntax(39, "DEFAULT"), outcome("CREATE TABLE s12 (y INT CONSTRAINT c12 DEFAULT 1)"));
    }

    @Test
    public void noTwoConstraintsOfATableShareAName() {
        assertEquals(taken("C2", "N2"),
            outcome("CREATE TABLE n2 (a INT CONSTRAINT c2 UNIQUE, b INT, CONSTRAINT c2 PRIMARY KEY (b))"));
        assertEquals(taken("C2", "N2"),
            outcome("CREATE TABLE n2 (a INT, CONSTRAINT c2 CHECK (a > 0), CONSTRAINT c2 UNIQUE (a))"));
        assertEquals("SQL compilation error:|Object 'C2' already exists.",
            outcome("CREATE TABLE n2 (a INT CONSTRAINT c2 UNIQUE, b INT CONSTRAINT c2 REFERENCES p1)"));
        assertEquals("ok",
            outcome("CREATE TABLE n2 (a INT, b INT, CONSTRAINT \"c2\" UNIQUE (a), CONSTRAINT c2 UNIQUE (b))"),
            "a name in another case is another name");
    }

    @Test
    public void anAlterAddFindsTheNameTaken() {
        engine.execute("CREATE TABLE n1 (a INT, b INT, CONSTRAINT c1 UNIQUE (a))");
        assertEquals(taken("C1", "N1"), outcome("ALTER TABLE n1 ADD CONSTRAINT c1 UNIQUE (b)"));
        assertEquals(taken("C1", "N1"), outcome("ALTER TABLE n1 ADD CONSTRAINT c1 PRIMARY KEY (b)"));
        assertEquals("SQL compilation error:|Object 'C1' already exists.",
            outcome("ALTER TABLE n1 ADD CONSTRAINT c1 FOREIGN KEY (b) REFERENCES p1"));
        assertEquals("ok", outcome("ALTER TABLE n1 ADD CONSTRAINT c1 UNIQUE (b) ENABLE"),
            "a key left out takes no name, so it meets none");
        assertEquals(rows("C1 UNIQUE"), named("N1"));
    }
}
