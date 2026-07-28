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
 * Snowflake-fidelity gaps found by triaging a large external test suite against the engine: FLATTEN over a
 * SQL NULL, the {@code \"} string escape, HAVING without GROUP BY, a projection over an empty implicit
 * group, and the VARIANT path operator being mistaken for a Scripting bind variable.
 */
public class WorkflowTriageFixesTest extends BaseDatabaseTest {

    private ResultSet q(final String sql) {
        return engine.executeQuery(sql);
    }

    private String scalar(final String sql) {
        final Object v = q(sql).getRows().get(0).getValue(0);
        return v == null ? null : v.toString();
    }

    // ---- FLATTEN over a SQL NULL is a non-expandable input, not an error ----

    @Test
    public void flattenSkipsRowsWhoseInputIsNull() {
        engine.execute("CREATE TABLE fl (id INTEGER, entries ARRAY)");
        engine.execute("INSERT INTO fl SELECT 1, PARSE_JSON('[1,2]')");
        engine.execute("INSERT INTO fl SELECT 2, NULL");
        // Previously the whole statement aborted with "FLATTEN requires INPUT parameter": the table function
        // is expanded for every left row, so a single NULL anywhere in the column killed the query.
        assertEquals(2, q("SELECT s.id FROM fl s, TABLE(FLATTEN(s.entries)) f").getRowCount());
    }

    @Test
    public void flattenOuterEmitsOneNullRowForANullInput() {
        engine.execute("CREATE TABLE fl (id INTEGER, entries ARRAY)");
        engine.execute("INSERT INTO fl SELECT 1, PARSE_JSON('[1,2]')");
        engine.execute("INSERT INTO fl SELECT 2, NULL");
        assertEquals(3, q("SELECT s.id FROM fl s, TABLE(FLATTEN(s.entries, outer => TRUE)) f").getRowCount());
        assertEquals(1, q("SELECT * FROM TABLE(FLATTEN(input => NULL, outer => TRUE))").getRowCount());
        assertEquals(0, q("SELECT * FROM TABLE(FLATTEN(input => NULL))").getRowCount());
    }

    // ---- string-literal escapes ----

    @Test
    public void doubleQuoteEscapeIsDecoded() {
        // '{\"a\":1}' denotes {"a":1}; leaving the backslash in made every such literal invalid JSON.
        assertEquals("{\"a\":1}", scalar("SELECT '{\\\"a\\\":1}'"));
        assertEquals("1", scalar("SELECT PARSE_JSON('{\\\"k\\\":1}'):k::VARCHAR"));
    }

    @Test
    public void otherSimpleAndHexEscapesAreDecoded() {
        assertEquals(2L, ((Number) q("SELECT LENGTH('\\b\\f')").getRows().get(0).getValue(0)).longValue());
        assertEquals("AB", scalar("SELECT '\\x41\\u0042'"));
    }

    @Test
    public void backslashDigitStaysVerbatimForRegexBackreferences() {
        // Regression guard: a digit after the backslash must NOT be decoded, or REGEXP_REPLACE's
        // replacement back-references break.
        assertEquals("[b][a]", scalar("SELECT REGEXP_REPLACE('ab', '(a)(b)', '[\\2][\\1]')"));
        assertEquals(1L, ((Number) q("SELECT LENGTH('\\\\')").getRows().get(0).getValue(0)).longValue());
    }

    // ---- HAVING with no GROUP BY forms a single implicit group ----

    @Test
    public void havingWithoutGroupByIsAnAggregateQuery() {
        engine.execute("CREATE TABLE t2 (k INTEGER)");
        engine.execute("INSERT INTO t2 VALUES (1), (2)");
        // Aggregate detection looked only at the SELECT list, so this was a plain row scan AND the HAVING
        // was dropped: it returned both input rows.
        assertEquals(1, q("SELECT 'X' FROM t2 HAVING COUNT(*) != 1").getRowCount());
        assertEquals(0, q("SELECT 'X' FROM t2 HAVING COUNT(*) = 1").getRowCount());
        assertEquals("X", scalar("SELECT 'X' FROM t2 HAVING COUNT(*) = 2"));
    }

    @Test
    public void constantProjectsOverAnEmptyImplicitGroup() {
        engine.execute("CREATE TABLE t2 (k INTEGER)");
        engine.execute("INSERT INTO t2 VALUES (1), (2)");
        // The row-count assertion idiom: no matching rows must still yield the literal, not NULL.
        assertEquals("MISSING", scalar("SELECT 'MISSING' FROM t2 WHERE k = 99 HAVING COUNT(*) != 1"));
        assertEquals("X", scalar("SELECT 'X', COUNT(*) FROM t2 WHERE k = 99"));
        assertEquals(0L, ((Number) q("SELECT 'X', COUNT(*) FROM t2 WHERE k = 99")
            .getRows().get(0).getValue(1)).longValue());
    }

    // ---- the VARIANT path operator is not a bind variable ----

    @Test
    public void variantPathSurvivesInsideAnIfBody() {
        engine.execute("CREATE TABLE vt (src VARIANT)");
        engine.execute("INSERT INTO vt SELECT PARSE_JSON('{\"a\":{\"b\":7}}')");
        // Statements nested in an IF/loop body are re-rendered through bind substitution, which took every
        // `:key` of a path for an unknown bind variable and spliced in the text NULL — so `s.src:a:b`
        // became the single identifier `s.srcNULLNULL` ("Column not found: S.SRCNULLNULL").
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p() RETURNS VARCHAR LANGUAGE SQL AS $$
            BEGIN
              IF (1 = 1) THEN
                RETURN (SELECT s.src:a:b::VARCHAR FROM vt s);
              END IF;
              RETURN 'no';
            END
            $$""");
        assertEquals("7", scalar("CALL p()"));
    }

    @Test
    public void realBindVariableStillSubstitutesAlongsideAPath() {
        engine.execute("CREATE TABLE vt (src VARIANT)");
        engine.execute("INSERT INTO vt SELECT PARSE_JSON('{\"a\":{\"b\":7}}')");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p2() RETURNS VARCHAR LANGUAGE SQL AS $$
            DECLARE
              v VARCHAR DEFAULT 'z';
            BEGIN
              IF (1 = 1) THEN
                RETURN (SELECT s.src:a:b::VARCHAR FROM vt s WHERE :v = 'z');
              END IF;
              RETURN 'no';
            END
            $$""");
        assertEquals("7", scalar("CALL p2()"));
    }

    // ---- FROM TABLE(<string|bind var>) — a Snowflake table literal, equivalent to IDENTIFIER() ----

    @Test
    public void fromTableLiteralResolvesATableViewOrStream() {
        engine.execute("CREATE TABLE t1 (c VARCHAR)");
        engine.execute("INSERT INTO t1 VALUES ('c1'), ('c2')");
        engine.execute("CREATE VIEW v1 AS SELECT c FROM t1");
        engine.execute("CREATE STREAM st1 ON TABLE t1 APPEND_ONLY=TRUE SHOW_INITIAL_ROWS=TRUE");
        assertEquals(2, q("SELECT * FROM TABLE('T1')").getRowCount());
        assertEquals(2, q("SELECT * FROM TABLE('t1')").getRowCount());
        assertEquals(2, q("SELECT * FROM TABLE('V1')").getRowCount());
        assertEquals(2, q("SELECT * FROM TABLE('ST1')").getRowCount());
        assertEquals("c2", scalar("SELECT z.c FROM TABLE('T1') z WHERE z.c = 'c2'"));
    }

    @Test
    public void fromTableLiteralAcceptsABindVariable() {
        engine.execute("CREATE TABLE t1 (c VARCHAR)");
        engine.execute("INSERT INTO t1 VALUES ('c1'), ('c2')");
        engine.execute("CREATE TABLE t2 (c VARCHAR)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p() RETURNS VARCHAR LANGUAGE SQL AS $$
            DECLARE
              src VARCHAR DEFAULT 'T1';
            BEGIN
              INSERT INTO t2 SELECT c FROM TABLE(:src);
              RETURN (SELECT COUNT(*)::VARCHAR FROM t2);
            END
            $$""");
        assertEquals("2", scalar("CALL p()"));
    }

    @Test
    public void tableFunctionCallsAndUnknownNamesAreUnaffected() {
        assertEquals(3L, ((Number) q("SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 3))")
            .getRows().get(0).getValue(0)).longValue());
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM TABLE('NO_SUCH_RELATION')");
            }
        });
    }

    // ---- CALL must not fail while restoring the caller's context; USE DATABASE moves the schema ----

    @Test
    public void useDatabaseResetsTheCurrentSchema() {
        engine.execute("CREATE DATABASE other_db");
        engine.execute("CREATE SCHEMA other_db.only_here");
        engine.execute("USE DATABASE other_db");
        engine.execute("USE SCHEMA only_here");
        engine.execute("USE DATABASE " + engine.getCurrentDatabase());
        // Leaving ONLY_HERE current would be an impossible (database, schema) pair.
        assertEquals("PUBLIC", scalar("SELECT CURRENT_SCHEMA()"));
    }

    @Test
    public void callRestoresContextWithoutFailingTheCall() {
        engine.execute("CREATE DATABASE tools_db");
        engine.execute("CREATE SCHEMA tools_db.stats");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE tools_db.stats.echo(o OBJECT) RETURNS OBJECT LANGUAGE SQL AS $$
            BEGIN
              RETURN :o;
            END
            $$""");
        engine.execute("CREATE DATABASE site_db");
        engine.execute("CREATE SCHEMA site_db.tdm");
        engine.execute("USE DATABASE site_db");
        engine.execute("USE SCHEMA tdm");
        engine.execute("USE DATABASE tools_db");
        // The restore used to re-validate the saved pair inside a finally, so a stale schema turned the
        // CALL's real result into "Schema does not exist" that no EXCEPTION handler could catch.
        assertEquals("{\"a\":1}", scalar("CALL stats.echo(OBJECT_CONSTRUCT('a', 1))"));
    }

    // ---- a numeric VARCHAR is coerced in an arithmetic context ----

    @Test
    public void numericVarcharIsCoercedInArithmetic() {
        assertEquals("-3", scalar("SELECT -'3'"));
        assertEquals("4", scalar("SELECT '3' + 1"));
        assertEquals("6", scalar("SELECT '3' * 2"));
        assertEquals("6", scalar("SELECT '10' - '4'"));
        // The loader shape: SPLIT_TO_TABLE's VALUE column is VARCHAR and gets negated.
        assertEquals("2024-04-15", scalar(
            "SELECT DATEADD(month, -x.value, DATE '2024-06-15') FROM TABLE(SPLIT_TO_TABLE('2', ',')) x"));
    }

    @Test
    public void nonNumericStringAndNullKeepTheirBehaviour() {
        assertEquals(Boolean.TRUE.toString(), scalar("SELECT (-CAST(NULL AS VARCHAR)) IS NULL"));
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT -'abc'");
            }
        });
        // Concatenation is || — unaffected.
        assertEquals("ab", scalar("SELECT 'a' || 'b'"));
        assertEquals("2024-01-06", scalar("SELECT DATE '2024-01-01' + 5"));
    }

    // ---- ::ARRAY / ::OBJECT follow TO_ARRAY semantics instead of doing nothing ----

    @Test
    public void castToArrayWrapsAScalar() {
        assertEquals("[\"X9\"]", scalar("SELECT 'X9'::ARRAY"));
        assertEquals("ARRAY", scalar("SELECT TYPEOF('X9'::ARRAY)"));
        assertEquals(Boolean.TRUE.toString(), scalar("SELECT ARRAY_CONTAINS('X9'::VARIANT, 'X9'::ARRAY)"));
        // An existing array is unchanged, and the function form still agrees.
        assertEquals("[\"a\",\"b\"]", scalar("SELECT ARRAY_CONSTRUCT('a','b')::ARRAY"));
        assertEquals("[\"X9\"]", scalar("SELECT TO_ARRAY('X9')"));
    }

    @Test
    public void castToArrayIsUsableInAnArrayColumn() {
        engine.execute("CREATE TABLE h (item_id VARCHAR, class_tags ARRAY)");
        engine.execute("INSERT INTO h SELECT 'a1', 'X9'::ARRAY");
        // A bare VARCHAR stored in an ARRAY column matched nothing here.
        assertEquals("a1", scalar("SELECT item_id FROM h WHERE ARRAY_CONTAINS('X9'::VARIANT, class_tags)"));
    }

    @Test
    public void castToObjectPassesObjectsAndRejectsScalars() {
        assertEquals("{\"k\":1}", scalar("SELECT OBJECT_CONSTRUCT('k',1)::OBJECT"));
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT 'x'::OBJECT");
            }
        });
    }

    // ---- a set operation's ORDER BY resolves against the produced result-column names ----

    @Test
    public void setOperationOrderByResolvesUnqualifiedResultColumn() {
        engine.execute("CREATE TABLE ca (region_id VARCHAR, item_key VARCHAR)");
        engine.execute("CREATE TABLE cb (region_id VARCHAR, item_key VARCHAR)");
        engine.execute("INSERT INTO ca VALUES ('c3','k1'), ('c1','k2')");
        engine.execute("INSERT INTO cb VALUES ('c2','k3')");
        final ResultSet rs = q("SELECT a.region_id, a.item_key FROM ca a "
            + "UNION ALL SELECT b.region_id, b.item_key FROM cb b ORDER BY region_id, item_key");
        assertEquals(3, rs.getRowCount());
        assertEquals("c1", rs.getRows().get(0).getValue(0).toString());
        assertEquals("c3", rs.getRows().get(2).getValue(0).toString());
        assertEquals(2, q("SELECT a.region_id FROM ca a EXCEPT SELECT b.region_id FROM cb b "
            + "ORDER BY region_id").getRowCount());
    }

    @Test
    public void orderByAnAbsentNameStillFails() {
        engine.execute("CREATE TABLE ca (region_id VARCHAR)");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT a.region_id FROM ca a UNION ALL "
                    + "SELECT b.region_id FROM ca b ORDER BY nope");
            }
        });
    }

    // ---- INSERT OVERWRITE must read its source BEFORE replacing the table ----

    @Test
    public void selfReferencingInsertOverwriteSeesTheOldRows() {
        engine.execute("CREATE TABLE so (id VARCHAR, n INTEGER)");
        engine.execute("INSERT INTO so VALUES ('A', 1), ('B', 2)");
        // Truncating first made the source SELECT read an empty table, so the statement silently wiped it.
        engine.execute("INSERT OVERWRITE INTO so (id, n) SELECT id, n + 10 FROM so");
        final ResultSet rs = q("SELECT id, n FROM so ORDER BY id");
        assertEquals(2, rs.getRowCount());
        assertEquals(11L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals(12L, ((Number) rs.getRows().get(1).getValue(1)).longValue());
    }

    @Test
    public void insertOverwriteStillReplacesContents() {
        engine.execute("CREATE TABLE so (id VARCHAR)");
        engine.execute("INSERT INTO so VALUES ('A'), ('B')");
        engine.execute("INSERT OVERWRITE INTO so (id) VALUES ('C')");
        assertEquals(1, q("SELECT id FROM so").getRowCount());
        assertEquals("C", scalar("SELECT id FROM so"));
    }

    // ---- an array literal equals the identical array built any other way ----

    @Test
    public void arrayLiteralEqualsArrayConstructAndParseJson() {
        assertEquals(Boolean.TRUE.toString(), scalar("SELECT ['x','y']::ARRAY = ARRAY_CONSTRUCT('x','y')"));
        engine.execute("CREATE TABLE l (a ARRAY)");
        engine.execute("INSERT INTO l SELECT ['x','y']::ARRAY");
        engine.execute("CREATE TABLE j (a ARRAY)");
        engine.execute("INSERT INTO j SELECT PARSE_JSON('{\"t\":[\"x\",\"y\"]}'):t::ARRAY");
        assertEquals(0, q("SELECT a FROM l EXCEPT SELECT a FROM j").getRowCount());
    }

    // ---- an unquoted date part survives a procedural assignment RHS ----

    @Test
    public void keywordArgumentSurvivesAProceduralAssignment() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p3() RETURNS VARCHAR LANGUAGE SQL AS $$
            BEGIN
              LET x TIMESTAMP := DATEADD(MINUTE, 30, TO_TIMESTAMP_NTZ('2024-01-01 00:00:00'));
              RETURN :x::VARCHAR;
            END
            $$""");
        // The RHS was rebuilt from evaluated argument VALUES, so the bare date part became the literal NULL.
        // (::VARCHAR renders Snowflake's default output form — space separator, FF3 — not java.time's T-form.)
        assertEquals("2024-01-01 00:30:00.000", scalar("CALL p3()"));
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p4() RETURNS VARCHAR LANGUAGE SQL AS $$
            BEGIN
              LET n VARCHAR := DATEDIFF(DAY, DATE '2024-01-01', DATE '2024-03-01')::VARCHAR;
              RETURN :n;
            END
            $$""");
        assertEquals("60", scalar("CALL p4()"));
    }

    @Test
    public void aRealVariableIsStillSubstitutedInAnAssignmentRhs() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p5() RETURNS VARCHAR LANGUAGE SQL AS $$
            DECLARE
              v VARCHAR DEFAULT 'minute';
            BEGIN
              LET x VARCHAR := UPPER(:v);
              RETURN :x;
            END
            $$""");
        assertEquals("MINUTE", scalar("CALL p5()"));
    }

    // ---- a Python UDF returning a dict/list yields a VARIANT, not a Jython object ----

    @Test
    public void pythonDictReturnBecomesAVariant() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION pyobj(k VARCHAR) RETURNS VARIANT
            LANGUAGE PYTHON RUNTIME_VERSION='3.11' HANDLER='h' AS $$
            def h(k):
                return {'a': k, 'n': 2}
            $$""");
        assertEquals("x", scalar("SELECT pyobj('x'):a::VARCHAR"));
        // PyObject.hashCode() delegates to Python __hash__, so any hash-based stage used to raise
        // "unhashable type: 'dict'".
        assertEquals(1, q("SELECT DISTINCT pyobj('x')").getRowCount());
    }

    @Test
    public void pythonListReturnBecomesAVariantArray() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION pylist(k VARCHAR) RETURNS VARIANT
            LANGUAGE PYTHON RUNTIME_VERSION='3.11' HANDLER='h' AS $$
            def h(k):
                return [k, 1, True]
            $$""");
        assertEquals("[\"x\",1,true]", scalar("SELECT pylist('x')"));
        assertEquals("x", scalar("SELECT pylist('x')[0]::VARCHAR"));
    }

    // ---- ANSI derived-column alias lists: FROM t AS d (a,b) and PIVOT(...) AS p (c1,c2) ----

    @Test
    public void baseTableAliasColumnListRenamesColumns() {
        engine.execute("CREATE TABLE qs (empid INTEGER, amount INTEGER, quarter VARCHAR)");
        engine.execute("INSERT INTO qs VALUES (1, 10, 'Q1')");
        // The list parsed but was honoured only for subquery / VALUES / table-function sources, so over a base
        // table the query then failed on the renamed columns.
        assertEquals("1", scalar("SELECT a FROM qs AS d (a, b, c)"));
        assertEquals("10", scalar("SELECT b FROM qs AS d (a, b, c) WHERE a = 1"));
    }

    @Test
    public void pivotAliasColumnListParsesAndRenames() {
        engine.execute("CREATE TABLE qs (empid INTEGER, amount INTEGER, quarter VARCHAR)");
        engine.execute("INSERT INTO qs VALUES (1, 10, 'Q1'), (1, 20, 'Q2')");
        // Previously a syntax error, which failed the parse of the whole enclosing block.
        final ResultSet rs = q("SELECT * FROM qs PIVOT(SUM(amount) FOR quarter IN ('Q1','Q2')) AS p (eid, q1, q2)");
        assertEquals("EID", rs.getColumns().get(0).getName());
        assertEquals("Q1", rs.getColumns().get(1).getName());
        assertEquals("Q2", rs.getColumns().get(2).getName());
        // A pivot's columns are otherwise named after the pivoted values, so this list is the only way an
        // outer query can reference them.
        assertEquals("1", scalar("SELECT eid FROM (SELECT * FROM qs "
            + "PIVOT(SUM(amount) FOR quarter IN ('Q1','Q2')) AS p (eid, q1, q2)) WHERE q2 > q1"));
    }

    @Test
    public void unpivotAliasColumnListParses() {
        assertEquals(1, q("SELECT * FROM (SELECT 1 AS empid, 10 AS q1) "
            + "UNPIVOT(amount FOR quarter IN (q1)) AS u (a, b)").getRowCount());
    }

    @Test
    public void pivotWithoutAnAliasListIsUnchanged() {
        engine.execute("CREATE TABLE qs (empid INTEGER, amount INTEGER, quarter VARCHAR)");
        engine.execute("INSERT INTO qs VALUES (1, 10, 'Q1'), (1, 20, 'Q2')");
        final ResultSet rs = q("SELECT * FROM qs PIVOT(SUM(amount) FOR quarter IN ('Q1','Q2')) AS p");
        assertEquals("EMPID", rs.getColumns().get(0).getName());
        assertEquals(3, rs.getColumns().size());
    }

    // ---- COUNT(DISTINCT a, b) counts distinct COMBINATIONS and drops NULL tuples ----

    @Test
    public void countDistinctOverSeveralArguments() {
        engine.execute("CREATE TABLE pairs (a INTEGER, b INTEGER)");
        engine.execute("INSERT INTO pairs VALUES (1,1), (1,1), (1,2), (2,1), (1,NULL)");
        // Only the first argument used to be evaluated, so this returned COUNT(DISTINCT a) = 2. The (1,NULL)
        // row is excluded because one expression is NULL.
        assertEquals(3L, ((Number) q("SELECT COUNT(DISTINCT a, b) FROM pairs").getRows().get(0).getValue(0)).longValue());
        assertEquals(2L, ((Number) q("SELECT COUNT(DISTINCT a) FROM pairs").getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void countDistinctWithAConditionalSecondArgument() {
        engine.execute("CREATE TABLE st (proc_name VARCHAR, priority VARCHAR)");
        engine.execute("INSERT INTO st VALUES ('p_crit','CRITICAL'), ('p_high','HIGH'), ('p_low','LOW')");
        // The loader shape: a CASE that is NULL for every row except the matching priority.
        final ResultSet rs = q("SELECT COUNT(DISTINCT proc_name, CASE WHEN priority = 'CRITICAL' THEN 1 END), "
            + "COUNT(DISTINCT proc_name) FROM st");
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(3L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
    }

    // ---- a SELECT-list alias is visible in the same query's WHERE ----

    @Test
    public void selectAliasIsVisibleInWhere() {
        engine.execute("CREATE TABLE f (metadata_file_name VARCHAR)");
        engine.execute("INSERT INTO f VALUES ('raw/kb/diff-202401011200.json.gz')");
        assertEquals("diff", scalar("SELECT 'diff' AS file_type FROM f WHERE file_type = 'diff'"));
        assertEquals("raw/kb/diff", scalar("SELECT SPLIT(metadata_file_name,'-')[0]::VARCHAR AS ft "
            + "FROM f WHERE LOWER(ft) LIKE '%diff%'"));
        // Filtering still works: a predicate the alias fails must exclude the row.
        assertEquals(0, q("SELECT 'diff' AS file_type FROM f WHERE file_type = 'all'").getRowCount());
    }

    @Test
    public void aRealColumnBeatsASameNamedAliasInWhere() {
        engine.execute("CREATE TABLE g (k VARCHAR, v VARCHAR)");
        engine.execute("INSERT INTO g VALUES ('real', 'x')");
        // `k` in the WHERE must be the table's column, not the aliased constant.
        assertEquals(1, q("SELECT 'alias' AS k, v FROM g WHERE k = 'real'").getRowCount());
        assertEquals(0, q("SELECT 'alias' AS k, v FROM g WHERE k = 'alias'").getRowCount());
    }

    @Test
    public void lateralAliasWorksWithoutAFromClause() {
        // SELECT 1 AS x, x + 1 AS y failed outright; the same query WITH a FROM already worked.
        final ResultSet rs = q("SELECT 1 AS x, x + 1 AS y");
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
    }

    // ---- PARSE_JSON tolerates the deviations Snowflake tolerates ----

    @Test
    public void parseJsonAcceptsATrailingComma() {
        assertEquals("{\"a\":1}", scalar("SELECT PARSE_JSON('{\"a\": 1, }')"));
        // The shape a real payload carries: a trailing comma after a nested array.
        assertEquals("{\"ip\":{\"IPv4\":[\"1.2.3.4\"]}}",
            scalar("SELECT PARSE_JSON('{\"ip\": {\"IPv4\": [\"1.2.3.4\" ], }}')"));
        assertEquals("7", scalar("SELECT PARSE_JSON('{\"a\": {\"b\": 7}, }'):a:b::VARCHAR"));
    }

    @Test
    public void parseJsonAcceptsARawControlCharacterInAString() {
        // Message keys arrive with a binary prefix, which strict JSON rejects inside a string. The control
        // character is PRESERVED in the parsed value, so the string is 5 characters and not 4.
        final String path = "PARSE_JSON('{\"k\": \"ab' || CHR(7) || 'cd\"}'):k::VARCHAR";
        assertEquals(5L, ((Number) q("SELECT LENGTH(" + path + ")").getRows().get(0).getValue(0)).longValue());
        assertEquals("ab", scalar("SELECT LEFT(" + path + ", 2)"));
    }

    @Test
    public void parseJsonStillRejectsGenuinelyInvalidText() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT PARSE_JSON('{not json')");
            }
        });
        assertEquals(Boolean.TRUE.toString(), scalar("SELECT TRY_PARSE_JSON('{not json') IS NULL"));
        // Existing leniency and well-formed input are unchanged.
        assertEquals("{\"a\":[1,2],\"b\":null}", scalar("SELECT PARSE_JSON('{\"a\":[1,2],\"b\":null}')"));
    }

    // ---- SELECT … INTO :v1, :v2 targets are not values ----

    @Test
    public void selectIntoTargetsSurviveInsideAnIfBody() {
        engine.execute("CREATE TABLE ab (a INTEGER, b INTEGER)");
        engine.execute("INSERT INTO ab VALUES (7, 9)");
        // Inside an IF body the statement is re-rendered through bind substitution, which replaced the INTO
        // TARGETS with their current values — producing `INTO '1', '2'` and a syntax error at the literal.
        engine.execute("""
            CREATE OR REPLACE PROCEDURE pinto() RETURNS VARCHAR LANGUAGE SQL AS $$
            DECLARE
              v1 INTEGER DEFAULT 1;
              v2 INTEGER DEFAULT 2;
            BEGIN
              IF (1 = 1) THEN
                SELECT a, b INTO :v1, :v2 FROM ab;
              END IF;
              RETURN :v1::VARCHAR || '/' || :v2::VARCHAR;
            END
            $$""");
        assertEquals("7/9", scalar("CALL pinto()"));
    }

    @Test
    public void insertIntoAndOrdinaryBindsAreUnaffected() {
        engine.execute("CREATE TABLE ab (a INTEGER, b INTEGER)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE pins() RETURNS VARCHAR LANGUAGE SQL AS $$
            DECLARE
              n INTEGER DEFAULT 5;
            BEGIN
              IF (1 = 1) THEN
                INSERT INTO ab (a, b) VALUES (:n, :n + 1);
              END IF;
              RETURN (SELECT a::VARCHAR || '/' || b::VARCHAR FROM ab);
            END
            $$""");
        assertEquals("5/6", scalar("CALL pins()"));
    }
}
