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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code DROP <kind> IDENTIFIER('<name>')} names the object to drop, for EVERY kind. Only DROP TABLE
 * read such a name; every other kind took a plain name, so a script that drops by a name it built —
 * {@code DROP VIEW IDENTIFIER($v)} — failed here and ran on the account.
 *
 * <p>The refusal for a name that exists nowhere is the proof the name was READ: it says which kind
 * looked and quotes the fully qualified name it resolved to.
 */
public class DropByIdentifierNameTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("USE ROLE ACCOUNTADMIN");
    }

    /** The refusal a statement raises, or "OK". */
    private String outcome(final String sql) {
        try {
            engine.execute(sql);
            return "OK";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** Each schema-scoped kind reads the name and says which kind missed. */
    @Test
    public void everySchemaScopedKindReadsTheName() {
        assertEquals(hinted("SQL compilation error: View 'TEST_DB.TEST_SCHEMA.V1' does not exist or not"
            + " authorized."), outcome("DROP VIEW IDENTIFIER('v1')"));
        assertEquals(hinted("SQL compilation error: Stage 'TEST_DB.TEST_SCHEMA.NOST' does not exist or not"
            + " authorized."), outcome("DROP STAGE IDENTIFIER('nost')"));
        assertEquals(hinted("SQL compilation error: Stream 'TEST_DB.TEST_SCHEMA.NOSTR' does not exist or not"
            + " authorized."), outcome("DROP STREAM IDENTIFIER('nostr')"));
        assertEquals(hinted("SQL compilation error: Task 'TEST_DB.TEST_SCHEMA.NOTASK' does not exist or not"
            + " authorized."), outcome("DROP TASK IDENTIFIER('notask')"));
        assertEquals(hinted("SQL compilation error: Sequence 'TEST_DB.TEST_SCHEMA.NOSEQ' does not exist or"
            + " not authorized."), outcome("DROP SEQUENCE IDENTIFIER('noseq')"));
        assertEquals(hinted("SQL compilation error: File format 'TEST_DB.TEST_SCHEMA.NOFF' does not exist or"
            + " not authorized."), outcome("DROP FILE FORMAT IDENTIFIER('noff')"));
        assertEquals(hinted("SQL compilation error: Tag 'TEST_DB.TEST_SCHEMA.NOTAG' does not exist or not"
            + " authorized."), outcome("DROP TAG IDENTIFIER('notag')"));
        assertEquals(hinted("SQL compilation error: Pipe 'TEST_DB.TEST_SCHEMA.NOPIPE' does not exist or not"
            + " authorized."), outcome("DROP PIPE IDENTIFIER('nopipe')"));
        assertEquals(hinted("SQL compilation error: Materialized view 'TEST_DB.TEST_SCHEMA.NOMV' does not"
            + " exist or not authorized."), outcome("DROP MATERIALIZED VIEW IDENTIFIER('nomv')"));
        assertEquals(hinted("SQL compilation error: Dynamic table 'TEST_DB.TEST_SCHEMA.NODT' does not exist"
            + " or not authorized."), outcome("DROP DYNAMIC TABLE IDENTIFIER('nodt')"));
        assertEquals(hinted("SQL compilation error: Masking policy 'TEST_DB.TEST_SCHEMA.NOMP' does not exist"
            + " or not authorized."), outcome("DROP MASKING POLICY IDENTIFIER('nomp')"));
        assertEquals(hinted("SQL compilation error: Row access policy 'TEST_DB.TEST_SCHEMA.NORAP' does not"
            + " exist or not authorized."), outcome("DROP ROW ACCESS POLICY IDENTIFIER('norap')"));
    }

    /** So do the kinds whose name has no schema: a schema, a database, a warehouse, a role. */
    @Test
    public void theAccountScopedKindsReadItToo() {
        assertEquals(hinted("SQL compilation error: Schema 'TEST_DB.S1' does not exist or not authorized."),
            outcome("DROP SCHEMA IDENTIFIER('s1')"));
        assertEquals(hinted("SQL compilation error: Database 'NODB' does not exist or not authorized."),
            outcome("DROP DATABASE IDENTIFIER('nodb')"));
        assertEquals(hinted("SQL compilation error: Warehouse 'NOWH' does not exist or not authorized."),
            outcome("DROP WAREHOUSE IDENTIFIER('nowh')"));
        assertEquals(hinted("SQL compilation error: Role 'NOROLE' does not exist or not authorized."),
            outcome("DROP ROLE IDENTIFIER('norole')"));
    }

    /** IF EXISTS is unaffected by the name's form. */
    @Test
    public void ifExistsStillSwallowsTheMiss() {
        assertEquals("OK", outcome("DROP VIEW IF EXISTS IDENTIFIER('v1')"));
        assertEquals("OK", outcome("DROP SCHEMA IF EXISTS IDENTIFIER('s1')"));
    }

    /** And the object really goes — by a bare name, a qualified one, and one held in a variable. */
    @Test
    public void theObjectIsActuallyDropped() {
        engine.execute("CREATE OR REPLACE VIEW dv1 AS SELECT 1 AS a");
        assertEquals("OK", outcome("DROP VIEW IDENTIFIER('dv1')"));

        engine.execute("CREATE OR REPLACE STAGE dst1");
        assertEquals("OK", outcome("DROP STAGE IDENTIFIER('dst1')"));

        engine.execute("CREATE OR REPLACE VIEW dv2 AS SELECT 1 AS a");
        assertEquals("OK", outcome("DROP VIEW IDENTIFIER('test_db.test_schema.dv2')"));

        engine.execute("SET vname = 'dv3'");
        engine.execute("CREATE OR REPLACE VIEW dv3 AS SELECT 1 AS a");
        assertEquals("OK", outcome("DROP VIEW IDENTIFIER($vname)"));
    }

    /** An EXPRESSION inside the call is still refused, at the expression and not at the word. */
    @Test
    public void anExpressionInsideTheCallIsRefusedWhereItStands() {
        final String view = outcome("DROP VIEW IDENTIFIER(UPPER('t1'))");
        assertTrue(view.contains("position 27 unexpected ''t1''"), view);
        final String schema = outcome("DROP SCHEMA IDENTIFIER(UPPER('t1'))");
        assertTrue(schema.contains("position 29 unexpected ''t1''"), schema);
    }
}
