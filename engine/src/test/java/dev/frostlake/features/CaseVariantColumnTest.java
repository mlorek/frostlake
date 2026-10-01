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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A relation may hold two columns whose names differ only in case — a quoted {@code "x"} beside an
 * unquoted {@code X} — and each name reaches its own column wherever it is written: a quoted reference,
 * a qualified one, a star and its modifiers, a join key, a DML target, an outer reference read by a
 * subquery. An unquoted name means its upper-cased spelling EXACTLY, so {@code t1.c} over a table whose
 * only such column is a quoted {@code "c"} is an invalid identifier, qualified or not. Live-verified.
 */
public class CaseVariantColumnTest extends BaseDatabaseTest {

    /** Every row of a query, each rendered as its cells joined by {@code |}, the rows by {@code ;}. */
    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            if (out.length() > 0) {
                out.append(';');
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append('|');
                }
                final Object value = row.getValue(i);
                out.append(value == null ? "NULL" : value.toString());
            }
        }
        return out.toString();
    }

    /** The result column names of a query, joined by {@code |}. */
    private String labels(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < rs.getColumns().size(); i++) {
            if (i > 0) {
                out.append('|');
            }
            out.append(rs.getColumns().get(i).getName());
        }
        return out.toString();
    }

    /** The message a refused statement fails with. */
    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        return refused.getMessage();
    }

    private void createKeywordTable() {
        engine.execute("CREATE OR REPLACE TABLE t_keywords (\"default\" INT, \"select\" INT, \"order\" INT,"
            + " \"true\" INT, \"DEFAULT\" INT)");
        engine.execute("INSERT INTO t_keywords VALUES (1, 2, 3, 4, 5)");
    }

    /** A quoted reference reads its own column, and the result column carries that spelling. */
    @Test
    public void aQuotedReferenceReadsItsOwnColumn() {
        createKeywordTable();
        assertEquals("5|1", rows("SELECT \"DEFAULT\", \"default\" FROM t_keywords"));
        assertEquals("DEFAULT|default", labels("SELECT \"DEFAULT\", \"default\" FROM t_keywords"));
        assertEquals("5|1", rows("SELECT k.\"DEFAULT\", k.\"default\" FROM t_keywords k"));
        assertEquals("5|1", rows("SELECT t_keywords.\"DEFAULT\", t_keywords.\"default\" FROM t_keywords"));
        assertEquals("5", rows("SELECT t_keywords.default FROM t_keywords"));
        assertEquals("5", rows("SELECT \"DEFAULT\" FROM t_keywords WHERE \"default\" = 1"));
        assertEquals("1", rows("SELECT \"default\" FROM t_keywords WHERE \"DEFAULT\" = 5"));
        assertEquals("", rows("SELECT \"default\" FROM t_keywords WHERE \"DEFAULT\" = 1"));
    }

    /** Every clause resolves the two spellings apart. */
    @Test
    public void everyClauseResolvesTheSpellingsApart() {
        createKeywordTable();
        assertEquals("5|1", rows("SELECT \"DEFAULT\", COUNT(*) FROM t_keywords GROUP BY \"DEFAULT\""));
        assertEquals("1|1", rows("SELECT \"default\", COUNT(*) FROM t_keywords GROUP BY \"default\""));
        assertEquals("5", rows("SELECT k.\"DEFAULT\" FROM t_keywords k GROUP BY k.\"DEFAULT\""
            + " HAVING k.\"DEFAULT\" > 0"));
        assertEquals("1", rows("SELECT \"default\" FROM t_keywords"
            + " QUALIFY ROW_NUMBER() OVER (ORDER BY \"DEFAULT\") = 1"));
        assertEquals("1", rows("SELECT k.\"default\" FROM t_keywords k ORDER BY k.\"DEFAULT\""));
    }

    /** A star copies both columns, qualified or not, and a derived table, a CTE and a view keep them apart. */
    @Test
    public void aStarAndEveryDerivedRelationKeepBothColumns() {
        createKeywordTable();
        assertEquals("1|2|3|4|5", rows("SELECT * FROM t_keywords"));
        assertEquals("1|2|3|4|5", rows("SELECT k.* FROM t_keywords k"));
        assertEquals("1|2|3|4|5", rows("SELECT t_keywords.* FROM t_keywords"));
        assertEquals("1|2|3|4|5", rows("SELECT k.* FROM t_keywords k JOIN (SELECT 1 AS \"default\") j ON TRUE"));
        assertEquals("5|1", rows("SELECT d.\"DEFAULT\", d.\"default\" FROM (SELECT * FROM t_keywords) d"));
        assertEquals("5|1", rows("WITH w AS (SELECT * FROM t_keywords) SELECT w.\"DEFAULT\", w.\"default\" FROM w"));
        engine.execute("CREATE OR REPLACE VIEW vk AS SELECT * FROM t_keywords");
        assertEquals("5|1", rows("SELECT \"DEFAULT\", \"default\" FROM vk"));
        assertEquals("1|2|3|4|5", rows("SELECT vk.* FROM vk"));
    }

    /** A star modifier names one spelling: EXCLUDE, RENAME, REPLACE and ILIKE over the pair. */
    @Test
    public void aStarModifierNamesOneSpelling() {
        createKeywordTable();
        assertEquals("1|5", rows("SELECT * EXCLUDE (\"select\", \"order\", \"true\") FROM t_keywords"));
        assertEquals("1|3|4|5", rows("SELECT * EXCLUDE (\"select\") FROM t_keywords"));
        assertEquals("2|3|4|5", rows("SELECT * EXCLUDE (\"default\") FROM t_keywords"));
        assertEquals("1|2|3|4", rows("SELECT * EXCLUDE (\"DEFAULT\") FROM t_keywords"));
        assertEquals("LO|select|order|true|DEFAULT", labels("SELECT * RENAME (\"default\" AS lo) FROM t_keywords"));
        assertEquals("default|select|order|true|UP", labels("SELECT * RENAME (\"DEFAULT\" AS up) FROM t_keywords"));
        assertEquals("101|2|3|4|5", rows("SELECT * REPLACE (\"default\" + 100 AS \"default\") FROM t_keywords"));
        assertEquals("1|2|3|4|105", rows("SELECT * REPLACE (\"DEFAULT\" + 100 AS \"DEFAULT\") FROM t_keywords"));
        assertEquals("1|5", rows("SELECT * ILIKE 'default' FROM t_keywords"));
    }

    /** An unquoted name means its upper-cased spelling — in an aggregate too. */
    @Test
    public void anUnquotedNameMeansTheUpperCasedColumn() {
        engine.execute("CREATE OR REPLACE TABLE e1 (\"x\" INT, \"X\" INT, n VARCHAR)");
        engine.execute("INSERT INTO e1 VALUES (1, 10, 'a'), (2, 20, 'b'), (3, 30, 'b')");
        assertEquals("6|60|60", rows("SELECT SUM(\"x\"), SUM(\"X\"), SUM(x) FROM e1"));
        assertEquals("b|5", rows("SELECT n, SUM(\"x\") FROM e1 GROUP BY n HAVING SUM(\"X\") > 25 ORDER BY n"));
        assertEquals("1|10|10|10;2|20|20|20;3|30|30|30", rows("SELECT \"x\", \"X\", x, X FROM e1 ORDER BY 1"));
        assertEquals("1|1;2|2;3|1",
            rows("SELECT \"x\", ROW_NUMBER() OVER (PARTITION BY n ORDER BY \"X\" DESC) AS rn FROM e1 ORDER BY \"x\""));
        assertEquals("2", rows("SELECT d.x FROM (SELECT 1 AS \"x\", 2 AS \"X\") d"));
        assertEquals("50", rows("WITH c (\"x\", \"X\") AS (SELECT 5, 50) SELECT c.x FROM c"));
    }

    /** GROUPING SETS, ROLLUP and GROUPING keep the two spellings apart as grouping keys. */
    @Test
    public void groupingSetsKeepTheSpellingsApart() {
        engine.execute("CREATE OR REPLACE TABLE g1 (\"x\" INT, \"X\" INT, v INT)");
        engine.execute("INSERT INTO g1 VALUES (1, 10, 5), (1, 20, 6), (2, 10, 7)");
        assertEquals("1|NULL|11;2|NULL|7;NULL|10|12;NULL|20|6", rows("SELECT \"x\", \"X\", SUM(v) FROM g1"
            + " GROUP BY GROUPING SETS ((\"x\"), (\"X\")) ORDER BY 1 NULLS LAST, 2 NULLS LAST"));
        assertEquals("1|0|1;2|0|1;NULL|1|0;NULL|1|0", rows("SELECT \"x\", GROUPING(\"x\"), GROUPING(\"X\")"
            + " FROM g1 GROUP BY GROUPING SETS ((\"x\"), (\"X\")) ORDER BY 1 NULLS LAST, 2, 3"));
        assertEquals("1|11;2|7;NULL|18",
            rows("SELECT \"x\", SUM(v) FROM g1 GROUP BY ROLLUP (\"x\") ORDER BY 1 NULLS LAST"));
        assertEquals("10|12;20|6;NULL|18",
            rows("SELECT \"X\", SUM(v) FROM g1 GROUP BY CUBE (\"X\") ORDER BY 1 NULLS LAST"));
    }

    /** A subquery and a lateral read the outer row's two columns apart. */
    @Test
    public void anOuterReferenceReadsItsOwnColumn() {
        engine.execute("CREATE OR REPLACE TABLE e1 (\"x\" INT, \"X\" INT)");
        engine.execute("INSERT INTO e1 VALUES (1, 10), (2, 20), (3, 30)");
        assertEquals("10;20;30",
            rows("SELECT (SELECT MAX(b.\"X\") FROM e1 b WHERE b.\"x\" = a.\"x\") AS m FROM e1 a ORDER BY 1"));
        assertEquals("2", rows("SELECT a.\"x\" FROM e1 a"
            + " WHERE EXISTS (SELECT 1 FROM e1 b WHERE b.\"X\" = a.\"X\" * 1 AND b.\"x\" = 2)"));
        assertEquals("1|10;2|20;3|30",
            rows("SELECT l.v, l.w FROM e1 a, LATERAL (SELECT a.\"x\" AS v, a.\"X\" AS w) l ORDER BY 1"));
        assertEquals("1|10;2|20;3|30", rows("SELECT a.\"x\", b.\"X\" FROM e1 a JOIN e1 b ON a.\"x\" = b.\"x\" ORDER BY 1"));
    }

    /** INSERT, UPDATE and MERGE write each spelling to its own column. */
    @Test
    public void dmlWritesEachSpellingToItsOwnColumn() {
        engine.execute("CREATE OR REPLACE TABLE d1 (\"x\" INT, \"X\" INT)");
        engine.execute("INSERT INTO d1 (\"x\", \"X\") VALUES (1, 2)");
        engine.execute("INSERT INTO d1 (\"X\", \"x\") VALUES (30, 40)");
        engine.execute("INSERT INTO d1 (x) VALUES (5)");
        engine.execute("INSERT INTO d1 (\"x\") VALUES (6)");
        assertEquals("1|2;NULL|5;40|30;6|NULL", rows("SELECT \"x\", \"X\" FROM d1 ORDER BY \"X\", \"x\""));
        engine.execute("UPDATE d1 SET \"x\" = 9 WHERE \"X\" = 2");
        engine.execute("UPDATE d1 SET x = 7 WHERE \"x\" = 9");
        engine.execute("UPDATE d1 SET d1.\"x\" = 11 WHERE d1.\"X\" = 30");
        assertEquals("NULL|5;9|7;11|30;6|NULL", rows("SELECT \"x\", \"X\" FROM d1 ORDER BY \"X\", \"x\""));
        engine.execute("MERGE INTO d1 USING (SELECT 5 AS \"X\") s ON d1.\"X\" = s.\"X\""
            + " WHEN MATCHED THEN UPDATE SET \"x\" = 55");
        engine.execute("MERGE INTO d1 USING (SELECT 100 AS k) s ON d1.\"X\" = s.k"
            + " WHEN NOT MATCHED THEN INSERT (\"x\", \"X\") VALUES (101, 100)");
        engine.execute("MERGE INTO d1 USING (SELECT 200 AS k) s ON d1.\"X\" = s.k"
            + " WHEN NOT MATCHED THEN INSERT (\"X\", \"x\") VALUES (200, 201)");
        assertEquals("55|5;9|7;11|30;101|100;201|200;6|NULL",
            rows("SELECT \"x\", \"X\" FROM d1 ORDER BY \"X\", \"x\""));
        engine.execute("CREATE OR REPLACE TABLE e3 (\"x\" INT, \"X\" INT)");
        engine.execute("INSERT INTO e3 (\"X\", \"x\") SELECT \"x\", \"X\" FROM d1 WHERE \"X\" = 5");
        assertEquals("5|55", rows("SELECT \"x\", \"X\" FROM e3"));
    }

    /** A column named twice is refused; the two spellings are two names, not one named twice. */
    @Test
    public void onlyTheSameSpellingIsADuplicate() {
        engine.execute("CREATE OR REPLACE TABLE d1 (\"x\" INT, \"X\" INT)");
        assertEquals("SQL compilation error:\nduplicate column name '\"x\"'",
            refusal("INSERT INTO d1 (\"x\", \"x\") VALUES (1, 2)"));
        assertEquals("SQL compilation error:\nduplicate column name 'X'",
            refusal("INSERT INTO d1 (\"X\", X) VALUES (1, 2)"));
        assertEquals("SQL compilation error:\nduplicate column name 'X'",
            refusal("INSERT INTO d1 (\"x\", \"X\", x) VALUES (1, 2, 3)"));
        assertEquals("SQL compilation error:\nduplicate column name '\"x\"'",
            refusal("UPDATE d1 SET \"x\" = 1, \"x\" = 2 WHERE FALSE"));
        engine.execute("UPDATE d1 SET \"x\" = 1, \"X\" = 2 WHERE FALSE");
    }

    /** A USING or NATURAL join keys on the spelling it names, and a star puts that key first. */
    @Test
    public void aJoinKeyIsTheSpellingItNames() {
        engine.execute("CREATE OR REPLACE TABLE u1 (\"k\" INT, \"K\" INT, v INT)");
        engine.execute("INSERT INTO u1 VALUES (1, 2, 100)");
        engine.execute("CREATE OR REPLACE TABLE u2 (\"k\" INT, w INT)");
        engine.execute("INSERT INTO u2 VALUES (1, 200)");
        engine.execute("CREATE OR REPLACE TABLE u3 (\"K\" INT, z INT)");
        engine.execute("INSERT INTO u3 VALUES (2, 300)");
        assertEquals("1|2|100|200", rows("SELECT * FROM u1 JOIN u2 USING (\"k\")"));
        assertEquals("k|K|V|W", labels("SELECT * FROM u1 JOIN u2 USING (\"k\")"));
        assertEquals("1|2", rows("SELECT \"k\", \"K\" FROM u1 JOIN u2 USING (\"k\")"));
        assertEquals("2|1|1", rows("SELECT u1.\"K\", u1.\"k\", u2.\"k\" FROM u1 JOIN u2 USING (\"k\")"));
        assertEquals("1|2|100|200", rows("SELECT * FROM u1 NATURAL JOIN u2"));
        assertEquals("2|1|100|300", rows("SELECT * FROM u1 JOIN u3 USING (k)"));
        assertEquals("K|k|V|Z", labels("SELECT * FROM u1 JOIN u3 USING (k)"));
        assertEquals("2|1", rows("SELECT k, \"k\" FROM u1 JOIN u3 USING (k)"));
    }

    /** A qualified column part is matched exactly: t1.c is the column C, which a quoted "c" is not. */
    @Test
    public void aQualifiedReferenceIsExact() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT, \"c\" INT)");
        engine.execute("INSERT INTO t1 VALUES (1, 3)");
        assertEquals("3", rows("SELECT t1.\"c\" FROM t1"));
        assertEquals("3", rows("SELECT x.\"c\" FROM t1 x"));
        assertEquals("SQL compilation error: error line 1 at position 7\ninvalid identifier 'T1.C'",
            refusal("SELECT t1.c FROM t1"));
        assertEquals("SQL compilation error: error line 1 at position 7\ninvalid identifier 'X.C'",
            refusal("SELECT x.c FROM t1 x"));
        assertEquals("SQL compilation error: error line 1 at position 7\ninvalid identifier 'T1.C'",
            refusal("SELECT t1.\"C\" FROM t1"));
        assertEquals("SQL compilation error: error line 1 at position 7\ninvalid identifier 'T1.\"Cx\"'",
            refusal("SELECT t1.\"Cx\" FROM t1"));
        assertEquals("SQL compilation error: error line 1 at position 11\ninvalid identifier 'T1.C'",
            refusal("SELECT SUM(t1.c) FROM t1"));
    }

    /** ... in every clause, and through a derived table, a CTE and a view. */
    @Test
    public void aQualifiedReferenceIsExactInEveryClause() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT, \"c\" INT)");
        engine.execute("INSERT INTO t1 VALUES (1, 3)");
        assertEquals("SQL compilation error: error line 1 at position 23\ninvalid identifier 'T1.C'",
            refusal("SELECT a FROM t1 WHERE t1.c = 3"));
        assertEquals("SQL compilation error: error line 1 at position 26\ninvalid identifier 'T1.C'",
            refusal("SELECT a FROM t1 ORDER BY t1.c"));
        assertEquals("SQL compilation error: error line 1 at position 33\ninvalid identifier 'T1.C'",
            refusal("SELECT COUNT(*) FROM t1 GROUP BY t1.c"));
        assertEquals("SQL compilation error: error line 1 at position 39\ninvalid identifier 'T1.C'",
            refusal("SELECT a FROM t1 GROUP BY a HAVING MAX(t1.c) > 0"));
        assertEquals("SQL compilation error: error line 1 at position 53\ninvalid identifier 'T1.C'",
            refusal("SELECT a FROM t1 QUALIFY ROW_NUMBER() OVER (ORDER BY t1.c) = 1"));
        assertEquals("SQL compilation error: error line 1 at position 34\ninvalid identifier 'T1.C'",
            refusal("SELECT t1.a FROM t1 JOIN t1 t2 ON t1.c = t2.\"c\""));
        assertEquals("SQL compilation error: error line 1 at position 7\ninvalid identifier 'D.C'",
            refusal("SELECT d.c FROM (SELECT \"c\" FROM t1) d"));
        assertEquals("SQL compilation error: error line 1 at position 38\ninvalid identifier 'W.C'",
            refusal("WITH w AS (SELECT \"c\" FROM t1) SELECT w.c FROM w"));
        engine.execute("CREATE OR REPLACE VIEW v1 AS SELECT \"c\" FROM t1");
        assertEquals("SQL compilation error: error line 1 at position 7\ninvalid identifier 'V1.C'",
            refusal("SELECT v1.c FROM v1"));
        assertEquals("3", rows("SELECT v1.\"c\" FROM v1"));
    }

    /** DML reads a qualified part exactly too. */
    @Test
    public void dmlReadsAQualifiedPartExactly() {
        engine.execute("CREATE OR REPLACE TABLE e3 (\"x\" INT, \"X\" INT)");
        engine.execute("INSERT INTO e3 VALUES (1, 2)");
        assertEquals("SQL compilation error: error line 1 at position 20\ninvalid identifier 'E3.C'",
            refusal("UPDATE e3 SET \"x\" = e3.c"));
        assertEquals("SQL compilation error: error line 1 at position 28\ninvalid identifier 'E3.C'",
            refusal("UPDATE e3 SET \"x\" = 1 WHERE e3.c = 1"));
        assertEquals("SQL compilation error: error line 1 at position 21\ninvalid identifier 'E3.C'",
            refusal("DELETE FROM e3 WHERE e3.c = 1"));
        engine.execute("UPDATE e3 SET \"x\" = e3.\"X\" + 100");
        assertEquals("102|2", rows("SELECT \"x\", \"X\" FROM e3"));
    }

    /** The predicate refusal spells a quoted column as the plan holds it, quoted beside its relation. */
    @Test
    public void thePredicateRefusalKeepsTheQuotedSpelling() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT, \"c\" INT)");
        assertEquals("SQL compilation error:\nInvalid data type [NUMBER(38,0)] for predicate [T1.\"c\"]",
            refusal("SELECT 1 FROM t1 WHERE \"c\""));
        assertEquals("SQL compilation error:\nInvalid data type [NUMBER(38,0)] for predicate [X.\"c\"]",
            refusal("SELECT 1 FROM t1 x WHERE x.\"c\""));
        assertEquals("SQL compilation error:\nInvalid data type [NUMBER(38,0)] for predicate [T1.A]",
            refusal("SELECT 1 FROM t1 WHERE a"));
    }

    /** ADD COLUMN adds the other spelling, and refuses the same one under its canonical name. */
    @Test
    public void addColumnTellsTheSpellingsApart() {
        engine.execute("CREATE OR REPLACE TABLE f2 (\"n\" INT)");
        assertEquals("SQL compilation error:\ncolumn 'n' already exists",
            refusal("ALTER TABLE f2 ADD COLUMN \"n\" INT"));
        engine.execute("ALTER TABLE f2 ADD COLUMN IF NOT EXISTS \"n\" INT");
        engine.execute("ALTER TABLE f2 ADD COLUMN n INT");
        assertEquals("n|N", labels("SELECT * FROM f2"));
        assertEquals("SQL compilation error:\ncolumn 'N' already exists",
            refusal("ALTER TABLE f2 ADD COLUMN \"N\" INT"));
        engine.execute("INSERT INTO f2 (n) VALUES (5)");
        engine.execute("INSERT INTO f2 (\"n\") VALUES (6)");
        assertEquals("6|NULL;NULL|5", rows("SELECT \"n\", n FROM f2 ORDER BY 1 NULLS LAST"));
    }

    /** A set operation's ORDER BY names an output column by its canonical spelling, exactly. */
    @Test
    public void aSetOperationOrderByNamesItsOutputExactly() {
        engine.execute("CREATE OR REPLACE TABLE f1 (\"x\" INT, \"X\" INT)");
        engine.execute("INSERT INTO f1 VALUES (1, 10), (2, 20)");
        assertEquals("SQL compilation error: error line 1 at position 57\ninvalid identifier 'X'",
            refusal("SELECT \"x\" FROM f1 UNION ALL SELECT \"X\" FROM f1 ORDER BY x"));
        assertEquals("1;2;10;20", rows("SELECT \"x\" FROM f1 UNION ALL SELECT \"X\" FROM f1 ORDER BY \"x\""));
        assertEquals("1;2;10;20", rows("SELECT \"X\" FROM f1 UNION ALL SELECT \"x\" FROM f1 ORDER BY x"));
    }

    /** A star inside a function call excludes the spelling it names, exactly as a select-list star does. */
    @Test
    public void aStarInsideAFunctionExcludesExactly() {
        engine.execute("CREATE OR REPLACE TABLE e2 (\"x\" INT, \"X\" INT, y INT)");
        engine.execute("INSERT INTO e2 VALUES (1, 2, 3)");
        assertEquals("[1,3]", rows("SELECT TO_VARCHAR(ARRAY_CONSTRUCT(* EXCLUDE (\"X\"))) FROM e2"));
        assertEquals("[2,3]", rows("SELECT TO_VARCHAR(ARRAY_CONSTRUCT(e2.* EXCLUDE (\"x\"))) FROM e2"));
        assertEquals("true", rows("SELECT HASH(e2.* EXCLUDE (\"X\")) = HASH(\"x\", y) FROM e2"));
        assertEquals("{\"X\":2,\"Y\":3}",
            rows("SELECT TO_VARCHAR(OBJECT_CONSTRUCT(* EXCLUDE (\"x\"))) FROM e2"));
    }

    /** DROP COLUMN reaches only the column its name spells, and never the other spelling. */
    @Test
    public void dropColumnNamesOneSpelling() {
        engine.execute("CREATE OR REPLACE TABLE d9 (a INT, b INT)");
        engine.execute("INSERT INTO d9 VALUES (1, 2)");
        assertEquals("SQL compilation error:\ncolumn 'a' does not exist",
            refusal("ALTER TABLE d9 DROP COLUMN \"a\""));
        assertEquals("1|2", rows("SELECT * FROM d9"));
        engine.execute("CREATE OR REPLACE TABLE e3 (\"x\" INT, \"X\" INT, y INT)");
        engine.execute("INSERT INTO e3 VALUES (1, 2, 3)");
        engine.execute("ALTER TABLE e3 DROP COLUMN \"X\"");
        assertEquals("x|Y", labels("SELECT * FROM e3"));
        assertEquals("1|3", rows("SELECT * FROM e3"));
    }
    /**
     * A GROUP BY key groups the one of two case variants spelled as it resolves: the other in the select list is
     * ungrouped, named in full with its own spelling, and a grouped query's ORDER BY naming it is no valid key.
     */
    @Test
    public void aGroupingKeyGroupsOnlyTheVariantItNames() {
        engine.execute("CREATE TABLE cvg (\"x\" INT, X INT)");
        engine.execute("INSERT INTO cvg VALUES (1, 2)");
        assertEquals("SQL compilation error: error line 1 at position 7\n'CVG.\"x\"' in select clause is neither an "
            + "aggregate nor in the group by clause.", refusal("SELECT \"x\" FROM cvg GROUP BY X"));
        assertEquals("SQL compilation error: error line 1 at position 7\n'CVG.X' in select clause is neither an "
            + "aggregate nor in the group by clause.", refusal("SELECT X FROM cvg GROUP BY \"x\""));
        assertEquals("1|2|1", rows("SELECT \"x\", X, COUNT(*) FROM cvg GROUP BY \"x\", X"));
        assertEquals("SQL compilation error:\n[CVG.X] is not a valid order by expression",
            refusal("SELECT \"x\" FROM cvg GROUP BY \"x\" ORDER BY X"));
    }

    /** A table-level key's column list names the variant spelled as it resolves, and no other. */
    @Test
    public void aKeyNamesOnlyTheVariantItSpells() {
        engine.execute("CREATE TABLE cvp (\"x\" INT, X INT, PRIMARY KEY (\"x\"))");
        final ResultSet keys = engine.executeQuery("SHOW PRIMARY KEYS IN TABLE cvp");
        assertEquals(1, keys.getRowCount());
        assertEquals("x", String.valueOf(keys.getRows().get(0).getValue(keys.getColumnIndex("column_name"))));
    }

    /** CREATE OR ALTER keeps, adds and drops each variant as a column of its own, and answers as an ALTER does. */
    @Test
    public void createOrAlterTellsTheVariantsApart() {
        engine.execute("CREATE TABLE cvo2 (\"x\" INT)");
        assertEquals("Statement executed successfully.", rows("CREATE OR ALTER TABLE cvo2 (\"x\" INT, X INT)"));
        assertEquals("x|X", labels("SELECT * FROM cvo2"));
        engine.execute("CREATE TABLE cvo3 (\"x\" INT, X INT)");
        assertEquals("Statement executed successfully.", rows("CREATE OR ALTER TABLE cvo3 (\"x\" INT)"));
        assertEquals("x", labels("SELECT * FROM cvo3"));
    }

    /** A COPY column list maps each field to the variant it names. */
    @Test
    public void aCopyColumnListMapsEachVariant() {
        engine.execute("CREATE STAGE cvst");
        engine.execute("COPY INTO @cvst/cvcsv/f.csv FROM (SELECT 7, 8) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) "
            + "SINGLE = TRUE");
        engine.execute("CREATE TABLE cv3 (\"x\" INT, X INT)");
        engine.execute("COPY INTO cv3 (X, \"x\") FROM @cvst/cvcsv/ FILE_FORMAT = (TYPE = CSV)");
        assertEquals("8|7", rows("SELECT \"x\", X FROM cv3"));
    }
}
