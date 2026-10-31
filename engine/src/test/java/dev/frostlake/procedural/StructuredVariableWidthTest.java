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
package dev.frostlake.procedural;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A block writes a structured ARRAY(...), OBJECT(...) or MAP variable out as it writes a plain VARIANT, OBJECT or ARRAY
 * one where a refusal places what follows it on the line: read back through PARSE_JSON and cast to its type, a text
 * leaf at the width a cast gives a bare VARCHAR, so the place moves as far as that text is wide (live-verified).
 */
public class StructuredVariableWidthTest extends MeasuredCellsTestSupport {

    /** Structured arrays, objects and maps of whole numbers, texts, floats and nested shapes, by name and as a bind. */
    @Test
    public void aStructuredVariableIsWrittenOutThroughParseJson() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE a ARRAY; BEGIN a := [1, 2]; RETURN (ARRAY_SIZE(a) > 0) || ' ' || TRUE; END; "
                + "$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 44 : SQL compilation "
                + "error: error line 1 at position 48 | Invalid argument types for function '||': (BOOLEAN, "
                + "VARCHAR(1), BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE a ARRAY(INT); BEGIN a := [1, 2]; RETURN (ARRAY_SIZE(a) > 0) || ' ' || TRUE; "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 49 : SQL compilation "
                + "error: error line 1 at position 62 | Invalid argument types for function '||': (BOOLEAN, "
                + "VARCHAR(1), BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE a ARRAY(VARCHAR); BEGIN a := ['x']; RETURN (ARRAY_SIZE(a) > 0) || ' ' || "
                + "TRUE; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 52 : SQL compilation "
                + "error: error line 1 at position 68 | Invalid argument types for function '||': (BOOLEAN, "
                + "VARCHAR(1), BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE a ARRAY(INT NOT NULL); BEGIN a := [1]; RETURN (ARRAY_SIZE(a) > 0) || ' ' || "
                + "TRUE; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 55 : SQL compilation "
                + "error: error line 1 at position 71 | Invalid argument types for function '||': (BOOLEAN, "
                + "VARCHAR(1), BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE a ARRAY(ARRAY(INT)); BEGIN a := [[1]]; RETURN (ARRAY_SIZE(a) > 0) || ' ' || "
                + "TRUE; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 55 : SQL compilation "
                + "error: error line 1 at position 69 | Invalid argument types for function '||': (BOOLEAN, "
                + "VARCHAR(1), BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE o OBJECT(x INT); BEGIN o := {'x': 1}::OBJECT(x INT); RETURN (o['x'] > 0) || "
                + "' ' || TRUE; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 69 : SQL compilation "
                + "error: error line 1 at position 58 | Invalid argument types for function '||': (BOOLEAN, "
                + "VARCHAR(1), BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE o OBJECT(x INT, y VARCHAR); BEGIN o := {'x': 1, 'y': 'a'}::OBJECT(x INT, y "
                + "VARCHAR); RETURN (o['x'] > 0) || ' ' || TRUE; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 101 : SQL compilation "
                + "error: error line 1 at position 80 | Invalid argument types for function '||': (BOOLEAN, "
                + "VARCHAR(1), BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE m MAP(VARCHAR, INT); BEGIN m := {'a': 1}::MAP(VARCHAR, INT); RETURN "
                + "(MAP_SIZE(m) > 0) || ' ' || TRUE; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 77 : SQL compilation "
                + "error: error line 1 at position 78 | Invalid argument types for function '||': (BOOLEAN, "
                + "VARCHAR(1), BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE a ARRAY(INT) DEFAULT [1]; BEGIN RETURN IS_ARRAY(a) || ' ' || TRUE; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 48 : SQL compilation "
                + "error: error line 1 at position 0 | Invalid argument types for function 'IS_ARRAY': "
                + "(ARRAY(NUMBER(38,0)))"},
            {"EXECUTE IMMEDIATE $$ DECLARE a ARRAY(INT); BEGIN a := [1]; RETURN IS_ARRAY(a::VARIANT) || ' ' || TRUE; "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 46 : SQL compilation "
                + "error: error line 1 at position 63 | Invalid argument types for function '||': (BOOLEAN, "
                + "VARCHAR(1), BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE o OBJECT; BEGIN o := {'x': 1}; RETURN (o['x'] > 0) || ' ' || TRUE; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : SQL compilation "
                + "error: error line 1 at position 42 | Invalid argument types for function '||': (BOOLEAN, "
                + "VARCHAR(1), BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE a ARRAY(INT); BEGIN a := [1]; RETURN (ARRAY_SIZE(:a) > 0) || ' ' || TRUE; "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 46 : SQL compilation "
                + "error: error line 1 at position 60 | Invalid argument types for function '||': (BOOLEAN, "
                + "VARCHAR(1), BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE a ARRAY(FLOAT); BEGIN a := [1.5]; RETURN (ARRAY_SIZE(a) > 0) || ' ' || "
                + "TRUE; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 50 : SQL compilation "
                + "error: error line 1 at position 55 | Invalid argument types for function '||': (BOOLEAN, "
                + "VARCHAR(1), BOOLEAN)"},
        });
    }
}
