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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fully qualified object name may lead with the ACCOUNT locator — {@code <account>.db.schema.t}, the
 * locator folding like any bare identifier — and then names exactly what its three-part spelling names,
 * in every statement: queries, INSERT / UPDATE / DELETE / MERGE / TRUNCATE, CTAS and CREATE VIEW,
 * DESCRIBE, SHOW … IN SCHEMA, DROP and USE SCHEMA. Any other leading part — a wrong account, the locator
 * quoted in the wrong case, a fifth part — names nothing, and a column reference never takes the account
 * at all. Every expectation is live-verified; the locator is read from CURRENT_ACCOUNT(), so the test runs
 * against whichever account it meets.
 */
public class AccountQualifiedNameTest extends BaseDatabaseTest {

    private static final String MISSING = "SQL compilation error:|Object does not exist, or operation cannot be performed.";

    private String account;

    @BeforeEach
    public void seed() {
        account = String.valueOf(engine.executeQuery("SELECT CURRENT_ACCOUNT()").getRows().get(0).getValue(0));
        engine.execute("CREATE OR REPLACE SCHEMA acs");
        engine.execute("CREATE OR REPLACE TABLE acs.two (s NUMBER(2,0))");
        engine.execute("USE SCHEMA test_schema");
    }

    /** The four-part name of an object in schema ACS, the locator written bare in lower case. */
    private String four(final String object) {
        return account.toLowerCase() + ".test_db.acs." + object;
    }

    private String text(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    /** One statement's refusal, or "ACCEPTED". */
    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "ACCEPTED";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    @Test
    public void aNameLeadingWithThisAccountNamesWhatItsThreePartSpellingNames() {
        engine.execute("INSERT INTO " + four("two") + " VALUES (5)");
        assertEquals("5", text("SELECT s FROM " + four("two")));
        assertEquals("5", text("SELECT two.s FROM " + four("two")));
        assertEquals("5", text("SELECT test_db.acs.two.s FROM " + four("two")));
        assertEquals("5", text("SELECT s FROM \"" + account + "\".test_db.acs.two"));
        engine.execute("UPDATE " + four("two") + " SET s = 6");
        engine.execute("DELETE FROM " + four("two") + " WHERE s = 99");
        engine.execute("MERGE INTO " + four("two") + " t USING (SELECT 6 AS s) u ON t.s = u.s "
            + "WHEN MATCHED THEN UPDATE SET s = 7");
        assertEquals("7", text("SELECT s FROM test_db.acs.two"));
        engine.execute("CREATE TABLE " + four("three") + " AS SELECT 1 AS x");
        assertEquals("1", text("SELECT x FROM test_db.acs.three"));
        engine.execute("CREATE VIEW " + four("v1") + " AS SELECT 1 AS x");
        assertEquals("1", text("SELECT x FROM " + four("v1")));
        assertEquals("S", text("DESCRIBE TABLE " + four("two")));
        final ResultSet tables = engine.executeQuery("SHOW TABLES IN SCHEMA " + account.toLowerCase() + ".test_db.acs");
        boolean listed = false;
        for (int i = 0; i < tables.getRowCount(); i++) {
            listed = listed || "TWO".equals(String.valueOf(tables.getRows().get(i).getValue(tables.getColumnIndex("name"))));
        }
        assertTrue(listed);
        engine.execute("TRUNCATE TABLE " + four("two"));
        assertEquals("0", text("SELECT COUNT(*) FROM test_db.acs.two"));
        engine.execute("DROP TABLE " + four("three"));
        assertTrue(refusal("SELECT x FROM test_db.acs.three").contains("does not exist or not authorized"));
        engine.execute("USE SCHEMA " + account.toLowerCase() + ".test_db.acs");
        assertEquals("ACS", text("SELECT CURRENT_SCHEMA()"));
    }

    @Test
    public void anyOtherLeadingPartNamesNothing() {
        assertEquals(MISSING, refusal("SELECT s FROM nosuch1.test_db.acs.two"));
        assertEquals(MISSING, refusal("INSERT INTO nosuch1.test_db.acs.two VALUES (1)"));
        assertEquals(MISSING, refusal("SELECT s FROM \"" + account.toLowerCase() + "\".test_db.acs.two"));
        assertEquals(MISSING, refusal("SELECT s FROM " + four("two") + ".x"));
        // Past the account, a missing table is spelled without it.
        assertEquals(hinted("SQL compilation error:|Object 'TEST_DB.ACS.NOSUCHTAB' does not exist or not authorized."),
            refusal("SELECT * FROM " + four("nosuchtab")));
    }

    @Test
    public void aColumnReferenceNeverTakesTheAccount() {
        final String whole = account.toUpperCase() + ".TEST_DB.ACS.TWO.S";
        assertEquals("SQL compilation error: error line 1 at position 7|invalid identifier '" + whole + "'",
            refusal("SELECT " + four("two") + ".s FROM test_db.acs.two"));
        assertEquals("SQL compilation error: error line 1 at position 7|invalid identifier '" + whole + "'",
            refusal("SELECT " + four("two") + ".s FROM " + four("two")));
        final String inWhere = "SELECT s FROM " + four("two") + " WHERE " + four("two") + ".s = 5";
        assertEquals("SQL compilation error: error line 1 at position " + inWhere.lastIndexOf(four("two"))
            + "|invalid identifier '" + whole + "'", refusal(inWhere));
        assertEquals("SQL compilation error: sequence identifier 'X.Y.TEST_DB.ACS.TWO.S' has too many qualifiers",
            refusal("SELECT x.y.test_db.acs.two.s FROM test_db.acs.two"));
    }
}
