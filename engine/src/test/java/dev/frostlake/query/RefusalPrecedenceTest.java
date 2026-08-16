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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a statement carries more than one problem, live reports them in an order fixed by KIND — not by
 * which item happens to come first:
 *
 * <pre>
 *   syntax error  &gt;  invalid identifier  &gt;  unknown function  &gt;  argument type / arity / semi-structured
 * </pre>
 *
 * <p>Frostlake reported whichever the plan-time walk reached first, so writing the same two problems in
 * the other order changed the answer. The select list is now walked once per KIND — column references,
 * then call names, then everything else — which is what makes the order independent of position.
 *
 * <p>Each pairing below is asserted BOTH WAYS ROUND for that reason.
 */
public class RefusalPrecedenceTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b BINARY(4), v VARCHAR(5), o OBJECT)");
        engine.execute("INSERT INTO t SELECT 1, TO_BINARY('AB'), 'x', OBJECT_CONSTRUCT('k', 1)");
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "accepted";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', ' ');
        }
    }

    /** An unknown NAME outranks an argument-type refusal, written either way round. */
    @Test
    public void anUnknownNameOutranksAnArgumentType() {
        assertTrue(refusal("SELECT nosuchfn(1), OBJECT_CONSTRUCT(a, 1) FROM t")
            .contains("Unknown function NOSUCHFN."),
            refusal("SELECT nosuchfn(1), OBJECT_CONSTRUCT(a, 1) FROM t"));
        assertTrue(refusal("SELECT OBJECT_CONSTRUCT(a, 1), nosuchfn(1) FROM t")
            .contains("Unknown function NOSUCHFN."),
            refusal("SELECT OBJECT_CONSTRUCT(a, 1), nosuchfn(1) FROM t"));
    }

    /** It outranks an incomparable-operand refusal too. */
    @Test
    public void anUnknownNameOutranksIncomparableOperands() {
        assertTrue(refusal("SELECT nosuchfn(1), GREATEST(b, v) FROM t")
            .contains("Unknown function NOSUCHFN."),
            refusal("SELECT nosuchfn(1), GREATEST(b, v) FROM t"));
        assertTrue(refusal("SELECT GREATEST(b, v), nosuchfn(1) FROM t")
            .contains("Unknown function NOSUCHFN."),
            refusal("SELECT GREATEST(b, v), nosuchfn(1) FROM t"));
    }

    /** And an arity refusal, and a semi-structured one. */
    @Test
    public void anUnknownNameOutranksArityAndSemiStructured() {
        assertTrue(refusal("SELECT nosuchfn(1), ABS(1, 2) FROM t")
            .contains("Unknown function NOSUCHFN."),
            refusal("SELECT nosuchfn(1), ABS(1, 2) FROM t"));
        assertTrue(refusal("SELECT ABS(1, 2), nosuchfn(1) FROM t")
            .contains("Unknown function NOSUCHFN."),
            refusal("SELECT ABS(1, 2), nosuchfn(1) FROM t"));
        assertTrue(refusal("SELECT nosuchfn(1), o || 'x' FROM t")
            .contains("Unknown function NOSUCHFN."),
            refusal("SELECT nosuchfn(1), o || 'x' FROM t"));
    }

    /** An invalid IDENTIFIER outranks the unknown name, wherever the two stand. */
    @Test
    public void anInvalidIdentifierOutranksTheUnknownName() {
        assertTrue(refusal("SELECT nosuchfn(1), nosuchcol, OBJECT_CONSTRUCT(a, 1) FROM t")
            .contains("invalid identifier 'NOSUCHCOL'"),
            refusal("SELECT nosuchfn(1), nosuchcol, OBJECT_CONSTRUCT(a, 1) FROM t"));
        assertTrue(refusal("SELECT nosuchcol, nosuchfn(1) FROM t")
            .contains("invalid identifier 'NOSUCHCOL'"),
            refusal("SELECT nosuchcol, nosuchfn(1) FROM t"));
    }

    /** Each of the outranked refusals still fires on its own — the ordering hides nothing. */
    @Test
    public void eachRefusalStillFiresAlone() {
        assertTrue(refusal("SELECT ABS(1, 2) FROM t").contains("too many arguments"),
            refusal("SELECT ABS(1, 2) FROM t"));
        assertTrue(refusal("SELECT OBJECT_CONSTRUCT(a, 1) FROM t")
            .contains("does not support NUMBER(38,0) argument type"),
            refusal("SELECT OBJECT_CONSTRUCT(a, 1) FROM t"));
        assertTrue(refusal("SELECT GREATEST(b, v) FROM t").contains("Can not convert parameter"),
            refusal("SELECT GREATEST(b, v) FROM t"));
        assertTrue(refusal("SELECT o || 'x' FROM t").contains("Invalid argument type"),
            refusal("SELECT o || 'x' FROM t"));
    }
}
