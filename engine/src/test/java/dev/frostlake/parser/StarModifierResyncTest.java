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

package dev.frostlake.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A star's misplaced RENAME or REPLACE outside a select item of its own — in a clause, under another call of a select
 * item — is refused at the keyword, and live reads on from the first token that may follow the star's call: a '(' there
 * is the outer-join marker, whose inner token is refused. In a derived table's or a CTE's item the keyword reads as the
 * item's alias (live-verified).
 */
public class StarModifierResyncTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String lines(final String... lines) {
        return "SQL compilation error:\n" + String.join("\n", lines);
    }

    private static String at(final int position, final String token) {
        return "syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void aClauseRefusesTheTokenInsideTheMarkerAndReadsOn() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        assertEquals(lines(at(30, "REPLACE"), at(39, "1")),
            refusal("SELECT 1 FROM fz WHERE HASH(* REPLACE (1 AS id)) = 1"));
        assertEquals(lines(at(30, "REPLACE"), at(39, "id")), refusal("SELECT 1 FROM fz WHERE HASH(* REPLACE (id)) = 1"));
        assertEquals(lines(at(30, "REPLACE"), at(39, "(")), refusal("SELECT 1 FROM fz WHERE HASH(* REPLACE ((1))) = 1"));
        assertEquals(lines(at(30, "REPLACE"), at(39, "1"), at(59, "y")),
            refusal("SELECT 1 FROM fz WHERE HASH(* REPLACE (1 AS id)) = 1 AND x y"));
        assertEquals(lines(at(33, "REPLACE"), at(42, "1")), refusal("SELECT 1 FROM fz ORDER BY HASH(* REPLACE (1 AS id))"));
        assertEquals(lines(at(33, "REPLACE"), at(42, "1")), refusal("SELECT 1 FROM fz GROUP BY HASH(* REPLACE (1 AS id))"));
        assertEquals(lines(at(31, "REPLACE"), at(40, "1")),
            refusal("SELECT 1 FROM fz HAVING HASH(* REPLACE (1 AS id)) = 1"));
        assertEquals(lines(at(32, "REPLACE"), at(41, "1")),
            refusal("SELECT 1 FROM fz QUALIFY HASH(* REPLACE (1 AS id)) = 1"));
        assertEquals(lines(at(30, "RENAME"), at(38, "id"), at(58, "y")),
            refusal("SELECT 1 FROM fz WHERE HASH(* RENAME (id AS k)) = 1 AND x y"));
        assertEquals(lines(at(33, "REPLACE"), at(42, "1"), at(55, "y")),
            refusal("SELECT 1 FROM fz ORDER BY HASH(* REPLACE (1 AS id)), x y"));
        assertEquals(lines(at(28, "REPLACE"), at(37, "1"), at(57, "y")),
            refusal("DELETE FROM fz WHERE HASH(* REPLACE (1 AS id)) = 1 AND x y"));
        assertEquals(lines(at(30, "REPLACE"), at(39, ")"), at(40, ")")),
            refusal("SELECT 1 FROM fz WHERE HASH(* REPLACE ()) = 1 AND x y"));
        assertEquals(lines(at(30, "REPLACE"), at(41, ")")), refusal("SELECT 1 FROM fz WHERE HASH(* REPLACE (+)) = 1 AND x y"));
        assertEquals(lines(at(30, "REPLACE"), at(52, "y")), refusal("SELECT 1 FROM fz WHERE HASH(* REPLACE (+) = 1 AND x y"));
        assertEquals(lines(at(30, "REPLACE"), at(39, "1")),
            refusal("SELECT 1 FROM fz WHERE HASH(* REPLACE (1 + 2)) = 1 AND x y"));
        assertEquals(lines(at(30, "REPLACE"), at(39, "id"), at(64, "y")),
            refusal("SELECT 1 FROM fz WHERE HASH(* REPLACE (id + 1 AS id)) = 1 AND x y"));
        assertEquals(lines(at(30, "REPLACE"), at(39, "<EOF>")), refusal("SELECT 1 FROM fz WHERE HASH(* REPLACE ("));
    }

    @Test
    public void aClauseSkipsToWhereTheCallMayBeFollowed() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        assertEquals(lines(at(30, "REPLACE")), refusal("SELECT 1 FROM fz WHERE HASH(* REPLACE x) = 1"));
        assertEquals(lines(at(30, "REPLACE"), at(51, "y")), refusal("SELECT 1 FROM fz WHERE HASH(* REPLACE x) = 1 AND x y"));
        assertEquals(lines(at(30, "REPLACE")), refusal("SELECT 1 FROM fz WHERE HASH(* REPLACE) = 1"));
        assertEquals(lines(at(30, "REPLACE")), refusal("SELECT 1 FROM fz WHERE HASH(* REPLACE)) = 1 AND x y"));
        assertEquals(lines(at(30, "REPLACE"), at(52, "y")), refusal("SELECT 1 FROM fz WHERE HASH(* REPLACE, 1) = 1 AND x y"));
        assertEquals(lines(at(30, "RENAME")), refusal("SELECT 1 FROM fz WHERE HASH(* RENAME id AS k) = 1"));
        assertEquals(lines(at(36, "REPLACE"), at(48, ")")),
            refusal("SELECT 1 FROM fz WHERE UPPER(HASH(* REPLACE x y)) = 1 AND x y"));
        assertEquals(lines(at(36, "REPLACE"), at(45, "1"), at(53, ")")),
            refusal("SELECT 1 FROM fz WHERE UPPER(HASH(* REPLACE (1 AS id))) = 1"));
        assertEquals(lines(at(33, "REPLACE"), at(43, ")")), refusal("SELECT 1 FROM fz ORDER BY HASH(* REPLACE, 1), x y"));
        assertEquals(lines(at(33, "REPLACE"), at(45, "y")), refusal("SELECT 1 FROM fz ORDER BY HASH(* REPLACE), x y"));
    }

    @Test
    public void underAnotherCallTheItemsAliasFollows() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        assertEquals(lines(at(20, "REPLACE"), at(29, "1"), at(36, ")")),
            refusal("SELECT UPPER(HASH(* REPLACE (1 AS id))) FROM fz"));
        assertEquals(lines(at(20, "REPLACE"), at(29, "1"), at(33, ")")),
            refusal("SELECT UPPER(HASH(* REPLACE (1, 2))) FROM fz"));
        assertEquals(lines(at(20, "REPLACE"), at(29, "id"), at(31, ")")),
            refusal("SELECT UPPER(HASH(* REPLACE (id))) FROM fz"));
        assertEquals(lines(at(20, "REPLACE")), refusal("SELECT UPPER(HASH(* REPLACE ())) FROM fz"));
        assertEquals(lines(at(20, "REPLACE"), at(31, ")")), refusal("SELECT UPPER(HASH(* REPLACE (+))) FROM fz"));
        assertEquals(lines(at(26, "REPLACE"), at(35, "1"), at(42, ")")),
            refusal("SELECT COALESCE(1, HASH(* REPLACE (1 AS id))) FROM fz"));
        assertEquals(lines(at(20, "REPLACE"), at(30, "y")), refusal("SELECT UPPER(HASH(* REPLACE x y)) FROM fz"));
        assertEquals(lines(at(20, "RENAME"), at(30, "AS")), refusal("SELECT UPPER(HASH(* RENAME id AS k)) FROM fz"));
        assertEquals(lines(at(20, "REPLACE")), refusal("SELECT UPPER(HASH(* REPLACE x)) FROM fz"));
        assertEquals(lines(at(20, "REPLACE"), at(32, ")")), refusal("SELECT UPPER(HASH(* REPLACE 'x')) FROM fz"));
        assertEquals(lines(at(20, "REPLACE"), at(33, ")")), refusal("SELECT UPPER(HASH(* REPLACE 1 + 2)) FROM fz"));
        assertEquals(lines(at(20, "REPLACE"), at(31, ")")), refusal("SELECT UPPER(HASH(* REPLACE, 1)) FROM fz"));
        assertEquals(lines(at(26, "REPLACE"), at(38, ")")), refusal("SELECT UPPER(UPPER(HASH(* REPLACE x y))) FROM fz"));
        assertEquals(lines(at(20, "REPLACE"), at(48, "b")), refusal("SELECT UPPER(HASH(* REPLACE 'x' FROM fz WHERE a b"));
    }

    @Test
    public void aTokenDroppedBeforeTheMarkersCloseSilencesTheFaultRightAfterIt() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        assertEquals(lines(at(30, "REPLACE"), at(41, "1")),
            refusal("SELECT 1 FROM fz WHERE HASH(* REPLACE (+ 1)) = 1 AND x y"));
        assertEquals(lines(at(30, "REPLACE"), at(41, "+")),
            refusal("SELECT 1 FROM fz WHERE HASH(* REPLACE (+ +)) = 1 AND x y"));
        assertEquals(lines(at(30, "REPLACE"), at(41, "'a'")),
            refusal("SELECT 1 FROM fz WHERE HASH(* REPLACE (+ 'a')) = 1 AND x y"));
        assertEquals(lines(at(30, "REPLACE"), at(41, "1"), at(54, "y")),
            refusal("SELECT 1 FROM fz WHERE HASH(* REPLACE (+ 1) = 1 AND x y"));
        assertEquals(lines(at(30, "REPLACE"), at(41, "x"), at(57, "y")),
            refusal("SELECT 1 FROM fz WHERE HASH(* REPLACE (+ x y)) = 1 AND x y"));
        assertEquals(lines(at(33, "REPLACE"), at(44, "1")), refusal("SELECT 1 FROM fz ORDER BY HASH(* REPLACE (+ 1)), x y"));
        assertEquals(lines(at(33, "REPLACE"), at(44, "1"), at(51, ")")),
            refusal("SELECT 1 FROM fz ORDER BY HASH(* REPLACE (+ 1) DESC), x y"));
        assertEquals(lines(at(33, "REPLACE"), at(44, "1")),
            refusal("SELECT 1 FROM fz GROUP BY HASH(* REPLACE (+ 1)), id HAVING x y"));
        assertEquals(lines(at(20, "REPLACE"), at(31, "1")),
            refusal("SELECT UPPER(HASH(* REPLACE (+ 1))) FROM fz WHERE a b"));
        assertEquals(lines(at(20, "REPLACE"), at(31, "1")),
            refusal("SELECT UPPER(HASH(* REPLACE (+ 1)), 2) FROM fz WHERE a b"));
        assertEquals(lines(at(20, "REPLACE"), at(31, "1")), refusal("SELECT UPPER(HASH(* REPLACE (+ 1))) x y FROM fz"));
    }

    @Test
    public void aDerivedTablesItemReadsTheKeywordAsItsAlias() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("CREATE TABLE fy (id INT)");
        assertEquals(lines(at(29, "REPLACE"), at(37, "x")), refusal("SELECT a FROM (SELECT HASH(* REPLACE x) a FROM fz)"));
        assertEquals(lines(at(29, "REPLACE"), at(37, "x"), at(59, "y")),
            refusal("SELECT a FROM (SELECT HASH(* REPLACE x) a FROM fz) WHERE x y"));
        assertEquals(lines(at(29, "RENAME"), at(36, "id"), at(64, "b")),
            refusal("SELECT a FROM (SELECT HASH(* RENAME id AS k) a FROM fz) WHERE a b"));
        assertEquals(lines(at(29, "REPLACE"), at(37, "x"), at(61, ")")),
            refusal("SELECT a FROM (SELECT HASH(* REPLACE x) a FROM fz GROUP BY id) WHERE a b"));
        assertEquals(lines(at(29, "REPLACE"), at(37, "x"), at(65, "b")),
            refusal("SELECT a FROM (SELECT HASH(* REPLACE x) a FROM fz) d, fy WHERE a b"));
        assertEquals(lines(at(29, "REPLACE"), at(57, "y")),
            refusal("SELECT a FROM (SELECT HASH(* REPLACE) a FROM fz) WHERE x y"));
        assertEquals(lines(at(29, "REPLACE"), at(43, "FROM")), refusal("SELECT a FROM (SELECT HASH(* REPLACE, 1) a FROM fz)"));
        assertEquals(lines(at(29, "REPLACE"), at(46, "FROM")),
            refusal("SELECT a FROM (SELECT HASH(* REPLACE, 1, 2) a FROM fz)"));
        assertEquals(lines(at(29, "REPLACE"), at(40, ")")), refusal("SELECT a FROM (SELECT HASH(* REPLACE, 1)) WHERE x y"));
        assertEquals(lines(at(25, "REPLACE"), at(35, "}")), refusal("SELECT a FROM (SELECT {* REPLACE, 1} a FROM fz)"));
        assertEquals(lines(at(35, "REPLACE"), at(44, "1"), at(52, ")")),
            refusal("SELECT a FROM (SELECT UPPER(HASH(* REPLACE (1 AS id))) a FROM fz)"));
    }

    @Test
    public void aBracketAfterADerivedTablesKeywordEndsTheReportWhenAnAliasFillsIt() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        assertEquals(lines(at(29, "REPLACE"), at(37, "(")),
            refusal("SELECT a FROM (SELECT HASH(* REPLACE (id AS k)) a FROM fz)"));
        assertEquals(lines(at(29, "RENAME"), at(36, "(")), refusal("SELECT a FROM (SELECT HASH(* RENAME (id k)) a FROM fz)"));
        assertEquals(lines(at(29, "REPLACE"), at(37, "(")), refusal("SELECT a FROM (SELECT HASH(* REPLACE (1)) a FROM fz)"));
        assertEquals(lines(at(29, "REPLACE"), at(37, "(")), refusal("SELECT a FROM (SELECT HASH(* REPLACE ()) a FROM fz)"));
    }

    @Test
    public void aBracketAfterACtesKeywordReadsAsTheMainQuery() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        assertEquals(lines(at(25, "REPLACE"), at(33, "("), at(34, "1"), at(42, ")")),
            refusal("WITH c AS (SELECT HASH(* REPLACE (1 AS id)) h FROM fz) SELECT * FROM c"));
        assertEquals(lines(at(25, "REPLACE"), at(33, "("), at(34, "id"), at(37, ")")),
            refusal("WITH c AS (SELECT HASH(* REPLACE (id)) h FROM fz) SELECT * FROM c"));
        assertEquals(lines(at(25, "REPLACE"), at(33, "("), at(34, "id"), at(42, ")")),
            refusal("WITH c AS (SELECT HASH(* REPLACE (id AS k)) h FROM fz) SELECT * FROM c"));
        assertEquals(lines(at(25, "REPLACE"), at(33, "("), at(34, "1"), at(42, ",")),
            refusal("WITH c AS (SELECT HASH(* REPLACE (1 AS id), 2) h FROM fz) SELECT * FROM c"));
        assertEquals(lines(at(25, "REPLACE"), at(33, "("), at(35, "1"), at(38, ")")),
            refusal("WITH c AS (SELECT HASH(* REPLACE ((1))) h FROM fz) SELECT * FROM c"));
        assertEquals(lines(at(25, "REPLACE"), at(33, "(")),
            refusal("WITH c AS (SELECT HASH(* REPLACE ()) h FROM fz) SELECT * FROM c"));
        assertEquals(lines(at(25, "REPLACE"), at(33, "("), at(34, "1"), at(42, ")")),
            refusal("WITH c AS (SELECT HASH(* REPLACE (1 AS id)) h FROM fz) SELECT * FROM c; SELECT 1 x y"));
        assertEquals(lines(at(25, "REPLACE"), at(33, "x")),
            refusal("WITH c AS (SELECT HASH(* REPLACE x) h FROM fz) SELECT * FROM c"));
        assertEquals(lines(at(25, "REPLACE"), at(33, "x")),
            refusal("WITH c AS (SELECT HASH(* REPLACE x) h FROM fz) SELECT * FROM c WHERE a b"));
        assertEquals(lines(at(25, "REPLACE"), at(37, "h")),
            refusal("WITH c AS (SELECT HASH(* REPLACE, 1) h FROM fz) SELECT * FROM c"));
    }

    @Test
    public void aLongChainOfMisplacedKeywordsIsReportedWhole() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        final StringBuilder items = new StringBuilder("SELECT ");
        final String[] itemLines = new String[12];
        for (int i = 0; i < itemLines.length; i++) {
            items.append(i == 0 ? "" : ", ").append("HASH(* REPLACE)");
            itemLines[i] = at(14 + 17 * i, "REPLACE");
        }
        assertEquals(lines(itemLines), refusal(items.append(" FROM fz").toString()));
        final StringBuilder conjuncts = new StringBuilder("SELECT 1 FROM fz WHERE ");
        final String[] conjunctLines = new String[8];
        for (int i = 0; i < conjunctLines.length; i++) {
            conjuncts.append(i == 0 ? "" : " AND ").append("HASH(* REPLACE x) = 1");
            conjunctLines[i] = at(30 + 26 * i, "REPLACE");
        }
        assertEquals(lines(conjunctLines), refusal(conjuncts.toString()));
        final StringBuilder markers = new StringBuilder("SELECT 1 FROM fz WHERE ");
        final String[] markerLines = new String[24];
        for (int i = 0; i < markerLines.length / 2; i++) {
            markers.append(i == 0 ? "" : " AND ").append("HASH(* REPLACE (1 AS id)) = 1");
            markerLines[2 * i] = at(30 + 34 * i, "REPLACE");
            markerLines[2 * i + 1] = at(39 + 34 * i, "1");
        }
        assertEquals(lines(markerLines), refusal(markers.toString()));
    }
}
