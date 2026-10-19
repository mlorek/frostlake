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

import static dev.frostlake.query.QueryAnswers.answer;
import static dev.frostlake.query.QueryAnswers.assertCells;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * A SHOW listing's scope names its schema and its database exactly: a quoted {@code "ss"} reaches no schema created
 * as SS, and the listing is refused as it is for a scope that names nothing — in the generic sentence or naming the
 * schema or the database, kind by kind (live-verified).
 */
public class ShowScopeExactNamesTest extends BaseDatabaseTest {

    private static final String GENERIC =
        "SQL compilation error:|Object does not exist, or operation cannot be performed.";
    private static final String NO_SCHEMA =
        "SQL compilation error:|Schema 'TEST_DB.\"ss\"' does not exist or not authorized.";
    private static final String NO_DATABASE =
        "SQL compilation error:|Database '\"test_db\"' does not exist or not authorized.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE SCHEMA ss");
        engine.execute("CREATE TABLE ss.t1 (a INT)");
        engine.execute("CREATE VIEW ss.v1 AS SELECT a FROM ss.t1");
        engine.execute("CREATE SEQUENCE ss.q1");
        engine.execute("CREATE STAGE ss.st1");
        engine.execute("CREATE FUNCTION ss.f1() RETURNS INT AS '1'");
        engine.execute("CREATE STREAM ss.s1 ON TABLE ss.t1");
    }

    @Test
    public void aSchemaNamedInAnotherCaseIsNoScope() {
        assertCells(engine, new String[][] {
            {"SHOW TABLES IN SCHEMA \"ss\"", GENERIC},
            {"SHOW TERSE VIEWS IN SCHEMA \"ss\"", GENERIC},
            {"SHOW OBJECTS IN SCHEMA \"ss\"", GENERIC},
            {"SHOW STAGES IN SCHEMA \"ss\"", GENERIC},
            {"SHOW TABLES IN \"ss\"", GENERIC},
            {"SHOW TABLES IN SCHEMA test_db.\"ss\"", GENERIC},
            {"SHOW COLUMNS IN SCHEMA \"ss\"", "SQL compilation error:|Must specify the full search path starting from"
                + " database for ss"},
            {"SHOW TABLES IN SCHEMA \"SS\" ->> SELECT \"name\" FROM $1", "T1"},
        });
        assertRefusedWith("SHOW SEQUENCES IN SCHEMA \"ss\"", NO_SCHEMA);
        assertRefusedWith("SHOW USER FUNCTIONS IN SCHEMA \"ss\"", NO_SCHEMA);
        assertRefusedWith("SHOW STREAMS IN SCHEMA \"ss\"", NO_SCHEMA);
        assertRefusedWith("SHOW MATERIALIZED VIEWS IN SCHEMA \"ss\"", NO_SCHEMA);
    }

    @Test
    public void aDatabaseNamedInAnotherCaseIsNoScope() {
        assertCells(engine, new String[][] {
            {"SHOW TABLES IN DATABASE \"test_db\"", GENERIC},
            {"SHOW SCHEMAS IN DATABASE \"test_db\"", GENERIC},
            {"SHOW FILE FORMATS IN DATABASE \"test_db\"", GENERIC},
            {"SHOW TABLES IN DATABASE \"TEST_DB\" ->> SELECT \"name\", \"schema_name\" FROM $1 ORDER BY 1", "T1, SS"},
        });
        assertRefusedWith("SHOW SEQUENCES IN DATABASE \"test_db\"", NO_DATABASE);
        assertRefusedWith("SHOW COLUMNS IN DATABASE \"test_db\"", NO_DATABASE);
        assertRefusedWith("SHOW TAGS IN DATABASE \"test_db\"", NO_DATABASE);
    }

    /** Asserts a refusal opens with {@code sentence}; the privilege hint after it names the session's role. */
    private void assertRefusedWith(final String sql, final String sentence) {
        final String refused = answer(engine, sql);
        assertTrue(refused.startsWith(sentence), sql + " -> " + refused);
    }
}
