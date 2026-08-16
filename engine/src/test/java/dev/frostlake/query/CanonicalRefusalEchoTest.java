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
 * When a refusal NAMES the expression it is refusing, Snowflake prints that expression from its
 * analysed plan rather than echoing the source text. Two consequences, and Frostlake used to have
 * neither:
 *
 * <ul>
 *   <li>a bare column comes back QUALIFIED by the name its relation is known by IN SCOPE — its alias
 *       where one was written, its own name otherwise, and a derived table's or CTE's alias equally;
 *   <li>a window's sort keys come back with a direction and a null placement even when neither was
 *       typed, while its FRAME and its null treatment are dropped altogether.
 * </ul>
 *
 * <p>The sort defaults are the ones the sort itself uses — ASC takes NULLS LAST, DESC takes NULLS
 * FIRST — and the direction is printed whichever way it was written or left out. A PARTITION BY key
 * takes neither, and its parentheses go.
 *
 * <p>Two shapes still print differently, and belong to the separate question of whether Frostlake
 * should reproduce a TYPE-CHECKED plan at all: live's echo carries implicit casts that are nowhere in
 * the source ({@code EB.N + (CAST(1 AS NUMBER(10,2)))}) and rewrites a CASE into
 * {@code CASE_FLATTENED}. Neither is asserted here.
 */
public class CanonicalRefusalEchoTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE eb (bn BINARY, n NUMBER(10,2), g VARCHAR)");
        engine.execute("CREATE OR REPLACE TABLE eb2 (m NUMBER(10,2))");
        engine.execute("INSERT INTO eb VALUES (TO_BINARY('AB','HEX'), 1.5, 'a')");
        engine.execute("INSERT INTO eb VALUES (TO_BINARY('CD','HEX'), 2.5, 'a')");
        engine.execute("INSERT INTO eb2 VALUES (1.5)");
    }

    /** What a statement's refusal prints inside its brackets, or "accepted" when it raises none. */
    private String echo(final String sql) {
        try {
            engine.execute(sql);
            return "accepted";
        } catch (final RuntimeException refused) {
            final String message = String.valueOf(refused.getMessage());
            final int open = message.indexOf('[');
            final int close = message.lastIndexOf(']');
            return open >= 0 && close > open ? message.substring(open + 1, close) : message;
        }
    }

    /** A bare column is qualified by its relation, and a written qualifier is kept. */
    @Test
    public void bareColumnCarriesItsRelation() {
        assertEquals("HEX_ENCODE(EB.BN, 1, 1)", echo("SELECT HEX_ENCODE(bn, 1, 1) FROM eb"));
        assertEquals("HEX_ENCODE(EB.BN, 1, 1)", echo("SELECT HEX_ENCODE(eb.bn, 1, 1) FROM eb"));
        assertEquals("HEX_ENCODE(X.BN, 1, 1)", echo("SELECT HEX_ENCODE(bn, 1, 1) FROM eb AS x"));
        assertEquals("HEX_ENCODE(X.BN, 1, 1)", echo("SELECT HEX_ENCODE(bn, 1, 1) FROM eb x"));
    }

    /** The qualifier is the FROM-clause name, so a join alias, a derived table and a CTE all print theirs. */
    @Test
    public void derivedRelationsPrintTheirOwnName() {
        assertEquals("HEX_ENCODE(A.BN, 1, 1)",
            echo("SELECT HEX_ENCODE(bn, 1, 1) FROM eb a JOIN eb2 b ON a.n = b.m"));
        assertEquals("HEX_ENCODE(Q.BN, 1, 1)",
            echo("SELECT HEX_ENCODE(bn, 1, 1) FROM (SELECT bn FROM eb) q"));
        assertEquals("HEX_ENCODE(C.BN, 1, 1)",
            echo("WITH c AS (SELECT bn FROM eb) SELECT HEX_ENCODE(bn, 1, 1) FROM c"));
    }

    /** Nothing but a column reference gains a qualifier — a literal and a nested call print as they are. */
    @Test
    public void onlyColumnsAreQualified() {
        assertEquals("HEX_ENCODE(TO_BINARY('AB', 'HEX'), 1, 1)",
            echo("SELECT HEX_ENCODE(TO_BINARY('AB','HEX'), 1, 1)"));
        assertEquals("HEX_ENCODE(EB.BN, EB.N, 1)", echo("SELECT HEX_ENCODE(bn, n, 1) FROM eb"));
        assertEquals("LOG(EB.N)", echo("SELECT LOG(n) FROM eb"));
        assertEquals("TO_VARCHAR(PARSE_JSON(EB.G), 'x')", echo("SELECT TO_VARCHAR(PARSE_JSON(g), 'x') FROM eb"));
    }

    /** A window's sort key is printed with a direction and a null placement, written or not. */
    @Test
    public void windowSortDefaultsAreFilledIn() {
        assertEquals("ROW_NUMBER() OVER (ORDER BY EB.N ASC NULLS LAST)",
            echo("SELECT n FROM eb WHERE ROW_NUMBER() OVER (ORDER BY n) = 1"));
        assertEquals("ROW_NUMBER() OVER (ORDER BY EB.N DESC NULLS FIRST)",
            echo("SELECT n FROM eb WHERE ROW_NUMBER() OVER (ORDER BY n DESC) = 1"));
        assertEquals("ROW_NUMBER() OVER (ORDER BY EB.N ASC NULLS FIRST)",
            echo("SELECT n FROM eb WHERE ROW_NUMBER() OVER (ORDER BY n NULLS FIRST) = 1"));
        assertEquals("ROW_NUMBER() OVER (ORDER BY EB.N DESC NULLS LAST)",
            echo("SELECT n FROM eb WHERE ROW_NUMBER() OVER (ORDER BY n DESC NULLS LAST) = 1"));
        assertEquals("ROW_NUMBER() OVER (ORDER BY EB.N DESC NULLS FIRST, EB.G ASC NULLS LAST)",
            echo("SELECT n FROM eb WHERE ROW_NUMBER() OVER (ORDER BY n DESC, g) = 1"));
    }

    /** A PARTITION BY key takes no direction and loses its parentheses; an empty OVER stays empty. */
    @Test
    public void partitionKeysTakeNoDirection() {
        assertEquals("ROW_NUMBER() OVER (PARTITION BY EB.G ORDER BY EB.N ASC NULLS LAST)",
            echo("SELECT n FROM eb WHERE ROW_NUMBER() OVER (PARTITION BY g ORDER BY n) = 1"));
        assertEquals("ROW_NUMBER() OVER (PARTITION BY EB.G ORDER BY EB.N ASC NULLS LAST)",
            echo("SELECT n FROM eb WHERE ROW_NUMBER() OVER (PARTITION BY (g) ORDER BY n) = 1"));
        assertEquals("ROW_NUMBER() OVER (PARTITION BY EB.G, EB.N ORDER BY EB.N ASC NULLS LAST)",
            echo("SELECT n FROM eb WHERE ROW_NUMBER() OVER (PARTITION BY g, n ORDER BY n) = 1"));
        assertEquals("COUNT(*) OVER (PARTITION BY EB.G)",
            echo("SELECT n FROM eb WHERE COUNT(*) OVER (PARTITION BY g) = 1"));
        assertEquals("COUNT(*) OVER ()", echo("SELECT n FROM eb WHERE COUNT(*) OVER () = 1"));
    }

    /** The frame and the null treatment are in the source and not in the echo. */
    @Test
    public void frameAndNullTreatmentAreDropped() {
        assertEquals("SUM(EB.N) OVER (ORDER BY EB.N ASC NULLS LAST)",
            echo("SELECT n FROM eb WHERE SUM(n) OVER"
                + " (ORDER BY n ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) = 1"));
        assertEquals("SUM(EB.N) OVER (PARTITION BY EB.G ORDER BY EB.N ASC NULLS LAST)",
            echo("SELECT n FROM eb WHERE SUM(n) OVER"
                + " (PARTITION BY g ORDER BY n RANGE UNBOUNDED PRECEDING) = 1"));
        assertEquals("FIRST_VALUE(EB.N) OVER (ORDER BY EB.N ASC NULLS LAST)",
            echo("SELECT n FROM eb WHERE FIRST_VALUE(n) IGNORE NULLS OVER (ORDER BY n) = 1"));
        assertEquals("FIRST_VALUE(EB.N) OVER (ORDER BY EB.N ASC NULLS LAST)",
            echo("SELECT n FROM eb WHERE FIRST_VALUE(n) RESPECT NULLS OVER (ORDER BY n) = 1"));
    }

    /** The call around the OVER is canonicalised too — case, spacing, DISTINCT and every argument. */
    @Test
    public void theCallItselfIsCanonicalised() {
        assertEquals("ROW_NUMBER() OVER (ORDER BY EB.N ASC NULLS LAST)",
            echo("SELECT n FROM eb WHERE ROW_NUMBER()  OVER  (  ORDER   BY  n  ) = 1"));
        assertEquals("ROW_NUMBER() OVER (ORDER BY EB.N ASC NULLS LAST)",
            echo("SELECT n FROM eb WHERE row_number() OVER (ORDER BY n) = 1"));
        assertEquals("COUNT(DISTINCT EB.N) OVER (PARTITION BY EB.G)",
            echo("SELECT n FROM eb WHERE COUNT(DISTINCT n) OVER (PARTITION BY g) = 1"));
        assertEquals("LAG(EB.N, 1) OVER (ORDER BY EB.N ASC NULLS LAST)",
            echo("SELECT n FROM eb WHERE LAG(n, 1) OVER (ORDER BY n) = 1"));
        assertEquals("SUM(EB.N) OVER (ORDER BY EB.N ASC NULLS LAST)",
            echo("SELECT n FROM eb WHERE SUM(n) OVER (ORDER BY n) = 1"));
        assertEquals("ROW_NUMBER() OVER (ORDER BY UPPER(EB.G) ASC NULLS LAST)",
            echo("SELECT n FROM eb WHERE ROW_NUMBER() OVER (ORDER BY UPPER(g)) = 1"));
    }

    /** A sort key that is a LITERAL is printed as one — it is not read as an output position. */
    @Test
    public void literalSortKeysStayLiterals() {
        assertEquals("ROW_NUMBER() OVER (ORDER BY 1 ASC NULLS LAST)",
            echo("SELECT n FROM eb WHERE ROW_NUMBER() OVER (ORDER BY 1) = 1"));
        assertEquals("ROW_NUMBER() OVER (ORDER BY 'a' ASC NULLS LAST)",
            echo("SELECT n FROM eb WHERE ROW_NUMBER() OVER (ORDER BY 'a') = 1"));
    }

    /** The relation's in-scope name reaches inside the OVER clause as well as into the arguments. */
    @Test
    public void windowKeysCarryTheirRelationToo() {
        assertEquals("ROW_NUMBER() OVER (ORDER BY Z.N ASC NULLS LAST)",
            echo("SELECT n FROM eb z WHERE ROW_NUMBER() OVER (ORDER BY n) = 1"));
        assertEquals("ROW_NUMBER() OVER (ORDER BY C.N ASC NULLS LAST)",
            echo("WITH c AS (SELECT n FROM eb) SELECT n FROM c WHERE ROW_NUMBER() OVER (ORDER BY n) = 1"));
        assertEquals("ROW_NUMBER() OVER (ORDER BY Q.N ASC NULLS LAST)",
            echo("SELECT n FROM (SELECT n FROM eb) q WHERE ROW_NUMBER() OVER (ORDER BY n) = 1"));
        assertEquals("ROW_NUMBER() OVER (ORDER BY A.N ASC NULLS LAST)",
            echo("SELECT a.n FROM eb a JOIN eb2 b ON a.n = b.m WHERE ROW_NUMBER() OVER (ORDER BY n) = 1"));
    }

    /** A misplaced AGGREGATE is named the same way — the same printer, a different sentence. */
    @Test
    public void misplacedAggregatesAreEchoedTheSameWay() {
        assertEquals("SUM(EB.N)", echo("SELECT n FROM eb WHERE SUM(n) > 0"));
        assertEquals("SUM(EB.N)", echo("SELECT n FROM eb WHERE ABS(SUM(n)) > 0"));
        assertEquals("COUNT(*)", echo("SELECT n FROM eb WHERE COUNT(*) > 0"));
        assertEquals("SUM(EB.N)", echo("SELECT eb.n FROM eb JOIN eb2 ON SUM(eb.n) = eb2.m"));
        assertEquals("SUM(EB.N)", echo("SELECT COUNT(*) FROM eb GROUP BY SUM(n)"));
        assertEquals("ROW_NUMBER() OVER (ORDER BY EB.N ASC NULLS LAST)",
            echo("SELECT SUM(ROW_NUMBER() OVER (ORDER BY n)) FROM eb"));
    }

    /** Every clause that refuses a window prints it the same way, whichever sentence it uses. */
    @Test
    public void everyRefusingClauseSharesTheEcho() {
        assertEquals("ROW_NUMBER() OVER (ORDER BY EB.N ASC NULLS LAST)",
            echo("SELECT eb.n FROM eb JOIN eb2 ON ROW_NUMBER() OVER (ORDER BY eb.n) = eb2.m"));
        assertEquals("ROW_NUMBER() OVER (ORDER BY EB.N ASC NULLS LAST)",
            echo("SELECT COUNT(*) FROM eb GROUP BY ROW_NUMBER() OVER (ORDER BY n)"));
        assertEquals("ROW_NUMBER() OVER (ORDER BY EB.G ASC NULLS LAST)",
            echo("SELECT COUNT(*) FROM eb GROUP BY g HAVING ROW_NUMBER() OVER (ORDER BY g) = 1"));
        assertEquals("ROW_NUMBER() OVER (ORDER BY EB.G ASC NULLS LAST)",
            echo("SELECT COUNT(*) FROM eb GROUP BY g ORDER BY ROW_NUMBER() OVER (ORDER BY g)"));
        assertEquals("ROW_NUMBER() OVER (ORDER BY SUM(EB.N) ASC NULLS LAST)",
            echo("SELECT COUNT(*) FROM eb GROUP BY g ORDER BY ROW_NUMBER() OVER (ORDER BY SUM(n))"));
    }
}
