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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A call that resolves to nothing is a COMPILATION error: {@code Unknown function NAME.} — the name
 * upper-cased however it was written, a full stop at the end, and no position. A QUALIFIED name gets a
 * different sentence, {@code Unknown user-defined function SCHEMA.NAME.}
 *
 * <p>Live-verified. The prefix is load-bearing beyond its wording: it is what marks the error
 * compile-time, which is how a CREATE VIEW over an unknown function comes to be refused at all.
 *
 * <p>The refusal is a COMPILE-time one on both engines: a table with no rows is refused identically,
 * because the name is resolved while the statement is planned rather than while rows are produced.
 *
 * <p>It does not outrank everything, and the order is measured: an invalid IDENTIFIER wins over it
 * wherever each of them stands, and a syntax error wins over both.
 *
 * <p>Measured and deliberately NOT asserted: live ACCUMULATES the names when a statement has more than
 * one, pluralising the noun and listing one entry per OCCURRENCE — {@code SELECT zzzfn(1), aaafn(2)} is
 * "Unknown functions ZZZFN, AAAFN." where Frostlake names the first only. Inside the super-groups the
 * counts follow Snowflake's grouping-set EXPANSION: {@code CUBE (a, ROLLUP (b))} lists ROLLUP twice and
 * {@code CUBE (ROLLUP (a), ROLLUP (b))} lists it four times.
 */
public class UnknownFunctionRefusalTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE uf_t (k NUMBER)");
        // A row so the expression is actually evaluated: Frostlake raises this while
        // producing rows, where live refuses at compile time — see the class note.
        engine.execute("INSERT INTO uf_t VALUES (1)");
    }

    private String refusalOf(final String sql) {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
        return ex.getMessage();
    }

    /** The refusal is COMPILE-time: an EMPTY table is refused exactly the same. */
    @Test
    public void anEmptyTableIsRefusedIdentically() {
        engine.execute("CREATE OR REPLACE TABLE uf_empty (a INT)");

        assertEquals("SQL compilation error:\nUnknown function NOSUCHFN.",
            refusalOf("SELECT nosuchfn(a) FROM uf_empty"));
    }

    /** CUBE and ROLLUP are ordinary unknown names when nested inside another super-group. */
    @Test
    public void aNestedSuperGroupIsAnUnknownName() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");

        assertEquals("SQL compilation error:\nUnknown function CUBE.",
            refusalOf("SELECT SUM(b) FROM t GROUP BY ROLLUP (a, CUBE (b))"));
    }

    /** An invalid IDENTIFIER outranks it — whichever of the two is written first. */
    @Test
    public void anInvalidIdentifierOutranksIt() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");

        assertTrue(refusalOf("SELECT nosuchfn(1), nosuchcol FROM t")
            .contains("error line 1 at position 20"),
            refusalOf("SELECT nosuchfn(1), nosuchcol FROM t"));
        assertTrue(refusalOf("SELECT nosuchfn(1), nosuchcol FROM t")
            .contains("invalid identifier 'NOSUCHCOL'"),
            refusalOf("SELECT nosuchfn(1), nosuchcol FROM t"));
        assertTrue(refusalOf("SELECT nosuchcol, nosuchfn(1) FROM t")
            .contains("error line 1 at position 7"),
            refusalOf("SELECT nosuchcol, nosuchfn(1) FROM t"));
        assertTrue(refusalOf("SELECT nosuchfn(nosuchcol) FROM t")
            .contains("error line 1 at position 16"),
            refusalOf("SELECT nosuchfn(nosuchcol) FROM t"));
    }

    /** And a syntax error outranks both. */
    @Test
    public void aSyntaxErrorOutranksBoth() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");

        assertEquals("SQL compilation error:\nsyntax error line 1 at position 32 unexpected '<EOF>'.",
            refusalOf("SELECT nosuchfn(1), FROM t WHERE"));
    }

    /** The name is upper-cased however it was written, and the sentence ends with a full stop. */
    @Test
    public void anUnknownFunctionNamesItselfUpperCased() {
        assertEquals("SQL compilation error:\nUnknown function NO_SUCH_FN.",
            refusalOf("SELECT no_such_fn(k) FROM uf_t"));
        assertEquals("SQL compilation error:\nUnknown function NO_SUCH_FN.",
            refusalOf("SELECT NO_SUCH_FN(k) FROM uf_t"));
    }

    /** An argument-less call reads the same. */
    @Test
    public void anArgumentlessCallReadsTheSame() {
        assertEquals("SQL compilation error:\nUnknown function NO_SUCH_FN.",
            refusalOf("SELECT no_such_fn() FROM uf_t"));
    }

    /** A QUALIFIED name is reported as a user-defined function instead. */
    @Test
    public void aQualifiedNameIsAUserDefinedFunction() {
        assertEquals("SQL compilation error:\nUnknown user-defined function"
            + " NOSUCHSCHEMA.NO_SUCH_FN.",
            refusalOf("SELECT nosuchschema.no_such_fn(k) FROM uf_t"));
    }

    /** And a CREATE VIEW over one is refused with the same sentence — the body will not compile. */
    @Test
    public void aViewOverAnUnknownFunctionIsRefused() {
        assertEquals("SQL compilation error:\nUnknown function NO_SUCH_FN.",
            refusalOf("CREATE VIEW uf_v AS SELECT no_such_fn(k) AS x FROM uf_t"));
    }
}
