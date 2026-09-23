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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A finished predicate takes no value operator: after {@code IS [NOT] NULL}, a LIKE with its ESCAPE, a LIKE ANY
 * list with its ESCAPE, an IN list or subquery and EXISTS, a concatenation, an arithmetic operator, a cast, a
 * path, a subscript, the outer-join marker, IS or a COLLATE is a syntax error at that operator, and EXISTS takes
 * no comparison either. An IS DISTINCT FROM takes no IS. What the predicate may still meet answers as before: a
 * comparison after it, a predicate on the right of an operator, and a parenthesized predicate. Every cell is
 * live-verified; where the account's recovery adds a second line, the first is the one compared.
 */
public class FinishedPredicateSyntaxTest extends BaseDatabaseTest {

    private void assertUnexpected(final String[][] cells) {
        for (final String[] cell : cells) {
            final String sql = cell[0];
            final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery(sql);
                }
            }, sql);
            final String line = "syntax error line 1 at position " + cell[1] + " unexpected '" + cell[2] + "'.";
            assertTrue(String.valueOf(refused.getMessage()).contains("SQL compilation error:\n" + line),
                sql + " should be refused with [" + line + "] but read: " + refused.getMessage());
        }
    }

    /** The first row's first cell, lower-cased. */
    private String answer(final String sql) {
        for (final Row row : engine.executeQuery(sql).getRows()) {
            return String.valueOf(row.getValue(0)).toLowerCase();
        }
        return "no row";
    }

    @Test
    public void aValueOperatorAfterAFinishedPredicateIsRefusedThere() {
        assertUnexpected(new String[][] {
            {"SELECT 'a' IS NULL || ''", "19", "||"},
            {"SELECT 'a' IS NULL ::VARCHAR", "19", "::"},
            {"SELECT 'a' IS NULL + 1", "19", "+"},
            {"SELECT 'a' IS NULL * 1", "19", "*"},
            {"SELECT 'a' IS NULL COLLATE 'de'", "27", "'de'"},
            {"SELECT 'a' IS NULL IS NULL", "19", "IS"},
            {"SELECT 'a' IS NULL :x", "19", ":"},
            {"SELECT 'a' IS NULL [0]", "19", "["},
            {"SELECT 'a' IS NOT NULL || ''", "23", "||"},
            {"SELECT 'a' IS NOT NULL ::VARCHAR", "23", "::"},
            {"SELECT 'a' IS NOT NULL + 1", "23", "+"},
            {"SELECT 'a' IS NOT NULL * 1", "23", "*"},
            {"SELECT 'a' IS NOT NULL COLLATE 'de'", "31", "'de'"},
            {"SELECT 'a' IS NOT NULL IS NULL", "23", "IS"},
            {"SELECT 'a' IS NOT NULL :x", "23", ":"},
            {"SELECT 'a' IS NOT NULL [0]", "23", "["},
            {"SELECT 'a' LIKE 'a' ESCAPE '!' || ''", "31", "||"},
            {"SELECT 'a' LIKE 'a' ESCAPE '!' ::VARCHAR", "31", "::"},
            {"SELECT 'a' LIKE 'a' ESCAPE '!' + 1", "31", "+"},
            {"SELECT 'a' LIKE 'a' ESCAPE '!' * 1", "31", "*"},
            {"SELECT 'a' LIKE 'a' ESCAPE '!' COLLATE 'de'", "39", "'de'"},
            {"SELECT 'a' LIKE 'a' ESCAPE '!' IS NULL", "31", "IS"},
            {"SELECT 'a' LIKE 'a' ESCAPE '!' :x", "31", ":"},
            {"SELECT 'a' LIKE 'a' ESCAPE '!' [0]", "31", "["},
            {"SELECT 'a' NOT LIKE 'a' ESCAPE '!' || ''", "35", "||"},
            {"SELECT 'a' NOT LIKE 'a' ESCAPE '!' ::VARCHAR", "35", "::"},
            {"SELECT 'a' NOT LIKE 'a' ESCAPE '!' + 1", "35", "+"},
            {"SELECT 'a' NOT LIKE 'a' ESCAPE '!' * 1", "35", "*"},
            {"SELECT 'a' NOT LIKE 'a' ESCAPE '!' COLLATE 'de'", "43", "'de'"},
            {"SELECT 'a' NOT LIKE 'a' ESCAPE '!' IS NULL", "35", "IS"},
            {"SELECT 'a' NOT LIKE 'a' ESCAPE '!' :x", "35", ":"},
            {"SELECT 'a' NOT LIKE 'a' ESCAPE '!' [0]", "35", "["},
            {"SELECT 'a' ILIKE 'a' ESCAPE '!' || ''", "32", "||"},
            {"SELECT 'a' ILIKE 'a' ESCAPE '!' ::VARCHAR", "32", "::"},
            {"SELECT 'a' ILIKE 'a' ESCAPE '!' + 1", "32", "+"},
            {"SELECT 'a' ILIKE 'a' ESCAPE '!' * 1", "32", "*"},
            {"SELECT 'a' ILIKE 'a' ESCAPE '!' COLLATE 'de'", "40", "'de'"},
            {"SELECT 'a' ILIKE 'a' ESCAPE '!' IS NULL", "32", "IS"},
            {"SELECT 'a' ILIKE 'a' ESCAPE '!' :x", "32", ":"},
            {"SELECT 'a' ILIKE 'a' ESCAPE '!' [0]", "32", "["},
            {"SELECT 'a' LIKE ANY ('a') ESCAPE '!' || ''", "37", "||"},
            {"SELECT 'a' LIKE ANY ('a') ESCAPE '!' ::VARCHAR", "37", "::"},
            {"SELECT 'a' LIKE ANY ('a') ESCAPE '!' + 1", "37", "+"},
            {"SELECT 'a' LIKE ANY ('a') ESCAPE '!' * 1", "37", "*"},
            {"SELECT 'a' LIKE ANY ('a') ESCAPE '!' COLLATE 'de'", "45", "'de'"},
            {"SELECT 'a' LIKE ANY ('a') ESCAPE '!' IS NULL", "37", "IS"},
            {"SELECT 'a' LIKE ANY ('a') ESCAPE '!' :x", "37", ":"},
            {"SELECT 'a' LIKE ANY ('a') ESCAPE '!' [0]", "37", "["},
            {"SELECT 1 IN (1, 2) || ''", "19", "||"},
            {"SELECT 1 IN (1, 2) ::VARCHAR", "19", "::"},
            {"SELECT 1 IN (1, 2) + 1", "19", "+"},
            {"SELECT 1 IN (1, 2) * 1", "19", "*"},
            {"SELECT 1 IN (1, 2) COLLATE 'de'", "27", "'de'"},
            {"SELECT 1 IN (1, 2) IS NULL", "19", "IS"},
            {"SELECT 1 IN (1, 2) :x", "19", ":"},
            {"SELECT 1 IN (1, 2) [0]", "19", "["},
            {"SELECT 1 NOT IN (1, 2) || ''", "23", "||"},
            {"SELECT 1 NOT IN (1, 2) ::VARCHAR", "23", "::"},
            {"SELECT 1 NOT IN (1, 2) + 1", "23", "+"},
            {"SELECT 1 NOT IN (1, 2) * 1", "23", "*"},
            {"SELECT 1 NOT IN (1, 2) COLLATE 'de'", "31", "'de'"},
            {"SELECT 1 NOT IN (1, 2) IS NULL", "23", "IS"},
            {"SELECT 1 NOT IN (1, 2) :x", "23", ":"},
            {"SELECT 1 NOT IN (1, 2) [0]", "23", "["},
            {"SELECT 1 IN (SELECT 1) || ''", "23", "||"},
            {"SELECT 1 IN (SELECT 1) ::VARCHAR", "23", "::"},
            {"SELECT 1 IN (SELECT 1) + 1", "23", "+"},
            {"SELECT 1 IN (SELECT 1) * 1", "23", "*"},
            {"SELECT 1 IN (SELECT 1) COLLATE 'de'", "31", "'de'"},
            {"SELECT 1 IN (SELECT 1) IS NULL", "23", "IS"},
            {"SELECT 1 IN (SELECT 1) :x", "23", ":"},
            {"SELECT 1 IN (SELECT 1) [0]", "23", "["},
            {"SELECT (1, 2) IN ((1, 2)) || ''", "26", "||"},
            {"SELECT (1, 2) IN ((1, 2)) ::VARCHAR", "26", "::"},
            {"SELECT (1, 2) IN ((1, 2)) + 1", "26", "+"},
            {"SELECT (1, 2) IN ((1, 2)) * 1", "26", "*"},
            {"SELECT (1, 2) IN ((1, 2)) COLLATE 'de'", "34", "'de'"},
            {"SELECT (1, 2) IN ((1, 2)) IS NULL", "26", "IS"},
            {"SELECT (1, 2) IN ((1, 2)) :x", "26", ":"},
            {"SELECT (1, 2) IN ((1, 2)) [0]", "26", "["},
            {"SELECT 1 IS DISTINCT FROM 2 IS NULL", "28", "IS"},
            {"SELECT 'a' IS NULL .x", "19", "."},
            {"SELECT 'a' IS NULL IS DISTINCT FROM TRUE", "19", "IS"},
            {"SELECT 'a' IS NULL - 1", "19", "-"},
            {"SELECT 'a' IS NULL / 1", "19", "/"},
            {"SELECT 'a' IS NULL % 1", "19", "%"},
            {"SELECT 'a' IS NULL IS NOT NULL", "19", "IS"},
            {"SELECT 'a' IS NULL (+)", "19", "("},
            {"SELECT 1 IN (1, 2) .x", "19", "."},
            {"SELECT 1 IN (1, 2) IS DISTINCT FROM TRUE", "19", "IS"},
            {"SELECT 1 IN (1, 2) - 1", "19", "-"},
            {"SELECT 1 IN (1, 2) / 1", "19", "/"},
            {"SELECT 1 IN (1, 2) % 1", "19", "%"},
            {"SELECT 1 IN (1, 2) IS NOT NULL", "19", "IS"},
            {"SELECT 1 IN (1, 2) (+)", "19", "("},
            {"SELECT 'a' LIKE 'a' ESCAPE '!' .x", "31", "."},
            {"SELECT 'a' LIKE 'a' ESCAPE '!' IS DISTINCT FROM TRUE", "31", "IS"},
            {"SELECT 'a' LIKE 'a' ESCAPE '!' - 1", "31", "-"},
            {"SELECT 'a' LIKE 'a' ESCAPE '!' / 1", "31", "/"},
            {"SELECT 'a' LIKE 'a' ESCAPE '!' % 1", "31", "%"},
            {"SELECT 'a' LIKE 'a' ESCAPE '!' IS NOT NULL", "31", "IS"},
            {"SELECT 'a' LIKE 'a' ESCAPE '!' (+)", "31", "("},
            {"SELECT 'a' LIKE ALL ('a') ESCAPE '!' .x", "37", "."},
            {"SELECT 'a' LIKE ALL ('a') ESCAPE '!' IS DISTINCT FROM TRUE", "37", "IS"},
            {"SELECT 'a' LIKE ALL ('a') ESCAPE '!' - 1", "37", "-"},
            {"SELECT 'a' LIKE ALL ('a') ESCAPE '!' / 1", "37", "/"},
            {"SELECT 'a' LIKE ALL ('a') ESCAPE '!' % 1", "37", "%"},
            {"SELECT 'a' LIKE ALL ('a') ESCAPE '!' IS NOT NULL", "37", "IS"},
            {"SELECT 'a' LIKE ALL ('a') ESCAPE '!' (+)", "37", "("},
            {"SELECT 'a' ILIKE ANY ('a') ESCAPE '!' .x", "38", "."},
            {"SELECT 'a' ILIKE ANY ('a') ESCAPE '!' IS DISTINCT FROM TRUE", "38", "IS"},
            {"SELECT 'a' ILIKE ANY ('a') ESCAPE '!' - 1", "38", "-"},
            {"SELECT 'a' ILIKE ANY ('a') ESCAPE '!' / 1", "38", "/"},
            {"SELECT 'a' ILIKE ANY ('a') ESCAPE '!' % 1", "38", "%"},
            {"SELECT 'a' ILIKE ANY ('a') ESCAPE '!' IS NOT NULL", "38", "IS"},
            {"SELECT 'a' ILIKE ANY ('a') ESCAPE '!' (+)", "38", "("},
            {"SELECT 1 NOT IN (SELECT 1) .x", "27", "."},
            {"SELECT 1 NOT IN (SELECT 1) IS DISTINCT FROM TRUE", "27", "IS"},
            {"SELECT 1 NOT IN (SELECT 1) - 1", "27", "-"},
            {"SELECT 1 NOT IN (SELECT 1) / 1", "27", "/"},
            {"SELECT 1 NOT IN (SELECT 1) % 1", "27", "%"},
            {"SELECT 1 NOT IN (SELECT 1) IS NOT NULL", "27", "IS"},
            {"SELECT 1 NOT IN (SELECT 1) (+)", "27", "("},
            {"SELECT (1, 2) IN (SELECT 1, 2) .x", "31", "."},
            {"SELECT (1, 2) IN (SELECT 1, 2) IS DISTINCT FROM TRUE", "31", "IS"},
            {"SELECT (1, 2) IN (SELECT 1, 2) - 1", "31", "-"},
            {"SELECT (1, 2) IN (SELECT 1, 2) / 1", "31", "/"},
            {"SELECT (1, 2) IN (SELECT 1, 2) % 1", "31", "%"},
            {"SELECT (1, 2) IN (SELECT 1, 2) IS NOT NULL", "31", "IS"},
            {"SELECT (1, 2) IN (SELECT 1, 2) (+)", "31", "("},
            {"SELECT (1, 2) IN (1, 2) .x", "24", "."},
            {"SELECT (1, 2) IN (1, 2) IS DISTINCT FROM TRUE", "24", "IS"},
            {"SELECT (1, 2) IN (1, 2) - 1", "24", "-"},
            {"SELECT (1, 2) IN (1, 2) / 1", "24", "/"},
            {"SELECT (1, 2) IN (1, 2) % 1", "24", "%"},
            {"SELECT (1, 2) IN (1, 2) IS NOT NULL", "24", "IS"},
            {"SELECT (1, 2) IN (1, 2) (+)", "24", "("},
        });
    }

    @Test
    public void existsTakesNoOperatorAtAll() {
        assertUnexpected(new String[][] {
            {"SELECT EXISTS (SELECT 1) || ''", "25", "||"},
            {"SELECT EXISTS (SELECT 1) ::VARCHAR", "25", "::"},
            {"SELECT EXISTS (SELECT 1) + 1", "25", "+"},
            {"SELECT EXISTS (SELECT 1) * 1", "25", "*"},
            {"SELECT EXISTS (SELECT 1) COLLATE 'de'", "33", "'de'"},
            {"SELECT EXISTS (SELECT 1) = TRUE", "25", "="},
            {"SELECT EXISTS (SELECT 1) IS NULL", "25", "IS"},
            {"SELECT EXISTS (SELECT 1) :x", "25", ":"},
            {"SELECT EXISTS (SELECT 1) [0]", "25", "["},
            {"SELECT EXISTS (SELECT 1) .x", "25", "."},
            {"SELECT EXISTS (SELECT 1) LIKE 'a'", "25", "LIKE"},
            {"SELECT EXISTS (SELECT 1) NOT LIKE 'a'", "25", "NOT"},
            {"SELECT EXISTS (SELECT 1) RLIKE 'a'", "25", "RLIKE"},
            {"SELECT EXISTS (SELECT 1) BETWEEN FALSE AND TRUE", "25", "BETWEEN"},
            {"SELECT EXISTS (SELECT 1) IN (TRUE)", "25", "IN"},
            {"SELECT EXISTS (SELECT 1) IS DISTINCT FROM TRUE", "25", "IS"},
            {"SELECT EXISTS (SELECT 1) <> TRUE", "25", "<>"},
            {"SELECT EXISTS (SELECT 1) < TRUE", "25", "<"},
            {"SELECT EXISTS (SELECT 1) = ANY (SELECT TRUE)", "25", "="},
            {"SELECT EXISTS (SELECT 1) - 1", "25", "-"},
            {"SELECT EXISTS (SELECT 1) / 1", "25", "/"},
            {"SELECT EXISTS (SELECT 1) % 1", "25", "%"},
            {"SELECT EXISTS (SELECT 1) = TRUE", "25", "="},
            {"SELECT EXISTS (SELECT 1) IS NOT NULL", "25", "IS"},
            {"SELECT EXISTS (SELECT 1) (+)", "25", "("},
            {"SELECT EXISTS (SELECT 1) ILIKE ANY ('a')", "25", "ILIKE"},
        });
    }

    @Test
    public void theRefusalStandsWhereverThePredicateIsWritten() {
        assertUnexpected(new String[][] {
            {"SELECT 1 IN (1, 2)::INT + 1", "18", "::"},
            {"SELECT TRUE AND 1 IN (1, 2)::INT", "27", "::"},
            {"SELECT NOT 1 IN (1, 2)::INT", "22", "::"},
            {"SELECT CASE WHEN 1 IN (1, 2)::INT THEN 1 END", "28", "::"},
            {"SELECT IFF(1 IN (1, 2) || '', 1, 2)", "23", "||"},
            {"SELECT COALESCE('a' IS NULL::INT, 1)", "27", "::"},
            {"SELECT b FROM bt WHERE 1 IN (1, 2)::BOOLEAN", "34", "::"},
            {"SELECT 1 FROM bt ORDER BY 1 IN (1, 2)::INT", "37", "::"},
            {"SELECT 1 IN (1, 2) IS DISTINCT FROM TRUE", "19", "IS"},
            {"SELECT 1 WHERE EXISTS (SELECT 1) = TRUE", "33", "="},
            {"SELECT 1 IN (1, 2)::INT, 2", "18", "::"},
            {"SELECT 1 IN (1, 2)::INT FROM bt", "18", "::"},
            {"SELECT 'a' LIKE 'a' ESCAPE '!'::VARCHAR", "30", "::"},
            {"SELECT 'a' LIKE ANY ('a') ESCAPE '!'::VARCHAR", "36", "::"},
            {"SELECT 'a' IS NULL || '' || ''", "19", "||"},
            {"SELECT 1 IN (1, 2) + 1 + 1", "19", "+"},
            {"SELECT 1 IN (1, 2)::INT::VARCHAR", "18", "::"},
            {"SELECT 1 IN (1, 2)[0][1]", "18", "["},
            {"SELECT EXISTS (SELECT 1) = TRUE = TRUE", "25", "="},
            {"SELECT 'a' IS NULL IS NULL IS NULL", "19", "IS"},
            {"SELECT 1 IS DISTINCT FROM 2 IS NULL", "28", "IS"},
            {"SELECT 1 IS DISTINCT FROM 2 IS NOT NULL", "28", "IS"},
            {"SELECT 1 IS DISTINCT FROM 2 IS DISTINCT FROM 3", "28", "IS"},
            {"SELECT 1 IS DISTINCT FROM 2 IS NOT DISTINCT FROM TRUE", "28", "IS"},
            {"SELECT 1 IS NOT DISTINCT FROM 2 IS NULL", "32", "IS"},
            {"SELECT 'a' IS NULL IS DISTINCT FROM TRUE", "19", "IS"},
            {"SELECT TRUE IS DISTINCT FROM 'a' IS NULL", "33", "IS"},
        });
    }

    @Test
    public void whatThePredicateMayMeetStillAnswers() {
        for (final String[] cell : new String[][] {
            {"SELECT 'a' IS NULL = TRUE", "false"},
            {"SELECT 'a' IS NOT NULL = TRUE", "true"},
            {"SELECT 'a' LIKE 'a' ESCAPE '!' = TRUE", "true"},
            {"SELECT 'a' NOT LIKE 'a' ESCAPE '!' = TRUE", "false"},
            {"SELECT 'a' ILIKE 'a' ESCAPE '!' = TRUE", "true"},
            {"SELECT 'a' LIKE ANY ('a') ::VARCHAR", "true"},
            {"SELECT 'a' LIKE ANY ('a') = TRUE", "true"},
            {"SELECT 'a' LIKE ANY ('a') ESCAPE '!' = TRUE", "true"},
            {"SELECT 1 IN (1, 2) = TRUE", "true"},
            {"SELECT 1 NOT IN (1, 2) = TRUE", "false"},
            {"SELECT 1 IN (SELECT 1) = TRUE", "true"},
            {"SELECT (1, 2) IN ((1, 2)) = TRUE", "true"},
            {"SELECT 1 = ANY (SELECT 1) = TRUE", "true"},
            {"SELECT 'a' LIKE 'a' || ''", "true"},
            {"SELECT 'a' LIKE 'a' ::VARCHAR", "true"},
            {"SELECT 'a' LIKE 'a' COLLATE 'de'", "true"},
            {"SELECT 1 IS DISTINCT FROM 2 || ''", "true"},
            {"SELECT 1 IS DISTINCT FROM 2 ::VARCHAR", "true"},
            {"SELECT 1 IS DISTINCT FROM 2 + 1", "true"},
            {"SELECT 1 IS DISTINCT FROM 2 * 1", "true"},
            {"SELECT 1 IS DISTINCT FROM 2 = TRUE", "true"},
            {"SELECT 1 IS DISTINCT FROM 2 :x", "true"},
            {"SELECT 1 IS DISTINCT FROM 2 [0]", "true"},
            {"SELECT 1 BETWEEN 0 AND 2 || ''", "true"},
            {"SELECT 1 BETWEEN 0 AND 2 ::VARCHAR", "true"},
            {"SELECT 1 BETWEEN 0 AND 2 + 1", "true"},
            {"SELECT 1 BETWEEN 0 AND 2 * 1", "true"},
            {"SELECT 1 BETWEEN 0 AND 2 = TRUE", "true"},
            {"SELECT 1 = 1 || ''", "true"},
            {"SELECT 1 = 1 ::VARCHAR", "true"},
            {"SELECT 1 = 1 + 1", "false"},
            {"SELECT 1 = 1 * 1", "true"},
            {"SELECT 1 = 1 = TRUE", "true"},
            {"SELECT 1 = 1 :x", "null"},
            {"SELECT 1 = 1 [0]", "null"},
            {"SELECT 'a' RLIKE 'a' || ''", "true"},
            {"SELECT 'a' RLIKE 'a' ::VARCHAR", "true"},
            {"SELECT 'a' RLIKE 'a' COLLATE 'de'", "true"},
            {"SELECT 'a' RLIKE 'a' = TRUE", "true"},
            {"SELECT TRUE || ''", "true"},
            {"SELECT TO_BOOLEAN('yes') || ''", "true"},
            {"SELECT IFF(TRUE, TRUE, FALSE) || ''", "true"},
            {"SELECT (NOT TRUE) || ''", "false"},
            {"SELECT (TRUE AND FALSE) || ''", "false"},
            {"SELECT NULL::BOOLEAN || ''", "null"},
            {"SELECT (SELECT TRUE) || ''", "true"},
            {"SELECT PARSE_JSON('true') || ''", "true"},
            {"SELECT (1 = 1)::INT", "1"},
            {"SELECT CONCAT(TRUE, '')", "true"},
            {"SELECT CASE WHEN TRUE THEN TRUE END || ''", "true"},
            {"SELECT COALESCE(1 = 1, FALSE) || ''", "true"},
            {"SELECT NOT TRUE || ''", "false"},
            {"SELECT (1 = 1)::VARCHAR || ''", "true"},
            {"SELECT ('a' LIKE 'a')::VARCHAR", "true"},
            {"SELECT (1 IN (1, 2))::INT", "1"},
            {"SELECT 'a' LIKE 'a' || ''", "true"},
            {"SELECT 'a' LIKE 'a' :: VARCHAR", "true"},
            {"SELECT 'a' IS NULL BETWEEN FALSE AND TRUE", "true"},
            {"SELECT 'a' IS NULL IN (TRUE)", "false"},
            {"SELECT 'a' IS NULL <> TRUE", "true"},
            {"SELECT 'a' IS NULL < TRUE", "true"},
            {"SELECT 'a' IS NULL = ANY (SELECT TRUE)", "false"},
            {"SELECT 'a' IS NULL = TRUE", "false"},
            {"SELECT 1 IN (1, 2) BETWEEN FALSE AND TRUE", "true"},
            {"SELECT 1 IN (1, 2) IN (TRUE)", "true"},
            {"SELECT 1 IN (1, 2) <> TRUE", "false"},
            {"SELECT 1 IN (1, 2) < TRUE", "false"},
            {"SELECT 1 IN (1, 2) = ANY (SELECT TRUE)", "true"},
            {"SELECT 1 IN (1, 2) = TRUE", "true"},
            {"SELECT 'a' LIKE 'a' ESCAPE '!' BETWEEN FALSE AND TRUE", "true"},
            {"SELECT 'a' LIKE 'a' ESCAPE '!' IN (TRUE)", "true"},
            {"SELECT 'a' LIKE 'a' ESCAPE '!' <> TRUE", "false"},
            {"SELECT 'a' LIKE 'a' ESCAPE '!' < TRUE", "false"},
            {"SELECT 'a' LIKE 'a' ESCAPE '!' = ANY (SELECT TRUE)", "true"},
            {"SELECT 'a' LIKE 'a' ESCAPE '!' = TRUE", "true"},
            {"SELECT 'a' LIKE ALL ('a') ESCAPE '!' BETWEEN FALSE AND TRUE", "true"},
            {"SELECT 'a' LIKE ALL ('a') ESCAPE '!' IN (TRUE)", "true"},
            {"SELECT 'a' LIKE ALL ('a') ESCAPE '!' <> TRUE", "false"},
            {"SELECT 'a' LIKE ALL ('a') ESCAPE '!' < TRUE", "false"},
            {"SELECT 'a' LIKE ALL ('a') ESCAPE '!' = ANY (SELECT TRUE)", "true"},
            {"SELECT 'a' LIKE ALL ('a') ESCAPE '!' = TRUE", "true"},
            {"SELECT 'a' ILIKE ANY ('a') ESCAPE '!' BETWEEN FALSE AND TRUE", "true"},
            {"SELECT 'a' ILIKE ANY ('a') ESCAPE '!' IN (TRUE)", "true"},
            {"SELECT 'a' ILIKE ANY ('a') ESCAPE '!' <> TRUE", "false"},
            {"SELECT 'a' ILIKE ANY ('a') ESCAPE '!' < TRUE", "false"},
            {"SELECT 'a' ILIKE ANY ('a') ESCAPE '!' = ANY (SELECT TRUE)", "true"},
            {"SELECT 'a' ILIKE ANY ('a') ESCAPE '!' = TRUE", "true"},
            {"SELECT 1 NOT IN (SELECT 1) BETWEEN FALSE AND TRUE", "true"},
            {"SELECT 1 NOT IN (SELECT 1) IN (TRUE)", "false"},
            {"SELECT 1 NOT IN (SELECT 1) <> TRUE", "true"},
            {"SELECT 1 NOT IN (SELECT 1) < TRUE", "true"},
            {"SELECT 1 NOT IN (SELECT 1) = ANY (SELECT TRUE)", "false"},
            {"SELECT 1 NOT IN (SELECT 1) = TRUE", "false"},
            {"SELECT (1, 2) IN (SELECT 1, 2) BETWEEN FALSE AND TRUE", "true"},
            {"SELECT (1, 2) IN (SELECT 1, 2) IN (TRUE)", "true"},
            {"SELECT (1, 2) IN (SELECT 1, 2) <> TRUE", "false"},
            {"SELECT (1, 2) IN (SELECT 1, 2) < TRUE", "false"},
            {"SELECT (1, 2) IN (SELECT 1, 2) = ANY (SELECT TRUE)", "true"},
            {"SELECT (1, 2) IN (SELECT 1, 2) = TRUE", "true"},
            {"SELECT TRUE = 'a' IS NULL", "false"},
            {"SELECT TRUE = 1 IN (1)", "true"},
            {"SELECT '' || 'a' IS NULL", "false"},
            {"SELECT 1 + 1 IN (1, 2)", "true"},
            {"SELECT 1 IN (1, 2) AND 'a' IS NULL", "false"},
            {"SELECT 1 IN (1, 2) = TRUE", "true"},
            {"SELECT 'a' IS NULL AND TRUE || ''", "false"},
            {"SELECT ('a' IS NULL)::INT", "0"},
            {"SELECT EXISTS (SELECT 1) OR FALSE", "true"},
            {"SELECT 'a' IS NULL AS x", "false"},
            {"SELECT 'a' IS NULL x", "false"},
            {"SELECT 'a' IS NULL COLLATE", "false"},
            {"SELECT 1 BETWEEN 0 AND 2 IN (TRUE)", "true"},
            {"SELECT 1 = 1 IN (TRUE)", "true"},
            {"SELECT (1 IS DISTINCT FROM 2) IS NULL", "false"},
            {"SELECT 1 IS DISTINCT FROM 1 IN (1)", "false"},
            {"SELECT 1 IS DISTINCT FROM 1 = 1", "false"},
        }) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }
}
