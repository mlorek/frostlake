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

package dev.frostlake.constraints;

import dev.frostlake.DatabaseEngine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A CHECK refusal names the table it guards by the table's own database and schema. It qualified the bare name with
 * the session's, so a row written into P.PUBLIC.NO_RAP from D2.PUBLIC was refused naming D2.PUBLIC.NO_RAP.
 */
public class CheckRefusalNamesItsTableTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE p");
        engine.execute("CREATE TABLE p.PUBLIC.no_rap (a INT CHECK (a > 0))");
        engine.execute("CREATE DATABASE d2");
        engine.execute("USE SCHEMA d2.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
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
    public void anInsertFromAnotherDatabaseNamesTheTablesOwnSchema() {
        final String refused = refusal("INSERT INTO p.PUBLIC.no_rap VALUES (-1)");
        assertTrue(refused.startsWith("Operation on table P.PUBLIC.NO_RAP failed because CHECK constraint "), refused);
    }

    @Test
    public void anUpdateFromAnotherDatabaseNamesTheTablesOwnSchema() {
        engine.execute("INSERT INTO p.PUBLIC.no_rap VALUES (3)");
        final String refused = refusal("UPDATE p.PUBLIC.no_rap SET a = -5");
        assertTrue(refused.startsWith("Operation on table P.PUBLIC.NO_RAP failed because CHECK constraint "), refused);
    }

    @Test
    public void aBareNameInTheTablesOwnSchemaStillNamesItInFull() {
        engine.execute("USE SCHEMA p.PUBLIC");
        final String refused = refusal("INSERT INTO no_rap VALUES (-2)");
        assertTrue(refused.startsWith("Operation on table P.PUBLIC.NO_RAP failed because CHECK constraint "), refused);
    }
}
