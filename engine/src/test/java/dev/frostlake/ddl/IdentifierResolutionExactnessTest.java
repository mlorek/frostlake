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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A SQL reference resolves to a name — bare folded to upper case, quoted kept verbatim — and that name
 * must then match a stored one EXACTLY. Measured on every leg against a live account:
 *
 * <pre>
 *   SELECT * FROM "mixedDb"."mixedSch"."mixedTbl"   OK
 *   ... FROM mixedDb.…                              Database 'MIXEDDB' does not exist or not authorized.
 *   ... FROM "mixedDb".mixedSch.…                   Schema '…MIXEDSCH' does not exist or not authorized.
 *   ... FROM "mixedDb"."mixedSch".mixedTbl          Object '…MIXEDTBL' does not exist or not authorized.
 *   SELECT mixedCol FROM …                          invalid identifier 'MIXEDCOL'
 *   SELECT * FROM db.public."plain"                 Object '…"plain"' — a quoted reference to an
 *                                                   unquoted-and-therefore-upper-cased table misses too
 * </pre>
 *
 * <p>Frostlake used to accept every one of those: the catalog folded both the stored name and the lookup
 * key, so mixedDb and MIXEDDB were the same object. Worse, a database's name was folded when it was
 * CREATED, so {@code CREATE DATABASE "mixedDb"} produced one called MIXEDDB and the spelling was gone.
 *
 * <p>The exactness lives at the boundary where a reference meets the catalog, NOT in
 * {@code Catalog.getDatabase} / {@code Database.getSchema} / {@code Schema.getTable}: those are an
 * internal Java API that engine plumbing and tests call with whatever case is at hand, the same
 * distinction that keeps {@code Table}'s column accessors case-insensitive.
 */
public class IdentifierResolutionExactnessTest extends BaseDatabaseTest {

    /** A database, schema, table and column each created with a quoted, lower-case name. */
    private void mixedCaseObjects() {
        // OR REPLACE, as BaseDatabaseTest does for test_db: this database lives OUTSIDE test_db,
        // so the per-test recreate never clears it and a plain CREATE fails on every rerun.
        engine.execute("CREATE OR REPLACE DATABASE \"mixedDb\"");
        engine.execute("USE DATABASE \"mixedDb\"");
        engine.execute("CREATE SCHEMA \"mixedSch\"");
        engine.execute("CREATE TABLE \"mixedSch\".\"mixedTbl\" (\"mixedCol\" INTEGER)");
    }

    private String refusalOf(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        return String.valueOf(e.getMessage());
    }

    /** A quoted name survives creation — folding it there is what made every other check unwinnable. */
    @Test
    public void aQuotedDatabaseKeepsItsName() {
        mixedCaseObjects();
        final ResultSet databases = engine.executeQuery("SHOW DATABASES");
        final int name = databases.getColumnIndex("name");
        boolean found = false;
        for (final Row row : databases.getRows()) {
            found = found || "mixedDb".equals(String.valueOf(row.getValue(name)));
        }
        assertTrue(found, "CREATE DATABASE \"mixedDb\" must store mixedDb, not MIXEDDB");
    }

    /** Fully quoted, every leg matches. */
    @Test
    public void theFullyQuotedReferenceResolves() {
        mixedCaseObjects();
        assertEquals(0, engine.executeQuery(
            "SELECT * FROM \"mixedDb\".\"mixedSch\".\"mixedTbl\"").getRowCount());
    }

    /** Each leg refused on its own, under the kind live names it by. */
    @Test
    public void eachLegIsMatchedExactly() {
        mixedCaseObjects();
        assertTrue(refusalOf("SELECT * FROM mixedDb.\"mixedSch\".\"mixedTbl\"")
            .contains("Database 'MIXEDDB' does not exist or not authorized."), "database leg");
        assertTrue(refusalOf("SELECT * FROM \"mixedDb\".mixedSch.\"mixedTbl\"")
            .contains("MIXEDSCH' does not exist or not authorized."), "schema leg");
        assertTrue(refusalOf("SELECT * FROM \"mixedDb\".\"mixedSch\".mixedTbl")
            .contains("MIXEDTBL' does not exist or not authorized."), "table leg");
        assertTrue(refusalOf("SELECT mixedCol FROM \"mixedDb\".\"mixedSch\".\"mixedTbl\"")
            .contains("invalid identifier 'MIXEDCOL'"), "column leg");
    }

