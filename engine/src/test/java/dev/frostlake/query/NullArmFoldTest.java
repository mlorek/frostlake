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
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A bare {@code NULL} arm of a set operation contributes NOTHING to the fold — the other arms decide
 * the type between themselves and the NULL joins whatever they settle on. Written SECOND that already
 * happened, because the walk kept the type it had; written FIRST the NULL's own reported
 * VARCHAR(16777216) became the answer and every real arm was folded into a 16MB string:
 *
 * <pre>
 *   SELECT NULL UNION SELECT i     was VARCHAR(16777216)   is NUMBER(38,0)
 *   SELECT NULL UNION SELECT d     was VARCHAR(16777216)   is DATE
 * </pre>
 *
 * <p>The ALL-NULL case declares VARCHAR(0), as live does — a width no column can hold and no literal
 * can produce, which is live's way of saying nothing was named. It is set on the REPORTED type ONLY,
 * and that distinction is what makes it possible at all: giving the column a STATIC type instead hands
 * the INSERT's type check a concrete type to compare for the first time, and a string does not match a
 * VARIANT, an ARRAY or an OBJECT. Fourteen vendor loaders insert an all-NULL set operation into
 * exactly those columns, and an earlier attempt that set the static type refused every one of them
 * while the engine's own suite stayed green — which is why the INSERT below is not decoration.
 *
 * <p>Whether an arm is a NULL has to be read off the AST, because the reported type cannot tell: a bare
 * NULL reports VARCHAR(16777216) and so does a real 16MB VARCHAR column, so a fix driven by the type
 * alone would have made every wide VARCHAR arm vanish from the fold instead.
 *
 * <p>A TYPED null is not one of these at all. {@code NULL::INT} and {@code CAST(NULL AS DATE)} parse to
 * a cast rather than a literal and fold as the type they name — both already agreed and must keep
 * agreeing, which is what stops the rule from being "an arm whose value is null".
 *
 * <p>Skipping arms COMPACTS the list the fold walks, so each surviving arm carries the index of the
 * branch it came from: a string literal's numeric measurement is recorded against the branch it was
 * written in, and {@code i UNION NULL UNION v} reads the wrong measurement without it.
 */
