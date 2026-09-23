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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A table function joined with an ON condition: one that reads nothing of the relations to its left joins as
 * an ordinary relation, every join kind null-extending as it does elsewhere; one that reads the left row is
 * the lateral form live refuses with {@code Unsupported feature 'lateral table function called with OUTER
 * JOIN syntax or a join predicate (ON clause)'.}, and so is one reading the row of the query around a subquery
 * the join sits in, whichever clause holds the subquery. An argument naming nothing is refused as an invalid
 * identifier first.
 */
public class TableFunctionOnJoinTest extends JoinScopeTestSupport {

    private static final String LATERAL_REFUSAL = "Unsupported feature 'lateral table function called with "
        + "OUTER JOIN syntax or a join predicate (ON clause)'.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE full_t (a INT, b INT)");
        engine.execute("INSERT INTO full_t VALUES (1, 2)");
        engine.execute("CREATE TABLE k1 (k INT, b INT)");
        engine.execute("INSERT INTO k1 VALUES (1, 10)");
    }

    @Test
    public void aSelfContainedCallJoinsAsAnOrdinaryRelation() {
        assertEquals("1|2|1|0",
            rows("SELECT f.a, f.b, fl.value::INT, fl.index FROM full_t f JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1))) fl"
                + " ON fl.value = f.a"));
        assertEquals("1|1", rows("SELECT f.a, fl.value::INT FROM full_t f INNER JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1, 5))) fl"
            + " ON fl.value = f.a"));
        assertEquals("1|1", rows("SELECT f.a, fl.value::INT FROM full_t f JOIN TABLE(FLATTEN(ARRAY_CONSTRUCT(1))) fl"
            + " ON fl.value = f.a"));
        assertEquals("1|1", rows("SELECT f.a, fl.value::INT FROM full_t f JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1))) AS fl"
            + " ON fl.value = f.a AND f.b = 2"));
        assertEquals("", rows("SELECT f.a, fl.value::INT FROM full_t f JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1))) fl"
            + " ON f.b = 3"));
        assertEquals("3", rows("SELECT COUNT(*) FROM full_t f JOIN TABLE(GENERATOR(ROWCOUNT => 3)) g ON TRUE"));
        assertEquals("1|1", rows("SELECT f.a, s.value FROM full_t f JOIN TABLE(SPLIT_TO_TABLE('1,2', ',')) s"
            + " ON s.value = f.a::STRING"));
        assertEquals("1|x;1|y", rows("SELECT f.a, s.value FROM full_t f JOIN TABLE(SPLIT_TO_TABLE('x,y', ',')) s ON TRUE"
            + " ORDER BY s.index"));
        assertEquals("1|1|2", rows("SELECT f.a, fl.value::INT, fl2.value::INT FROM full_t f"
            + " JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1))) fl ON fl.value = f.a"
            + " JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(2))) fl2 ON fl2.value = f.b"));
        assertEquals("1", rows("SELECT f.a FROM TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1))) fl JOIN full_t f ON fl.value = f.a"));
        assertEquals("", rows("SELECT f.a FROM full_t f JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1))) fl"
            + " ON fl.value = f.a WHERE FALSE"));
    }

    @Test
    public void everyJoinKindNullExtendsAsItDoesElsewhere() {
        assertEquals("1|null", rows("SELECT f.a, fl.value::INT FROM full_t f LEFT JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(5))) fl"
            + " ON fl.value = f.a"));
        assertEquals("1|null", rows("SELECT f.a, fl.value::INT FROM full_t f LEFT JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1))) fl"
            + " ON f.b = 3"));
        assertEquals("1|2", rows("SELECT f.a, fl.value::INT FROM full_t f LEFT JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1, 2))) fl"
            + " ON fl.value > f.a"));
        assertEquals("null|1", rows("SELECT f.a, fl.value::INT FROM full_t f RIGHT JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1))) fl"
            + " ON f.b = 3"));
        assertEquals("1|1;null|5", rows("SELECT f.a, fl.value::INT FROM full_t f"
            + " RIGHT JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1, 5))) fl ON fl.value = f.a ORDER BY fl.index"));
        assertEquals("1|null;null|1", rows("SELECT f.a, fl.value::INT FROM full_t f"
            + " FULL JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1))) fl ON FALSE ORDER BY 1"));
        assertEquals("1", rows("SELECT COUNT(*) FROM full_t f LEFT JOIN TABLE(GENERATOR(ROWCOUNT => 3)) g ON FALSE"));
        assertEquals("1|null", rows("SELECT f.a, s.value FROM full_t f LEFT JOIN TABLE(SPLIT_TO_TABLE('3,4', ',')) s"
            + " ON s.value = f.a::STRING"));
    }

    @Test
    public void theLateralKeywordOverASelfContainedCallJoinsTheSameWay() {
        assertEquals("1|1", rows("SELECT f.a, fl.value::INT FROM full_t f JOIN LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT(1)) fl"
            + " ON fl.value = f.a"));
        assertEquals("1|null", rows("SELECT f.a, fl.value::INT FROM full_t f LEFT JOIN LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT(1)) fl"
            + " ON f.b = 3"));
        assertEquals("null|1", rows("SELECT f.a, fl.value::INT FROM full_t f RIGHT JOIN LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT(1)) fl"
            + " ON f.b = 3"));
        assertEquals("1|null;null|1", rows("SELECT f.a, fl.value::INT FROM full_t f"
            + " FULL JOIN LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT(1)) fl ON FALSE ORDER BY 1"));
        assertEquals("1|1", rows("SELECT f.a, s.value FROM full_t f JOIN LATERAL SPLIT_TO_TABLE('1,2', ',') s"
            + " ON s.value = f.a::STRING"));
        assertRefused("SELECT f.a, fl.value FROM full_t f JOIN LATERAL TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1))) fl ON TRUE",
            "syntax error line 1 at position 48 unexpected 'TABLE'.");
    }

    @Test
    public void argumentsThatReadNothingOfTheLeftSide() {
        assertEquals("1|1", rows("SELECT f.a, fl.value::INT FROM full_t f"
            + " JOIN TABLE(FLATTEN(INPUT => (SELECT ARRAY_AGG(a) FROM full_t))) fl ON fl.value = f.a"));
        assertEquals("1", rows("SELECT COUNT(*) FROM full_t f"
            + " JOIN TABLE(FLATTEN(INPUT => TRANSFORM(ARRAY_CONSTRUCT(1), a -> a + 1))) fl ON TRUE"));
        assertEquals("1", rows("SELECT COUNT(*) FROM full_t f"
            + " JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(CURRENT_DATE))) fl ON TRUE"));
        assertEquals("1", rows("SELECT f.a FROM full_t f JOIN TABLE(FLATTEN(INPUT => PARSE_JSON('[1,2]'))) fl"
            + " ON fl.value = f.a"));
    }

    @Test
    public void aCallReadingTheLeftRowIsTheRefusedLateralForm() {
        assertRefused("SELECT * FROM full_t f JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(f.a))) fl ON fl.value = f.a",
            LATERAL_REFUSAL);
        assertRefused("SELECT * FROM full_t f LEFT JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(f.a))) fl ON fl.value = f.a",
            LATERAL_REFUSAL);
        assertRefused("SELECT * FROM full_t f JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(a))) fl ON fl.value = f.a",
            LATERAL_REFUSAL);
        assertRefused("SELECT * FROM full_t f JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(f.a))) fl ON TRUE", LATERAL_REFUSAL);
        assertRefused("SELECT * FROM full_t f JOIN LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT(f.a)) fl ON fl.value = f.a",
            LATERAL_REFUSAL);
        assertRefused("SELECT f.a, s.value FROM full_t f JOIN TABLE(SPLIT_TO_TABLE(a::STRING, ',')) s ON TRUE",
            LATERAL_REFUSAL);
        assertRefused("SELECT f.a, fl.value FROM full_t f JOIN full_t g ON g.a = f.a"
            + " JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(g.a))) fl ON fl.value = f.a", LATERAL_REFUSAL);
        assertRefused("SELECT fl.value, fl2.value FROM TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1, 2))) fl"
            + " JOIN TABLE(FLATTEN(INPUT => fl.this)) fl2 ON TRUE", LATERAL_REFUSAL);
        assertRefused("SELECT ARRAY_CONSTRUCT(f.a) obs, fl.value FROM full_t f JOIN TABLE(FLATTEN(obs)) fl ON TRUE",
            LATERAL_REFUSAL);
        assertRefused("SELECT (SELECT COUNT(*) FROM full_t g JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(f.a))) fl ON TRUE)"
            + " FROM full_t f", LATERAL_REFUSAL);
        // Without a condition the lateral form joins.
        assertEquals("1|1", rows("SELECT f.a, fl.value::INT FROM full_t f JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(f.a))) fl"));
    }

    @Test
    public void anArgumentNamingNothingIsAnInvalidIdentifierFirst() {
        assertRefused("SELECT f.a FROM full_t f JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(nosuch))) fl ON TRUE",
            "error line 1 at position 69", "invalid identifier 'NOSUCH'");
        assertRefused("SELECT f.a FROM full_t f JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(f.nosuch))) fl ON TRUE",
            "error line 1 at position 69", "invalid identifier 'F.NOSUCH'");
        assertRefused("SELECT f.a FROM full_t f JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(zz.a))) fl ON TRUE",
            "error line 1 at position 69", "invalid identifier 'ZZ.A'");
        assertRefused("SELECT f.a, fl.value FROM full_t f JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(f.a, nosuch))) fl ON TRUE",
            "error line 1 at position 84", "invalid identifier 'NOSUCH'");
        assertRefused("SELECT f.a, fl.value FROM full_t f JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(nosuch, f.a))) fl ON TRUE",
            "error line 1 at position 79", "invalid identifier 'NOSUCH'");
        assertRefused("SELECT f.a, fl.value FROM full_t f JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1))) fl ON fl.nosuch = f.a",
            "error line 1 at position 90", "invalid identifier 'FL.NOSUCH'");
    }

    @Test
    public void aCallReadingTheOuterQueryInsideASubqueryIsRefusedInEveryClause() {
        final String outerCall = "TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(f.a))) fl";
        assertRefused("SELECT a FROM full_t f WHERE EXISTS (SELECT 1 FROM k1 g JOIN " + outerCall + " ON TRUE)", LATERAL_REFUSAL);
        assertRefused("SELECT a FROM full_t f WHERE EXISTS (SELECT 1 FROM k1 g JOIN " + outerCall + " ON fl.value = g.k)",
            LATERAL_REFUSAL);
        assertRefused("SELECT a FROM full_t f WHERE EXISTS (SELECT 1 FROM k1 g LEFT JOIN " + outerCall + " ON fl.value = g.k)",
            LATERAL_REFUSAL);
        assertRefused("SELECT a FROM full_t f WHERE NOT EXISTS (SELECT 1 FROM full_t g JOIN " + outerCall
            + " ON fl.value = g.a)", LATERAL_REFUSAL);
        assertRefused("SELECT a FROM full_t f WHERE a IN (SELECT fl.value::INT FROM full_t g JOIN " + outerCall
            + " ON fl.value = g.a)", LATERAL_REFUSAL);
        assertRefused("SELECT a FROM full_t f WHERE (SELECT COUNT(*) FROM full_t g JOIN " + outerCall + " ON TRUE) > 0",
            LATERAL_REFUSAL);
        assertRefused("SELECT a FROM full_t f WHERE a = (SELECT MAX(fl.value::INT) FROM k1 g JOIN " + outerCall + " ON TRUE)",
            LATERAL_REFUSAL);
        assertRefused("SELECT a FROM full_t f WHERE a = ANY (SELECT fl.value::INT FROM k1 g JOIN " + outerCall + " ON TRUE)",
            LATERAL_REFUSAL);
        assertRefused("SELECT a FROM full_t f WHERE EXISTS (SELECT 1 FROM full_t g"
            + " JOIN LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT(f.a)) fl ON TRUE)", LATERAL_REFUSAL);
        assertRefused("SELECT f.a FROM full_t f JOIN k1 h ON TRUE WHERE EXISTS (SELECT 1 FROM k1 g JOIN " + outerCall
            + " ON TRUE)", LATERAL_REFUSAL);
        assertRefused("SELECT a FROM full_t f WHERE EXISTS (SELECT 1 FROM k1 g"
            + " JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(b))) fl ON TRUE)", LATERAL_REFUSAL);
        assertRefused("SELECT f.a, h.k FROM full_t f JOIN k1 h ON EXISTS (SELECT 1 FROM k1 g JOIN " + outerCall + " ON TRUE)",
            LATERAL_REFUSAL);
        assertRefused("SELECT f.a, (SELECT MAX(fl.value::INT) FROM k1 g"
            + " JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(f.b))) fl ON TRUE) FROM full_t f JOIN k1 h ON TRUE", LATERAL_REFUSAL);
        // A name the query around does not own is the identifier it is.
        assertRefused("SELECT a FROM full_t f WHERE EXISTS (SELECT 1 FROM k1 g"
            + " JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(full_t.a))) fl ON TRUE)",
            "error line 1 at position 100", "invalid identifier 'FULL_T.A'");
        assertRefused("SELECT a FROM full_t f WHERE EXISTS (SELECT 1 FROM k1 g"
            + " JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(f.nosuch))) fl ON TRUE)",
            "error line 1 at position 100", "invalid identifier 'F.NOSUCH'");
    }

    @Test
    public void theOuterRowReadInsideAnArgumentSubqueryOrTheConditionJoins() {
        assertEquals("1", rows("SELECT a FROM full_t f WHERE EXISTS (SELECT 1 FROM k1 g"
            + " JOIN TABLE(FLATTEN(INPUT => (SELECT ARRAY_AGG(z.k) FROM k1 z WHERE z.k = f.a))) fl ON TRUE)"));
        assertEquals("", rows("SELECT a FROM full_t f WHERE EXISTS (SELECT 1 FROM k1 g"
            + " JOIN TABLE(FLATTEN(INPUT => (SELECT ARRAY_AGG(z.k) FROM k1 z WHERE z.k = f.a + 1))) fl ON TRUE)"));
        assertEquals("1", rows("SELECT (SELECT COUNT(*) FROM k1 g"
            + " JOIN TABLE(FLATTEN(INPUT => (SELECT ARRAY_AGG(z.k) FROM k1 z WHERE z.k = f.a))) fl ON TRUE) FROM full_t f"));
        assertEquals("1", rows("SELECT a FROM full_t f WHERE EXISTS (SELECT 1 FROM k1 g"
            + " JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1))) fl ON fl.value = f.a)"));
        assertEquals("", rows("SELECT a FROM full_t f WHERE EXISTS (SELECT 1 FROM k1 g"
            + " JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(2))) fl ON fl.value = f.a)"));
        assertEquals("1", rows("SELECT a FROM full_t f WHERE EXISTS (SELECT 1 FROM k1 g"
            + " JOIN TABLE(FLATTEN(INPUT => (SELECT ARRAY_AGG(z.k) FROM k1 z))) fl ON fl.value = g.k)"));
    }
}
