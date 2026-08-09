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
import org.junit.jupiter.api.function.Executable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two scope forms every schema-object SHOW accepts, measured against live:
 *
 * <pre>
 *   SHOW &lt;kind&gt; IN SCHEMA other_db.public   answers about other_db, from a session pointed elsewhere
 *   SHOW &lt;kind&gt; IN ACCOUNT                  spans every database, not just the current schema
 * </pre>
 *
 * <p>Both used to be broken in the same place. The qualified form looked the whole dotted string up as
 * a schema NAME under the current database and reported {@code Schema 'CUR.OTHER.PUBLIC' does not
 * exist}; {@code IN ACCOUNT} was a syntax error for most kinds and, where it parsed, silently returned
 * the current schema's rows — an empty listing whenever the session sat in a different database.
 */
public class ShowQualifiedAndAccountScopeTest extends BaseDatabaseTest {

    /**
     * One object of each kind in {@code fl_scope_a.public}, one more in {@code fl_scope_b.public}, and the
     * session left pointing at {@code fl_scope_b} — so a listing that ignores its scope and answers about
     * the current schema is distinguishable from one that honours it.
     */
    private void twoDatabasesOfObjects() {
        engine.execute("CREATE OR REPLACE DATABASE fl_scope_a");
        engine.execute("USE DATABASE fl_scope_a");
        engine.execute("USE SCHEMA public");
        engine.execute("CREATE STAGE a_stage");
        engine.execute("CREATE TABLE a_tbl (id INTEGER)");
        engine.execute("CREATE STREAM a_stream ON TABLE a_tbl");
        engine.execute("CREATE SEQUENCE a_seq");
        engine.execute("CREATE FILE FORMAT a_ff TYPE = CSV");
        engine.execute("CREATE TAG a_tag");
        engine.execute("CREATE MASKING POLICY a_mp AS (v VARCHAR) RETURNS VARCHAR -> v");
        engine.execute("CREATE ROW ACCESS POLICY a_rap AS (v VARCHAR) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE TASK a_task SCHEDULE = '1 MINUTE' AS SELECT 1");

        engine.execute("CREATE OR REPLACE DATABASE fl_scope_b");
        engine.execute("USE DATABASE fl_scope_b");
        engine.execute("USE SCHEMA public");
        engine.execute("CREATE STAGE b_stage");
        engine.execute("CREATE TABLE b_tbl (id INTEGER)");
        engine.execute("CREATE STREAM b_stream ON TABLE b_tbl");
        engine.execute("CREATE SEQUENCE b_seq");
        engine.execute("CREATE FILE FORMAT b_ff TYPE = CSV");
        engine.execute("CREATE TAG b_tag");
        engine.execute("CREATE MASKING POLICY b_mp AS (v VARCHAR) RETURNS VARCHAR -> v");
        engine.execute("CREATE ROW ACCESS POLICY b_rap AS (v VARCHAR) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE TASK b_task SCHEDULE = '1 MINUTE' AS SELECT 1");
    }

    private Set<String> names(final ResultSet rs) {
        final int nameIndex = rs.getColumnIndex("name");
        final Set<String> out = new HashSet<String>();
        for (final Row row : rs.getRows()) {
            out.add(String.valueOf(row.getValue(nameIndex)).toUpperCase());
        }
        return out;
    }

    /** The kinds that take the plain {@code SHOW <kind> IN …} shape, and the object each one owns. */
    private static final List<String> KINDS = List.of(
        "STAGES", "STREAMS", "TASKS", "SEQUENCES", "FILE FORMATS",
        "TAGS", "MASKING POLICIES", "ROW ACCESS POLICIES");

    private static final List<String> IN_A = List.of(
        "A_STAGE", "A_STREAM", "A_TASK", "A_SEQ", "A_FF", "A_TAG", "A_MP", "A_RAP");

    private static final List<String> IN_B = List.of(
        "B_STAGE", "B_STREAM", "B_TASK", "B_SEQ", "B_FF", "B_TAG", "B_MP", "B_RAP");

