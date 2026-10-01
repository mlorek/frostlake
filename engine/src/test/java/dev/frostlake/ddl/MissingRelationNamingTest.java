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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * How a missing relation is NAMED, which is neither uniform across statements nor simply the name as
 * written. Both halves are live-measured:
 *
 * <ul>
 *   <li>A bare name stays BARE for SELECT, UPDATE, DELETE and INSERT — but is expanded to
 *       database.schema.name for TRUNCATE and DROP.</li>
 *   <li>A PARTIALLY qualified name is always expanded to the full three parts, whichever statement
 *       named it.</li>
 *   <li>The KIND word differs: SELECT, UPDATE and DELETE say Object; INSERT, TRUNCATE and DROP say
 *       Table. A missing schema and a missing database have their own sentences, and the schema one
 *       carries its database while the database one stands alone.</li>
 * </ul>
 *
 * <p>KNOWN DIVERGENCE, deliberately not asserted: inside a CREATE VIEW body live expands even a bare
 * name to its full three parts, where Frostlake leaves it as written. That is the one row of this
 * matrix the two sides disagree on.
 */
public class MissingRelationNamingTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE mo_t (k NUMBER)");
    }

    private String refusalOf(final String sql) {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
        return ex.getMessage();
    }

    private String missing(final String kind, final String name) {
        return hinted("SQL compilation error:\n" + kind + " '" + name + "' does not exist or not authorized.");
    }

    /** A read of a bare name reports it bare, as an Object. */
    @Test
    public void aBareNameStaysBareForARead() {
        assertEquals(missing("Object", "NOPE"), refusalOf("SELECT * FROM nope"));
        assertEquals(missing("Object", "NOPE"), refusalOf("UPDATE nope SET k = 1"));
        assertEquals(missing("Object", "NOPE"), refusalOf("DELETE FROM nope"));
    }

    /** A partially qualified name is expanded to all three parts. */
    @Test
    public void aPartiallyQualifiedNameIsExpanded() {
        assertEquals(missing("Object", "TEST_DB.TEST_SCHEMA.NOPE"),
            refusalOf("SELECT * FROM test_schema.nope"));
        assertEquals(missing("Object", "TEST_DB.TEST_SCHEMA.NOPE"),
            refusalOf("SELECT * FROM test_db.test_schema.nope"));
    }

    /** INSERT says Table rather than Object, and still reports the bare name. */
    @Test
    public void insertSaysTable() {
        assertEquals(missing("Table", "NOPE"), refusalOf("INSERT INTO nope VALUES (1)"));
    }

    /** TRUNCATE and DROP say Table AND expand a bare name to its full three parts. */
    @Test
    public void truncateAndDropExpandABareName() {
        assertEquals(missing("Table", "TEST_DB.TEST_SCHEMA.NOPE"), refusalOf("TRUNCATE TABLE nope"));
        assertEquals(missing("Table", "TEST_DB.TEST_SCHEMA.NOPE"), refusalOf("DROP TABLE nope"));
    }

    /** A missing schema carries its database; a missing database stands alone. */
    @Test
    public void aMissingContainerHasItsOwnSentence() {
        assertEquals(missing("Schema", "TEST_DB.NOSUCHSCHEMA"),
            refusalOf("SELECT * FROM nosuchschema.nope"));
        assertEquals(missing("Database", "NOSUCHDB"),
            refusalOf("SELECT * FROM nosuchdb.public.nope"));
    }
}
