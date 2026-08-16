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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Snowflake's "Missing column specification" rule for CTAS and CREATE VIEW, live-verified: with no
 * explicit column list, an unaliased select item that is not a column reference cannot name its
 * output column, and the statement is refused BEFORE the source query runs. A (possibly
 * parenthesized, possibly qualified) column reference always qualifies — even one whose name would
 * need quoting ({@code "A B"}) — as do an aliased expression and a star item. The refusal is the
 * bare sentence, with no error position.
 */
public class CtasMissingColumnSpecificationTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE sales (region VARCHAR, product VARCHAR, amount NUMBER)");
        engine.execute("INSERT INTO sales VALUES ('east','a',10),('west','b',20)");
        engine.execute("CREATE TABLE bw (\"A B\" INT)");
    }

    private void expectMissingColumnSpecification(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertEquals("SQL compilation error:\nMissing column specification", e.getMessage());
    }

    // ── the refusals ─────────────────────────────────────────────────────────

    @Test
    public void aCtasOverAnUnaliasedLiteralIsRefused() {
        expectMissingColumnSpecification("CREATE OR REPLACE TABLE t1 AS SELECT 1");
    }

    @Test
    public void aCtasOverAnUnaliasedFunctionCallIsRefused() {
        expectMissingColumnSpecification("CREATE OR REPLACE TABLE t6 AS SELECT count(*) FROM sales");
    }

    @Test
    public void aViewOverAnUnaliasedExpressionIsRefused() {
        expectMissingColumnSpecification("CREATE OR REPLACE VIEW vw1 AS SELECT amount + 1 FROM sales");
        expectMissingColumnSpecification("CREATE OR REPLACE VIEW vw2 AS SELECT 1");
    }

    // ── the shapes that name themselves ──────────────────────────────────────

    @Test
    public void aParenthesizedColumnReferenceIsAccepted() {
        engine.execute("CREATE OR REPLACE TABLE t2 AS SELECT (amount) FROM sales");
        assertEquals("AMOUNT",
            engine.executeQuery("SELECT * FROM t2").getColumns().get(0).getName());
    }

    @Test
    public void aQualifiedColumnReferenceIsAccepted() {
        engine.execute("CREATE OR REPLACE TABLE t3 AS SELECT s.amount FROM sales s");
        assertEquals(2L, engine.executeQuery("SELECT COUNT(*) FROM t3").getRows().get(0).getValue(0));
    }

    @Test
    public void aColumnReferenceWithAnUnquotableNameIsAccepted() {
        engine.execute("CREATE OR REPLACE TABLE t4 AS SELECT \"A B\" FROM bw");
        assertEquals("A B",
            engine.executeQuery("SELECT * FROM t4").getColumns().get(0).getName());
    }

    @Test
    public void anAliasedExpressionIsAccepted() {
        engine.execute("CREATE OR REPLACE TABLE t5 AS SELECT amount + 1 AS x FROM sales");
        assertEquals("X",
            engine.executeQuery("SELECT * FROM t5").getColumns().get(0).getName());
    }

    @Test
    public void aStarItemIsAccepted() {
        engine.execute("CREATE OR REPLACE TABLE t7 AS SELECT * FROM sales");
        assertEquals(2L, engine.executeQuery("SELECT COUNT(*) FROM t7").getRows().get(0).getValue(0));
    }

    @Test
    public void anExplicitColumnListSuppliesTheMissingNames() {
        engine.execute("CREATE OR REPLACE TABLE t8 (total) AS SELECT amount + 1 FROM sales");
        assertEquals("TOTAL",
            engine.executeQuery("SELECT * FROM t8").getColumns().get(0).getName());
    }

    // ── the set-operation exemption ──────────────────────────────────────────

    /** A set operation is exempt: the union output names its columns (live-verified). */
    @Test
    public void aCtasOverAUnionOfUnaliasedLiteralsIsAccepted() {
        engine.execute("CREATE OR REPLACE TABLE tu1 AS SELECT 1 FROM sales WHERE FALSE"
            + " UNION ALL SELECT 1 FROM sales WHERE FALSE");
        assertEquals("1",
            engine.executeQuery("SELECT * FROM tu1").getColumns().get(0).getName());

        engine.execute("CREATE OR REPLACE TABLE tu2 AS SELECT 1 UNION ALL SELECT 2");
        assertEquals(2, engine.executeQuery("SELECT * FROM tu2").getRowCount());
    }
}
