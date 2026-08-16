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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A scalar {@code LANGUAGE SQL} UDF may have a Snowflake-Scripting block for a body, and the block really
 * executes. Every expectation here was measured on a real account — the
 * account is the specification, and each test names the live result it encodes.
 *
 * <p>The body language is NOT the full scripting language a stored procedure gets. Snowflake calls this
 * restricted form a <em>Snowscript UDF</em>: control flow and scalar expressions only, so a function stays
 * side-effect-free and never reaches a table. The rejection tests below record exactly where that line
 * falls, with Snowflake's own error wording.
 */
public class ScriptingUdfBodyTest extends BaseDatabaseTest {

    // ---------------------------------------------------------------- the three body delimiters

    /** Live: {@code AS $$ BEGIN RETURN 1; END $$} then {@code SELECT f()} → 1. */
    @Test
    public void dollarQuotedBlockBodyRuns() {
        engine.execute("CREATE FUNCTION f_dollar() RETURNS INT AS $$ BEGIN RETURN 1; END $$");
        assertEquals(1, intResult("SELECT f_dollar()"));
    }

    /** Live: {@code AS ' BEGIN RETURN 1; END '} → 1. The delimiter is irrelevant — the body arrives
     *  unquoted either way — but all three spellings were confirmed separately. */
    @Test
    public void singleQuotedBlockBodyRuns() {
        engine.execute("CREATE FUNCTION f_quoted() RETURNS INT AS ' BEGIN RETURN 1; END '");
        assertEquals(1, intResult("SELECT f_quoted()"));
    }

    /** Live: {@code AS 'BEGIN RETURN ''hi''; END'} → {@code hi}. */
    @Test
    public void singleQuotedBlockBodyWithEscapedQuotesRuns() {
        engine.execute("CREATE FUNCTION f_escaped() RETURNS VARCHAR AS 'BEGIN RETURN ''hi''; END'");
        assertEquals("hi", engine.executeQuery("SELECT f_escaped()").getRows().get(0).getValue(0));
    }

    /** Live: {@code LANGUAGE SQL AS 'BEGIN RETURN 3; END'} → 3. */
    @Test
    public void explicitLanguageSqlBlockBodyRuns() {
        engine.execute("CREATE FUNCTION f_explicit() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 3; END'");
        assertEquals(3, intResult("SELECT f_explicit()"));
    }

    /** Live: the UNQUOTED {@code AS BEGIN RETURN 1; END} form → 1. It is legal only for a block. */
    @Test
    public void unquotedBlockBodyRuns() {
        engine.execute("CREATE FUNCTION f_unquoted() RETURNS INT AS BEGIN RETURN 1; END");
        assertEquals(1, intResult("SELECT f_unquoted()"));
    }

