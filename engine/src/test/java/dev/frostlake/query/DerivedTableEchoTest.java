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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a refusal's echo names the relation a column came from. An unaliased derived table goes by the
 * account's quoted moniker, {@code "values"} — nested or filtered alike, and in the conversion and the
 * arity sentences alike — while an unaliased VALUES clause is spelled {@code VALUES}. A written alias is
 * upper-cased, a quoted one is kept exactly as quoted, and a CTE is its name. Every cell is live-verified.
 */
public class DerivedTableEchoTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE re (a NUMBER(10,2))");
        engine.execute("CREATE TABLE ft (d DATE)");
        engine.execute("CREATE TABLE rt (g VARCHAR(10))");
    }

    /**
     * The conversion sentence a CASE condition earns spells the moniker the same way — in a SELECT
     * list, beside a JOIN and in a WHERE alike — and an alias as written.
     */
    @Test
    public void theCaseConditionSentenceQuotesTheUnaliasedMonikerToo() {
        assertEcho("SELECT CASE WHEN x THEN 1 ELSE 2 END FROM (SELECT g AS x FROM rt)",
            "Can not convert parameter '\"values\".X' of type [VARCHAR(10)] into expected type [BOOLEAN]");
        assertEcho("SELECT CASE WHEN x THEN 1 ELSE 2 END FROM (SELECT g AS x FROM rt) JOIN rt ON TRUE",
            "Can not convert parameter '\"values\".X' of type [VARCHAR(10)] into expected type [BOOLEAN]");
        assertEcho("SELECT 1 FROM (SELECT g AS x FROM rt) WHERE CASE WHEN x THEN TRUE END",
            "Can not convert parameter '\"values\".X' of type [VARCHAR(10)] into expected type [BOOLEAN]");
        assertEcho("SELECT CASE WHEN x THEN 1 ELSE 2 END FROM (SELECT g AS x FROM rt) d",
            "Can not convert parameter 'D.X' of type [VARCHAR(10)] into expected type [BOOLEAN]");
    }

    private void assertEcho(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), sql + " -> " + refused.getMessage());
    }

    @Test
    public void anUnaliasedDerivedTableIsQuotedValues() {
        assertEcho("SELECT x::DATE FROM (SELECT a AS x FROM re)",
            "invalid type [CAST(\"values\".X AS DATE)] for parameter 'TO_DATE'");
        assertEcho("SELECT x::DATE FROM (SELECT x FROM (SELECT a AS x FROM re))",
            "invalid type [CAST(\"values\".X AS DATE)] for parameter 'TO_DATE'");
        assertEcho("SELECT x::DATE FROM (SELECT a AS x FROM re) WHERE x > 0",
            "invalid type [CAST(\"values\".X AS DATE)] for parameter 'TO_DATE'");
        assertEcho("SELECT TO_DATE(d, s, 1) FROM (SELECT d, 'YYYY-MM-DD' AS s FROM ft)",
            "too many arguments for function [TO_DATE(\"values\".D, \"values\".S, 1)] expected 2, got 3");
    }

    @Test
    public void aValuesClauseAnAliasAndACteKeepTheirNames() {
        assertEcho("SELECT column1::DATE FROM (VALUES (1.5::NUMBER(10,2)))",
            "invalid type [CAST(VALUES.COLUMN1 AS DATE)] for parameter 'TO_DATE'");
        assertEcho("SELECT column1::DATE FROM VALUES (1.5::NUMBER(10,2))",
            "invalid type [CAST(VALUES.COLUMN1 AS DATE)] for parameter 'TO_DATE'");
        assertEcho("SELECT x::DATE FROM (SELECT a AS x FROM re) AS sub",
            "invalid type [CAST(SUB.X AS DATE)] for parameter 'TO_DATE'");
        assertEcho("SELECT x::DATE FROM (SELECT a AS x FROM re) AS \"sub\"",
            "invalid type [CAST(\"sub\".X AS DATE)] for parameter 'TO_DATE'");
        assertEcho("WITH c AS (SELECT a AS x FROM re) SELECT x::DATE FROM c",
            "invalid type [CAST(C.X AS DATE)] for parameter 'TO_DATE'");
    }
}
