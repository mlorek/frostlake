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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A dotted column reference is checked PART BY PART, however many parts it has. Frostlake asked the
 * question only of a single-part qualifier — the comment said multi-part ones "resolve further down" —
 * so {@code test_schema.nosuchtable.a} found the column by name alone and answered a row.
 *
 * <pre>
 *   SELECT test_schema.kw.a FROM kw          reads it — every part matches
 *   SELECT test_db.test_schema.kw.a FROM kw  four parts, the same
 *   SELECT test_schema.nosuchtable.a …       invalid identifier 'TEST_SCHEMA.NOSUCHTABLE.A'
 *   SELECT test_schema.kw.a FROM kw t        invalid identifier — an ALIAS REPLACES the name, and it
 *                                            replaces it however many parts precede it
 * </pre>
 */
public class QualifiedReferenceTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE kw (a INT, b INT)");
        engine.execute("INSERT INTO kw VALUES (1, 2)");
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return null;
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', ' ');
        }
    }

    private int value(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return ((Number) rs.getValue(0)).intValue();
    }

    /** A reference whose every part matches reads the column, at two, three and four parts. */
    @Test
    public void everyMatchingPartReadsTheColumn() {
        assertEquals(1, value("SELECT kw.a FROM kw"));
        assertEquals(1, value("SELECT test_schema.kw.a FROM kw"));
        assertEquals(1, value("SELECT test_db.test_schema.kw.a FROM kw"));
        assertEquals(1, value("SELECT t.a FROM kw t"));
    }

    /** A wrong COLUMN is refused whatever the qualifier's length, naming the whole path. */
    @Test
    public void aWrongColumnIsRefusedAtEveryLength() {
        assertTrue(String.valueOf(refusal("SELECT kw.nosuchcol FROM kw"))
            .contains("error line 1 at position 7 invalid identifier 'KW.NOSUCHCOL'"),
            refusal("SELECT kw.nosuchcol FROM kw"));
        assertTrue(String.valueOf(refusal("SELECT test_schema.kw.nosuchcol FROM kw"))
            .contains("error line 1 at position 7 invalid identifier 'TEST_SCHEMA.KW.NOSUCHCOL'"),
            refusal("SELECT test_schema.kw.nosuchcol FROM kw"));
        assertTrue(String.valueOf(refusal("SELECT test_db.test_schema.kw.nosuchcol FROM kw"))
            .contains("invalid identifier 'TEST_DB.TEST_SCHEMA.KW.NOSUCHCOL'"),
            refusal("SELECT test_db.test_schema.kw.nosuchcol FROM kw"));
    }

    /** A wrong TABLE part is refused too — the part the reference names must be the one in FROM. */
    @Test
    public void aWrongTablePartIsRefused() {
        assertTrue(String.valueOf(refusal("SELECT test_schema.nosuchtable.a FROM kw"))
            .contains("error line 1 at position 7 invalid identifier 'TEST_SCHEMA.NOSUCHTABLE.A'"),
            refusal("SELECT test_schema.nosuchtable.a FROM kw"));
    }

    /** And an ALIAS replaces the name however many parts precede it. */
    @Test
    public void anAliasReplacesTheQualifiedNameToo() {
        assertTrue(String.valueOf(refusal("SELECT kw.a FROM kw t"))
            .contains("invalid identifier 'KW.A'"), refusal("SELECT kw.a FROM kw t"));
        assertTrue(String.valueOf(refusal("SELECT test_schema.kw.a FROM kw t"))
            .contains("error line 1 at position 7 invalid identifier 'TEST_SCHEMA.KW.A'"),
            refusal("SELECT test_schema.kw.a FROM kw t"));
    }

    /**
     * The qualifier must be a contiguous right-SUFFIX of the relation's full name. Skipping a level is
     * refused as firmly as naming the wrong one — {@code test_db.kw.a} leaves out the schema, and live
     * will not have it.
     */
    @Test
    public void theQualifierMustBeASuffixOfTheFullName() {
        assertEquals(1, value("SELECT test_db.test_schema.kw.a FROM kw"));
        assertTrue(String.valueOf(refusal("SELECT nosuchschema.kw.a FROM kw"))
            .contains("error line 1 at position 7 invalid identifier 'NOSUCHSCHEMA.KW.A'"),
            refusal("SELECT nosuchschema.kw.a FROM kw"));
        assertTrue(String.valueOf(refusal("SELECT test_db.kw.a FROM kw"))
            .contains("error line 1 at position 7 invalid identifier 'TEST_DB.KW.A'"),
            refusal("SELECT test_db.kw.a FROM kw"));
    }

    /** And it is the relation's OWN name that counts, not the one currently in use. */
    @Test
    public void aCrossSchemaRelationIsQualifiedByItsOwnName() {
        engine.execute("CREATE SCHEMA IF NOT EXISTS other_schema");
        engine.execute("CREATE OR REPLACE TABLE other_schema.t (a INT)");
        engine.execute("INSERT INTO other_schema.t VALUES (2)");
        engine.execute("USE SCHEMA test_schema");
        assertEquals(2, value("SELECT t.a FROM other_schema.t"));
        assertEquals(2, value("SELECT other_schema.t.a FROM other_schema.t"));
        assertEquals(2, value("SELECT test_db.other_schema.t.a FROM other_schema.t"));
        assertTrue(String.valueOf(refusal("SELECT test_schema.t.a FROM other_schema.t"))
            .contains("invalid identifier 'TEST_SCHEMA.T.A'"),
            refusal("SELECT test_schema.t.a FROM other_schema.t"));
        assertTrue(String.valueOf(refusal("SELECT other_schema.t.a FROM other_schema.t x"))
            .contains("invalid identifier 'OTHER_SCHEMA.T.A'"),
            refusal("SELECT other_schema.t.a FROM other_schema.t x"));
    }

    /** A keyword after the dots is a name like any other, and refused the same way. */
    @Test
    public void aKeywordPartIsRefusedByName() {
        assertTrue(String.valueOf(refusal("SELECT test_schema.kw.case FROM kw"))
            .contains("invalid identifier 'TEST_SCHEMA.KW.CASE'"),
            refusal("SELECT test_schema.kw.case FROM kw"));
    }
}
