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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a refusal SPELLS a name, on the surfaces that name an object or a grouped reference. One rule
 * runs through all of them, measured spelling by spelling: the name is the CANONICAL one — resolved,
 * not as written — and it carries quotes only when it could not have been written without them.
 *
 * <pre>
 *   FROM "kw"              Object '"kw"' does not exist       lower case, so quoted
 *   FROM "KW"…"NOSUCHTBL"  Object 'NOSUCHTABLE' does not exist  upper case, so bare
 *   FROM "no such"         Object '"no such"'                 a space cannot be written bare
 *   FROM "1t"              Object '"1t"'                      nor can a leading digit
 *   COUNT(*), "A"          [KW.A] is not a valid group by …   the RESOLVED name, unquoted
 *   COUNT(*), "a" (q2)     [Q2."a"]                           quoted only because it must be
 * </pre>
 *
 * <p>The grouped sentence is the one that shows the rule is about the RESOLVED name rather than the
 * written one: {@code A}, {@code "A"}, {@code kw."A"} and {@code "KW"."A"} are four spellings of one
 * column, and live prints {@code [KW.A]} for every one of them.
 */
public class QuotedNameSpellingTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE kw (a INT, b INT)");
        engine.execute("INSERT INTO kw VALUES (1, 2)");
        engine.execute("CREATE OR REPLACE TABLE q2 (\"a\" INT, b INT)");
        engine.execute("INSERT INTO q2 VALUES (1, 2)");
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "accepted";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', ' ');
        }
    }

    private void says(final String sql, final String fragment) {
        final String answer = refusal(sql);
        assertTrue(answer.contains(fragment), sql + " => " + answer);
    }

    /** A missing relation is named canonically, quoted only where it has to be. */
    @Test
    public void aMissingRelationIsNamedCanonically() {
        says("SELECT * FROM \"nosuchtable\"", "Object '\"nosuchtable\"' does not exist");
        says("SELECT * FROM \"NOSUCHTABLE\"", "Object 'NOSUCHTABLE' does not exist");
        says("SELECT * FROM nosuchtable", "Object 'NOSUCHTABLE' does not exist");
        says("SELECT * FROM \"no such\"", "Object '\"no such\"' does not exist");
        says("SELECT * FROM \"1t\"", "Object '\"1t\"' does not exist");
        says("SELECT * FROM \"Q\"", "Object 'Q' does not exist");
        says("SELECT * FROM \"kw\"", "Object '\"kw\"' does not exist");
    }

    /** The grouped sentence names the RESOLVED column, however the item was spelled. */
    @Test
    public void theGroupedSentenceNamesTheResolvedColumn() {
        says("SELECT COUNT(*), \"A\" FROM kw", "[KW.A] is not a valid group by expression");
        says("SELECT COUNT(*), a FROM kw", "[KW.A] is not a valid group by expression");
        says("SELECT COUNT(*), kw.\"A\" FROM kw", "[KW.A] is not a valid group by expression");
        says("SELECT COUNT(*), \"KW\".\"A\" FROM kw", "[KW.A] is not a valid group by expression");
        says("SELECT COUNT(*), UPPER(\"A\") FROM kw", "[KW.A] is not a valid group by expression");
        says("SELECT COUNT(*), \"A\" + 1 FROM kw", "[KW.A] is not a valid group by expression");
    }

    /** And a column that REALLY is lower case keeps its quotes there. */
    @Test
    public void aLowerCaseColumnKeepsItsQuotes() {
        says("SELECT COUNT(*), \"a\" FROM q2", "[Q2.\"a\"] is not a valid group by expression");
        says("SELECT COUNT(*), q2.\"a\" FROM q2", "[Q2.\"a\"] is not a valid group by expression");
    }

    /** An unknown function is named without quotes when it needs none. */
    @Test
    public void anUnknownFunctionIsNamedPlainly() {
        says("SELECT nosuchfn(1) FROM kw", "Unknown function NOSUCHFN.");
        says("SELECT \"NOSUCHFN\"(1) FROM kw", "Unknown function NOSUCHFN.");
    }

    /** The names that DO resolve still read, so none of the spelling rules narrowed resolution. */
    @Test
    public void theResolvingNamesStillRead() {
        says("SELECT * FROM \"KW\"", "accepted");
        says("SELECT \"a\" FROM q2", "accepted");
        says("SELECT COUNT(*), a FROM kw GROUP BY a", "accepted");
    }
}