    /** Live: {@code AS 1+1} and {@code AS SELECT 1} are SYNTAX ERRORS — only a block may go unquoted. */
    @Test
    public void unquotedExpressionAndQueryBodiesStayRejected() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE FUNCTION f_bad_expr() RETURNS INT AS 1+1");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE FUNCTION f_bad_query() RETURNS INT AS SELECT 1");
            }
        });
    }

    // ---------------------------------------------------------------- it is a real block, not an expression

    /** Live: {@code BEGIN RETURN 1; RETURN 2; END} → 1. A multi-statement body proves it is a real block. */
    @Test
    public void firstReturnWins() {
        engine.execute("CREATE FUNCTION f_two() RETURNS INT AS $$ BEGIN RETURN 1; RETURN 2; END $$");
        assertEquals(1, intResult("SELECT f_two()"));
    }

    /** Live: {@code BEGIN LET x INT := 5; RETURN x * 2; END} → 10. */
    @Test
    public void letVariableIsUsable() {
        engine.execute("CREATE FUNCTION f_let() RETURNS INT AS $$ BEGIN LET x INT := 5; RETURN x * 2; END $$");
        assertEquals(10, intResult("SELECT f_let()"));
    }

    /** Live: {@code DECLARE x INT DEFAULT 7; BEGIN RETURN x; END} → 7. */
    @Test
    public void declareSectionIsUsable() {
        engine.execute("""
            CREATE FUNCTION f_declare() RETURNS INT AS
            $$
            DECLARE
              x INT DEFAULT 7;
            BEGIN
              RETURN x;
            END
            $$
            """);
        assertEquals(7, intResult("SELECT f_declare()"));
    }

    /** Live: {@code BEGIN IF (1=1) THEN RETURN 9; END IF; RETURN 0; END} → 9. */
    @Test
    public void controlFlowRuns() {
        engine.execute("CREATE FUNCTION f_if() RETURNS INT AS "
            + "$$ BEGIN IF (1=1) THEN RETURN 9; END IF; RETURN 0; END $$");
        assertEquals(9, intResult("SELECT f_if()"));
    }

    /** Live: a WHILE loop accumulating 1..4 → 10. */
    @Test
    public void whileLoopRuns() {
        engine.execute("""
            CREATE FUNCTION f_while(n INT) RETURNS INT AS
            $$
            DECLARE
              i INT DEFAULT 0;
              s INT DEFAULT 0;
            BEGIN
              WHILE (i < n) DO
                i := i + 1;
                s := s + i;
              END WHILE;
              RETURN s;
            END
            $$
            """);
        assertEquals(10, intResult("SELECT f_while(4)"));
    }

    /** Live: {@code BEGIN LET x INT := 1/0; RETURN 1; EXCEPTION WHEN OTHER THEN RETURN -1; END} → -1.
     *  EXCEPTION handlers are part of the restricted body language. */
    @Test
    public void exceptionHandlerRuns() {
        engine.execute("CREATE FUNCTION f_exc() RETURNS INT AS "
            + "$$ BEGIN LET x INT := 1/0; RETURN 1; EXCEPTION WHEN OTHER THEN RETURN -1; END $$");
        assertEquals(-1, intResult("SELECT f_exc()"));
    }

    /** Live: a body with no RETURN at all — {@code BEGIN LET x INT := 1; END} — yields NULL, not an error. */
    @Test
    public void bodyWithoutReturnYieldsNull() {
        engine.execute("CREATE FUNCTION f_noret() RETURNS INT AS $$ BEGIN LET x INT := 1; END $$");
        assertNull(engine.executeQuery("SELECT f_noret()").getRows().get(0).getValue(0));
    }

    // ---------------------------------------------------------------- parameters

    /** Live: a parameter is referenced BARE inside the block — {@code BEGIN RETURN n + 1; END}, f(10) → 11. */
    @Test
    public void parameterIsReferencedBare() {
        engine.execute("CREATE FUNCTION f_param(n INT) RETURNS INT AS $$ BEGIN RETURN n + 1; END $$");
        assertEquals(11, intResult("SELECT f_param(10)"));
    }

    /** Live: a parameter may SHADOW the routine's own name — {@code FUNCTION f(f int)}, f(7) → 7. */
    @Test
    public void parameterMayShadowTheRoutineName() {
        engine.execute("CREATE FUNCTION f_shadow(f_shadow INT) RETURNS INT AS $$ BEGIN RETURN f_shadow; END $$");
        assertEquals(7, intResult("SELECT f_shadow(7)"));
    }

    /** Live: a parameter may shadow a COLUMN name — {@code FUNCTION fv(v int)} over a table with column v. */
    @Test
    public void parameterMayShadowAColumnName() {
        engine.execute("CREATE TABLE shadow_src (v INTEGER)");
        engine.execute("INSERT INTO shadow_src VALUES (3)");
        engine.execute("CREATE FUNCTION f_col(v INT) RETURNS INT AS $$ BEGIN RETURN v; END $$");
        assertEquals(7, intResult("SELECT f_col(7)"));
        assertEquals(3, intResult("SELECT f_col(v) FROM shadow_src"));
    }

    /** Live: zero-parameter and many-parameter forms both work — f_many(1,2,3) → 123. */
    @Test
    public void zeroAndManyParameterFormsRun() {
        engine.execute("CREATE FUNCTION f_zero() RETURNS INT AS $$ BEGIN RETURN 5; END $$");
        engine.execute("CREATE FUNCTION f_many(a INT, b INT, c INT) RETURNS INT AS "
            + "$$ BEGIN RETURN a * 100 + b * 10 + c; END $$");
        assertEquals(5, intResult("SELECT f_zero()"));
        assertEquals(123, intResult("SELECT f_many(1, 2, 3)"));
    }

    /** Live: a NULL argument reaches the block (f_many(1, NULL, 3) → NULL through the arithmetic), and a
     *  body that tests for it sees the NULL — {@code IF (n IS NULL) THEN RETURN 'was-null'} → was-null. */
    @Test
    public void nullArgumentReachesTheBlock() {
        engine.execute("CREATE FUNCTION f_null(n INT) RETURNS VARCHAR AS "
            + "$$ BEGIN IF (n IS NULL) THEN RETURN 'was-null'; END IF; RETURN 'not-null'; END $$");
        assertEquals("was-null", engine.executeQuery("SELECT f_null(NULL)").getRows().get(0).getValue(0));
        assertEquals("not-null", engine.executeQuery("SELECT f_null(5)").getRows().get(0).getValue(0));
    }

    /** Live: RETURNS NULL ON NULL INPUT still short-circuits BEFORE the block — f_strict(NULL) → NULL. */
    @Test
    public void strictNullHandlingShortCircuitsTheBlock() {
        engine.execute("CREATE FUNCTION f_strict(n INT) RETURNS VARCHAR RETURNS NULL ON NULL INPUT AS "
            + "$$ BEGIN IF (n IS NULL) THEN RETURN 'was-null'; END IF; RETURN 'not-null'; END $$");
        assertNull(engine.executeQuery("SELECT f_strict(NULL)").getRows().get(0).getValue(0));
    }

    // ---------------------------------------------------------------- the declared RETURNS type

    /** Live: the RETURNed value is cast to the DECLARED type — {@code RETURNS INT} over {@code RETURN '7'}
     *  is 7, and {@code RETURNS VARCHAR} over {@code RETURN 42} is the text 42. */
    @Test
    public void returnValueIsCastToTheDeclaredType() {
        engine.execute("CREATE FUNCTION f_str_to_int() RETURNS INT AS $$ BEGIN RETURN '7'; END $$");
        engine.execute("CREATE FUNCTION f_int_to_str() RETURNS VARCHAR AS $$ BEGIN RETURN 42; END $$");
        assertEquals(7, intResult("SELECT f_str_to_int()"));
        assertEquals("42", engine.executeQuery("SELECT f_int_to_str()").getRows().get(0).getValue(0));
    }

    /** Live: a value the declared type cannot hold fails AT CALL TIME with
     *  {@code Numeric value 'abc' is not recognized} — the CREATE itself succeeds. */
    @Test
    public void returnValueThatCannotBeCastFailsAtCallTime() {
        engine.execute("CREATE FUNCTION f_bad_cast() RETURNS INT AS $$ BEGIN RETURN 'abc'; END $$");
        final RuntimeException failure = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT f_bad_cast()");
            }
        });
        assertTrue(failure.getMessage().contains("Numeric value 'abc' is not recognized"), failure.getMessage());
    }

    /** Live: a block-bodied UDF declared {@code RETURNS VARIANT} / ARRAY / OBJECT is CREATED without
     *  complaint and then fails on every call — {@code Unsupported return type for Snowscript UDF: VARIANT}
     *  (Snowflake compiles the block lazily). BINARY, DATE, TIMESTAMP_NTZ, BOOLEAN, FLOAT and NUMBER(p,s)
     *  were all verified to work. */
    @Test
    public void semiStructuredReturnTypeIsRejectedAtCallTime() {
        engine.execute("CREATE FUNCTION f_variant() RETURNS VARIANT AS $$ BEGIN RETURN 1; END $$");
        final RuntimeException failure = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT f_variant()");
            }
        });
        assertTrue(failure.getMessage().contains("Unsupported return type for Snowscript UDF: VARIANT"),
            failure.getMessage());
    }

    /** Live: NUMBER(p,s) and BOOLEAN block-bodied functions return normally. */
    @Test
    public void scalarReturnTypesOtherThanSemiStructuredWork() {
        engine.execute("CREATE FUNCTION f_num() RETURNS NUMBER(10,2) AS $$ BEGIN RETURN 1.25; END $$");
        engine.execute("CREATE FUNCTION f_bool() RETURNS BOOLEAN AS $$ BEGIN RETURN TRUE; END $$");
        assertEquals(0, new BigDecimal("1.25").compareTo(
            new BigDecimal(engine.executeQuery("SELECT f_num()").getRows().get(0).getValue(0).toString())));
        assertEquals(Boolean.TRUE, engine.executeQuery("SELECT f_bool()").getRows().get(0).getValue(0));
    }

    // ---------------------------------------------------------------- the restricted body language

    /**
     * Live: DML in a function body is refused at CREATE —
     * {@code Unsupported statement type for Snowscript UDF: query statement}. This is how Snowflake keeps
     * a UDF side-effect-free, and the table is verifiably untouched afterwards.
     */
    @Test
    public void dmlInAFunctionBodyIsRejected() {
        engine.execute("CREATE TABLE dml_src (v INTEGER)");
        engine.execute("INSERT INTO dml_src VALUES (1), (2), (3)");
        assertUnsupportedStatement("query statement",
            "CREATE FUNCTION f_dml() RETURNS INT AS $$ BEGIN INSERT INTO dml_src VALUES (99); RETURN 1; END $$");
        assertUnsupportedStatement("query statement",
            "CREATE FUNCTION f_upd() RETURNS INT AS $$ BEGIN UPDATE dml_src SET v = 0; RETURN 1; END $$");
        assertUnsupportedStatement("query statement",
            "CREATE FUNCTION f_ddl() RETURNS INT AS $$ BEGIN CREATE TABLE zz (a INT); RETURN 1; END $$");
        assertEquals(3, intResult("SELECT COUNT(*) FROM dml_src"));
    }

    /** Live: a bare query statement, EXECUTE IMMEDIATE and CALL are all "query statement" too — every
     *  route by which a body could reach arbitrary SQL is closed. */
    @Test
    public void arbitrarySqlInAFunctionBodyIsRejected() {
        assertUnsupportedStatement("query statement",
            "CREATE FUNCTION f_select() RETURNS INT AS $$ BEGIN SELECT 1; RETURN 2; END $$");
        assertUnsupportedStatement("query statement",
            "CREATE FUNCTION f_exec() RETURNS INT AS $$ BEGIN EXECUTE IMMEDIATE 'SELECT 1'; RETURN 1; END $$");
        assertUnsupportedStatement("query statement",
            "CREATE FUNCTION f_call() RETURNS INT AS $$ BEGIN CALL some_proc(); RETURN 1; END $$");
    }

    /** Live: {@code SELECT COUNT(*) INTO x FROM t} → {@code … : select into statement}. */
    @Test
    public void selectIntoInAFunctionBodyIsRejected() {
        engine.execute("CREATE TABLE into_src (v INTEGER)");
        assertUnsupportedStatement("select into statement",
            "CREATE FUNCTION f_into() RETURNS INT AS "
                + "$$ DECLARE x INT; BEGIN SELECT COUNT(*) INTO x FROM into_src; RETURN x; END $$");
    }

    /** Live: {@code DECLARE c CURSOR FOR SELECT …} → {@code … : cursor declaration}. */
    @Test
    public void cursorInAFunctionBodyIsRejected() {
        engine.execute("CREATE TABLE cursor_src (v INTEGER)");
        assertUnsupportedStatement("cursor declaration",
            "CREATE FUNCTION f_cursor() RETURNS INT AS "
                + "$$ DECLARE c CURSOR FOR SELECT v FROM cursor_src; BEGIN RETURN 1; END $$");
    }

    /**
     * The bare-vs-{@code :name} split does NOT apply here, and this is where a real account contradicted
     * the expectation: a Snowscript UDF body may contain no embedded SQL at all, so BOTH spellings of a
     * subquery are refused at CREATE with {@code Unsupported expression for Snowscript UDF} — live-verified
     * for {@code v = :n}, for bare {@code v = n}, and for a subquery with no parameter at all. The split is
     * real for a stored PROCEDURE — see {@link #bindVariableSplitIsRealForAProcedure()} — so it is a
     * property of the ROUTINE KIND here, not of the SQL context.
     */
    @Test
    public void subqueryInAFunctionBodyIsRejectedWithEitherBindStyle() {
        engine.execute("CREATE TABLE sub_src (v INTEGER)");
        engine.execute("INSERT INTO sub_src VALUES (1), (2), (3)");
        assertUnsupportedExpression("CREATE FUNCTION f_colon(n INT) RETURNS INT AS "
            + "$$ BEGIN RETURN (SELECT COUNT(*) FROM sub_src WHERE v = :n); END $$");
        assertUnsupportedExpression("CREATE FUNCTION f_bare(n INT) RETURNS INT AS "
            + "$$ BEGIN RETURN (SELECT COUNT(*) FROM sub_src WHERE v = n); END $$");
        assertUnsupportedExpression("CREATE FUNCTION f_plain() RETURNS INT AS "
            + "$$ BEGIN RETURN (SELECT COUNT(*) FROM sub_src); END $$");
        assertUnsupportedExpression("CREATE FUNCTION f_exists() RETURNS BOOLEAN AS "
            + "$$ BEGIN RETURN EXISTS (SELECT 1 FROM sub_src); END $$");
    }

    /**
     * The counterpart of the test above, on the routine kind that DOES have the split: a stored procedure
     * body may contain embedded SQL, and there a parameter must be written {@code :name} — live-verified,
     * {@code CALL pc(1)} → 1 with the colon, while the bare spelling creates fine and then fails the CALL
     * with {@code invalid identifier 'N'} (Snowflake compiles procedure bodies lazily).
     */
    @Test
    public void bindVariableSplitIsRealForAProcedure() {
        engine.execute("CREATE TABLE split_src (v INTEGER)");
        engine.execute("INSERT INTO split_src VALUES (1), (2), (3)");
        engine.execute("CREATE PROCEDURE p_colon(n INT) RETURNS INT LANGUAGE SQL AS "
            + "$$ BEGIN RETURN (SELECT COUNT(*) FROM split_src WHERE v = :n); END $$");
        assertEquals(1, ((Number) engine.executeQuery("CALL p_colon(1)")
            .getRows().get(0).getValue(0)).intValue());
    }

    /** Live: a Snowscript UDF may call BUILT-INs but never another UDF —
     *  {@code Unsupported expression for Snowscript UDF: G_IN(N)}. */
    @Test
    public void callingAnotherUdfFromABlockBodyIsRejected() {
        engine.execute("CREATE FUNCTION f_helper(n INT) RETURNS INT AS 'n * 2'");
        assertUnsupportedExpression("CREATE FUNCTION f_caller(n INT) RETURNS INT AS "
            + "$$ BEGIN RETURN f_helper(n) + 1; END $$");
        assertUnsupportedExpression("CREATE FUNCTION f_caller2(n INT) RETURNS INT AS "
            + "$$ DECLARE x INT DEFAULT f_helper(2); BEGIN RETURN x; END $$");
        assertUnsupportedExpression("CREATE FUNCTION f_caller3(n INT) RETURNS INT AS "
            + "$$ BEGIN LET x INT := f_helper(2); RETURN x; END $$");
    }

    /** Live: a self-call is rejected the same way, so a Snowscript UDF cannot recurse — the account
     *  refused {@code RETURN n * f_fact(n - 1)} at CREATE, before f_fact even existed. */
    @Test
    public void recursionFromABlockBodyIsRejected() {
        assertUnsupportedExpression("CREATE FUNCTION f_fact(n INT) RETURNS INT AS "
            + "$$ BEGIN IF (n <= 1) THEN RETURN 1; END IF; RETURN n * f_fact(n - 1); END $$");
    }

    /** Live: built-in scalar functions ARE available — {@code ABS(n) + 1}, IFF, DATEADD, CURRENT_DATABASE. */
    @Test
    public void builtInFunctionsAreAvailableInABlockBody() {
        engine.execute("CREATE FUNCTION f_abs(n INT) RETURNS INT AS $$ BEGIN RETURN ABS(n) + 1; END $$");
        engine.execute("CREATE FUNCTION f_iff(n INT) RETURNS INT AS $$ BEGIN RETURN IFF(n > 0, 1, 0); END $$");
        assertEquals(6, intResult("SELECT f_abs(-5)"));
        assertEquals(1, intResult("SELECT f_iff(3)"));
    }

    /** Live: a TABLE function takes no block — {@code RETURNS TABLE(x INT) AS $$ BEGIN … END $$} is the
     *  ordinary UDF syntax error there, not a Snowscript-UDF diagnostic. */
    @Test
    public void tableFunctionWithABlockBodyIsRejected() {
        engine.execute("CREATE TABLE tf_src (v INTEGER)");
        final RuntimeException failure = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE FUNCTION f_table() RETURNS TABLE(x INT) AS "
                    + "$$ BEGIN RETURN (SELECT COUNT(*) FROM tf_src); END $$");
            }
        });
        assertTrue(failure.getMessage().contains("Compilation of SQL UDF failed"), failure.getMessage());
    }

    // ---------------------------------------------------------------- scope hygiene and nesting

    /**
     * Live: a block-bodied UDF called from inside a stored procedure runs, and the two variable
     * namespaces stay apart — a procedure with {@code x INT DEFAULT 5} calling a UDF that declares its own
     * {@code x INT DEFAULT 999} still reports 5 afterwards.
     */
    @Test
    public void udfVariablesDoNotClobberACallingProcedure() {
        engine.execute("CREATE FUNCTION f_own_x() RETURNS INT AS "
            + "$$ DECLARE x INT DEFAULT 999; BEGIN RETURN x; END $$");
        engine.execute("""
            CREATE PROCEDURE p_caller() RETURNS VARCHAR LANGUAGE SQL AS
            $$
            DECLARE
              x INT DEFAULT 5;
              y INT;
            BEGIN
              y := f_own_x();
              RETURN x || '/' || y;
            END
            $$
            """);
        assertEquals("5/999", engine.executeQuery("CALL p_caller()").getRows().get(0).getValue(0));
    }

    /**
     * Live: the UDF cannot see the caller's variables either — a body naming a procedure's {@code zz}
     * fails there with {@code invalid identifier 'ZZ'}. Frostlake is laxer about an unknown bare name in a
     * scripting expression (it evaluates to NULL rather than erroring — engine-wide behaviour, not
     * specific to UDFs), so the two backends part ways on the DIAGNOSTIC while agreeing on the property
     * under test, which is the one asserted: the caller's 7 is never visible.
     */
    @Test
    public void udfCannotSeeACallingProceduresVariables() {
        engine.execute("CREATE FUNCTION f_peek() RETURNS INT AS $$ BEGIN RETURN zz; END $$");
        engine.execute("""
            CREATE PROCEDURE p_owner() RETURNS INT LANGUAGE SQL AS
            $$
            DECLARE
              zz INT DEFAULT 7;
            BEGIN
              RETURN f_peek();
            END
            $$
            """);
        Object seen = null;
        try {
            seen = engine.executeQuery("CALL p_owner()").getRows().get(0).getValue(0);
        } catch (final RuntimeException invalidIdentifier) {
            // Snowflake's answer: the name simply does not resolve inside the function.
            assertTrue(invalidIdentifier.getMessage().toUpperCase().contains("ZZ"),
                invalidIdentifier.getMessage());
            return;
        }
        assertNull(seen);
    }

    /** A UDF's own RETURN must not look to the calling block like a RETURN of its own: the procedure
     *  below calls the UDF from an assignment and must still run on to its own RETURN 55. */
    @Test
    public void udfReturnDoesNotTerminateTheCallingBlock() {
        engine.execute("CREATE FUNCTION f_val() RETURNS INT AS $$ BEGIN RETURN 1; END $$");
        engine.execute("""
            CREATE PROCEDURE p_continues() RETURNS INT LANGUAGE SQL AS
            $$
            DECLARE
              a INT;
            BEGIN
              a := f_val();
              RETURN 55;
            END
            $$
            """);
        assertEquals(55, ((Number) engine.executeQuery("CALL p_continues()")
            .getRows().get(0).getValue(0)).intValue());
    }

    /**
     * The rule from the other side: NO UDF may call a block-bodied one, whatever its own body shape.
     * Live-verified — with {@code blk} a Snowscript UDF, both {@code AS 'blk(n) + 1'} and
     * {@code AS 'SELECT blk(n) + 1'} fail at CREATE, quoting the INNER block as a syntax error, while an
     * expression body calling an ordinary expression-bodied UDF is accepted and returns 16. So a
     * Snowscript UDF composes with no other UDF in either direction, and cannot recurse through one.
     */
    @Test
    public void noUdfMayCallABlockBodiedUdf() {
        engine.execute("CREATE FUNCTION f_blk(n INT) RETURNS INT AS $$ BEGIN RETURN n * 2; END $$");
        engine.execute("CREATE FUNCTION f_plain_udf(n INT) RETURNS INT AS 'n * 3'");
        assertRejectedScriptingUdfCall("CREATE FUNCTION f_expr_calls(n INT) RETURNS INT AS 'f_blk(n) + 1'");
        assertRejectedScriptingUdfCall(
            "CREATE FUNCTION f_query_calls(n INT) RETURNS INT AS 'SELECT f_blk(n) + 1'");
        // An expression body calling an EXPRESSION-bodied UDF stays legal (live: 16).
        engine.execute("CREATE FUNCTION f_expr_ok(n INT) RETURNS INT AS 'f_plain_udf(n) + 1'");
        assertEquals(16, intResult("SELECT f_expr_ok(5)"));
    }

    /** Live: everything OTHER than a UDF may call a block-bodied one — a view, a WHERE clause and
     *  {@code INSERT … SELECT} were all verified against the account. */
    @Test
    public void nonUdfCallersMayUseABlockBodiedUdf() {
        engine.execute("CREATE TABLE caller_src (v INTEGER)");
        engine.execute("INSERT INTO caller_src VALUES (1), (2), (3)");
        engine.execute("CREATE FUNCTION f_dbl(n INT) RETURNS INT AS $$ BEGIN RETURN n * 2; END $$");
        engine.execute("CREATE VIEW caller_view AS SELECT f_dbl(v) AS d FROM caller_src");
        assertEquals(12, intResult("SELECT SUM(d) FROM caller_view"));
        assertEquals(2, intResult("SELECT COUNT(*) FROM caller_src WHERE f_dbl(v) > 2"));
        engine.execute("CREATE TABLE caller_dst (d INTEGER)");
        engine.execute("INSERT INTO caller_dst SELECT f_dbl(v) FROM caller_src");
        assertEquals(12, intResult("SELECT SUM(d) FROM caller_dst"));
    }

    /** A block-bodied UDF evaluated once per row keeps its state per call. */
    @Test
    public void blockBodyRunsPerRow() {
        engine.execute("CREATE TABLE row_src (v INTEGER)");
        engine.execute("INSERT INTO row_src VALUES (1), (2), (3)");
        engine.execute("CREATE FUNCTION f_double(n INT) RETURNS INT AS "
            + "$$ DECLARE acc INT DEFAULT 0; BEGIN acc := acc + n; RETURN acc * 2; END $$");
        assertEquals(12, intResult("SELECT SUM(f_double(v)) FROM row_src"));
    }

    // ---------------------------------------------------------------- the untouched body shapes

    /** The expression body path is unchanged. */
    @Test
    public void expressionBodyStillRuns() {
        engine.execute("CREATE FUNCTION f_expr(x INT) RETURNS INT AS 'x + 10'");
        assertEquals(15, intResult("SELECT f_expr(5)"));
    }

    /** The query body path is unchanged. */
    @Test
    public void queryBodyStillRuns() {
        engine.execute("CREATE TABLE query_src (n INTEGER)");
        engine.execute("INSERT INTO query_src VALUES (7), (8)");
        engine.execute("CREATE FUNCTION f_query() RETURNS INT AS 'SELECT MAX(n) FROM query_src'");
        assertEquals(8, intResult("SELECT f_query()"));
    }

    /** A scalar SUBQUERY inside an expression body is unchanged — the new block branch must not steal it. */
    @Test
    public void expressionBodyWithAScalarSubqueryStillRuns() {
        engine.execute("CREATE TABLE scalar_src (n INTEGER)");
        engine.execute("INSERT INTO scalar_src VALUES (1), (2), (3)");
        engine.execute("CREATE FUNCTION f_scalar(x INT) RETURNS INT AS "
            + "'(SELECT COUNT(*) FROM scalar_src WHERE n > x)'");
        assertEquals(2, intResult("SELECT f_scalar(1)"));
    }

    /** A stored procedure body is still required to BE a block — the procedure rule is the correct one
     *  and must not have been loosened along with the function one. */
    @Test
    public void procedureBodyMustStillBeABlock() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE PROCEDURE p_plain(a INTEGER) RETURNS INTEGER LANGUAGE SQL "
                    + "AS 'SELECT a + 1'");
            }
        });
    }

    // ---------------------------------------------------------------- helpers

    private int intResult(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        assertNotNull(result);
        return ((Number) result.getRows().get(0).getValue(0)).intValue();
    }

    private void assertUnsupportedStatement(final String kind, final String ddl) {
        final RuntimeException failure = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(ddl);
            }
        });
        assertTrue(failure.getMessage().contains("Unsupported statement type for Snowscript UDF: " + kind),
            failure.getMessage());
    }

    /** Both engines refuse the CREATE; the wording is each engine's own (Snowflake quotes the callee's
     *  block as a syntax error, Frostlake names the callee), so only the refusal itself is asserted. */
    private void assertRejectedScriptingUdfCall(final String ddl) {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(ddl);
            }
        });
    }

    private void assertUnsupportedExpression(final String ddl) {
        final RuntimeException failure = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(ddl);
            }
        });
        assertTrue(failure.getMessage().contains("Unsupported expression for Snowscript UDF"),
            failure.getMessage());
    }
}
