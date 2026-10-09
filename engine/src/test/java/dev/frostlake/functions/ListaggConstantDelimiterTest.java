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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * LISTAGG's delimiter is a constant: a literal, a session variable, a bind or a call over them is evaluated, and
 * anything a row or the session decides is refused while the statement compiles.
 */
public class ListaggConstantDelimiterTest extends BaseDatabaseTest {

    private static final String NOT_CONSTANT =
        "SQL compilation error:\nargument 2 to function LISTAGG needs to be constant, found '";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5))");
        engine.execute("INSERT INTO rt VALUES (1, 1.5, 'x'), (2, 2.5, 'y')");
        engine.execute("CREATE OR REPLACE TABLE lt (a INT, b INT)");
    }

    @Override
    protected void teardownTest() {
        engine.execute("UNSET lcd_var");
    }

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    /** Each row's cells joined by bars, rows by semicolons. */
    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (int r = 0; r < rs.getRowCount(); r++) {
            out.append(r > 0 ? " ; " : "");
            for (int c = 0; c < rs.getColumnCount(); c++) {
                out.append(c > 0 ? "|" : "").append(rs.getRows().get(r).getValue(c));
            }
        }
        return out.toString();
    }

    private String joined(final String delimiter) {
        return rows("SELECT LISTAGG(n, " + delimiter + ") WITHIN GROUP (ORDER BY n) FROM rt");
    }

    @Test
    public void aConstantDelimiterIsEvaluated() {
        assertEquals("1,2", joined("','"));
        assertEquals("1xy2", joined("'x' || 'y'"));
        assertEquals("1X2", joined("UPPER('x')"));
        assertEquals("112", joined("1"));
        assertEquals("112", joined("1::VARCHAR"));
        assertEquals("12", joined("NULL"));
        engine.execute("SET lcd_var = '-'");
        assertEquals("1-2", joined("$lcd_var"));
        assertEquals("1+2", rows("EXECUTE IMMEDIATE $$ DECLARE d VARCHAR DEFAULT '+'; r VARCHAR; BEGIN SELECT"
            + " LISTAGG(n, :d) WITHIN GROUP (ORDER BY n) INTO :r FROM rt; RETURN r; END; $$"));
    }

    @Test
    public void aDelimiterARowDecidesIsRefused() {
        assertEquals(NOT_CONSTANT + "RT.G'", refusalOf("SELECT LISTAGG(n, g) FROM rt"));
        assertEquals(NOT_CONSTANT + "RT.F'", refusalOf("SELECT LISTAGG(n, f) FROM rt"));
        assertEquals(NOT_CONSTANT + "RT.G || '-''", refusalOf("SELECT LISTAGG(n, g || '-') FROM rt"));
        assertEquals(NOT_CONSTANT + "RT.G'", refusalOf("SELECT LISTAGG(n, g) WITHIN GROUP (ORDER BY n) FROM rt"));
        assertEquals(NOT_CONSTANT + "RT.G'", refusalOf("SELECT LISTAGG(DISTINCT n, g) FROM rt"));
        assertEquals(NOT_CONSTANT + "RT.G'", refusalOf("SELECT LISTAGG(n, g) OVER () FROM rt"));
        assertEquals(NOT_CONSTANT + "RT.G'", refusalOf("SELECT g, LISTAGG(n, g) FROM rt GROUP BY g"));
        assertEquals(NOT_CONSTANT + "RT.G'", refusalOf("SELECT LISTAGG(n, g) FROM rt QUALIFY 1 = 1"));
        // Over a table holding no row too.
        assertEquals(NOT_CONSTANT + "CAST(LT.B AS VARCHAR(134217728))'",
            refusalOf("SELECT LISTAGG(a, TO_VARCHAR(b)) FROM lt"));
    }

    @Test
    public void aDelimiterTheSessionOrARandomDrawDecidesIsRefused() {
        assertEquals(NOT_CONSTANT + "CURRENT_DATABASE()'", refusalOf("SELECT LISTAGG(n, CURRENT_DATABASE()) FROM rt"));
        assertEquals(NOT_CONSTANT + "CAST(RANDOM() AS VARCHAR(134217728))'",
            refusalOf("SELECT LISTAGG(n, RANDOM()::VARCHAR) FROM rt"));
    }

    @Test
    public void aNameThatDoesNotResolveComesFirst() {
        assertEquals("SQL compilation error: error line 1 at position 22\ninvalid identifier 'NOSUCH'",
            refusalOf("SELECT LISTAGG(n, g), nosuch FROM rt"));
        assertEquals("", rows("SELECT LISTAGG(a, ',') FROM lt"));
    }
}
