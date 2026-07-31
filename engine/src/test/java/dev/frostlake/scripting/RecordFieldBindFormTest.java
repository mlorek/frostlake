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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A cursor loop record cannot be bound into embedded SQL by name.
 *
 * <p>Live-verified on a real account, inside {@code FOR r IN c DO … END FOR}:
 * <pre>
 *   INSERT INTO t VALUES (:r.price)  -&gt; "syntax error line 4 at position 31 unexpected '.'"
 *   INSERT INTO t VALUES (r.price)   -&gt; "invalid identifier 'R.PRICE'"
 *   INSERT INTO t VALUES (r)         -&gt; "invalid identifier 'R'"
 *   INSERT INTO t VALUES (:r)        -&gt; "Bind variable :r not set."
 *   pv := r.price; INSERT INTO t VALUES (:pv)   -&gt; works, sums to 30
 *   n := r.price + 1                            -&gt; works (11): the field reads fine in an EXPRESSION
 * </pre>
 * Frostlake used to substitute only the {@code :r} part of {@code :r.price}, leaving {@code NULL.price}
 * in the SQL — so the documented-looking spelling silently inserted NULL instead of the field value.
 */
public class RecordFieldBindFormTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void dottedBindVariableIsRejectedRatherThanSilentlyNull() {
        engine.execute("CREATE TABLE bind_out (v INTEGER)");
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("""
                    DECLARE
                      c CURSOR FOR SELECT 10 AS price UNION ALL SELECT 20;
                    BEGIN
                      FOR r IN c DO
                        INSERT INTO bind_out VALUES (:r.price);
                      END FOR;
                      RETURN 1;
                    END""");
            }
        });
        assertTrue(rootMessage(error).contains("unexpected '.'"),
            "expected the live syntax-error shape, got: " + rootMessage(error));
        // The decisive part: nothing was written. Previously this inserted two NULL rows.
        assertEquals(0L, scalar("SELECT COUNT(*) FROM bind_out"));
    }

    @Test
    public void copyingTheFieldIntoAVariableAndBindingThatWorks() {
        // The idiom Snowflake supports — live it sums to 30.
        engine.execute("CREATE TABLE bind_ok (v INTEGER)");
        engine.execute("""
            DECLARE
              c CURSOR FOR SELECT 10 AS price UNION ALL SELECT 20;
              pv INTEGER;
            BEGIN
              FOR r IN c DO
                pv := r.price;
                INSERT INTO bind_ok VALUES (:pv);
              END FOR;
              RETURN 1;
            END""");
        assertEquals(30L, scalar("SELECT SUM(v) FROM bind_ok"));
    }

    @Test
    public void theFieldStillReadsInAProceduralExpression() {
        // Live: n := r.price + 1 inside the loop returns 11 — expressions are not embedded SQL.
        assertEquals("11", String.valueOf(scalar("""
            DECLARE
              c CURSOR FOR SELECT 10 AS price;
              n INTEGER DEFAULT 0;
            BEGIN
              FOR r IN c DO
                n := r.price + 1;
              END FOR;
              RETURN n;
            END""")));
    }

    @Test
    public void aVariantPathColonIsNotABindVariable() {
        // Regression guard: `src:a.b` inside a loop body is a semi-structured path, not `:a` + `.b`.
        engine.execute("CREATE TABLE bind_variant (src VARIANT)");
        engine.execute("INSERT INTO bind_variant SELECT PARSE_JSON('{\"a\":{\"b\":7}}')");
        engine.execute("CREATE TABLE bind_variant_out (v INTEGER)");
        engine.execute("""
            DECLARE
              c CURSOR FOR SELECT 1 AS n;
            BEGIN
              FOR r IN c DO
                INSERT INTO bind_variant_out SELECT src:a.b FROM bind_variant;
              END FOR;
              RETURN 1;
            END""");
        assertEquals(7L, scalar("SELECT SUM(v) FROM bind_variant_out"));
    }

    private String rootMessage(final Throwable error) {
        Throwable current = error;
        final StringBuilder all = new StringBuilder();
        while (current != null) {
            all.append(current.getMessage()).append(" | ");
            current = current.getCause();
        }
        return all.toString();
    }
}
