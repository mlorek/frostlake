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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

/**
 * DROP of the single-word kinds live has and Frostlake does not model — an alert, a notebook, a secret, a model, a
 * streamlit and a listing: no such object exists, so the name resolves as any object's and then misses, which IF
 * EXISTS forgives with the ordinary already-dropped status; a missing database or schema and a name of four parts
 * are refused, IF EXISTS or not. CASCADE and RESTRICT change nothing. Every cell is live-verified.
 *
 * <p>The status sentence is read off a raw JDBC connection in live mode, where the harness reports a statement's
 * count instead.
 */
public class UnmodelledDropKindTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t1 (x INT)");
    }

    /** Every row's first cell, a bar between rows, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                out.append(out.length() > 0 ? " | " : "").append(row.getValue(0));
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String statusOf(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    private String missing(final String kind, final String name) {
        return hinted("SQL compilation error:|" + kind + " '" + name + "' does not exist or not authorized.");
    }

    @Test
    public void theObjectIsMissing() {
        assertEquals(missing("Alert", "TEST_DB.TEST_SCHEMA.A"), answer("DROP ALERT a"));
        assertEquals(missing("Notebook", "TEST_DB.TEST_SCHEMA.N"), answer("DROP NOTEBOOK n"));
        assertEquals(missing("Secret", "TEST_DB.TEST_SCHEMA.S"), answer("DROP SECRET s"));
        assertEquals(missing("Model", "TEST_DB.TEST_SCHEMA.M"), answer("DROP MODEL m"));
        assertEquals(missing("Streamlit", "TEST_DB.TEST_SCHEMA.S"), answer("DROP STREAMLIT s"));
        assertEquals(missing("Data exchange listing", "L"), answer("DROP LISTING l"));
        assertEquals(missing("Data exchange listing", "\"l\""), answer("DROP LISTING \"l\""));
        assertEquals(missing("Notebook", "TEST_DB.TEST_SCHEMA.\"n\""), answer("DROP NOTEBOOK \"n\""));
        assertEquals(missing("Notebook", "TEST_DB.TEST_SCHEMA.N"), answer("DROP NOTEBOOK IDENTIFIER('n')"));
        assertEquals(missing("Notebook", "TEST_DB.PUBLIC.N"), answer("DROP NOTEBOOK test_db..n"));
        assertEquals(missing("Alert", "TEST_DB.TEST_SCHEMA.T1"), answer("DROP ALERT t1"));
        assertEquals(missing("Alert", "TEST_DB.TEST_SCHEMA.A"), answer("DROP ALERT a CASCADE"));
        assertEquals(missing("Secret", "TEST_DB.TEST_SCHEMA.S"), answer("DROP SECRET s RESTRICT"));
        assertEquals(missing("Data exchange listing", "L"), answer("DROP LISTING l CASCADE"));
    }

    @Test
    public void ifExistsForgivesTheObjectAlone() {
        assertEquals("Drop statement executed successfully (A already dropped).", statusOf("DROP ALERT IF EXISTS a"));
        assertEquals("Drop statement executed successfully (N already dropped).",
            statusOf("DROP NOTEBOOK IF EXISTS n"));
        assertEquals("Drop statement executed successfully (q\"x already dropped).",
            statusOf("DROP NOTEBOOK IF EXISTS \"q\"\"x\""));
        assertEquals("Drop statement executed successfully (L already dropped).", statusOf("DROP LISTING IF EXISTS l"));
        assertEquals("Drop statement executed successfully (A already dropped).",
            statusOf("DROP ALERT IF EXISTS a CASCADE"));
        assertEquals(hinted("SQL compilation error:|Schema 'TEST_DB.NOSCH' does not exist or not authorized."),
            answer("DROP ALERT IF EXISTS test_db.nosch.a"));
        assertEquals(hinted("SQL compilation error:|Schema 'TEST_DB.NOSCH' does not exist or not authorized."),
            answer("DROP SECRET nosch.s"));
        assertEquals(hinted("SQL compilation error:|Database 'NOSUCHDB' does not exist or not authorized."),
            answer("DROP ALERT IF EXISTS nosuchdb.public.a"));
        assertEquals("SQL compilation error:|Object does not exist, or operation cannot be performed.",
            answer("DROP ALERT a.b.c.d"));
        assertEquals("SQL compilation error:|Object does not exist, or operation cannot be performed.",
            answer("DROP LISTING a.b"));
    }

    @Test
    public void theKindStillNeedsItsName() {
        assertEquals("SQL compilation error:|syntax error line 1 at position 10 unexpected '<EOF>'.",
            answer("DROP ALERT"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 20 unexpected '<EOF>'.",
            answer("DROP ALERT IF EXISTS"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 13 unexpected 'b'.",
            answer("DROP ALERT a b"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 5 unexpected 'ALERT'.",
            answer("SHOW ALERT"));
    }
}
