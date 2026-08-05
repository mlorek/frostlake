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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snowflake's structured-nullability DDL rule: a {@code NOT NULL} field inside a structured type is
 * legal only when everything enclosing it is itself non-nullable, and never inside an ARRAY or MAP.
 * Every expectation was measured on a real Snowflake account, one CREATE per cell —
 * including the recursion cells (nested objects, arrays inside objects, the MAP {@code .value} path
 * segment), not just the flat ones.
 */
public class StructuredNullabilityDdlTest extends BaseDatabaseTest {

    private void assertRejected(final String sql, final String expectedFragment) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(error.getMessage()).contains(expectedFragment),
            "expected \"" + expectedFragment + "\" in: " + error.getMessage());
    }

    /** The flat cell: a NOT NULL field in an object on a NULLABLE column is refused. */
    @Test
    public void notNullFieldOnANullableColumnIsRejected() {
        assertRejected("CREATE TABLE nn_flat (nn OBJECT(x VARCHAR NOT NULL))",
            "DDL operation failed because it would result in a non-nullable structured type field"
                + " 'NN.x' contained within a nullable object");
    }

    /** Making the COLUMN itself NOT NULL legalizes a direct NOT NULL field. */
    @Test
    public void notNullFieldOnANotNullColumnIsAccepted() {
        engine.execute("CREATE TABLE nn_ok (nn OBJECT(x VARCHAR NOT NULL) NOT NULL)");
    }

    /**
     * The rule is PER LEVEL, not per column: a NOT NULL field inside a nullable object-typed field
     * fails even when the column is NOT NULL, and marking every level NOT NULL is what fixes it.
     * The path prefixes the upper-cased column name and keeps field names verbatim.
     */
    @Test
    public void everyEnclosingLevelMustBeNonNullable() {
        assertRejected("CREATE TABLE nn_nested (o OBJECT(inner OBJECT(x INT NOT NULL)) NOT NULL)",
            "non-nullable structured type field 'O.inner.x' contained within a nullable object");
        engine.execute(
            "CREATE TABLE nn_nested_ok (o OBJECT(inner OBJECT(x INT NOT NULL) NOT NULL) NOT NULL)");
    }

    /** Under an ARRAY the refusal is unconditional — a NOT NULL column does not help. */
    @Test
    public void notNullFieldUnderAnArrayIsAlwaysRejected() {
        assertRejected("CREATE TABLE nn_arr (na ARRAY(OBJECT(x INT NOT NULL)))",
            "non-nullable structured type field 'NA.element.x' contained within an array or map");
        assertRejected("CREATE TABLE nn_arr2 (na ARRAY(OBJECT(x INT NOT NULL)) NOT NULL)",
            "non-nullable structured type field 'NA.element.x' contained within an array or map");
        // Reached through an object field, the path walks the field chain before .element.
        assertRejected("CREATE TABLE nn_arr3 (o OBJECT(a ARRAY(OBJECT(x INT NOT NULL))) NOT NULL)",
            "non-nullable structured type field 'O.a.element.x' contained within an array or map");
    }

    /** Under a MAP the same sentence uses the {@code .value} path segment. */
    @Test
    public void notNullFieldUnderAMapIsAlwaysRejected() {
        assertRejected("CREATE TABLE nn_map (mm MAP(VARCHAR, OBJECT(x INT NOT NULL)))",
            "non-nullable structured type field 'MM.value.x' contained within an array or map");
        assertRejected("CREATE TABLE nn_map2 (mm MAP(VARCHAR, OBJECT(x INT NOT NULL)) NOT NULL)",
            "non-nullable structured type field 'MM.value.x' contained within an array or map");
    }

    /** Structured types WITHOUT any NOT NULL field are untouched by the rule. */
    @Test
    public void plainStructuredColumnsStillCreate() {
        engine.execute("CREATE TABLE nn_plain (o OBJECT(x INT, y VARCHAR), na ARRAY(OBJECT(x INT)),"
            + " mm MAP(VARCHAR, ARRAY(INT)))");
    }
}
