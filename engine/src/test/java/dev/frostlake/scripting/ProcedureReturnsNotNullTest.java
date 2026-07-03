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

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CREATE FUNCTION / PROCEDURE accepts a {@code RETURNS <type> NOT NULL} return clause (the NOT NULL is
 * informational). Previously the NOT NULL suffix failed to parse.
 */
public class ProcedureReturnsNotNullTest extends BaseDatabaseTest {

    private String scalar(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void createProcedureReturnsNotNull() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p() RETURNS VARCHAR NOT NULL LANGUAGE SQL AS $$
            BEGIN
              RETURN 'hi';
            END;
            $$""");
        assertEquals("hi", scalar("CALL p()"));
    }

    @Test
    public void createFunctionReturnsNotNull() {
        engine.execute("CREATE OR REPLACE FUNCTION f() RETURNS NUMBER NOT NULL AS $$ 42 $$");
        assertEquals("42", scalar("SELECT f()"));
    }

    @Test
    public void returnsWithoutNotNullStillWorks() {
        engine.execute("CREATE OR REPLACE FUNCTION g() RETURNS NUMBER AS $$ 7 $$");
        assertEquals("7", scalar("SELECT g()"));
    }
}
