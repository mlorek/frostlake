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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An alias replaces a relation's whole name, its database and schema included: a column reference or a star
 * that reaches an aliased relation through a qualified name is refused — {@code invalid identifier} at the
 * reference in every clause, {@code Object '…' does not exist} for a star — even where the alias folds to the
 * table's own name, while an unaliased relation is read through any suffix of its name. Live-verified.
 */
public class AliasedRelationQualifierTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE DATABASE P546_DB");
        engine.execute("CREATE OR REPLACE TABLE P546_DB.PUBLIC.T (x INT)");
        engine.execute("CREATE OR REPLACE TABLE P546_DB.PUBLIC.T2 (x INT, y INT)");
        engine.execute("INSERT INTO P546_DB.PUBLIC.T VALUES (1)");
        engine.execute("INSERT INTO P546_DB.PUBLIC.T2 VALUES (1, 2)");
    }

    @Override
    protected void teardownTest() {
        engine.execute("DROP DATABASE IF EXISTS P546_DB");
    }

    /** The one cell of a single-row query, as text. */
    private String scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    /** The message a refused statement carries. */
    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        return refused.getMessage();
    }

    private static String invalid(final int position, final String name) {
        return "SQL compilation error: error line 1 at position " + position + "\ninvalid identifier '" + name + "'";
    }

    private static String noObject(final String name) {
        return "SQL compilation error:\nObject '" + name + "' does not exist or not authorized.";
    }

    @Test
    public void aQualifiedNameDoesNotReachAnAliasedRelation() {
        assertEquals(invalid(7, "P546_DB.PUBLIC.T.X"), refusal("SELECT P546_DB.PUBLIC.T.x FROM P546_DB.PUBLIC.T t"));
        assertEquals(invalid(7, "PUBLIC.T.X"), refusal("SELECT PUBLIC.T.x FROM P546_DB.PUBLIC.T t"));
        assertEquals(invalid(7, "P546_DB.PUBLIC.T.X"), refusal("SELECT P546_DB..T.x FROM P546_DB..T t"));
        assertEquals(invalid(7, "P546_DB.PUBLIC.T.X"), refusal("SELECT P546_DB.PUBLIC.T.x FROM P546_DB.PUBLIC.T AS t"));
        assertEquals(invalid(7, "P546_DB.PUBLIC.T.X"),
            refusal("SELECT P546_DB.PUBLIC.T.x FROM P546_DB.PUBLIC.T t JOIN P546_DB.PUBLIC.T2 u ON t.x = u.x"));
        assertEquals(invalid(7, "PUBLIC.T.X"), refusal("SELECT PUBLIC.T.x + 1 FROM P546_DB.PUBLIC.T t"));
    }

    @Test
    public void everyClauseRefusesItAtTheReference() {
        assertEquals(invalid(10, "PUBLIC.T.X"), refusal("SELECT 1, PUBLIC.T.x FROM P546_DB.PUBLIC.T t"));
        assertEquals(invalid(39, "PUBLIC.T.X"), refusal("SELECT 1 FROM P546_DB.PUBLIC.T t WHERE PUBLIC.T.x = 1"));
        assertEquals(invalid(42, "PUBLIC.T.X"), refusal("SELECT x FROM P546_DB.PUBLIC.T t ORDER BY PUBLIC.T.x"));
        assertEquals(invalid(7, "PUBLIC.T.X"), refusal("SELECT PUBLIC.T.x FROM P546_DB.PUBLIC.T t GROUP BY PUBLIC.T.x"));
        assertEquals(invalid(7, "PUBLIC.T.X"), refusal("SELECT PUBLIC.T.x, missing FROM P546_DB.PUBLIC.T t"));
        assertEquals(invalid(7, "MISSING"), refusal("SELECT missing, PUBLIC.T.x FROM P546_DB.PUBLIC.T t"));
    }

    @Test
    public void theAliasAndAnUnaliasedNameStillRead() {
        assertEquals("1", scalar("SELECT T.x FROM P546_DB.PUBLIC.T t"));
        assertEquals("1", scalar("SELECT t.x FROM P546_DB.PUBLIC.T t"));
        assertEquals("1", scalar("SELECT P546_DB.PUBLIC.T.x FROM P546_DB.PUBLIC.T"));
        assertEquals("1", scalar("SELECT PUBLIC.T.x FROM P546_DB.PUBLIC.T"));
        assertEquals("2", scalar("SELECT P546_DB.PUBLIC.T2.y FROM P546_DB.PUBLIC.T t JOIN P546_DB.PUBLIC.T2 ON t.x = T2.x"));
    }

    @Test
    public void aStarThatNamesNoRelationIsNoObject() {
        assertEquals(noObject("PUBLIC.T"), refusal("SELECT PUBLIC.T.* FROM P546_DB.PUBLIC.T t"));
        assertEquals(noObject("P546_DB.PUBLIC.T"), refusal("SELECT P546_DB.PUBLIC.T.* FROM P546_DB.PUBLIC.T t"));
        assertEquals(noObject("P546_DB.PUBLIC.T"), refusal("SELECT P546_DB..T.* FROM P546_DB..T t"));
        assertEquals(noObject("PUBLIC.T"), refusal("SELECT 1, PUBLIC.T.* FROM P546_DB.PUBLIC.T t"));
        assertEquals(noObject("Q"), refusal("SELECT q.* FROM P546_DB.PUBLIC.T t"));
        assertEquals(noObject("NOSUCH.T"), refusal("SELECT nosuch.T.* FROM P546_DB.PUBLIC.T"));
        assertEquals("1", scalar("SELECT T.* FROM P546_DB.PUBLIC.T t"));
        assertEquals("1", scalar("SELECT PUBLIC.T.* FROM P546_DB.PUBLIC.T"));
    }
}
