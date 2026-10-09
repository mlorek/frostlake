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
 * A foreign key's MATCH clause, and which foreign keys a statement keeps. The engine refused MATCH as a syntax
 * error, a foreign key without its parent's columns at table level, and a constraint named in a column definition.
 *
 * <p>On the account MATCH FULL, SIMPLE or PARTIAL stands right after the REFERENCES target, before the ON UPDATE and
 * ON DELETE rules and the constraint's properties. A key is kept only as the account can hold it: with MATCH FULL
 * or none and with NO ACTION rules or none. Any other is left out without a word — the table is created, and the
 * key is in no listing — though its own columns and its parent still have to exist. Every cell is live-verified.
 */
public class ForeignKeyMatchTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE p1 (id INT PRIMARY KEY)");
        engine.execute("CREATE TABLE p2 (a INT, b INT, PRIMARY KEY (a, b))");
    }

    /** The statement's refusal on one line, cut at its first line break after the prefix, or "ok". */
    private String outcome(final String sql) {
        try {
            engine.execute(sql);
            return "ok";
        } catch (final RuntimeException refused) {
            final String[] lines = String.valueOf(refused.getMessage()).split("\n");
            return lines.length > 1 ? lines[0] + "|" + lines[1] : lines[0];
        }
    }

    /** The table's foreign keys as {@code MATCH_OPTION UPDATE_RULE DELETE_RULE}, named ones by name. */
    private List<String> keys(final String table) {
        final List<String> out = new ArrayList<>();
        for (final Row row : engine.executeQuery("SELECT IFF(rc.constraint_name LIKE 'SYS%', '', rc.constraint_name"
                + " || ' ') || match_option || ' ' || update_rule || ' ' || delete_rule"
                + " FROM information_schema.referential_constraints rc"
                + " JOIN information_schema.table_constraints tc ON tc.constraint_name = rc.constraint_name"
                + " WHERE tc.table_name = '" + table + "' AND tc.table_schema = 'TEST_SCHEMA' ORDER BY 1").getRows()) {
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

    private static final String KEPT = "FULL NO ACTION NO ACTION";

    @Test
    public void matchFullIsKeptAndTheOthersAreLeftOut() {
        assertEquals("ok", outcome("CREATE TABLE f_full (y INT, FOREIGN KEY (y) REFERENCES p1 (id) MATCH FULL)"));
        assertEquals(rows(KEPT), keys("F_FULL"));
        assertEquals("ok", outcome("CREATE TABLE f_none (y INT, FOREIGN KEY (y) REFERENCES p1 (id))"));
        assertEquals(rows(KEPT), keys("F_NONE"));
        for (final String kind : new String[] {"SIMPLE", "PARTIAL", "simple"}) {
            assertEquals("ok", outcome("CREATE OR REPLACE TABLE f_out (y INT, FOREIGN KEY (y) REFERENCES p1 (id) MATCH "
                + kind + ")"), kind);
            assertEquals(rows(), keys("F_OUT"), kind);
        }
        assertEquals("ok", outcome("CREATE OR REPLACE TABLE f_out (y INT, FOREIGN KEY (y) REFERENCES p1 MATCH SIMPLE)"));
        assertEquals("ok", outcome("CREATE OR REPLACE TABLE f_out (a INT, b INT, FOREIGN KEY (a, b) REFERENCES p2 (a, b)"
            + " MATCH PARTIAL)"));
        assertEquals(rows(), keys("F_OUT"));
    }

    @Test
    public void matchStandsRightAfterTheTarget() {
        assertEquals("ok", outcome("CREATE OR REPLACE TABLE f_o (y INT, FOREIGN KEY (y) REFERENCES p1 (id) MATCH FULL"
            + " ON DELETE NO ACTION NOT ENFORCED RELY)"));
        assertEquals(rows(KEPT), keys("F_O"));
        assertEquals(syntax(108, "MATCH"), outcome("CREATE OR REPLACE TABLE f_o (y INT, FOREIGN KEY (y) REFERENCES p1 (id)"
            + " ON UPDATE CASCADE ON DELETE SET NULL MATCH PARTIAL)"));
        assertEquals(syntax(84, "MATCH"), outcome("CREATE OR REPLACE TABLE f_o (y INT, FOREIGN KEY (y) REFERENCES p1 (id)"
            + " NOT ENFORCED MATCH SIMPLE)"));
        assertEquals(syntax(84, "MATCH"), outcome("CREATE OR REPLACE TABLE f_o (y INT, FOREIGN KEY (y) REFERENCES p1 (id)"
            + " MATCH SIMPLE MATCH FULL)"));
        assertEquals(syntax(77, "NOPE"), outcome("CREATE OR REPLACE TABLE f_o (y INT, FOREIGN KEY (y) REFERENCES p1 (id)"
            + " MATCH NOPE)"));
        assertEquals(syntax(76, ")"), outcome("CREATE OR REPLACE TABLE f_o (y INT, FOREIGN KEY (y) REFERENCES p1 (id)"
            + " MATCH)"));
        assertEquals(syntax(58, ","), outcome("CREATE OR REPLACE TABLE n5 (y INT REFERENCES p1 (id) MATCH, z INT)"));
        assertEquals(syntax(84, "ON"), outcome("CREATE OR REPLACE TABLE f_o (y INT, FOREIGN KEY (y) REFERENCES p1 (id)"
            + " NOT ENFORCED ON DELETE NO ACTION)"));
    }

    @Test
    public void aRuleOtherThanNoActionLeavesTheKeyOut() {
        final String[] kept = {"", " ON UPDATE NO ACTION ON DELETE NO ACTION", " ON DELETE NO ACTION", " MATCH FULL",
            " MATCH FULL ON DELETE NO ACTION", " MATCH FULL NOT ENFORCED", " NOT ENFORCED", " RELY"};
        for (final String tail : kept) {
            assertEquals("ok", outcome("CREATE OR REPLACE TABLE k (y INT, CONSTRAINT fk_k FOREIGN KEY (y) REFERENCES p1"
                + " (id)" + tail + ")"), tail);
            assertEquals(rows("FK_K " + KEPT), keys("K"), tail);
        }
        final String[] leftOut = {" ON DELETE CASCADE", " ON UPDATE CASCADE", " ON DELETE SET NULL", " ON DELETE RESTRICT",
            " ON DELETE SET DEFAULT", " ON UPDATE RESTRICT", " ON DELETE CASCADE ON UPDATE SET NULL",
            " MATCH FULL ON DELETE CASCADE", " MATCH FULL ON UPDATE CASCADE", " DEFERRABLE INITIALLY DEFERRED",
            " ON DELETE CASCADE NOT ENFORCED", " MATCH SIMPLE"};
        for (final String tail : leftOut) {
            assertEquals("ok", outcome("CREATE OR REPLACE TABLE k (y INT, CONSTRAINT fk_k FOREIGN KEY (y) REFERENCES p1"
                + " (id)" + tail + ")"), tail);
            assertEquals(rows(), keys("K"), tail);
        }
    }

    @Test
    public void aKeyInAColumnDefinitionFollowsTheSameRules() {
        assertEquals("ok", outcome("CREATE TABLE i1 (y INT REFERENCES p1 (id) MATCH FULL)"));
        assertEquals(rows(KEPT), keys("I1"));
        assertEquals("ok", outcome("CREATE TABLE i2 (y INT REFERENCES p1 (id) MATCH SIMPLE)"));
        assertEquals(rows(), keys("I2"));
        assertEquals("ok", outcome("CREATE TABLE i3 (y INT FOREIGN KEY REFERENCES p1 (id) MATCH PARTIAL)"));
        assertEquals(rows(), keys("I3"));
        assertEquals("ok", outcome("CREATE TABLE i4 (y INT REFERENCES p1 MATCH PARTIAL)"));
        assertEquals(rows(), keys("I4"));
        assertEquals("ok", outcome("CREATE TABLE i5 (y INT REFERENCES p1 (id) MATCH SIMPLE ON DELETE CASCADE)"));
        assertEquals(rows(), keys("I5"));
        assertEquals("ok", outcome("CREATE TABLE i6 (y INT CONSTRAINT fk_in FOREIGN KEY REFERENCES p1 (id) MATCH FULL)"));
        assertEquals(rows("FK_IN " + KEPT), keys("I6"));
        assertEquals("ok", outcome("CREATE TABLE i7 (y INT REFERENCES p1 (id) MATCH FULL ON UPDATE NO ACTION NOT ENFORCED"
            + " RELY)"));
        assertEquals(rows(KEPT), keys("I7"));
    }

    @Test
    public void aKeyLeftOutLeavesNothingBehind() {
        engine.execute("CREATE TABLE f_mixed (y INT, z INT, FOREIGN KEY (y) REFERENCES p1 (id) MATCH SIMPLE,"
            + " FOREIGN KEY (z) REFERENCES p1 (id) MATCH FULL)");
        assertEquals(rows(KEPT), keys("F_MIXED"));
        assertEquals(1, engine.executeQuery("SHOW IMPORTED KEYS IN TABLE f_mixed").getRows().size());
        engine.execute("CREATE TABLE f_alter (y INT, z INT)");
        assertEquals("ok", outcome("ALTER TABLE f_alter ADD FOREIGN KEY (y) REFERENCES p1 (id) MATCH SIMPLE"));
        assertEquals("ok", outcome("ALTER TABLE f_alter ADD CONSTRAINT fk_gone FOREIGN KEY (z) REFERENCES p1 (id)"
            + " MATCH PARTIAL ON DELETE CASCADE"));
        assertEquals(rows(), keys("F_ALTER"));
        assertEquals("SQL compilation error:|constraint 'FK_GONE' does not exist",
            outcome("ALTER TABLE f_alter DROP CONSTRAINT fk_gone"));
        assertEquals("ok", outcome("CREATE TABLE f_twice (y INT, CONSTRAINT fk_d FOREIGN KEY (y) REFERENCES p1 (id)"
            + " MATCH SIMPLE, CONSTRAINT fk_d FOREIGN KEY (y) REFERENCES p1 (id) MATCH FULL)"));
        assertEquals(rows("FK_D " + KEPT), keys("F_TWICE"));
    }

    @Test
    public void aKeyLeftOutStillNeedsItsColumnsAndItsParent() {
        assertEquals(hinted("SQL compilation error:|Table 'TEST_DB.TEST_SCHEMA.NOSUCH_PARENT' does not exist or not"
            + " authorized."), outcome("CREATE TABLE f_bad (y INT, FOREIGN KEY (y) REFERENCES nosuch_parent (id)"
            + " MATCH SIMPLE)"));
        assertEquals("SQL compilation error: error line 1 at position 40|invalid identifier 'NOSUCH'",
            outcome("CREATE TABLE f_bad (y INT, FOREIGN KEY (nosuch) REFERENCES p1 (id) MATCH SIMPLE)"));
        assertEquals("ok", outcome("CREATE TABLE f_lax (y INT, FOREIGN KEY (y) REFERENCES p1 (nosuch) MATCH SIMPLE)"),
            "what a key left out points at is not looked at");
        assertEquals("ok", outcome("CREATE TABLE f_lax2 (y INT, FOREIGN KEY (y) REFERENCES f_lax (y) MATCH PARTIAL)"));
    }
}
