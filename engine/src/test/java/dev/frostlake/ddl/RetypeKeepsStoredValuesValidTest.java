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

import dev.frostlake.DatabaseEngine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ALTER COLUMN ... SET DATA TYPE refuses a change the stored values cannot follow. The check compared a number's
 * scale alone and let binaries, vectors and structured types through. So FLOAT became NUMBER(38,9) and back,
 * BINARY(100) shrank to BINARY(4) over an 8-byte value, a VECTOR changed shape and an OBJECT's member its type over
 * values the new type refuses, leaving a table that holds values its own INSERT rejects.
 */
public class RetypeKeepsStoredValuesValidTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE rt_db");
        engine.execute("USE SCHEMA rt_db.PUBLIC");
        engine.execute("CREATE TABLE a (f FLOAT, n NUMBER(38,9), b BINARY(100), vec VECTOR(INT, 3),"
            + " o OBJECT(a INT))");
        engine.execute("INSERT INTO a SELECT 1.25, 1.25, X'0102030405060708', [1,2,3]::VECTOR(INT, 3),"
            + " {'a':1}::OBJECT(a INT)");
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
            return String.valueOf(refused.getMessage()).trim();
        }
    }

    @Test
    public void floatAndNumberDoNotTurnIntoEachOther() {
        assertEquals("SQL compilation error: cannot change column F from type FLOAT to NUMBER(38,9)",
            refusal("ALTER TABLE a ALTER COLUMN f SET DATA TYPE NUMBER(38,9)"));
        assertEquals("SQL compilation error: cannot change column N from type NUMBER(38,9) to FLOAT",
            refusal("ALTER TABLE a ALTER COLUMN n SET DATA TYPE FLOAT"));
        assertEquals("not refused", refusal("ALTER TABLE a ALTER COLUMN f SET DATA TYPE DOUBLE"));
    }

    @Test
    public void aBinaryMayWidenButNotNarrow() {
        assertEquals("SQL compilation error: cannot change column B from type BINARY(100) to BINARY(4)",
            refusal("ALTER TABLE a ALTER COLUMN b SET DATA TYPE BINARY(4)"));
        assertEquals("not refused", refusal("ALTER TABLE a ALTER COLUMN b SET DATA TYPE BINARY(200)"));
    }

    @Test
    public void aVectorOrAStructuredShapeDoesNotChange() {
        assertEquals("SQL compilation error: cannot change column VEC from type VECTOR(INT, 3) to VECTOR(FLOAT, 5)",
            refusal("ALTER TABLE a ALTER COLUMN vec SET DATA TYPE VECTOR(FLOAT, 5)"));
        assertEquals("SQL compilation error: cannot change column O.a from type NUMBER(38,0) to VARCHAR(16777216)",
            refusal("ALTER TABLE a ALTER COLUMN o SET DATA TYPE OBJECT(a VARCHAR)"));
        assertEquals("not refused", refusal("ALTER TABLE a ALTER COLUMN vec SET DATA TYPE VECTOR(INT, 3)"));
        assertEquals("not refused", refusal("ALTER TABLE a ALTER COLUMN o SET DATA TYPE OBJECT(a INT)"));
    }
}
