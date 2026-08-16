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

/**
 * A CALL's arguments are matched against the procedure's parameter types while the statement compiles.
 * A pair no implicit conversion carries is refused, anchored nowhere, listing every argument's type:
 *
 * <pre>
 *   SQL compilation error: error line 0 at position -1
 *   Invalid argument types for function 'CAT_M': (NUMBER(1,0), TIMESTAMP_TZ(9))
 * </pre>
 *
 * Every cell was measured on a real account.
 */
public class CallArgumentTypeTest extends BaseDatabaseTest {

    private static final String TZ = "'2020-01-15 10:00:00 +0530'::TIMESTAMP_TZ";
    private static final String LTZ = "'2020-01-15 10:00:00'::TIMESTAMP_LTZ";
    private static final String NTZ = "'2020-01-15 10:00:00'::TIMESTAMP_NTZ";
    private static final String DATE = "'2020-01-15'::DATE";

    private void proc(final String name, final String parameters) {
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + "(" + parameters + ") RETURNS VARCHAR"
            + " LANGUAGE SQL AS $$ BEGIN RETURN 'ok'; END; $$");
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return String.valueOf(refused.getMessage()).replace('\n', '|');
    }

    private String called(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private static String argumentTypes(final String proc, final String types) {
        return "SQL compilation error: error line 0 at position -1|Invalid argument types for function '" + proc
            + "': (" + types + ")";
    }

    /** A TIMESTAMP_TZ binds to a TZ parameter only; a number, a BOOLEAN or a FLOAT to no timestamp. */
    @Test
    public void theTimestampParametersTakeATimestampADateOrText() {
        proc("cat_ltz", "a TIMESTAMP_LTZ");
        assertEquals(argumentTypes("CAT_LTZ", "TIMESTAMP_TZ(9)"), refusal("CALL cat_ltz(" + TZ + ")"));
        assertEquals(argumentTypes("CAT_LTZ", "NUMBER(1,0)"), refusal("CALL cat_ltz(5)"));
        assertEquals(argumentTypes("CAT_LTZ", "NUMBER(2,1)"), refusal("CALL cat_ltz(1.5)"));
        assertEquals(argumentTypes("CAT_LTZ", "BOOLEAN"), refusal("CALL cat_ltz(TRUE)"));
        assertEquals(argumentTypes("CAT_LTZ", "FLOAT"), refusal("CALL cat_ltz(1.5::FLOAT)"));
        assertEquals("ok", called("CALL cat_ltz(" + LTZ + ")"));
        assertEquals("ok", called("CALL cat_ltz(" + NTZ + ")"));
        assertEquals("ok", called("CALL cat_ltz(" + DATE + ")"));
        assertEquals("ok", called("CALL cat_ltz('2020-01-15')"));
        proc("cat_ntz", "a TIMESTAMP_NTZ");
        assertEquals(argumentTypes("CAT_NTZ", "TIMESTAMP_TZ(9)"), refusal("CALL cat_ntz(" + TZ + ")"));
        proc("cat_tz", "a TIMESTAMP_TZ");
        assertEquals("ok", called("CALL cat_tz(" + TZ + ")"));
        assertEquals(argumentTypes("CAT_TZ", "NUMBER(1,0)"), refusal("CALL cat_tz(5)"));
    }

    @Test
    public void theDateAndTimeParameters() {
        proc("cat_d", "a DATE");
        assertEquals(argumentTypes("CAT_D", "NUMBER(1,0)"), refusal("CALL cat_d(5)"));
        assertEquals(argumentTypes("CAT_D", "BOOLEAN"), refusal("CALL cat_d(TRUE)"));
        assertEquals(argumentTypes("CAT_D", "TIME(9)"), refusal("CALL cat_d('10:00:00'::TIME)"));
        assertEquals("ok", called("CALL cat_d(" + TZ + ")"));
        assertEquals("ok", called("CALL cat_d('2020-01-15')"));
        proc("cat_t", "a TIME");
        assertEquals(argumentTypes("CAT_T", "NUMBER(1,0)"), refusal("CALL cat_t(5)"));
        assertEquals(argumentTypes("CAT_T", "DATE"), refusal("CALL cat_t(" + DATE + ")"));
        assertEquals("ok", called("CALL cat_t(" + NTZ + ")"));
        assertEquals("ok", called("CALL cat_t('10:00:00')"));
    }

    @Test
    public void theNumberFloatAndBooleanParameters() {
        proc("cat_n", "a NUMBER");
        assertEquals(argumentTypes("CAT_N", "TIMESTAMP_TZ(9)"), refusal("CALL cat_n(" + TZ + ")"));
        assertEquals(argumentTypes("CAT_N", "DATE"), refusal("CALL cat_n(" + DATE + ")"));
        assertEquals(argumentTypes("CAT_N", "BOOLEAN"), refusal("CALL cat_n(TRUE)"));
        assertEquals(argumentTypes("CAT_N", "TIME(9)"), refusal("CALL cat_n('10:00:00'::TIME)"));
        assertEquals(argumentTypes("CAT_N", "BINARY(67108864)"), refusal("CALL cat_n(TO_BINARY('AB'))"));
        assertEquals(argumentTypes("CAT_N", "OBJECT"), refusal("CALL cat_n(OBJECT_CONSTRUCT('a', 1))"));
        assertEquals("ok", called("CALL cat_n(1.5::FLOAT)"));
        assertEquals("ok", called("CALL cat_n('5')"));
        assertEquals("ok", called("CALL cat_n(PARSE_JSON('1'))"));
        proc("cat_f", "a FLOAT");
        assertEquals(argumentTypes("CAT_F", "BOOLEAN"), refusal("CALL cat_f(TRUE)"));
        assertEquals(argumentTypes("CAT_F", "DATE"), refusal("CALL cat_f(" + DATE + ")"));
        assertEquals("ok", called("CALL cat_f(5)"));
        proc("cat_b", "a BOOLEAN");
        assertEquals(argumentTypes("CAT_B", "DATE"), refusal("CALL cat_b(" + DATE + ")"));
        assertEquals(argumentTypes("CAT_B", "TIME(9)"), refusal("CALL cat_b('10:00:00'::TIME)"));
        assertEquals("ok", called("CALL cat_b(5)"));
        assertEquals("ok", called("CALL cat_b(1.5::FLOAT)"));
    }

    @Test
    public void theTextBinaryAndSemiStructuredParameters() {
        proc("cat_v", "a VARCHAR");
        assertEquals(argumentTypes("CAT_V", "OBJECT"), refusal("CALL cat_v(OBJECT_CONSTRUCT('a', 1))"));
        assertEquals(argumentTypes("CAT_V", "ARRAY"), refusal("CALL cat_v(ARRAY_CONSTRUCT(1))"));
        assertEquals(argumentTypes("CAT_V", "BINARY(67108864)"), refusal("CALL cat_v(TO_BINARY('AB'))"));
        assertEquals("ok", called("CALL cat_v(TRUE)"));
        assertEquals("ok", called("CALL cat_v('10:00:00'::TIME)"));
        assertEquals("ok", called("CALL cat_v(PARSE_JSON('1'))"));
        proc("cat_bn", "a BINARY");
        assertEquals(argumentTypes("CAT_BN", "VARCHAR(2)"), refusal("CALL cat_bn('AB')"));
        assertEquals(argumentTypes("CAT_BN", "NUMBER(1,0)"), refusal("CALL cat_bn(5)"));
        assertEquals("ok", called("CALL cat_bn(TO_BINARY('AB'))"));
        proc("cat_var", "a VARIANT");
        assertEquals(argumentTypes("CAT_VAR", "VARCHAR(1)"), refusal("CALL cat_var('x')"));
        assertEquals(argumentTypes("CAT_VAR", "DATE"), refusal("CALL cat_var(" + DATE + ")"));
        assertEquals("ok", called("CALL cat_var(5)"));
        assertEquals("ok", called("CALL cat_var(TRUE)"));
        assertEquals("ok", called("CALL cat_var(OBJECT_CONSTRUCT('a', 1))"));
        proc("cat_ob", "a OBJECT");
        assertEquals(argumentTypes("CAT_OB", "NUMBER(1,0)"), refusal("CALL cat_ob(5)"));
        assertEquals(argumentTypes("CAT_OB", "VARCHAR(1)"), refusal("CALL cat_ob('x')"));
        assertEquals(argumentTypes("CAT_OB", "ARRAY"), refusal("CALL cat_ob(ARRAY_CONSTRUCT(1))"));
        assertEquals("ok", called("CALL cat_ob(OBJECT_CONSTRUCT('a', 1))"));
        assertEquals("ok", called("CALL cat_ob(PARSE_JSON('{\"a\":1}'))"));
        proc("cat_ar", "a ARRAY");
        assertEquals(argumentTypes("CAT_AR", "NUMBER(1,0)"), refusal("CALL cat_ar(5)"));
        assertEquals(argumentTypes("CAT_AR", "OBJECT"), refusal("CALL cat_ar(OBJECT_CONSTRUCT('a', 1))"));
        assertEquals("ok", called("CALL cat_ar(ARRAY_CONSTRUCT(1))"));
    }

    /** The sentence lists every argument, an untyped NULL as NULL; too few is the same sentence. */
    @Test
    public void theSentenceListsEveryArgument() {
        proc("cat_m", "a NUMBER, b TIMESTAMP_LTZ");
        assertEquals(argumentTypes("CAT_M", "NUMBER(1,0), TIMESTAMP_TZ(9)"), refusal("CALL cat_m(5, " + TZ + ")"));
        assertEquals(argumentTypes("CAT_M", "BOOLEAN, DATE"), refusal("CALL cat_m(TRUE, " + DATE + ")"));
        assertEquals(argumentTypes("CAT_M", "NULL, TIMESTAMP_TZ(9)"), refusal("CALL cat_m(NULL, " + TZ + ")"));
        assertEquals("ok", called("CALL cat_m(NULL, NULL)"));
        assertEquals(argumentTypes("CAT_M", "NUMBER(1,0)"), refusal("CALL cat_m(5)"), "too few is the same sentence");
        assertEquals("SQL compilation error: error line 0 at position -1|too many arguments for function"
            + " [CAT_M(5, CURRENT_DATE(), 7)] expected 2, got 3", refusal("CALL cat_m(5, CURRENT_DATE(), 7)"));
        proc("cat_x", "zeta NUMBER, alpha DATE");
        assertEquals(argumentTypes("CAT_X", "NUMBER(2,0), NUMBER(2,0)"), refusal("CALL cat_x(1 + 1, 2 + 2)"));
        assertEquals("ok", called("CALL cat_x(1 + 1, '2020-01-15'::DATE + 1)"));
    }

    /** An all-named CALL has its own sentence, naming the parameters in declaration order. */
    @Test
    public void aNamedCallHasItsOwnSentence() {
        proc("cat_m", "a NUMBER, b TIMESTAMP_LTZ");
        assertEquals("SQL compilation error: error line 0 at position -1|named arguments [A, B] do not match any"
            + " signature for function CAT_M", refusal("CALL cat_m(a => TRUE, b => " + TZ + ")"));
        assertEquals("ok", called("CALL cat_m(b => " + DATE + ", a => 5)"));
        proc("cat_x", "zeta NUMBER, alpha DATE");
        assertEquals("SQL compilation error: error line 0 at position -1|named arguments [ZETA, ALPHA] do not"
            + " match any signature for function CAT_X", refusal("CALL cat_x(alpha => 5, zeta => 5)"));
    }

    /** A CALL issued from inside a block binds its variables by their declared types. */
    @Test
    public void aCallInsideABlockBindsItsVariables() {
        proc("cat_ob", "a OBJECT");
        assertEquals("ok", called("EXECUTE IMMEDIATE $$ DECLARE o OBJECT; r VARCHAR; BEGIN"
            + " o := OBJECT_CONSTRUCT('a', 1); r := (CALL cat_ob(:o)); RETURN r; END; $$"));
    }

    /** What binds converts as it arrives: a NUMBER(5,2) parameter reads its argument at its scale. */
    @Test
    public void whatBindsConvertsAsItArrives() {
        engine.execute("CREATE OR REPLACE PROCEDURE cat_n52(a NUMBER(5,2)) RETURNS VARCHAR LANGUAGE SQL"
            + " AS $$ BEGIN RETURN a::VARCHAR; END; $$");
        assertEquals("1.78", called("CALL cat_n52(1.7777)"));
        assertEquals("1.50", called("CALL cat_n52('1.5')"));
        assertEquals(argumentTypes("CAT_N52", "BOOLEAN"), refusal("CALL cat_n52(TRUE)"));
    }
}