    /**
     * A qualified schema reference names its own database. The session sits in {@code fl_scope_b}, so a
     * listing that assumed the current database would have failed outright.
     */
    @Test
    public void qualifiedSchemaReferenceAnswersAboutTheNamedDatabase() {
        twoDatabasesOfObjects();
        for (int i = 0; i < KINDS.size(); i++) {
            final String kind = KINDS.get(i);
            final Set<String> listed = names(engine.executeQuery("SHOW " + kind + " IN SCHEMA fl_scope_a.public"));
            assertTrue(listed.contains(IN_A.get(i)),
                "SHOW " + kind + " IN SCHEMA fl_scope_a.public must list " + IN_A.get(i) + ", listed " + listed);
            assertTrue(!listed.contains(IN_B.get(i)),
                "SHOW " + kind + " IN SCHEMA fl_scope_a.public must not reach fl_scope_b, listed " + listed);
        }
    }

    /** IN ACCOUNT spans every database — both objects, from a session that can only see one. */
    @Test
    public void inAccountSpansEveryDatabase() {
        twoDatabasesOfObjects();
        for (int i = 0; i < KINDS.size(); i++) {
            final String kind = KINDS.get(i);
            final Set<String> listed = names(engine.executeQuery("SHOW " + kind + " IN ACCOUNT"));
            assertTrue(listed.contains(IN_A.get(i)) && listed.contains(IN_B.get(i)),
                "SHOW " + kind + " IN ACCOUNT must list both databases' objects, listed " + listed);
        }
    }

    /** The unscoped listing stays scoped to the current schema, which is what makes IN ACCOUNT mean something. */
    @Test
    public void theUnscopedListingStillSeesOnlyTheCurrentSchema() {
        twoDatabasesOfObjects();
        for (int i = 0; i < KINDS.size(); i++) {
            final String kind = KINDS.get(i);
            final Set<String> listed = names(engine.executeQuery("SHOW " + kind));
            assertTrue(!listed.contains(IN_A.get(i)),
                "unscoped SHOW " + kind + " must not reach fl_scope_a from fl_scope_b, listed " + listed);
        }
    }

    /** The kinds carrying a LIKE clause take both scopes too, and the filter still applies. */
    @Test
    public void likeBearingKindsTakeBothScopes() {
        twoDatabasesOfObjects();
        engine.execute("USE DATABASE fl_scope_b");
        engine.execute("USE SCHEMA public");
        assertEquals(Set.of("A_STAGE"),
            names(engine.executeQuery("SHOW STAGES LIKE 'A%' IN SCHEMA fl_scope_a.public")));

        // PIPES and CORTEX SEARCH SERVICES decode their LIKE before reading the scope: the filter must
        // survive the account-wide fold rather than being dropped along with the schema reference.
        assertEquals(0, engine.executeQuery("SHOW PIPES LIKE 'no_such_pipe%' IN ACCOUNT").getRowCount());
        assertEquals(0, engine.executeQuery(
            "SHOW CORTEX SEARCH SERVICES LIKE 'no_such_service%' IN ACCOUNT").getRowCount());
        // Row counts aside, the account-wide form must answer in the listing's own column shape.
        assertTrue(engine.executeQuery("SHOW DYNAMIC TABLES IN ACCOUNT").getColumnIndex("name") >= 0);
    }

    /**
     * A qualified reference to a schema that does not exist fails rather than listing nothing.
     *
     * <p>The message is asserted only on SHOW TAGS, because live does not use one message here. Measured:
     * SHOW TAGS names the schema, while SHOW STAGES, SHOW TABLES and SHOW … IN DATABASE all answer the
     * generic {@code Object does not exist, or operation cannot be performed.} — so all that is portable
     * across the kinds is that the statement fails at all.
     */
    @Test
    public void aQualifiedReferenceToAMissingSchemaStillFails() {
        twoDatabasesOfObjects();
        for (final String kind : KINDS) {
            assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery("SHOW " + kind + " IN SCHEMA fl_scope_a.nosuchschema");
                }
            }, "SHOW " + kind + " over a missing schema must fail, not list nothing");
        }
        final RuntimeException tagged = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SHOW TAGS IN SCHEMA fl_scope_a.nosuchschema");
            }
        });
        assertTrue(String.valueOf(tagged.getMessage())
                .contains("Schema 'FL_SCOPE_A.NOSUCHSCHEMA' does not exist or not authorized"),
            "SHOW TAGS names the schema on live: " + tagged.getMessage());
    }
}