    /** And the other direction: a quoted reference does not reach an unquoted-and-folded name. */
    @Test
    public void aQuotedReferenceDoesNotReachAFoldedName() {
        engine.execute("CREATE TABLE plain (a INTEGER)");
        assertEquals(0, engine.executeQuery("SELECT * FROM plain").getRowCount());
        assertTrue(refusalOf("SELECT * FROM \"plain\"").contains("does not exist or not authorized."),
            "\"plain\" must not reach the stored PLAIN");
    }

    /** The SHOW scope resolves its schema the same way. */
    @Test
    public void theShowScopeIsMatchedExactly() {
        mixedCaseObjects();
        assertEquals(1, engine.executeQuery(
            "SHOW TABLES IN SCHEMA \"mixedDb\".\"mixedSch\"").getRowCount());
        assertTrue(refusalOf("SHOW TABLES IN SCHEMA \"mixedDb\".mixedSch").contains("does not exist"));
    }

    /**
     * A name that arrives as a runtime STRING is an identifier reference too, so it folds the same way —
     * {@code IDENTIFIER('t')} reaches T. These used to work only because lookup was case-insensitive.
     */
    @Test
    public void aNameGivenAsAStringFoldsLikeAReference() {
        engine.execute("CREATE TABLE plain (a INTEGER)");
        engine.execute("INSERT INTO plain VALUES (1)");
        assertEquals(1, engine.executeQuery("SELECT * FROM IDENTIFIER('plain')").getRowCount());
        assertEquals(1, engine.executeQuery("SELECT * FROM IDENTIFIER('PLAIN')").getRowCount());
        assertEquals(1, engine.executeQuery("SELECT * FROM TABLE('plain')").getRowCount());
        assertTrue(String.valueOf(engine.executeQuery("SELECT GET_DDL('TABLE', 'plain')")
            .getRows().get(0).getValue(0)).toUpperCase().contains("PLAIN"));
    }

    /** SHOW TABLES / VIEWS / STAGES / FILE FORMATS / DYNAMIC TABLES / OBJECTS: live does not say what was missing. */
    private static final List<String> GENERIC_KINDS =
        List.of("TABLES", "VIEWS", "STAGES", "FILE FORMATS", "DYNAMIC TABLES", "OBJECTS");

    /** Every other kind names the schema it could not reach. */
    private static final List<String> NAMING_KINDS =
        List.of("PIPES", "STREAMS", "TASKS", "SEQUENCES", "TAGS", "MASKING POLICIES",
            "ROW ACCESS POLICIES", "MATERIALIZED VIEWS", "PROCEDURES", "FUNCTIONS");

    /**
     * Which sentence a SHOW gives for an unreachable scope is a property of the KIND, measured live and
     * consistent across {@code IN SCHEMA db.missing}, {@code IN SCHEMA missing} and
     * {@code IN DATABASE missing}. Frostlake named the schema for all of them.
     */
    @Test
    public void aMissingShowScopeIsReportedPerKind() {
        for (final String kind : GENERIC_KINDS) {
            assertEquals("SQL compilation error:\nObject does not exist, or operation cannot be performed.",
                refusalOf("SHOW " + kind + " IN SCHEMA test_db.nosuchschema"), kind);
        }
        for (final String kind : NAMING_KINDS) {
            assertTrue(refusalOf("SHOW " + kind + " IN SCHEMA test_db.nosuchschema")
                    .contains("Schema 'TEST_DB.NOSUCHSCHEMA' does not exist or not authorized."),
                kind + " names the schema on live");
        }
    }
}
