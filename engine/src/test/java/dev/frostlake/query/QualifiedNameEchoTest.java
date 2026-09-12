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

/**
 * A name that resolves to nothing is echoed the way it was WRITTEN, part by part — a quoted part keeps
 * its quotes and its case while an unquoted one folds and prints bare. The single-part form already
 * did this; a QUALIFIED one folded every part instead.
 *
 * <p>The cause is the same double canonicalisation that folded a quoted function name: the name handed
 * to the sentence is ALREADY canonical, its quotes removed and its case kept, and canonicalising it a
 * second time saw an unquoted {@code test_schema} with no quotes left to protect it. The single-part
 * case escaped only because the speller short-circuits when there is no dot to split on.
 */
public class QualifiedNameEchoTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE kw (a INT)");
        engine.execute("INSERT INTO kw VALUES (1)");
    }

    /** The message of the refusal a statement raises, or its answer when there is none. */
    private String refusalOf(final String sql) {
        try {
            engine.executeQuery(sql);
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
        return "accepted";
    }

    /** A quoted RELATION part keeps its case inside an otherwise unquoted path. */
    @Test
    public void aQuotedRelationPartKeepsItsCase() {
        assertEquals("SQL compilation error: Object 'TEST_DB.TEST_SCHEMA.\"kw\"'"
            + " does not exist or not authorized.",
            refusalOf("SELECT * FROM test_schema.\"kw\""));
    }

    /** And a quoted SCHEMA part does, in both the spellings that reach it. */
    @Test
    public void aQuotedSchemaPartKeepsItsCase() {
        assertEquals("SQL compilation error: Schema 'TEST_DB.\"test_schema\"'"
            + " does not exist or not authorized.",
            refusalOf("SELECT * FROM \"test_schema\".kw"));
        assertEquals("SQL compilation error: Schema 'TEST_DB.\"test_schema\"'"
            + " does not exist or not authorized.",
            refusalOf("SELECT * FROM \"test_schema\".\"kw\""));
    }

    /** The single-part form, which already behaved, and the unquoted path, which resolves. */
    @Test
    public void theSinglePartAndUnquotedFormsAreUnchanged() {
        assertEquals("SQL compilation error: Object '\"kw\"' does not exist or not authorized.",
            refusalOf("SELECT * FROM \"kw\""));
        assertEquals("accepted", refusalOf("SELECT * FROM test_schema.kw"));
    }
}
