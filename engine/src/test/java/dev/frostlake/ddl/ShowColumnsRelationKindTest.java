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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SHOW COLUMNS' relation-kind keyword is NOT a filter. TABLE, VIEW and the bare form each resolve a
 * table, a view or a materialized view alike — the same interchangeability DESCRIBE has — and the
 * keyword decides only two things: whether a fully-qualified name is demanded, and the noun in the
 * refusal when nothing by that name exists.
 *
 * <p>The VIEW spelling is the odd one: it requires the FULL search path, and requires it whatever the
 * object turns out to be, a plain table included. That is a property of the keyword, not of the
 * relation.
 */
public class ShowColumnsRelationKindTest extends BaseDatabaseTest {

    private static final String QUALIFIED = "test_db.test_schema.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE rk_base (id NUMBER, label VARCHAR(20))");
        engine.execute("CREATE VIEW rk_view AS SELECT id FROM rk_base");
        engine.execute("CREATE MATERIALIZED VIEW rk_mv AS SELECT id, label FROM rk_base");
    }

    private ResultSet columns(final String sql) {
        return engine.executeQuery(sql);
    }

    /** The bare form takes any relation kind, unqualified. */
    @Test
    public void theBareFormResolvesEveryRelationKind() {
        assertEquals(2, columns("SHOW COLUMNS IN rk_base").getRows().size());
        assertEquals(1, columns("SHOW COLUMNS IN rk_view").getRows().size());
        assertEquals(2, columns("SHOW COLUMNS IN rk_mv").getRows().size());
    }

    /** So does the TABLE spelling — over a view and a materialized view, qualified or not. */
    @Test
    public void theTableSpellingResolvesAViewAndAMaterializedView() {
        assertEquals(1, columns("SHOW COLUMNS IN TABLE rk_view").getRows().size());
        assertEquals(2, columns("SHOW COLUMNS IN TABLE rk_mv").getRows().size());
        assertEquals(1, columns("SHOW COLUMNS IN TABLE " + QUALIFIED + "rk_view").getRows().size());
        assertEquals(2, columns("SHOW COLUMNS IN TABLE " + QUALIFIED + "rk_mv").getRows().size());
    }

    /** And so does the VIEW spelling, over a plain TABLE as readily as over a view. */
    @Test
    public void theViewSpellingResolvesATableAndAMaterializedView() {
        assertEquals(2, columns("SHOW COLUMNS IN VIEW " + QUALIFIED + "rk_base").getRows().size());
        assertEquals(1, columns("SHOW COLUMNS IN VIEW " + QUALIFIED + "rk_view").getRows().size());
        assertEquals(2, columns("SHOW COLUMNS IN VIEW " + QUALIFIED + "rk_mv").getRows().size());
    }

    /**
     * The VIEW spelling demands the full search path for EVERY kind — including a plain table, which
     * the TABLE spelling happily takes unqualified.
     */
    @Test
    public void theViewSpellingDemandsTheFullSearchPathWhateverTheObjectIs() {
        for (final String name : new String[]{"rk_base", "rk_view", "rk_mv"}) {
            final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    columns("SHOW COLUMNS IN VIEW " + name);
                }
            });
            assertTrue(e.getMessage().contains(
                    "Must specify the full search path starting from database for " + name.toUpperCase()),
                "unqualified VIEW spelling over " + name + " said: " + e.getMessage());
        }
    }

    /** A materialized view's row names the view itself and its own container, like any other. */
    @Test
    public void theMaterializedViewsRowNamesItselfAndItsContainer() {
        final ResultSet rs = columns("SHOW COLUMNS IN TABLE " + QUALIFIED + "rk_mv");
        assertEquals("RK_MV", cell(rs, soleRowWhere(rs, "column_name", "ID"), "table_name"));
        assertEquals("TEST_SCHEMA", cell(rs, soleRowWhere(rs, "column_name", "ID"), "schema_name"));
        assertEquals("TEST_DB", cell(rs, soleRowWhere(rs, "column_name", "ID"), "database_name"));
        assertEquals("COLUMN", cell(rs, soleRowWhere(rs, "column_name", "ID"), "kind"));
    }

    /** The unnamed listing walks every relation in the schema, not just the tables. */
    @Test
    public void theUnnamedListingCoversAllThreeKinds() {
        // rk_base(2) + rk_view(1) + rk_mv(2)
        assertEquals(5, columns("SHOW COLUMNS").getRows().size());
    }

    /**
     * When nothing by the name exists the refusal names the kind the STATEMENT spelled, and echoes the
     * name exactly as written — an unqualified name is not expanded to its search path.
     */
    @Test
    public void theRefusalSpeaksTheNamedKindAndEchoesTheNameAsWritten() {
        assertEquals("Table 'NOSUCH' does not exist or not authorized.",
            refusalOf("SHOW COLUMNS IN nosuch"));
        assertEquals("Table 'NOSUCH' does not exist or not authorized.",
            refusalOf("SHOW COLUMNS IN TABLE nosuch"));
        assertEquals("Table 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized.",
            refusalOf("SHOW COLUMNS IN TABLE " + QUALIFIED + "nosuch"));
        assertEquals("View 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized.",
            refusalOf("SHOW COLUMNS IN VIEW " + QUALIFIED + "nosuch"));
    }

    /** The refusal's sentence, with the SQL-compilation prefix stripped. */
    private String refusalOf(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                columns(sql);
            }
        });
        final String message = e.getMessage().replace('\n', ' ');
        final int at = message.indexOf("error: ");
        return at < 0 ? message : message.substring(at + "error: ".length()).trim();
    }
}
