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
import org.junit.jupiter.api.Test;

/**
 * A SHOW scope written {@code IN VIEW | PIPE | SERVICE | APPLICATION | CLASS | ORGANIZATION | CONNECTION <name>}
 * reads the word as a scope kind, and each listing refuses it with its own sentence — the ones the scope's shape
 * decides before a LIMIT 0 or a WITH PRIVILEGES, the lookups after them (live-verified).
 */
public class ShowScopeKindTest extends BaseDatabaseTest {

    private String answer(final String sql) {
        try {
            return "rows " + engine.executeQuery(sql).getRowCount();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String cannotShow(final String kind, final String scope) {
        return "SQL compilation error:|Unsupported statement type 'Cannot show objects of type " + kind + " in "
            + scope + "'.";
    }

    private static String fullPath(final String name) {
        return "SQL compilation error:|Must specify the full search path starting from database for " + name;
    }

    private static String noObject() {
        return "SQL compilation error:|Object does not exist, or operation cannot be performed.";
    }

    @Test
    public void theKindsSeenFromTables() {
        assertEquals(cannotShow("TABLE", "VIEW"), answer("SHOW TABLES IN VIEW y"));
        assertEquals(cannotShow("TABLE", "VIEW"), answer("SHOW TABLES IN view y"));
        assertEquals(cannotShow("TABLE", "VIEW"), answer("SHOW TABLES IN VIEW"));
        assertEquals(cannotShow("TABLE", "VIEW"), answer("SHOW TABLES LIKE 'x' IN VIEW"));
        assertEquals(cannotShow("TABLE", "PIPE"), answer("SHOW TABLES IN PIPE y"));
        assertEquals(cannotShow("TABLE", "PIPE"), answer("SHOW TABLES IN PIPE"));
        assertEquals(cannotShow("TABLE", "SERVICE"), answer("SHOW TABLES IN SERVICE y"));
        assertEquals(noObject(), answer("SHOW TABLES IN APPLICATION y"));
        assertEquals(noObject(), answer("SHOW TABLES IN CLASS y"));
        assertEquals(fullPath("Y"), answer("SHOW TABLES IN ORGANIZATION y"));
        assertEquals(fullPath("Y"), answer("SHOW TABLES IN CONNECTION y"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 20 unexpected 'y'.",
            answer("SHOW TABLES IN USER y"));
        assertEquals("syntax error line 1 at position 15 unexpected '\"VIEW\"'.", answer("SHOW TABLES IN \"VIEW\" y"));
    }

    @Test
    public void eachListingAnswersTheKindsItsOwnWay() {
        assertEquals(cannotShow("VIEW", "CLASS"), answer("SHOW VIEWS IN CLASS y"));
        assertEquals(cannotShow("SCHEMA", "VIEW"), answer("SHOW SCHEMAS IN VIEW"));
        assertEquals(noObject(), answer("SHOW SCHEMAS IN SERVICE y"));
        assertEquals(cannotShow("COLUMN", "PIPE"), answer("SHOW COLUMNS IN PIPE"));
        assertEquals(hinted("SQL compilation error:|Application 'Y' does not exist or not authorized."),
            answer("SHOW COLUMNS IN APPLICATION y"));
        assertEquals(hinted("SQL compilation error:|Application 'Y' does not exist or not authorized."),
            answer("SHOW MASKING POLICIES IN APPLICATION y"));
        assertEquals(noObject(), answer("SHOW FILE FORMATS IN APPLICATION y"));
        assertEquals(cannotShow("MATERIALIZED VIEW", "CLASS"), answer("SHOW MATERIALIZED VIEWS IN CLASS y"));
        assertEquals(cannotShow("KEY VALUE TABLE", "PIPE"), answer("SHOW HYBRID TABLES IN PIPE"));
        assertEquals(cannotShow("PROCEDURE", "SERVICE"), answer("SHOW USER PROCEDURES IN SERVICE y"));
        assertEquals(hinted("SQL compilation error:|Service 'TEST_DB.TEST_SCHEMA.Y' does not exist or not authorized."),
            answer("SHOW FUNCTIONS IN SERVICE y"));
        assertEquals(hinted("SQL compilation error:|Application 'Y' does not exist or not authorized."),
            answer("SHOW BUILTIN FUNCTIONS IN APPLICATION y"));
        assertEquals("SQL compilation error: Object type or Class 'Y' does not exist or not authorized.",
            answer("SHOW BUILTIN PROCEDURES IN CLASS y"));
        assertEquals(cannotShow("FUNCTION", "VIEW"), answer("SHOW BUILTIN FUNCTIONS IN VIEW"));
        assertEquals(fullPath("Y"), answer("SHOW BUILTIN FUNCTIONS IN ORGANIZATION y"));
    }

    @Test
    public void theAccountLevelListingsAndTheKinds() {
        assertEquals(cannotShow("DATABASE", "VIEW"), answer("SHOW DATABASES IN VIEW"));
        assertEquals(cannotShow("WAREHOUSE", "PIPE"), answer("SHOW WAREHOUSES IN PIPE y"));
        assertEquals(cannotShow("USER", "APPLICATION"), answer("SHOW USERS IN APPLICATION y"));
        assertEquals(cannotShow("COMPUTE POOL", "CLASS"), answer("SHOW COMPUTE POOLS IN CLASS y"));
        assertEquals(fullPath("Y"), answer("SHOW DATABASES IN CONNECTION y"));
        assertEquals(hinted("SQL compilation error:|Service 'TEST_DB.TEST_SCHEMA.Y' does not exist or not authorized."),
            answer("SHOW ROLES IN SERVICE y"));
        assertEquals("SQL compilation error: Object type or Class 'Y' does not exist or not authorized.",
            answer("SHOW ROLES IN CLASS y"));
    }

    @Test
    public void primaryKeysReadAViewScopeAsATable() {
        engine.execute("CREATE OR REPLACE TABLE kind_keys (id INT PRIMARY KEY)");
        assertEquals(answer("SHOW PRIMARY KEYS IN TABLE"), answer("SHOW PRIMARY KEYS IN VIEW"));
        assertEquals(fullPath("KIND_KEYS"), answer("SHOW PRIMARY KEYS IN VIEW kind_keys"));
        assertEquals(cannotShow("CONSTRAINT", "PIPE"), answer("SHOW PRIMARY KEYS IN PIPE p"));
        assertEquals(hinted("SQL compilation error:|Application 'Y' does not exist or not authorized."),
            answer("SHOW UNIQUE KEYS IN APPLICATION y"));
    }

    @Test
    public void theShapeComesBeforeTheLimitAndTheLookupAfterIt() {
        final String zero = "page size \"0\" must be greater than 0 in limit clause";
        assertEquals(cannotShow("TABLE", "VIEW"), answer("SHOW TABLES IN VIEW LIMIT 0"));
        assertEquals(cannotShow("TABLE", "VIEW"), answer("SHOW TABLES IN VIEW WITH PRIVILEGES SELECT"));
        assertEquals(cannotShow("TABLE", "SERVICE"), answer("SHOW TABLES IN SERVICE y WITH PRIVILEGES SELECT"));
        assertEquals(fullPath("Y"), answer("SHOW TABLES IN ORGANIZATION y LIMIT 0"));
        assertEquals(fullPath("Y"), answer("SHOW TABLES IN CONNECTION y WITH PRIVILEGES SELECT"));
        assertEquals(zero, answer("SHOW TABLES IN APPLICATION y LIMIT 0"));
        assertEquals(zero, answer("SHOW TABLES IN CLASS y LIMIT 0"));
        assertEquals(zero, answer("SHOW FUNCTIONS IN SERVICE y LIMIT 0"));
        assertEquals(zero, answer("SHOW COLUMNS IN APPLICATION y LIMIT 0"));
        assertEquals("Unsupported feature 'SHOW TABLES ... WITH PRIVILEGES <>'.",
            answer("SHOW TABLES IN APPLICATION y WITH PRIVILEGES SELECT"));
        assertEquals(noObject(), answer("SHOW TABLES IN APPLICATION y LIMIT 1"));
    }
}
