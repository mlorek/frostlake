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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A conversion to OBJECT takes a VARIANT, an OBJECT or a NULL, and nothing else. A text — even one that
 * spells an object — a number, a BOOLEAN, a temporal value, a BINARY and an ARRAY are refused while the
 * statement COMPILES, the conversion echoed from the plan and no position, in the CAST, {@code ::},
 * TRY_CAST and TO_OBJECT spellings alike. Live-verified.
 */
public class CastToObjectSourceTest extends BaseDatabaseTest {

    private void createTable() {
        final String columns = "(g VARCHAR(10), i INT, f FLOAT, d DATE, b BOOLEAN, bn BINARY, a ARRAY, o OBJECT, v VARIANT)";
        engine.execute("CREATE OR REPLACE TABLE ct " + columns);
        engine.execute("CREATE OR REPLACE TABLE ct0 " + columns);
        engine.execute("INSERT INTO ct SELECT '2024-01-01', 3, 1.5, '2024-01-15', TRUE, TO_BINARY('01'),"
            + " ARRAY_CONSTRUCT(1), OBJECT_CONSTRUCT('k', 1), PARSE_JSON('{\"k\":2}')");
    }

    /** The one cell of a single-row query, as text. */
    private String scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? "NULL" : value.toString();
    }

    /** The message a refused statement carries. */
    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        return refused.getMessage();
    }

    /** The conversion refusal, echoing the conversion as the plan holds it. */
    private static String invalid(final String echo) {
        return "SQL compilation error:\ninvalid type [" + echo + "] for parameter 'TO_OBJECT'";
    }

    /** A scalar family is no OBJECT, over rows and over none. */
    @Test
    public void aScalarIsNoObject() {
        createTable();
        assertEquals(invalid("CAST(CT.G AS OBJECT)"), refusal("SELECT CAST(g AS OBJECT) FROM ct"));
        assertEquals(invalid("CAST(CT.G AS OBJECT)"), refusal("SELECT g::OBJECT FROM ct"));
        assertEquals(invalid("CAST(CT0.G AS OBJECT)"), refusal("SELECT CAST(g AS OBJECT) FROM ct0"));
        assertEquals(invalid("CAST(CT.I AS OBJECT)"), refusal("SELECT CAST(i AS OBJECT) FROM ct"));
        assertEquals(invalid("CAST(CT.F AS OBJECT)"), refusal("SELECT CAST(f AS OBJECT) FROM ct"));
        assertEquals(invalid("CAST(CT.D AS OBJECT)"), refusal("SELECT CAST(d AS OBJECT) FROM ct"));
        assertEquals(invalid("CAST(CT.B AS OBJECT)"), refusal("SELECT CAST(b AS OBJECT) FROM ct"));
        assertEquals(invalid("CAST(CT.BN AS OBJECT)"), refusal("SELECT CAST(bn AS OBJECT) FROM ct"));
    }

    /** A text is never parsed into an object here, and an ARRAY is no OBJECT either. */
    @Test
    public void aTextOrAnArrayIsNoObject() {
        createTable();
        assertEquals(invalid("CAST('{}' AS OBJECT)"), refusal("SELECT CAST('{}' AS OBJECT)"));
        assertEquals(invalid("CAST('x' AS OBJECT)"), refusal("SELECT 'x'::OBJECT"));
        assertEquals(invalid("CAST(CT.A AS OBJECT)"), refusal("SELECT CAST(a AS OBJECT) FROM ct"));
        assertEquals(invalid("CAST(ARRAY_CONSTRUCT(1) AS OBJECT)"), refusal("SELECT CAST(ARRAY_CONSTRUCT(1) AS OBJECT)"));
    }

    /** The source is echoed as the plan holds it: conversions, rewrites and qualifiers included. */
    @Test
    public void theSourceIsEchoedFromThePlan() {
        createTable();
        assertEquals(invalid("CAST(identity(CT.G) AS OBJECT)"), refusal("SELECT CAST(g::VARCHAR AS OBJECT) FROM ct"));
        assertEquals(invalid("CAST(TEXT_TO_TEXT(CT.G) AS OBJECT)"), refusal("SELECT CAST(g::VARCHAR(5) AS OBJECT) FROM ct"));
        assertEquals(invalid("CAST(TO_CHAR(CT.I) AS OBJECT)"), refusal("SELECT CAST(i::VARCHAR AS OBJECT) FROM ct"));
        assertEquals(invalid("CAST(NEGATE(CT.I) AS OBJECT)"), refusal("SELECT CAST(-i AS OBJECT) FROM ct"));
        assertEquals(invalid("CAST(IFNULL(CT.G, 'x') AS OBJECT)"), refusal("SELECT CAST(COALESCE(g, 'x') AS OBJECT) FROM ct"));
        assertEquals(invalid("CAST(UPPER(CT.G) AS OBJECT)"), refusal("SELECT CAST(UPPER(g) AS OBJECT) FROM ct"));
        assertEquals(invalid("CAST(CT.I + 1 AS OBJECT)"), refusal("SELECT CAST(i + 1 AS OBJECT) FROM ct"));
        assertEquals(invalid("CAST(X.G AS OBJECT)"), refusal("SELECT CAST(x.g AS OBJECT) FROM ct x"));
    }

    /** TO_OBJECT and TRY_CAST refuse the same sources, each echoing its own spelling. */
    @Test
    public void theConversionAndTryFormsAgree() {
        createTable();
        assertEquals(invalid("TO_OBJECT(CT.G)"), refusal("SELECT TO_OBJECT(g) FROM ct"));
        assertEquals(invalid("TO_OBJECT(1)"), refusal("SELECT TO_OBJECT(1)"));
        assertEquals(invalid("TO_OBJECT(CT.A)"), refusal("SELECT TO_OBJECT(a) FROM ct"));
        assertEquals(invalid("TO_OBJECT(identity(CT.G))"), refusal("SELECT TO_OBJECT(g::VARCHAR) FROM ct"));
        assertEquals(invalid("TRY_CAST(CT.G)"), refusal("SELECT TRY_CAST(g AS OBJECT) FROM ct"));
        assertEquals(invalid("TRY_CAST('x')"), refusal("SELECT TRY_CAST('x' AS OBJECT)"));
        assertEquals(invalid("TRY_CAST(identity(CT.G))"), refusal("SELECT TRY_CAST(g::VARCHAR AS OBJECT) FROM ct"));
    }

    /** An OBJECT, a VARIANT holding one and a NULL convert. */
    @Test
    public void anObjectAVariantOrANullConverts() {
        createTable();
        assertEquals("{\"k\":1}", scalar("SELECT CAST(o AS OBJECT)::VARCHAR FROM ct"));
        assertEquals("{\"k\":2}", scalar("SELECT CAST(v AS OBJECT)::VARCHAR FROM ct"));
        assertEquals("{}", scalar("SELECT CAST(PARSE_JSON('{}') AS OBJECT)::VARCHAR"));
        assertEquals("NULL", scalar("SELECT CAST(NULL AS OBJECT)"));
        assertEquals("NULL", scalar("SELECT TO_OBJECT(NULL)"));
    }

    /** An unknown name is reported ahead of the conversion. */
    @Test
    public void anUnknownNameComesFirst() {
        createTable();
        assertEquals("SQL compilation error: error line 1 at position 26\ninvalid identifier 'MISSING'",
            refusal("SELECT CAST(g AS OBJECT), missing FROM ct"));
    }
}
