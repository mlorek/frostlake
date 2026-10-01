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
 * A positional column {@code $n} over several relations resolves against the FROM clause's name scopes, as a
 * bare name does: two scopes at least n columns wide make it {@code ambiguous column name '$n'} while the
 * statement compiles, none leaves it {@code invalid identifier '$n'} at the reference, and one answers its
 * nth column. A USING or NATURAL join is one scope, laid out as its merged keys, then the left side's
 * columns without its key copies unless the join extends the left with NULLs, then the right side's the same
 * way. {@code t.$n} reads relation t's own nth column, unless a USING or NATURAL join merged it. An ORDER BY
 * key sorts by the column its position reads, and a staged-file query reads its fields by position, NULL past
 * them up to 4096, and never its METADATA$ columns.
 */
public class JoinedPositionalColumnTest extends JoinScopeTestSupport {

    /** Two rows whose second column orders them the other way round from their first. */
    private static final String TWO_ROWS = "(SELECT 1 y, 3 z UNION ALL SELECT 2, 1)";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (a INT, b INT)");
        engine.execute("CREATE TABLE full_t (a INT, b INT)");
        engine.execute("INSERT INTO full_t VALUES (1, 2)");
        engine.execute("CREATE TABLE k1 (k INT, b INT)");
        engine.execute("CREATE TABLE k2 (j INT, b INT)");
        engine.execute("CREATE TABLE k3 (k INT, e INT)");
        engine.execute("CREATE TABLE k4 (k INT, b INT)");
        engine.execute("INSERT INTO k1 VALUES (1, 10)");
        engine.execute("INSERT INTO k2 VALUES (1, 20)");
        engine.execute("INSERT INTO k3 VALUES (1, 5)");
        engine.execute("INSERT INTO k4 VALUES (1, 40)");
    }

    @Test
    public void twoRelationsWideEnoughMakeItAmbiguousWhileCompiling() {
        assertRefused("SELECT f.a FROM full_t f JOIN t ON t.a = f.a AND $1 = 1", "ambiguous column name '$1'");
        assertRefused("SELECT f.a FROM full_t f JOIN full_t g ON g.a = f.a AND $1 = 1", "ambiguous column name '$1'");
        assertRefused("SELECT $1 FROM full_t f JOIN t ON t.a = f.a", "ambiguous column name '$1'");
        assertRefused("SELECT f.a FROM full_t f JOIN t ON t.a = f.a WHERE $1 = 1", "ambiguous column name '$1'");
        assertRefused("SELECT $2 FROM full_t f JOIN full_t g ON g.a = f.a", "ambiguous column name '$2'");
        assertRefused("SELECT $1 FROM full_t f, full_t g", "ambiguous column name '$1'");
        assertRefused("SELECT $1 FROM full_t f CROSS JOIN full_t g", "ambiguous column name '$1'");
        assertRefused("SELECT $1 FROM full_t f LEFT JOIN full_t g ON g.a = f.a", "ambiguous column name '$1'");
        assertRefused("SELECT f.a FROM full_t f JOIN full_t g ON g.a = f.a ORDER BY $1", "ambiguous column name '$1'");
        assertRefused("SELECT f.a FROM full_t f JOIN full_t g ON g.a = f.a GROUP BY $1", "ambiguous column name '$1'");
        assertRefused("SELECT COUNT(*) FROM full_t f JOIN full_t g ON g.a = f.a HAVING COUNT($1) > 0",
            "ambiguous column name '$1'");
        assertRefused("SELECT f.a FROM full_t f JOIN full_t g ON g.a = f.a QUALIFY $1 = 1", "ambiguous column name '$1'");
        assertRefused("SELECT $1 FROM (SELECT 1 x) a, LATERAL (SELECT a.x AS y) l", "ambiguous column name '$1'");
        assertRefused("SELECT $1 FROM VALUES (1), (2) v JOIN full_t f ON TRUE", "ambiguous column name '$1'");
        assertRefused("WITH c AS (SELECT 1 x) SELECT $1 FROM c JOIN c d ON TRUE", "ambiguous column name '$1'");
        assertRefused("SELECT $1 FROM full_t f JOIN full_t g USING (a) JOIN full_t h ON TRUE",
            "ambiguous column name '$1'");
        assertRefused("SELECT f.a FROM full_t f WHERE f.a IN (SELECT $1 FROM full_t g JOIN t ON TRUE)",
            "ambiguous column name '$1'");
    }

    @Test
    public void noRelationWideEnoughLeavesItAnInvalidIdentifier() {
        assertRefused("SELECT $3 FROM full_t f JOIN full_t g ON g.a = f.a", "error line 1 at position 7",
            "invalid identifier '$3'");
        assertRefused("SELECT $4 FROM full_t f JOIN full_t g USING (a) JOIN full_t h ON TRUE",
            "error line 1 at position 7", "invalid identifier '$4'");
        assertRefused("SELECT $0 FROM full_t f JOIN full_t g ON TRUE", "error line 1 at position 7",
            "invalid identifier '$0'");
    }

    @Test
    public void theRefusalsRankAsANameWouldInTheWrittenOrder() {
        assertRefused("SELECT nosuch, $1 FROM full_t f JOIN full_t g ON TRUE", "invalid identifier 'NOSUCH'");
        assertRefused("SELECT $1, nosuch FROM full_t f JOIN full_t g ON TRUE", "ambiguous column name '$1'");
        assertRefused("SELECT f.a FROM full_t f JOIN full_t g ON TRUE WHERE nosuch = $1", "invalid identifier 'NOSUCH'");
        assertRefused("SELECT f.a FROM full_t f JOIN full_t g ON TRUE WHERE $1 = nosuch", "ambiguous column name '$1'");
    }

    @Test
    public void theOneRelationWideEnoughAnswersItsColumn() {
        assertEquals("3", rows("SELECT $2 FROM (SELECT 1 x) a JOIN (SELECT 2 y, 3 z) b ON TRUE"));
        assertEquals("3", rows("SELECT $2 FROM (SELECT 2 y, 3 z) b JOIN (SELECT 1 x) a ON TRUE"));
        assertEquals("2", rows("SELECT $2 FROM full_t f JOIN (SELECT 1 z) g ON TRUE"));
        assertEquals("3", rows("SELECT $2 FROM (SELECT 1 x) a, (SELECT 2 y, 3 z) b, (SELECT 4 p) c"));
        assertEquals("null", rows("SELECT $3 FROM k1 LEFT JOIN (SELECT 1 z, 2 w, 3 v) g ON FALSE"));
        assertEquals("4", rows("SELECT $2 + 1 FROM (SELECT 1 x) a JOIN (SELECT 2 y, 3 z) b ON TRUE"));
        assertEquals("3", rows("SELECT SUM($2) FROM (SELECT 1 x) a JOIN (SELECT 2 y, 3 z) b ON TRUE"));
        assertEquals("3", rows("SELECT $2 FROM (SELECT 1 x) a JOIN (SELECT 2 y, 3 z) b ON TRUE WHERE $2 = 3"));
        assertEquals("3", rows("SELECT $2 FROM (SELECT 1 x) a JOIN (SELECT 2 y, 3 z) b ON $2 = 3"));
        assertEquals("3", rows("SELECT $2 FROM (SELECT 1 x) a JOIN (SELECT 2 y, 3 z) b ON TRUE ORDER BY $2"));
        assertEquals("3", rows("SELECT $2 FROM (SELECT 1 x) a JOIN (SELECT 2 y, 3 z) b ON TRUE GROUP BY $2"));
        assertEquals("1", rows("SELECT COUNT($2) FROM (SELECT 1 x) a JOIN (SELECT 2 y, 3 z) b ON TRUE HAVING COUNT($2) > 0"));
        assertEquals("NUMBER(1,0)[SB1]",
            rows("SELECT SYSTEM$TYPEOF($2) FROM (SELECT 1 x) a JOIN (SELECT 2 y, 3 z) b ON TRUE"));
        assertEquals("1", rows("SELECT 1 FROM k1 JOIN k4 USING (k) JOIN k2 ON $3 = 40"));
        // A single relation, or a subquery's own, answers as ever.
        assertEquals("1", rows("SELECT f.a FROM full_t f WHERE $1 = 1"));
        assertEquals("1", rows("SELECT (SELECT $1 FROM full_t g WHERE g.a = f.a) FROM full_t f"));
    }

    @Test
    public void aUsingOrNaturalJoinIsOneRelationInItsOwnLayout() {
        assertEquals("1", rows("SELECT $1 FROM full_t f JOIN full_t g USING (a)"));
        assertEquals("1", rows("SELECT $1 FROM full_t f NATURAL JOIN full_t g"));
        assertEquals("2|1|3", rows("SELECT $1, $2, $3 FROM (SELECT 1 a, 2 k) f JOIN (SELECT 2 k, 3 c) g USING (k)"));
        assertRefused("SELECT $4 FROM (SELECT 1 a, 2 k) f JOIN (SELECT 2 k, 3 c) g USING (k)",
            "error line 1 at position 7", "invalid identifier '$4'");
        assertEquals("2|5|1|3",
            rows("SELECT $1, $2, $3, $4 FROM (SELECT 1 a, 2 k, 5 m) f NATURAL JOIN (SELECT 2 k, 3 c, 5 m) g"));
        assertEquals("1|2|2|2", rows("SELECT $1, $2, $3, $4 FROM full_t f JOIN full_t g USING (a) JOIN full_t h USING (a)"));
        assertEquals("1|2|3", rows("SELECT $1, $2, $3 FROM full_t f JOIN (SELECT 1 a, 2 b, 3 c) g USING (a, b)"));
        assertEquals("1", rows("SELECT $1 FROM full_t f NATURAL JOIN (SELECT 5 z) g"));
        assertEquals("1|10|1|20|5", rows("SELECT $1, $2, $3, $4, $5 FROM k1 JOIN k2 ON TRUE JOIN k3 USING (k)"));
        assertEquals("1|2|3",
            rows("SELECT $1, $2, $3 FROM (SELECT 1 x) a JOIN (SELECT 2 y, 3 z) b ON TRUE JOIN (SELECT 1 x) c USING (x)"));
        assertEquals("1", rows("SELECT $1 FROM full_t f JOIN full_t g USING (a) WHERE $2 = 2"));
        assertEquals("5", rows("SELECT $3 FROM k1 JOIN k3 USING (k), k2"));
        assertRefused("SELECT $1 FROM k1 JOIN k3 USING (k), k2", "ambiguous column name '$1'");
    }

    @Test
    public void anOuterUsingJoinKeepsTheKeyCopiesOfTheSidesItExtends() {
        assertEquals("1|10|20", rows("SELECT $1, $2, $3 FROM (SELECT 1 k, 10 v) a JOIN (SELECT 1 k, 20 w) b USING (k)"));
        assertEquals("1|10|null",
            rows("SELECT $1, $2, $3 FROM (SELECT 1 k, 10 v) a LEFT JOIN (SELECT 2 k, 20 w) b USING (k) ORDER BY 1"));
        assertEquals("null", rows("SELECT $4 FROM (SELECT 1 k, 10 v) a LEFT JOIN (SELECT 2 k, 20 w) b USING (k)"));
        assertEquals("2|null|null",
            rows("SELECT $1, $2, $3 FROM (SELECT 1 k, 10 v) a RIGHT JOIN (SELECT 2 k, 20 w) b USING (k) ORDER BY 1"));
        assertEquals("20", rows("SELECT $4 FROM (SELECT 1 k, 10 v) a RIGHT JOIN (SELECT 2 k, 20 w) b USING (k)"));
        assertEquals("1|1|10|null|null;2|null|null|2|20",
            rows("SELECT $1, $2, $3, $4, $5 FROM (SELECT 1 k, 10 v) a FULL JOIN (SELECT 2 k, 20 w) b USING (k) ORDER BY 1"));
        assertEquals("1|1|10|null|null;2|null|null|2|20",
            rows("SELECT $1, $2, $3, $4, $5 FROM (SELECT 1 k, 10 v) a NATURAL FULL JOIN (SELECT 2 k, 20 w) b ORDER BY 1"));
        assertRefused("SELECT $6 FROM (SELECT 1 k, 10 v) a FULL JOIN (SELECT 2 k, 20 w) b USING (k)",
            "error line 1 at position 7", "invalid identifier '$6'");
    }

    @Test
    public void aQualifiedPositionalReadsItsRelation() {
        assertEquals("1|2|5", rows("SELECT f.$1, f.$2, g.$1 FROM full_t f JOIN (SELECT 5 z) g ON TRUE"));
        assertEquals("2", rows("SELECT f.$2 FROM full_t f JOIN full_t g ON TRUE WHERE g.$1 = 1"));
        assertEquals("1", rows("SELECT t.$1 FROM full_t t"));
        assertEquals("1", rows("SELECT \"F\".$1 FROM full_t f"));
        assertEquals("1", rows("SELECT full_t.$1 FROM full_t"));
        assertEquals("20", rows("SELECT k2.$2 FROM k1 JOIN k4 USING (k) JOIN k2 ON TRUE"));
        assertRefused("SELECT f.$3 FROM full_t f", "error line 1 at position 7", "invalid identifier 'F.$3'");
        assertRefused("SELECT x.$1 FROM full_t f", "error line 1 at position 7", "invalid identifier 'X.$1'");
        assertRefused("SELECT f.$0 FROM full_t f", "error line 1 at position 7", "invalid identifier 'F.$0'");
        // A relation a USING join merged no longer answers by position.
        assertRefused("SELECT f.$1 FROM full_t f JOIN full_t g USING (a)", "error line 1 at position 7",
            "invalid identifier 'F.$1'");
        assertRefused("SELECT k1.$1 FROM k1 JOIN k4 USING (k) JOIN k2 ON TRUE", "error line 1 at position 7",
            "invalid identifier 'K1.$1'");
    }

    @Test
    public void anOrderByPositionSortsByTheColumnItReads() {
        final String joined = " FROM (SELECT 1 x) a JOIN " + TWO_ROWS + " b ON TRUE";
        assertEquals("2;1", rows("SELECT b.y" + joined + " ORDER BY $2"));
        assertEquals("1;2", rows("SELECT b.y" + joined + " ORDER BY $2 DESC"));
        assertEquals("2;1", rows("SELECT b.y" + joined + " ORDER BY b.$2"));
        assertEquals("2;1", rows("SELECT b.y" + joined + " ORDER BY $2 + 0"));
        assertEquals("1;2", rows("SELECT b.y" + joined + " ORDER BY -$2"));
        assertEquals("2;1", rows("SELECT b.y" + joined + " ORDER BY b.$2 + a.$1"));
        assertEquals("1|2;3|1", rows("SELECT b.z, b.y" + joined + " ORDER BY $2"));
        assertEquals("2;1", rows("SELECT b.y FROM (SELECT 1 x) a, " + TWO_ROWS + " b ORDER BY $2"));
        assertEquals("2", rows("SELECT (SELECT b.y" + joined + " ORDER BY $2 LIMIT 1)"));
        // A USING join sorts by its own layout: the merged key, then each side's other columns.
        assertEquals("2;1", rows("SELECT k FROM (SELECT 1 k, 3 v UNION ALL SELECT 2, 1) a"
            + " JOIN (SELECT 1 k, 5 w UNION ALL SELECT 2, 4) b USING (k) ORDER BY $2"));
        assertEquals("2;1", rows("SELECT k FROM (SELECT 1 k, 3 v UNION ALL SELECT 2, 1) a"
            + " JOIN (SELECT 1 k, 5 w UNION ALL SELECT 2, 6) b USING (k) ORDER BY $3 DESC"));
        assertRefused("SELECT a.k FROM (SELECT 1 k, 3 v UNION ALL SELECT 2, 1) a"
            + " JOIN (SELECT 1 k, 5 w UNION ALL SELECT 2, 4) b ON a.k = b.k ORDER BY $1", "ambiguous column name '$1'");
        assertRefused("SELECT a.k FROM (SELECT 1 k, 3 v UNION ALL SELECT 2, 1) a JOIN (SELECT 1 k UNION ALL SELECT 2) b"
            + " ON a.k = b.k ORDER BY $3", "error line 1 at position 119", "invalid identifier '$3'");
        // A single relation's position is its own column.
        engine.execute("INSERT INTO full_t VALUES (2, 1)");
        assertEquals("2;1", rows("SELECT a FROM full_t t ORDER BY t.$2"));
        assertEquals("1;2", rows("SELECT a FROM full_t t ORDER BY $2 DESC"));
    }

    @Test
    public void aStagedFileReadsNullPastItsFieldsAndNeverItsMetadataByPosition() {
        engine.execute("CREATE STAGE jpc_st");
        stageLocalFile("jpc_st", "f.csv", "1,2\n");
        assertEquals("1|2|null", rows("SELECT $1, $2, $3 FROM @jpc_st"));
        assertEquals("null|null", rows("SELECT $4096, t.$1000 FROM @jpc_st t"));
        assertEquals("f.csv|null|1", rows("SELECT METADATA$FILENAME, $3, METADATA$FILE_ROW_NUMBER FROM @jpc_st"));
        assertEquals("1", rows("SELECT $1 FROM @jpc_st WHERE $3 IS NULL"));
        assertEquals("z", rows("SELECT COALESCE($3, 'z') FROM @jpc_st"));
        assertRefused("SELECT $4097 FROM @jpc_st", "column '$4097' does not exist");
        assertRefused("SELECT t.$1000000 FROM @jpc_st t", "column '$1000000' does not exist");
        // Over a join the stage is wide enough for every position up to 4096.
        assertRefused("SELECT $2, x.a FROM @jpc_st t JOIN full_t x ON TRUE", "ambiguous column name '$2'");
        assertEquals("null|1", rows("SELECT $3, x.a FROM @jpc_st t JOIN full_t x ON TRUE"));
        assertEquals("null|null|1", rows("SELECT t.$3, t.$9, x.a FROM @jpc_st t JOIN full_t x ON TRUE"));
        assertEquals("null|1", rows("SELECT t.$3, u.$1 FROM @jpc_st t, @jpc_st u"));
        assertRefused("SELECT $3 FROM @jpc_st t, @jpc_st u", "ambiguous column name '$3'");
        assertRefused("SELECT $1000000, x.a FROM @jpc_st t JOIN full_t x ON TRUE", "column '$1000000' does not exist");
        // A derived table's METADATA$FILENAME is a column like any other.
        assertEquals("f.csv", rows("SELECT d.$3 FROM (SELECT $1, $2, METADATA$FILENAME FROM @jpc_st) d"));
        assertRefused("SELECT $4 FROM (SELECT $1, $2, METADATA$FILENAME FROM @jpc_st) d", "error line 1 at position 7",
            "invalid identifier '$4'");
    }

    @Test
    public void aTableStageReadsAsManyPositionsAsItsTableHasColumns() {
        engine.execute("CREATE TABLE te (x INT)");
        assertEquals("", rows("SELECT $1 FROM @%te"));
        assertRefused("SELECT $2 FROM @%te", "error line 1 at position 7", "invalid identifier '$2'");
        assertRefused("SELECT t.$1, t.$2 FROM @%te t", "error line 1 at position 13", "invalid identifier 'T.$2'");
        assertRefused("SELECT $1, x.a FROM @%te t JOIN full_t x ON TRUE", "ambiguous column name '$1'");
        engine.execute("CREATE TABLE tt3 (a INT, b INT, c INT)");
        engine.execute("COPY INTO @%tt3/g.csv FROM (SELECT 1, 2, 3) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE)"
            + " SINGLE = TRUE");
        assertEquals("1|2|3", rows("SELECT $1, $2, $3 FROM @%tt3"));
        assertRefused("SELECT $4 FROM @%tt3", "error line 1 at position 7", "invalid identifier '$4'");
        assertRefused("SELECT t.$4 FROM @%tt3 t", "error line 1 at position 7", "invalid identifier 'T.$4'");
        assertEquals("3|1", rows("SELECT $3, x.a FROM @%tt3 t JOIN full_t x ON TRUE"));
        assertRefused("SELECT $4, x.a FROM @%tt3 t JOIN full_t x ON TRUE", "error line 1 at position 7",
            "invalid identifier '$4'");
    }
}
