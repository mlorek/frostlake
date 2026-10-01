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
import dev.frostlake.storage.ResultSetColumn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code SHOW MODELS} is a listing, not a class name. Frostlake read the plural word as the name of a
 * class it does not model and refused the statement — where a listing that REFUSES is a different
 * thing from one that is EMPTY, and a client reading the account's columns back through RESULT_SCAN
 * needs the names to exist either way.
 *
 * <p>MODELS is a keyword after DROP, as the other listing words are, and STILL a name: the account
 * takes {@code models} as a column name and as a table name.
 */
public class ShowModelsListingTest extends BaseDatabaseTest {

    /** Live's layout, column for column. */
    private static final String LAYOUT = "created_on,name,model_type,database_name,schema_name,"
        + "comment,owner,default_version_name,versions,aliases";

    /** The listing's column names, joined. */
    private String layoutOf(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final ResultSetColumn col : rs.getColumns()) {
            if (out.length() > 0) {
                out.append(",");
            }
            out.append(col.getName());
        }
        return out.toString();
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

    /** The listing answers its layout, under every scope. */
    @Test
    public void showModelsAnswersItsLayout() {
        assertEquals(LAYOUT, layoutOf("SHOW MODELS"));
        assertEquals(LAYOUT, layoutOf("SHOW MODELS IN ACCOUNT"));
        assertEquals(LAYOUT, layoutOf("SHOW MODELS LIKE 'x%'"));
        assertEquals(LAYOUT, layoutOf("SHOW MODELS IN SCHEMA test_db.test_schema"));
    }

    /** A schema holding no model answers no rows, rather than refusing. */
    @Test
    public void aSchemaWithNoModelAnswersNoRows() {
        final ResultSet rs = engine.executeQuery("SHOW MODELS IN SCHEMA test_db.test_schema");
        assertTrue(!rs.next(), "no rows");
    }

    /** MODELS is a keyword where a class name stands, refused at the word. */
    @Test
    public void modelsIsAKeywordAfterDrop() {
        final String answer = outcome("DROP MODELS m");
        assertTrue(answer.contains("syntax error line 1 at position 5 unexpected 'MODELS'"), answer);
    }

    /** And still a name: a column called models, and a table called models. */
    @Test
    public void modelsIsStillAName() {
        final ResultSet column = engine.executeQuery("SELECT models FROM (SELECT 1 AS models)");
        assertEquals("MODELS", column.getColumns().get(0).getName());

        engine.execute("CREATE OR REPLACE TABLE models (a INT)");
        assertEquals("OK", outcome("SELECT a FROM models"));
        engine.execute("DROP TABLE IF EXISTS models");
    }
}
