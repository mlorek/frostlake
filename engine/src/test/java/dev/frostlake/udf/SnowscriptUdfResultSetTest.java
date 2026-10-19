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

package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A Snowscript UDF may declare no RESULTSET: the declaration itself is refused as
 * {@code Unsupported statement type for Snowscript UDF: resultset declaration} — written in the DECLARE section
 * or as a LET, with a query, with a dynamic statement or with nothing at all — and before anything its query
 * holds. What the body holds first is what is refused: an unsupported expression before the declaration, a
 * cursor declared before it. A FOR over a query is refused for the query, named as the tree as parsed prints it.
 * Every answer is live-verified.
 */
public class SnowscriptUdfResultSetTest extends MeasuredCellsTestSupport {

    private static final String DECLARATION =
        "REFUSED Unsupported statement type for Snowscript UDF: resultset declaration";
    private static final String CURSOR = "REFUSED Unsupported statement type for Snowscript UDF: cursor declaration";
    private static final String EXPRESSION = "REFUSED Unsupported expression for Snowscript UDF: ";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE sur_t (a INT, b INT)");
    }

    private static String udf(final String body) {
        return "CREATE OR REPLACE FUNCTION sur(x INT) RETURNS INT AS $$ " + body + " $$";
    }

    @Test
    public void aResultSetDeclarationIsRefusedHoweverItIsWritten() {
        assertAnswer(DECLARATION, udf("BEGIN LET rs RESULTSET; RETURN 1; END"));
        assertAnswer(DECLARATION, udf("BEGIN LET rs RESULTSET := (SELECT a FROM sur_t); RETURN 1; END"));
        assertAnswer(DECLARATION, udf("BEGIN LET rs RESULTSET DEFAULT (SELECT a FROM sur_t); RETURN 1; END"));
        assertAnswer(DECLARATION, udf("BEGIN LET rs RESULTSET := (EXECUTE IMMEDIATE 'SELECT 1'); RETURN 1; END"));
        assertAnswer(DECLARATION, udf("DECLARE rs RESULTSET; BEGIN RETURN 1; END"));
        assertAnswer(DECLARATION, udf("DECLARE rs RESULTSET DEFAULT (SELECT a FROM sur_t); BEGIN RETURN 1; END"));
    }

    @Test
    public void theDeclarationIsRefusedBeforeWhatItsQueryHolds() {
        assertAnswer(DECLARATION, udf("BEGIN LET rs RESULTSET := (SELECT nosuch(1)); RETURN 1; END"));
        assertAnswer(DECLARATION, udf("DECLARE rs RESULTSET; BEGIN rs := (SELECT a FROM sur_t); RETURN 1; END"));
        assertAnswer(DECLARATION, udf("BEGIN LET rs RESULTSET := (SELECT a FROM sur_t); RETURN nosuch(1); END"));
    }

    @Test
    public void whatTheBodyHoldsFirstIsRefused() {
        assertAnswer(EXPRESSION + "NOSUCH(1)",
            udf("BEGIN LET y INT := nosuch(1); LET rs RESULTSET := (SELECT a FROM sur_t); RETURN 1; END"));
        assertAnswer(EXPRESSION + "NOSUCH(1)",
            udf("DECLARE y INT DEFAULT nosuch(1); rs RESULTSET; BEGIN RETURN 1; END"));
        assertAnswer(DECLARATION, udf("DECLARE rs RESULTSET; y INT DEFAULT nosuch(1); BEGIN RETURN 1; END"));
        assertAnswer(CURSOR, udf("DECLARE c CURSOR FOR SELECT a FROM sur_t; rs RESULTSET; BEGIN RETURN 1; END"));
        assertAnswer(DECLARATION, udf("DECLARE rs RESULTSET; c CURSOR FOR SELECT a FROM sur_t; BEGIN RETURN 1; END"));
        assertAnswer(CURSOR, udf("BEGIN LET c CURSOR FOR SELECT a FROM sur_t; RETURN 1; END"));
    }

    @Test
    public void aForOverAQueryIsRefusedForTheQuery() {
        assertAnswer(EXPRESSION + "(SELECT A AS \"A\" FROM SUR_T AS SUR_T)",
            udf("BEGIN FOR r IN (SELECT a FROM sur_t) DO RETURN 1; END FOR; RETURN 2; END"));
        assertAnswer(
            EXPRESSION + "(SELECT A AS \"A\" FROM SUR_T AS SUR_T WHERE \"=\"(B, X)  ORDER BY A ASC NULLS LAST)",
            udf("BEGIN FOR r IN (SELECT a FROM sur_t WHERE b = x ORDER BY a) DO RETURN 1; END FOR; RETURN 2; END"));
        assertAnswer(EXPRESSION + "(SELECT A AS \"A\" FROM SUR_T AS SUR_T WHERE \"=\"(B, null))",
            udf("BEGIN RETURN (SELECT a FROM sur_t WHERE b = :x); END"));
    }
}
