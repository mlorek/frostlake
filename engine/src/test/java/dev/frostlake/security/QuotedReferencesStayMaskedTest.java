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

package dev.frostlake.security;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every reference to a masked column reads the masked value, however it is written and wherever it stands
 * (documented: the policy applies wherever the column appears in the query: projections, JOIN predicates, WHERE
 * predicates, ORDER BY and GROUP BY). Masking rewrote the select list's references and skipped every quoted one, so
 * {@code "S"} read the raw value in any expression, qualified or not, and through a view; a column with a quoted
 * mixed-case name was never masked; and a WHERE, a GROUP BY, a JOIN, a subquery and OBJECT_CONSTRUCT(*) read every
 * masked column raw however it was spelled.
 */
public class QuotedReferencesStayMaskedTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE qm_db");
        engine.execute("USE SCHEMA qm_db.PUBLIC");
        engine.execute("CREATE MASKING POLICY mp AS (v VARCHAR) RETURNS VARCHAR -> '***'");
        engine.execute("CREATE TABLE t (a INT, s VARCHAR WITH MASKING POLICY mp, \"mixed\" VARCHAR WITH MASKING POLICY mp)");
        engine.execute("ALTER TABLE t SET CHANGE_TRACKING = TRUE");
        engine.execute("INSERT INTO t VALUES (1, 'secret', 'quiet')");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? "; " : "").append(row.getValues());
        }
        return out.toString();
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    @Test
    public void everySpellingOfAMaskedColumnReadsMasked() {
        final String[] spellings = {"s", "\"S\"", "t.\"S\"", "T.S", "UPPER(\"S\")", "\"S\" || ''", "LOWER(t.\"S\")"};
        for (final String spelling : spellings) {
            assertEquals("[***]", rows("SELECT " + spelling + " FROM t"), spelling);
        }
        assertEquals("[***]", rows("SELECT x.\"S\" FROM t x"));
    }

    @Test
    public void aQuotedMixedCaseColumnIsMaskedEvenThroughAStar() {
        assertEquals("[***]", rows("SELECT \"mixed\" FROM t"));
        assertEquals("[1, ***, ***]", rows("SELECT * FROM t"));
        assertEquals("[{\"A\":1,\"S\":\"***\",\"mixed\":\"***\"}]", rows("SELECT OBJECT_CONSTRUCT(*)::VARCHAR FROM t"));
        assertEquals("[{\"A\":1,\"S\":\"***\",\"mixed\":\"***\"}]",
            rows("SELECT o::VARCHAR FROM (SELECT {*} AS o FROM t)"));
    }

    @Test
    public void viewsSubqueriesAndCommonTableExpressionsReadMasked() {
        engine.execute("CREATE VIEW v AS SELECT \"S\" AS col, \"mixed\" AS m FROM t");
        assertEquals("[***, ***]", rows("SELECT col, m FROM v"));
        assertEquals("[***]", rows("WITH c AS (SELECT \"S\" AS x FROM t) SELECT x FROM c"));
        assertEquals("[***]", rows("SELECT (SELECT MAX(\"S\") FROM t)"));
        assertEquals("[***]", rows("SELECT x FROM (SELECT t.\"S\" AS x FROM t)"));
    }

    @Test
    public void filtersGroupingAndJoinsSeeTheMaskedValue() {
        engine.execute("INSERT INTO t VALUES (2, 'other', 'loud')");
        assertEquals("", rows("SELECT a FROM t WHERE s = 'secret'"));
        assertEquals("", rows("SELECT a FROM t WHERE \"S\" = 'secret'"));
        assertEquals("[1]; [2]", rows("SELECT a FROM t WHERE \"S\" = '***' ORDER BY a"));
        assertEquals("[***, 2]", rows("SELECT \"S\", COUNT(*) FROM t GROUP BY \"S\""));
        assertEquals("", rows("SELECT t1.a FROM t t1 JOIN t t2 ON t1.a = t2.a AND t2.\"S\" = 'secret'"));
        assertEquals("[1]; [2]", rows("SELECT a FROM t ORDER BY \"S\", a"));
        assertEquals("[1]", rows("SELECT COUNT(DISTINCT \"mixed\") FROM t"));
    }

    @Test
    public void udfArgumentsTimeTravelStreamsChangesAndTableFunctionsReadMasked() throws InterruptedException {
        engine.execute("CREATE FUNCTION echo(x VARCHAR) RETURNS VARCHAR AS $$ x $$");
        assertEquals("[***]", rows("SELECT echo(\"S\") FROM t"));
        Thread.sleep(15);
        engine.execute("SET p = CURRENT_TIMESTAMP()");
        engine.execute("CREATE STREAM ts ON TABLE t");
        Thread.sleep(15);
        engine.execute("INSERT INTO t VALUES (2, 'secret2', 'quiet2')");
        assertEquals("[***]", rows("SELECT \"S\" FROM t AT(TIMESTAMP => $p)"));
        assertEquals("[***, ***]", rows("SELECT \"S\", \"mixed\" FROM ts"));
        assertEquals("[***, ***]", rows("SELECT \"S\", \"mixed\" FROM t CHANGES(INFORMATION => DEFAULT)"
            + " AT(TIMESTAMP => $p)"));
        assertEquals("[***]; [***]", rows("SELECT \"S\" FROM IDENTIFIER('t')"));
        assertEquals("[***]; [***]", rows("SELECT \"mixed\" FROM TABLE('t')"));
    }

    @Test
    public void writesAndUnloadsFromAMaskedColumnCarryTheMaskedValue() {
        engine.execute("CREATE TABLE copied AS SELECT \"S\", \"mixed\" FROM t");
        assertEquals("[***, ***]", rows("SELECT * FROM copied"));
        engine.execute("CREATE TABLE sink (x VARCHAR)");
        engine.execute("INSERT INTO sink SELECT \"mixed\" FROM t");
        assertEquals("[***]", rows("SELECT * FROM sink"));
        engine.execute("CREATE STAGE st");
        engine.execute("COPY INTO @st/whole FROM t FILE_FORMAT = (TYPE = CSV)");
        assertEquals("[1, ***, ***]", rows("SELECT $1, $2, $3 FROM @st/whole"));
        engine.execute("COPY INTO @st/query FROM (SELECT \"S\" FROM t) FILE_FORMAT = (TYPE = CSV)");
        assertEquals("[***]", rows("SELECT $1 FROM @st/query"));
    }

    @Test
    public void aPolicyThatExemptsARoleStillShowsThatRoleTheValue() {
        engine.execute("CREATE ROLE analyst");
        engine.execute("GRANT ROLE analyst TO ROLE SYSADMIN");
        engine.execute("GRANT USAGE ON DATABASE qm_db TO ROLE analyst");
        engine.execute("GRANT USAGE ON SCHEMA qm_db.PUBLIC TO ROLE analyst");
        engine.execute("CREATE MASKING POLICY by_role AS (v VARCHAR) RETURNS VARCHAR"
            + " -> CASE WHEN CURRENT_ROLE() = 'SYSADMIN' THEN v ELSE '***' END");
        engine.execute("CREATE TABLE r (\"Who\" VARCHAR WITH MASKING POLICY by_role)");
        engine.execute("GRANT SELECT ON TABLE r TO ROLE analyst");
        engine.execute("INSERT INTO r VALUES ('raw')");
        assertEquals("[raw]", rows("SELECT \"Who\" FROM r"));
        engine.execute("USE ROLE analyst");
        assertEquals("[***]", rows("SELECT \"Who\" FROM r"));
        assertEquals("", rows("SELECT \"Who\" FROM r WHERE \"Who\" = 'raw'"));
    }

    @Test
    public void aPolicyBodyThatWritesItsParameterQuotedBindsTheValue() {
        engine.execute("CREATE MASKING POLICY quoted AS (v VARCHAR) RETURNS VARCHAR"
            + " -> CASE WHEN \"V\" = 'show' THEN \"V\" ELSE 'len:' || LENGTH(\"V\") END");
        engine.execute("CREATE TABLE q (k INT, c VARCHAR WITH MASKING POLICY quoted)");
        engine.execute("INSERT INTO q VALUES (1, 'show'), (2, 'secret')");
        assertEquals("[show]; [len:6]", rows("SELECT c FROM q ORDER BY k"));

        // A row access body's quoted parameter is the attached column, not a column of the same name.
        engine.execute("CREATE ROW ACCESS POLICY rap AS (r VARCHAR) RETURNS BOOLEAN -> \"R\" <> 'hide'");
        engine.execute("CREATE TABLE ra (id INT, region VARCHAR, r VARCHAR)");
        engine.execute("INSERT INTO ra VALUES (1, 'keep', 'hide'), (2, 'hide', 'keep')");
        engine.execute("ALTER TABLE ra ADD ROW ACCESS POLICY rap ON (region)");
        assertEquals("[1, keep]", rows("SELECT id, region FROM ra ORDER BY id"));
    }

    @Test
    public void aConditionalPolicyReadsTheColumnsItsUsingNames() {
        engine.execute("CREATE MASKING POLICY cond AS (v VARCHAR, flag VARCHAR) RETURNS VARCHAR"
            + " -> CASE WHEN flag = 'open' THEN v ELSE '***' END");
        // FLAG is a decoy: the policy's second argument is "Visibility", which USING names.
        engine.execute("CREATE TABLE c (\"S\" VARCHAR, \"Visibility\" VARCHAR, flag VARCHAR)");
        engine.execute("INSERT INTO c VALUES ('a', 'open', 'closed'), ('b', 'closed', 'open')");
        engine.execute("ALTER TABLE c MODIFY COLUMN \"S\" SET MASKING POLICY cond USING (\"S\", \"Visibility\")");
        assertEquals("[a, open]; [***, closed]", rows("SELECT \"S\", \"Visibility\" FROM c ORDER BY \"S\" = '***'"));

        engine.execute("ALTER TABLE c RENAME COLUMN \"Visibility\" TO shown");
        assertEquals("[a, open]; [***, closed]", rows("SELECT \"S\", shown FROM c ORDER BY \"S\" = '***'"));
        assertTrue(rows("SELECT GET_DDL('TABLE', 'c')").contains("USING (S, SHOWN)"));

        // Attached without USING, the further argument reads NULL, never a column that shares its name.
        engine.execute("CREATE TABLE d (s VARCHAR, flag VARCHAR)");
        engine.execute("INSERT INTO d VALUES ('a', 'open')");
        engine.execute("ALTER TABLE d MODIFY COLUMN s SET MASKING POLICY cond");
        assertEquals("[***]", rows("SELECT s FROM d"));
    }

    @Test
    public void aConditionalPolicysUsingColumnsSurviveAnEngineClone() throws Exception {
        engine.execute("CREATE MASKING POLICY cond AS (v VARCHAR, flag VARCHAR) RETURNS VARCHAR"
            + " -> CASE WHEN flag = 'open' THEN v ELSE '***' END");
        engine.execute("CREATE TABLE c (s VARCHAR WITH MASKING POLICY cond USING (s, \"Shown\"), \"Shown\" VARCHAR,"
            + " flag VARCHAR)");
        engine.execute("INSERT INTO c VALUES ('a', 'open', 'closed'), ('b', 'closed', 'open')");

        final DatabaseEngine copy = engine.cloneInstance();
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : copy.executeQuery("SELECT s FROM qm_db.PUBLIC.c ORDER BY \"Shown\" DESC").getRows()) {
                out.append(out.length() > 0 ? "; " : "").append(row.getValues());
            }
            assertEquals("[a]; [***]", out.toString());
        } finally {
            copy.shutdown();
        }
    }

    @Test
    public void aProjectionPolicyRefusesEveryQuotedSpellingOfItsColumnAndNoOther() {
        engine.execute("CREATE PROJECTION POLICY pp AS () RETURNS PROJECTION_CONSTRAINT"
            + " -> PROJECTION_CONSTRAINT(ALLOW => false)");
        engine.execute("CREATE TABLE pt (id INT, p VARCHAR, \"p\" VARCHAR, \"Mixed\" VARCHAR)");
        engine.execute("INSERT INTO pt VALUES (1, 'P', 'lower', 'mx')");
        engine.execute("ALTER TABLE pt ALTER COLUMN p SET PROJECTION POLICY pp");
        engine.execute("ALTER TABLE pt ALTER COLUMN \"Mixed\" SET PROJECTION POLICY pp");
        final String[] refused = {"p", "\"P\"", "pt.\"P\"", "UPPER(\"P\")", "\"Mixed\""};
        for (final String spelling : refused) {
            assertTrue(refusal("SELECT " + spelling + " FROM pt").contains("restricted by a Projection Policy"),
                spelling);
        }
        assertEquals("[lower]", rows("SELECT \"p\" FROM pt"));
        assertTrue(refusal("SELECT * FROM pt").endsWith("\n P\n MIXED"), refusal("SELECT * FROM pt"));
    }
}
