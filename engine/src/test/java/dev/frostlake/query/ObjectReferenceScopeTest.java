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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A SYSTEM$REFERENCE token a query makes lasts as its scope says: a SESSION reference reads its table in a later
 * statement and in a later call, where a CALL or a PERSISTENT one names no object once its statement has ended, the
 * token read as a name of its own (all live-verified). The tokens differ on every call, so the refusals are held by
 * their shape.
 */
public class ObjectReferenceScopeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE tsr (a INT)");
        engine.execute("INSERT INTO tsr VALUES (1), (2)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE pr_id(r VARCHAR) RETURNS NUMBER LANGUAGE SQL AS \
            $$ BEGIN LET n NUMBER := (SELECT COUNT(*) FROM IDENTIFIER(:r)); RETURN n; END $$""");
    }

    /** The first cell, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static void assertNamesNoObject(final String refusal) {
        assertTrue(refusal.contains("SQL compilation error:|Object 'ENT_REF_TABLE_"), refusal);
        assertTrue(refusal.contains("' does not exist or not authorized."), refusal);
    }

    @Test
    public void aSessionReferenceLastsTheSession() {
        engine.execute("SET stok = (SELECT SYSTEM$REFERENCE('TABLE', 'tsr', 'SESSION', 'SELECT'))");
        assertEquals("2", answer("SELECT COUNT(*) FROM IDENTIFIER($stok)"));
        assertEquals("2", answer("CALL pr_id($stok)"));
    }

    @Test
    public void aCallReferenceAQueryMadeEndsWithIt() {
        engine.execute("SET tok = (SELECT SYSTEM$REFERENCE('TABLE', 'tsr', 'CALL', 'SELECT'))");
        assertNamesNoObject(answer("SELECT COUNT(*) FROM IDENTIFIER($tok)"));
        assertNamesNoObject(answer("CALL pr_id($tok)"));
        assertEquals("2", answer("CALL pr_id(SYSTEM$REFERENCE('TABLE', 'tsr', 'CALL', 'SELECT'))"));
    }

    @Test
    public void aPersistentReferenceAQueryMadeEndsWithItToo() {
        engine.execute("SET ptok = (SELECT SYSTEM$REFERENCE('TABLE', 'tsr', 'PERSISTENT', 'SELECT'))");
        assertNamesNoObject(answer("CALL pr_id($ptok)"));
    }
}
