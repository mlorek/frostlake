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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A view or a SQL UDF definition may carry no bind variable — not the unnamed {@code ?}, not a named
 * {@code :var}, not a positional {@code :1} — and a value being IN SCOPE changes nothing: inside a
 * Snowflake Scripting block, where {@code :var} would ordinarily bind the variable's value, the
 * definition is refused rather than the value being baked into it.
 *
 * <p>A CTAS and a stored procedure's body are not definitions in this sense and take the bind, and the
 * semi-structured path that shares the colon ({@code v:a}) is untouched.
 */
public class DefinitionBindRefusalTest extends BaseDatabaseTest {

    private static final String REFUSAL = "Bind variables not allowed in view and UDF definitions.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE tb (n INT, v VARIANT)");
        engine.execute("INSERT INTO tb SELECT 1, TO_VARIANT(OBJECT_CONSTRUCT('a', 5))");
    }

    /** The message of the refusal the statement raises. */
    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                final ResultSet rs = engine.executeQuery(sql);
                while (rs.next()) {
                    continue;
                }
            }
        }).getMessage().replace("\n", " ");
    }

    /** The first column of the first row, as text. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** A named bind in a view's query is refused at the bind, however it is written. */
    @Test
    public void aViewTakesNoNamedBind() {
        assertEquals("SQL compilation error: error line 1 at position 36 " + REFUSAL,
            refusal("CREATE OR REPLACE VIEW vx AS SELECT :x AS c"));
        assertEquals("SQL compilation error: error line 1 at position 36 " + REFUSAL,
            refusal("CREATE OR REPLACE VIEW vp AS SELECT :1 AS c"),
            "a positional bind is a bind too");
        assertEquals("SQL compilation error: error line 1 at position 36 " + REFUSAL,
            refusal("CREATE OR REPLACE VIEW vq AS SELECT ? AS c"),
            "and so is the unnamed one");
    }

    /** A materialized and a secure view are definitions like any other. */
    @Test
    public void everyViewShapeRefusesIt() {
        assertEquals("SQL compilation error: error line 1 at position 69 " + REFUSAL,
            refusal("CREATE OR REPLACE MATERIALIZED VIEW zz AS SELECT n FROM tb WHERE n = :x"));
        assertEquals("SQL compilation error: error line 1 at position 45 " + REFUSAL,
            refusal("CREATE OR REPLACE SECURE VIEW vsec AS SELECT :x AS c"));
    }

    /** A SQL UDF body is refused at the bind's place WITHIN the body. */
    @Test
    public void aUdfBodyTakesNoNamedBind() {
        assertEquals("SQL compilation error: error line 1 at position 1 " + REFUSAL,
            refusal("CREATE OR REPLACE FUNCTION fu() RETURNS VARCHAR AS ':x'"));
    }

    /** Inside a block, where the variable HAS a value, the definition is still refused. */
    @Test
    public void aBlockVariableIsNotBoundIntoADefinition() {
        assertTrue(refusal("DECLARE s VARCHAR DEFAULT 'abc'; BEGIN "
            + "CREATE OR REPLACE VIEW vs AS SELECT :s AS c; RETURN 'ok'; END;")
            .endsWith("SQL compilation error: error line 1 at position 36 " + REFUSAL));
        assertTrue(refusal("DECLARE s VARCHAR DEFAULT 'abc'; BEGIN "
            + "CREATE OR REPLACE VIEW vw AS SELECT n FROM tb WHERE n = :s; RETURN 'ok'; END;")
            .endsWith("SQL compilation error: error line 1 at position 56 " + REFUSAL));
        assertTrue(refusal("DECLARE s VARCHAR DEFAULT 'abc'; BEGIN "
            + "CREATE OR REPLACE FUNCTION fs() RETURNS VARCHAR AS ':s'; RETURN 'ok'; END;")
            .endsWith("SQL compilation error: error line 1 at position 1 " + REFUSAL),
            "a body's place is counted within the BODY, not within the block");
    }

    /** A CTAS and a procedure body are not held to it. */
    @Test
    public void aCtasAndAProcedureBodyTakeIt() {
        assertEquals("ok", answer("DECLARE s VARCHAR DEFAULT 'abc'; BEGIN "
            + "CREATE OR REPLACE TABLE tc AS SELECT :s AS c; RETURN 'ok'; END;"));
        assertEquals("ok", answer("DECLARE s VARCHAR DEFAULT 'abc'; BEGIN "
            + "CREATE OR REPLACE PROCEDURE pb() RETURNS VARCHAR LANGUAGE SQL AS "
            + "$$ BEGIN RETURN :s; END; $$; RETURN 'ok'; END;"));
    }

    /** A SCRIPTING body is the one shape where a colon name READS a parameter instead of binding. */
    @Test
    public void aScriptingBodyReadsItsParametersThatWay() {
        engine.execute("CREATE OR REPLACE FUNCTION fbk(n INT) RETURNS INT AS $$ BEGIN RETURN :n; END $$");
        assertEquals("4", answer("SELECT fbk(4)"));
        engine.execute("CREATE OR REPLACE FUNCTION fbd(n INT) RETURNS INT AS "
            + "$$ DECLARE q INT DEFAULT 3; BEGIN RETURN :q + n; END $$");
        assertEquals("7", answer("SELECT fbd(4)"), "a declared variable reads the same way");
    }

    /** An expression body and a query body are not scripting, and both refuse it. */
    @Test
    public void anExpressionOrQueryBodyStillRefusesIt() {
        assertEquals("SQL compilation error: error line 1 at position 1 " + REFUSAL,
            refusal("CREATE OR REPLACE FUNCTION fex(n INT) RETURNS INT AS ':n'"));
        assertEquals("SQL compilation error: error line 1 at position 8 " + REFUSAL,
            refusal("CREATE OR REPLACE FUNCTION fqy(n INT) RETURNS INT AS 'SELECT :n'"));
    }

    /** The colon of a semi-structured path is no bind, in a view or a UDF body. */
    @Test
    public void aSemiStructuredPathIsUntouched() {
        engine.execute("CREATE OR REPLACE VIEW vpath AS SELECT v:a AS c FROM tb");
        assertEquals("5", answer("SELECT c FROM vpath"));
        engine.execute("CREATE OR REPLACE FUNCTION fpath(p VARIANT) RETURNS VARIANT AS 'p:a'");
        assertEquals("5", answer("SELECT fpath(v) FROM tb"));
    }
}
