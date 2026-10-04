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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * POLICY_REFERENCES reads REF_ENTITY_NAME and POLICY_NAME as identifiers: unquoted parts fold to upper case, quoted
 * ones are kept, and a name with fewer parts is completed from the session. Only a bare name in the session's own
 * schema used to resolve; {@code 'mydb.myschema.mytable'}, the documented form, was refused as a missing table in
 * either case (live answers it normally, and refuses a missing table as
 * {@code Table 'DB.PUBLIC.NO_SUCH_TABLE' does not exist or not authorized.}, with no privilege sentence).
 */
public class PolicyReferencesNamesTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE prn");
        engine.execute("USE SCHEMA prn.PUBLIC");
        engine.execute("CREATE MASKING POLICY mp AS (v VARCHAR) RETURNS VARCHAR -> '***'");
        engine.execute("CREATE TABLE t (a INT, s VARCHAR WITH MASKING POLICY mp, s2 VARCHAR WITH MASKING POLICY mp)");
        engine.execute("CREATE SCHEMA sec");
        engine.execute("CREATE MASKING POLICY sec.mp AS (v VARCHAR) RETURNS VARCHAR -> 'x'");
        engine.execute("CREATE TABLE sec.t2 (s VARCHAR WITH MASKING POLICY sec.mp)");
        engine.execute("USE SCHEMA prn.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** Every row's cells, joined with '|'. */
    private List<String> rows(final String sql) {
        final List<String> out = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            final StringBuilder line = new StringBuilder();
            for (final Object value : row.getValues()) {
                line.append(line.length() == 0 ? "" : "|").append(value);
            }
            out.add(line.toString());
        }
        return out;
    }

    private List<String> byEntity(final String name, final String domain) {
        return rows("SELECT POLICY_DB, POLICY_SCHEMA, POLICY_NAME, REF_ENTITY_NAME, REF_COLUMN_NAME"
            + " FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => '" + name
            + "', REF_ENTITY_DOMAIN => '" + domain + "'))");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql).getMessage();
    }

    private String entityRefusal(final String name, final String domain) {
        return refusal("SELECT * FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => '" + name
            + "', REF_ENTITY_DOMAIN => '" + domain + "'))");
    }

    @Test
    public void aQualifiedEntityNameResolvesInEitherCase() {
        final List<String> expected = List.of("PRN|PUBLIC|MP|T|S", "PRN|PUBLIC|MP|T|S2");
        assertEquals(expected, byEntity("t", "TABLE"), "a policy on two columns is listed for each");
        assertEquals(expected, byEntity("PRN.PUBLIC.T", "TABLE"));
        assertEquals(expected, byEntity("prn.public.t", "table"));
        assertEquals(expected, byEntity("public.t", "TABLE"));
        engine.execute("USE SCHEMA prn.sec");
        assertEquals(expected, byEntity("prn.public.t", "TABLE"), "from another schema");
        assertEquals(List.of("PRN|SEC|MP|T2|S"), byEntity("t2", "TABLE"));
    }

    @Test
    public void aQuotedPartIsKeptAsWritten() {
        engine.execute("CREATE TABLE \"Mixed\" (s VARCHAR WITH MASKING POLICY mp)");
        assertEquals(List.of("PRN|PUBLIC|MP|Mixed|S"), byEntity("prn.public.\"Mixed\"", "TABLE"));
        assertEquals("SQL compilation error:\nTable 'MIXED' does not exist or not authorized.",
            entityRefusal("mixed", "TABLE"));
    }

    @Test
    public void aViewResolvesTheSameWay() {
        engine.execute("CREATE ROW ACCESS POLICY rap AS (a INT) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE VIEW v AS SELECT a FROM t");
        engine.execute("ALTER VIEW v ADD ROW ACCESS POLICY rap ON (a)");
        engine.execute("USE SCHEMA prn.sec");
        assertEquals(List.of("PRN|PUBLIC|RAP|V|null"), byEntity("prn.public.v", "view"));
        assertEquals("SQL compilation error:\nView 'PRN.PUBLIC.NOV' does not exist or not authorized.",
            entityRefusal("prn.public.nov", "VIEW"));
    }

    @Test
    public void aMissingEntityIsRefusedAsLiveRefusesIt() {
        assertEquals("SQL compilation error:\nTable 'PRN.PUBLIC.NO_SUCH_TABLE' does not exist or not authorized.",
            entityRefusal("prn.public.no_such_table", "TABLE"), "named in full, with no privilege sentence");
        assertEquals("SQL compilation error:\nTable 'PRN.PUBLIC.NO_SUCH_TABLE' does not exist or not authorized.",
            entityRefusal("public.no_such_table", "TABLE"), "a qualifier expands the name in full");
        assertEquals("SQL compilation error:\nTable 'NO_SUCH_TABLE' does not exist or not authorized.",
            entityRefusal("no_such_table", "TABLE"), "a bare name is reported as written");
        assertEquals("SQL compilation error:\nTable 'NODB.PUBLIC.T' does not exist or not authorized.",
            entityRefusal("nodb.public.t", "TABLE"), "a missing database misses the table");
    }

    @Test
    public void aPolicyNameIsResolvedTooAndListsOnlyThatPolicy() {
        final String sql = "SELECT POLICY_DB, POLICY_SCHEMA, POLICY_NAME, REF_ENTITY_NAME, REF_COLUMN_NAME"
            + " FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(POLICY_NAME => '%s'))";
        final List<String> publicMp = List.of("PRN|PUBLIC|MP|T|S", "PRN|PUBLIC|MP|T|S2");
        assertEquals(publicMp, rows(String.format(sql, "mp")), "not the namesake in SEC");
        assertEquals(publicMp, rows(String.format(sql, "prn.public.mp")));
        assertEquals(List.of("PRN|SEC|MP|T2|S"), rows(String.format(sql, "sec.mp")),
            "POLICY_SCHEMA is where the policy lives");
        assertEquals(List.of("PRN|SEC|MP|T2|S"), rows(String.format(sql, "PRN.SEC.MP")));
        assertEquals("SQL compilation error:\nPolicy 'PRN.SEC.NOSUCH' does not exist or not authorized.",
            refusal(String.format(sql, "sec.nosuch")), "a missing policy is named in full, with no privilege sentence");
    }

    @Test
    public void eitherDomainTakesATableOrAViewAndListsItsOwnKindOnly() {
        engine.execute("CREATE ROW ACCESS POLICY rap AS (a INT) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE VIEW v AS SELECT a FROM t");
        engine.execute("ALTER VIEW v ADD ROW ACCESS POLICY rap ON (a)");
        assertEquals(List.of(), byEntity("v", "table"), "a view under 'table' answers no rows");
        assertEquals(List.of(), byEntity("t", "view"), "a table under 'view' answers no rows");
        assertEquals(List.of("PRN|PUBLIC|RAP|V|null"), byEntity("v", "view"));
        assertEquals("SQL compilation error:\nView 'PRN.PUBLIC.NO_SUCH_TABLE' does not exist or not authorized.",
            entityRefusal("prn.public.no_such_table", "view"), "refused in the domain's word");
        assertEquals("SQL compilation error:\nInvalid value 'STAGE' for argument 'ref_entity_domain' for function"
            + " 'information_schema.policy_references'", entityRefusal("t", "stage"));
    }
}
