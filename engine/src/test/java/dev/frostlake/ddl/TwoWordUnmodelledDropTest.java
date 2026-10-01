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
 * DROP of the two-word kinds live has and Frostlake does not model — an external table, a failover group and a
 * replication group: the name resolves as any object's and then misses, which IF EXISTS forgives; an external table
 * whose name another relation holds is refused for the other kind, IF EXISTS or not; a failover or replication group
 * belongs to the account and is named as a replication group. A second word that completes no kind is a syntax error
 * at that word. Every cell is live-verified.
 *
 * <p>The status sentence is read off a raw JDBC connection in live mode, where the harness reports a statement's
 * count instead.
 */
public class TwoWordUnmodelledDropTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t1 (x INT)");
        engine.execute("CREATE VIEW v1 AS SELECT 1 AS x");
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

    private static String syntaxError(final int position, final String token) {
        return "SQL compilation error:|syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void anExternalTableIsMissing() {
        assertEquals(missing("External table", "TEST_DB.TEST_SCHEMA.X"), answer("DROP EXTERNAL TABLE x"));
        assertEquals(missing("External table", "TEST_DB.TEST_SCHEMA.X"), answer("drop external table x"));
        assertEquals(missing("External table", "TEST_DB.TEST_SCHEMA.\"x\""), answer("DROP EXTERNAL TABLE \"x\""));
        assertEquals(missing("External table", "TEST_DB.TEST_SCHEMA.X"),
            answer("DROP EXTERNAL TABLE IDENTIFIER('x')"));
        assertEquals(missing("External table", "TEST_DB.TEST_SCHEMA.X"), answer("DROP EXTERNAL TABLE test_schema.x"));
        assertEquals(missing("External table", "TEST_DB.TEST_SCHEMA.X"), answer("DROP EXTERNAL TABLE x (INT)"));
        assertEquals(missing("External table", "TEST_DB.TEST_SCHEMA.X"), answer("DROP EXTERNAL TABLE x CASCADE"));
        assertEquals(missing("External table", "TEST_DB.TEST_SCHEMA.X"), answer("DROP EXTERNAL TABLE x RESTRICT"));
        assertEquals(hinted("SQL compilation error:|Schema 'TEST_DB.NOSCH' does not exist or not authorized."),
            answer("DROP EXTERNAL TABLE nosch.x"));
        assertEquals(hinted("SQL compilation error:|Database 'NOSUCHDB' does not exist or not authorized."),
            answer("DROP EXTERNAL TABLE nosuchdb.public.x"));
        assertEquals("SQL compilation error:|Object does not exist, or operation cannot be performed.",
            answer("DROP EXTERNAL TABLE a.b.c.d"));
        assertEquals("Drop statement executed successfully (X already dropped).",
            statusOf("DROP EXTERNAL TABLE IF EXISTS x"));
    }

    @Test
    public void aRelationOfAnotherKindIsRefusedForItsKind() {
        assertEquals("SQL compilation error: Object found is of type 'TABLE', not specified type 'EXTERNAL_TABLE'.",
            answer("DROP EXTERNAL TABLE t1"));
        assertEquals("SQL compilation error: Object found is of type 'TABLE', not specified type 'EXTERNAL_TABLE'.",
            answer("DROP EXTERNAL TABLE IF EXISTS t1"));
        assertEquals("SQL compilation error: Object found is of type 'TABLE', not specified type 'EXTERNAL_TABLE'.",
            answer("DROP EXTERNAL TABLE t1 CASCADE"));
        assertEquals("SQL compilation error: Object found is of type 'VIEW', not specified type 'EXTERNAL_TABLE'.",
            answer("DROP EXTERNAL TABLE v1"));
        assertEquals("0", answer("SELECT COUNT(*) FROM t1"));
    }

    @Test
    public void aFailoverOrReplicationGroupIsAMissingReplicationGroup() {
        assertEquals(missing("Replication group", "G"), answer("DROP FAILOVER GROUP g"));
        assertEquals(missing("Replication group", "\"g\""), answer("DROP FAILOVER GROUP \"g\""));
        assertEquals(missing("Replication group", "G"), answer("DROP FAILOVER GROUP IDENTIFIER('g')"));
        assertEquals(missing("Replication group", "G"), answer("DROP FAILOVER GROUP g CASCADE"));
        assertEquals(missing("Replication group", "G"), answer("DROP FAILOVER GROUP g RESTRICT"));
        assertEquals(missing("Replication group", "G"), answer("DROP REPLICATION GROUP g"));
        assertEquals(missing("Replication group", "\"g\""), answer("DROP REPLICATION GROUP \"g\""));
        assertEquals("SQL compilation error:|Object does not exist, or operation cannot be performed.",
            answer("DROP FAILOVER GROUP a.g"));
        assertEquals("SQL compilation error:|Object does not exist, or operation cannot be performed.",
            answer("DROP REPLICATION GROUP a.b.c"));
        assertEquals("Drop statement executed successfully (G already dropped).",
            statusOf("DROP FAILOVER GROUP IF EXISTS g"));
        assertEquals("Drop statement executed successfully (G already dropped).",
            statusOf("DROP REPLICATION GROUP IF EXISTS g"));
    }

    @Test
    public void theKindStillNeedsItsNameAndItsOwnSecondWord() {
        assertEquals(syntaxError(19, "<EOF>"), answer("DROP EXTERNAL TABLE"));
        assertEquals(syntaxError(19, "<EOF>"), answer("DROP FAILOVER GROUP"));
        assertEquals(syntaxError(22, "<EOF>"), answer("DROP REPLICATION GROUP"));
        assertEquals(syntaxError(22, "y"), answer("DROP EXTERNAL TABLE x y"));
        assertEquals(syntaxError(22, "y"), answer("DROP EXTERNAL TABLE x y z"));
        assertEquals(syntaxError(25, "y"), answer("DROP REPLICATION GROUP g y"));
        assertEquals(syntaxError(14, "GROUP"), answer("DROP EXTERNAL GROUP x"));
        assertEquals(syntaxError(14, "TABLE"), answer("DROP FAILOVER TABLE x"));
        assertEquals(syntaxError(17, "TABLE"), answer("DROP REPLICATION TABLE x"));
        assertEquals(syntaxError(11, "TABLE"), answer("DROP ALERT TABLE x"));
        assertEquals(syntaxError(9, "TABLE"), answer("DROP foo TABLE x"));
        assertEquals(syntaxError(9, "GROUP"), answer("DROP foo GROUP x"));
        assertEquals(syntaxError(14, "VIEW"), answer("DROP EXTERNAL VIEW x"));
        assertEquals(syntaxError(14, "y"), answer("DROP FAILOVER y"));
    }
}
