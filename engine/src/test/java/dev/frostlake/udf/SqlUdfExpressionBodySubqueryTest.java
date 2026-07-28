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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A SQL UDF whose body is an EXPRESSION may contain a scalar subquery — even over nested, FROM-less
 * derived tables — that references the function's parameters (a real-world scoring-function shape:
 * a documentation comment, then {@code COALESCE((SELECT … FROM (SELECT … FROM (SELECT param …))), 0)}).
 * Parameters used to be bound only as columns of a synthetic parameter table, which the SUBQUERY's
 * execution (through the query engine) never saw — "Column not found". Parameters are now also
 * substituted into the body text (lexer-driven, quote- and comment-safe), making the body
 * self-contained wherever it executes.
 */
public class SqlUdfExpressionBodySubqueryTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void aSubqueryInAnExpressionBodySeesTheParameter() {
        engine.execute("""
            CREATE FUNCTION doubled(x FLOAT) RETURNS FLOAT LANGUAGE SQL AS $$
                COALESCE((SELECT v * 2 FROM (SELECT x AS v)), 0.0) $$""");
        assertEquals(4.0, ((Number) scalar("SELECT doubled(2.0)")).doubleValue());
    }

    @Test
    public void theFullScoringShapeWorks() {
        // Leading comment + CASE over a parsed token + nested FROM-less derived tables + COALESCE fallback.
        engine.execute("""
            CREATE FUNCTION score_of(spec VARCHAR) RETURNS FLOAT LANGUAGE SQL AS $$
                /* parses 'grade:X/weight:Y' and scores it; documentation comment. */
                COALESCE(
                    (
                        SELECT ROUND(CASE g WHEN 'A' THEN 10.0 * w WHEN 'B' THEN 5.0 * w ELSE 0.0 END, 1)
                        FROM (
                            SELECT g, w * 0.5 AS w
                            FROM (
                                SELECT
                                    SPLIT_PART(REGEXP_SUBSTR(spec, 'grade:[^/]+'), ':', 2) AS g,
                                    CASE SPLIT_PART(REGEXP_SUBSTR(spec, 'weight:[^/]+'), ':', 2)
                                        WHEN 'H' THEN 1.0 WHEN 'L' THEN 0.4 ELSE 0.0 END AS w
                            )
                        )
                    ),
                    0.0
                ) $$""");
        assertEquals(5.0, ((Number) scalar("SELECT score_of('grade:A/weight:H')")).doubleValue());
        assertEquals(1.0, ((Number) scalar("SELECT score_of('grade:B/weight:L')")).doubleValue());
        assertEquals(0.0, ((Number) scalar("SELECT score_of('nonsense')")).doubleValue());
        assertEquals(0.0, ((Number) scalar("SELECT score_of(NULL)")).doubleValue());
    }

    @Test
    public void stringParametersAreQuotedAndEscapedInTheSubstitution() {
        engine.execute("""
            CREATE FUNCTION tagged(label VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$
                COALESCE((SELECT 'tag=' || v FROM (SELECT label AS v)), 'none') $$""");
        assertEquals("tag=it's", String.valueOf(scalar("SELECT tagged('it''s')")));
        assertEquals("none", String.valueOf(scalar("SELECT tagged(NULL)")));
    }

    @Test
    public void anObjectParameterKeepsPathAccessAndWorksInSubqueries() {
        // A semi-structured parameter substitutes as PARSE_JSON('…'), not a bare string — bodies
        // apply path access to it (o:field), which must stay parseable and yield the structure.
        // (Substituting it as a plain string literal made this body throw ParseCancellationException.)
        engine.execute("""
            CREATE FUNCTION prefix_of("STATE" OBJECT)
            RETURNS VARCHAR LANGUAGE SQL IMMUTABLE
            AS '''logging/'' || state:step_name || ''/'' || lower(state:db_name)'""");
        assertEquals("logging/load/mydb", String.valueOf(scalar(
            "SELECT prefix_of(OBJECT_CONSTRUCT('step_name', 'load', 'db_name', 'MYDB'))")));
        engine.execute("""
            CREATE FUNCTION field_via_subq(o OBJECT) RETURNS VARCHAR LANGUAGE SQL AS $$
                COALESCE((SELECT v FROM (SELECT o:k::VARCHAR AS v)), 'none') $$""");
        assertEquals("hit", String.valueOf(scalar("SELECT field_via_subq(OBJECT_CONSTRUCT('k', 'hit'))")));
    }

    @Test
    public void aParameterNamedLikeASchemaQualifierDoesNotRewriteQualifiedNames() {
        // The parameter substitution must skip DOT-ADJACENT identifiers: in `util.stats.tag_of(...)`
        // the segment `stats` is a schema qualifier, not the parameter — rewriting it turned the
        // qualified call into nonsense (ParseCancellationException). `stats:field` (colon) still binds.
        engine.execute("CREATE DATABASE util");
        engine.execute("CREATE SCHEMA util.stats");
        engine.execute("""
            CREATE FUNCTION util.stats.tag_of(name VARCHAR) RETURNS VARCHAR LANGUAGE SQL
            AS 'UPPER(name)'""");
        engine.execute("""
            CREATE FUNCTION describe_run("STATS" OBJECT) RETURNS VARCHAR LANGUAGE SQL
            AS '''run/'' || stats:step || ''/'' || util.stats.tag_of(stats:owner)'""");
        assertEquals("run/load/ME", String.valueOf(scalar(
            "SELECT describe_run(OBJECT_CONSTRUCT('step', 'load', 'owner', 'me'))")));
    }

    @Test
    public void aParameterNamedLikeAPathSegmentDoesNotRewriteThePath() {
        // In `so:child_stats` the segment after the colon is a JSON FIELD NAME — it must survive even
        // when another parameter is also called child_stats (the stats-accumulator idiom:
        // OBJECT_INSERT(so, 'child_stats', ARRAY_APPEND(so:child_stats, child_stats), true)).
        engine.execute("""
            CREATE FUNCTION add_child("SO" OBJECT, "CHILD_STATS" OBJECT)
            RETURNS OBJECT LANGUAGE SQL IMMUTABLE
            AS 'OBJECT_INSERT(so, ''child_stats'', ARRAY_APPEND(so:child_stats, child_stats), true)'""");
        assertEquals("{\"child_stats\":[{\"name\":\"kid\"}],\"name\":\"parent\"}", String.valueOf(scalar("""
            SELECT add_child(OBJECT_CONSTRUCT('name', 'parent', 'child_stats', ARRAY_CONSTRUCT()),
                             OBJECT_CONSTRUCT('name', 'kid'))""")));
    }

    @Test
    public void aPlainExpressionBodyStillWorks() {
        engine.execute("CREATE FUNCTION twice(x NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ x * 2 $$");
        assertEquals(14L, ((Number) scalar("SELECT twice(7)")).longValue());
        engine.execute("CREATE FUNCTION always_null() RETURNS VARCHAR LANGUAGE SQL AS $$ NULL $$");
        assertNull(scalar("SELECT always_null()"));
    }
}