public class NullArmFoldTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE na (i INT, v VARCHAR(5), d DATE, n NUMBER(10,2))");
        engine.execute("INSERT INTO na (i) VALUES (1)");
    }

    /** The declared type of the statement's first column. */
    private String typeOf(final String sql) {
        final DataType type = engine.executeQuery(sql).getColumns().get(0).getDataType();
        if (type == null) {
            return "null";
        }
        if (type instanceof NumericType && !"FLOAT".equals(type.getName())) {
            return type.getName() + "(" + ((NumericType) type).getPrecision() + ","
                + ((NumericType) type).getScale() + ")";
        }
        if (type instanceof StringType) {
            return type.getName() + "(" + ((StringType) type).getMaxLength() + ")";
        }
        return type.getName();
    }

    /** Two arms of the named expressions. */
    private String twoArms(final String left, final String op, final String right) {
        return typeOf("SELECT " + left + " FROM na " + op + " SELECT " + right + " FROM na");
    }

    /** The reported defect: a LEADING NULL no longer decides the type. */
    @Test
    public void aLeadingNullContributesNothing() {
        assertEquals("NUMBER(38,0)", twoArms("NULL", "UNION", "i"));
        assertEquals("VARCHAR(5)", twoArms("NULL", "UNION", "v"));
        assertEquals("DATE", twoArms("NULL", "UNION", "d"));
        assertEquals("NUMBER(10,2)", twoArms("NULL", "UNION", "n"));
    }

    /** Every arm a bare NULL declares VARCHAR(0), whatever the operator and however many arms. */
    @Test
    public void anAllNullSetOperationDeclaresTheZeroWidth() {
        assertEquals("VARCHAR(0)", twoArms("NULL", "UNION", "NULL"));
        assertEquals("VARCHAR(0)", twoArms("NULL", "UNION ALL", "NULL"));
        assertEquals("VARCHAR(0)", twoArms("NULL", "EXCEPT", "NULL"));
        assertEquals("VARCHAR(0)", twoArms("NULL", "INTERSECT", "NULL"));
        assertEquals("VARCHAR(0)", typeOf("SELECT NULL FROM na UNION ALL SELECT NULL FROM na"
            + " UNION ALL SELECT NULL FROM na"), "three arms, the same");
        assertEquals("VARCHAR(0)", typeOf("SELECT NULL UNION SELECT NULL"),
            "and with no FROM at all");
    }

    /**
     * And it must STILL feed an INSERT into a semi-structured column — the regression net for the
     * fourteen loaders. The width above is reported; the STATIC type stays undetermined, so this
     * INSERT's type check has nothing to compare and takes the rows, exactly as live does.
     */
    @Test
    public void anAllNullSetOperationStillFeedsASemiStructuredColumn() {
        engine.execute("CREATE OR REPLACE TABLE nat (id INT, va VARIANT, ar ARRAY, ob OBJECT)");
        engine.execute("INSERT INTO nat (id, va) SELECT 1, NULL FROM na"
            + " UNION ALL SELECT 2, NULL FROM na");
        engine.execute("INSERT INTO nat (id, ar) SELECT 1, NULL FROM na"
            + " UNION ALL SELECT 2, NULL FROM na");
        engine.execute("INSERT INTO nat (id, ob) SELECT 1, NULL FROM na"
            + " UNION ALL SELECT 2, NULL FROM na");
    }

    /** A TRAILING NULL already behaved, and must keep behaving. */
    @Test
    public void aTrailingNullIsUnchanged() {
        assertEquals("NUMBER(38,0)", twoArms("i", "UNION", "NULL"));
        assertEquals("VARCHAR(5)", twoArms("v", "UNION", "NULL"));
        assertEquals("DATE", twoArms("d", "UNION", "NULL"));
    }

    /** Every operator carries the rule. */
    @Test
    public void everySetOperatorAgrees() {
        assertEquals("NUMBER(38,0)", twoArms("NULL", "UNION ALL", "i"));
        assertEquals("NUMBER(38,0)", twoArms("NULL", "INTERSECT", "i"));
        assertEquals("NUMBER(38,0)", twoArms("NULL", "EXCEPT", "i"));
        assertEquals("NUMBER(38,0)", twoArms("NULL", "MINUS", "i"));
    }

    /** Three arms, with the NULL leading, trailing and in the middle. */
    @Test
    public void aNullAnywhereInTheChainIsSkipped() {
        assertEquals("NUMBER(38,0)", typeOf("SELECT NULL FROM na UNION SELECT NULL FROM na"
            + " UNION SELECT i FROM na"));
        assertEquals("NUMBER(38,0)", typeOf("SELECT NULL FROM na UNION SELECT i FROM na"
            + " UNION SELECT NULL FROM na"));
        assertEquals("NUMBER(38,5)", typeOf("SELECT i FROM na UNION SELECT NULL FROM na"
            + " UNION SELECT v FROM na"),
            "the surviving arms fold with each other, measurement and all");
    }

    /** A TYPED null is not a NULL arm — it folds as the type it names. */
    @Test
    public void aTypedNullIsNotANullArm() {
        assertEquals("NUMBER(38,5)", twoArms("NULL::INT", "UNION", "v"));
        assertEquals("DATE", twoArms("CAST(NULL AS DATE)", "UNION", "d"));
    }

    /** Each column is folded on its own, so a NULL in one does not reach another. */
    @Test
    public void everyColumnFoldsSeparately() {
        assertEquals("NUMBER(38,0)",
            typeOf("SELECT NULL, i FROM na UNION SELECT i, NULL FROM na"));
        assertEquals("NUMBER(38,0)",
            typeOf("SELECT i, NULL FROM na UNION SELECT i, d FROM na"));
    }
}
