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

package dev.frostlake.ddl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

/**
 * The name a SHOW scope kind reads after its word or words — quoted, qualified or an IDENTIFIER() reference — and the
 * two-word kinds APPLICATION PACKAGE, COMPUTE POOL, FAILOVER GROUP and REPLICATION GROUP, each listing refused with
 * live's own answer: the full-search-path sentence names the name's last part as it resolves, an application scope
 * named by a path does not exist, a service and a routine listing's class resolve as any object's name, and a
 * compute pool spells the listing's kind as words. Every cell is live-verified.
 */
public class ShowScopeKindNameTest extends BaseDatabaseTest {

    private static final String CANNOT = "SQL compilation error:|Unsupported statement type 'Cannot show objects of type ";
    private static final String NO_OBJECT = "SQL compilation error:|Object does not exist, or operation cannot be performed.";

    /** Every row's first cell, a bar between rows, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                out.append(out.length() > 0 ? " | " : "").append(row.getValue(0));
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String fullPath(final String name) {
        return "SQL compilation error:|Must specify the full search path starting from database for " + name;
    }

    private static String syntaxError(final int position, final String token) {
        return "SQL compilation error:|syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void aKindReadsAQuotedQualifiedOrReferencedName() {
        assertEquals(CANNOT + "TABLE in VIEW'.", answer("SHOW TABLES IN VIEW a.b"));
        assertEquals(CANNOT + "TABLE in VIEW'.", answer("SHOW TABLES IN VIEW IDENTIFIER('v')"));
        assertEquals(CANNOT + "TABLE in VIEW'.", answer("SHOW TABLES IN VIEW \"a\".b"));
        assertEquals(CANNOT + "TABLE in VIEW'.", answer("SHOW TABLES IN VIEW a.b LIMIT 1"));
        assertEquals(CANNOT + "TABLE in PIPE'.", answer("SHOW TABLES IN PIPE \"p\""));
        assertEquals(CANNOT + "SCHEMA in PIPE'.", answer("SHOW SCHEMAS IN PIPE IDENTIFIER('p')"));
        assertEquals(CANNOT + "TABLE in SERVICE'.", answer("SHOW TABLES IN SERVICE a.b.c.d"));
        assertEquals(CANNOT + "VIEW in CLASS'.", answer("SHOW VIEWS IN CLASS a.b"));
        assertEquals(NO_OBJECT, answer("SHOW TABLES IN CLASS \"y\""));
        assertEquals(syntaxError(25, "."), answer("SHOW TABLES IN SERVICE a..b"));
    }

    @Test
    public void theFullSearchPathSentenceNamesTheLastPart() {
        assertEquals(fullPath("B"), answer("SHOW COLUMNS IN VIEW a.b"));
        assertEquals(fullPath("p"), answer("SHOW COLUMNS IN VIEW \"p\""));
        assertEquals(fullPath("D"), answer("SHOW COLUMNS IN VIEW a.b.c.d"));
        assertEquals(fullPath("V"), answer("SHOW PRIMARY KEYS IN VIEW IDENTIFIER('v')"));
        assertEquals(fullPath("B"), answer("SHOW TABLES IN ORGANIZATION a.b"));
        assertEquals(fullPath("y"), answer("SHOW TABLES IN ORGANIZATION \"y\""));
        assertEquals(fullPath("Y"), answer("SHOW TABLES IN ORGANIZATION IDENTIFIER('y')"));
        assertEquals(fullPath("C"), answer("SHOW TABLES IN CONNECTION a.b.c"));
    }

    @Test
    public void anApplicationServiceOrClassNameResolves() {
        assertEquals(hinted("SQL compilation error:|Application '\"y\"' does not exist or not authorized."),
            answer("SHOW COLUMNS IN APPLICATION \"y\""));
        assertEquals(hinted("SQL compilation error:|Application 'Y' does not exist or not authorized."),
            answer("SHOW COLUMNS IN APPLICATION IDENTIFIER('y')"));
        assertEquals(NO_OBJECT, answer("SHOW COLUMNS IN APPLICATION a.b"));
        assertEquals(NO_OBJECT, answer("SHOW TABLES IN APPLICATION a.b"));
        assertEquals(NO_OBJECT, answer("SHOW FUNCTIONS IN APPLICATION a.b"));
        assertEquals(hinted("SQL compilation error:|Service 'TEST_DB.TEST_SCHEMA.\"y\"' does not exist or not authorized."),
            answer("SHOW FUNCTIONS IN SERVICE \"y\""));
        assertEquals(hinted("SQL compilation error:|Service 'TEST_DB.TEST_SCHEMA.Y' does not exist or not authorized."),
            answer("SHOW FUNCTIONS IN SERVICE IDENTIFIER('y')"));
        assertEquals(hinted("SQL compilation error:|Schema 'TEST_DB.A' does not exist or not authorized."),
            answer("SHOW FUNCTIONS IN SERVICE a.b"));
        assertEquals(hinted("SQL compilation error:|Database 'A' does not exist or not authorized."),
            answer("SHOW FUNCTIONS IN SERVICE a.b.c"));
        assertEquals(NO_OBJECT, answer("SHOW FUNCTIONS IN SERVICE a.b.c.d"));
        assertEquals("SQL compilation error: Object type or Class '\"y\"' does not exist or not authorized.",
            answer("SHOW FUNCTIONS IN CLASS \"y\""));
        assertEquals(hinted("SQL compilation error:|Schema 'TEST_DB.A' does not exist or not authorized."),
            answer("SHOW FUNCTIONS IN CLASS a.b"));
        assertEquals(hinted("SQL compilation error:|Database 'A' does not exist or not authorized."),
            answer("SHOW PROCEDURES IN CLASS a.b.c"));
    }

    @Test
    public void anApplicationPackageNeedsItsName() {
        assertEquals(NO_OBJECT, answer("SHOW TABLES IN APPLICATION PACKAGE y"));
        assertEquals(NO_OBJECT, answer("SHOW TABLES IN APPLICATION PACKAGE IDENTIFIER('y')"));
        assertEquals(NO_OBJECT, answer("SHOW TABLES IN APPLICATION PACKAGE y LIMIT 1"));
        assertEquals(hinted("SQL compilation error:|Application package 'Y' does not exist or not authorized."),
            answer("SHOW COLUMNS IN APPLICATION PACKAGE y"));
        assertEquals(hinted("SQL compilation error:|Application package '\"y\"' does not exist or not authorized."),
            answer("SHOW FUNCTIONS IN APPLICATION PACKAGE \"y\""));
        assertEquals(hinted("SQL compilation error:|Application package 'Y' does not exist or not authorized."),
            answer("SHOW COLUMNS IN APPLICATION PACKAGE y LIMIT 1"));
        assertEquals(NO_OBJECT, answer("SHOW PROCEDURES IN APPLICATION PACKAGE a.b"));
        assertEquals(NO_OBJECT, answer("SHOW FUNCTIONS IN APPLICATION PACKAGE a.b"));
        assertEquals(CANNOT + "DATABASE in APPLICATION PACKAGE'.", answer("SHOW DATABASES IN APPLICATION PACKAGE y"));
        assertEquals(syntaxError(34, "<EOF>"), answer("SHOW TABLES IN APPLICATION PACKAGE"));
        assertEquals(syntaxError(37, "<EOF>"), answer("SHOW FUNCTIONS IN APPLICATION PACKAGE"));
        assertEquals(syntaxError(38, "<EOF>"), answer("SHOW PROCEDURES IN APPLICATION PACKAGE"));
        assertEquals(syntaxError(37, "z"), answer("SHOW TABLES IN APPLICATION PACKAGE y z"));
    }

    @Test
    public void aComputePoolSpellsTheListingsKindAsWords() {
        assertEquals(CANNOT + "Table in Compute pool'.", answer("SHOW TABLES IN COMPUTE POOL y"));
        assertEquals(CANNOT + "Table in Compute pool'.", answer("SHOW TABLES IN Compute Pool y"));
        assertEquals(CANNOT + "Table in Compute pool'.", answer("SHOW TERSE TABLES IN COMPUTE POOL a.b"));
        assertEquals(CANNOT + "Table in Compute pool'.", answer("SHOW TABLES IN COMPUTE POOL IDENTIFIER('y')"));
        assertEquals(CANNOT + "Table in Compute pool'.", answer("SHOW TABLES IN COMPUTE POOL y LIMIT 1"));
        assertEquals(CANNOT + "Materialized view in Compute pool'.",
            answer("SHOW MATERIALIZED VIEWS IN COMPUTE POOL y"));
        assertEquals(CANNOT + "Constraint in Compute pool'.", answer("SHOW PRIMARY KEYS IN COMPUTE POOL \"y\""));
        assertEquals(CANNOT + "Dynamic table in Compute pool'.", answer("SHOW DYNAMIC TABLES IN COMPUTE POOL y"));
        assertEquals(CANNOT + "User in Compute pool'.", answer("SHOW USERS IN COMPUTE POOL y"));
        assertEquals(syntaxError(27, "<EOF>"), answer("SHOW TABLES IN COMPUTE POOL"));
        assertEquals(syntaxError(30, "z"), answer("SHOW TABLES IN COMPUTE POOL y z"));
    }

    @Test
    public void aFailoverOrReplicationGroupNeedsNoName() {
        assertEquals(CANNOT + "TABLE in FAILOVER GROUP'.", answer("SHOW TABLES IN FAILOVER GROUP"));
        assertEquals(CANNOT + "TABLE in FAILOVER GROUP'.", answer("SHOW TABLES IN failover group"));
        assertEquals(CANNOT + "TABLE in FAILOVER GROUP'.", answer("SHOW TABLES IN FAILOVER GROUP y"));
        assertEquals(CANNOT + "TABLE in FAILOVER GROUP'.", answer("SHOW TABLES IN FAILOVER GROUP a.b"));
        assertEquals(CANNOT + "TABLE in FAILOVER GROUP'.", answer("SHOW TABLES IN FAILOVER GROUP IDENTIFIER('y')"));
        assertEquals(CANNOT + "TABLE in FAILOVER GROUP'.", answer("SHOW TABLES IN FAILOVER GROUP LIMIT 1"));
        assertEquals(CANNOT + "TABLE in FAILOVER GROUP'.", answer("SHOW TABLES LIKE 'x' IN FAILOVER GROUP"));
        assertEquals(CANNOT + "USER in FAILOVER GROUP'.", answer("SHOW USERS IN FAILOVER GROUP"));
        assertEquals(CANNOT + "WAREHOUSE in FAILOVER GROUP'.", answer("SHOW WAREHOUSES IN FAILOVER GROUP y"));
        assertEquals(CANNOT + "STAGE in REPLICATION GROUP'.", answer("SHOW STAGES IN REPLICATION GROUP y"));
        assertEquals(CANNOT + "FUNCTION in REPLICATION GROUP'.", answer("SHOW FUNCTIONS IN REPLICATION GROUP"));
        assertEquals(syntaxError(32, "z"), answer("SHOW TABLES IN FAILOVER GROUP y z"));
    }

    @Test
    public void onlyTheFunctionListingReadsAModel() {
        assertEquals(NO_OBJECT, answer("SHOW FUNCTIONS IN MODEL y"));
        assertEquals(NO_OBJECT, answer("SHOW FUNCTIONS IN MODEL \"y\""));
        assertEquals(NO_OBJECT, answer("SHOW FUNCTIONS IN MODEL a.b"));
        assertEquals(syntaxError(21, "y"), answer("SHOW TABLES IN MODEL y"));
        assertEquals(syntaxError(25, "y"), answer("SHOW PROCEDURES IN MODEL y"));
    }
}
