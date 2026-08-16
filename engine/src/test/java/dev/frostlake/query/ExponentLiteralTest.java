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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An exponent literal written without a decimal point — {@code 1e0}, {@code 1E+3}, {@code 1e-3} — is a
 * number wherever a number is allowed. The lexer used to require the point, so {@code 1e0} split into
 * the integer 1 and the identifier {@code e0}: a syntax error in every position except a select item,
 * where the identifier read as an ALIAS and the query quietly returned the wrong column name.
 *
 * <p>The exponent is folded in before the type is taken, so these are FIXED-point numbers and not
 * approximate ones — {@code 1e0} is NUMBER(1,0), {@code 1e20} is NUMBER(21,0) and {@code 1e-3} is
 * NUMBER(4,3). The value prints in plain notation at either extreme: {@code 1e20} is
 * 100000000000000000000, never {@code 1E+20}.
 */
public class ExponentLiteralTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ex (n NUMBER(10,2), f FLOAT, i INT)");
        engine.execute("INSERT INTO ex VALUES (1.5, 2.5, 3)");
    }

    /** The one-column answer to a query, as the engine renders it. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        while (rs.next()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            out.append(String.valueOf(rs.getValue(0)));
        }
        return out.toString();
    }

    /** The declared type of an expression, with the parameters a NUMBER carries. */
    private String typeOf(final String expr) {
        final DataType type = engine.executeQuery("SELECT " + expr + " AS x FROM ex")
            .getColumns().get(0).getDataType();
        if (type instanceof NumericType && "NUMBER".equalsIgnoreCase(type.getName())) {
            return "NUMBER(" + ((NumericType) type).getPrecision() + ","
                + ((NumericType) type).getScale() + ")";
        }
        return type == null ? "null" : type.getName();
    }

    /** The name given to the first result column. */
    private String nameOf(final String sql) {
        return engine.executeQuery(sql).getColumns().get(0).getName();
    }

    /** An exponent literal is fixed-point, and its precision counts the digits it expands to. */
    @Test
    public void anExponentLiteralIsAFixedPointNumber() {
        assertEquals("NUMBER(1,0)", typeOf("1e0"));
        assertEquals("NUMBER(2,1)", typeOf("1.5e0"));
        assertEquals("NUMBER(4,3)", typeOf("1e-3"));
        assertEquals("NUMBER(4,3)", typeOf("1.5E-2"));
        assertEquals("NUMBER(21,0)", typeOf("1e20"), "the expansion's 21 digits, not the written 4");
        assertEquals("NUMBER(21,0)", typeOf("1.5e20"));
    }

    /** And it prints expanded, at either end of the exponent's range. */
    @Test
    public void theValuePrintsInPlainNotation() {
        assertEquals("1", answer("SELECT TO_VARCHAR(1e0) FROM ex"));
        assertEquals("0.001", answer("SELECT TO_VARCHAR(1e-3) FROM ex"));
        assertEquals("1000", answer("SELECT TO_VARCHAR(1E+3) FROM ex"));
        assertEquals("100000000000000000000", answer("SELECT TO_VARCHAR(1e20) FROM ex"));
        assertEquals("150000000000000000000", answer("SELECT TO_VARCHAR(1.5e20) FROM ex"));
    }

    /** The argument position that used to refuse it — the whole point of the token. */
    @Test
    public void itReadsAsAFunctionArgument() {
        assertEquals("1.50", answer("SELECT TO_VARCHAR(COALESCE(n, 1e0)) FROM ex"));
        assertEquals("2.5", answer("SELECT TO_VARCHAR(COALESCE(f, 1e0)) FROM ex"));
        assertEquals("1.50", answer("SELECT TO_VARCHAR(GREATEST(n, 1e0)) FROM ex"));
        assertEquals("1.50", answer("SELECT TO_VARCHAR(COALESCE(n, 1E0)) FROM ex"));
        assertEquals("1.50", answer("SELECT TO_VARCHAR(COALESCE(n, 1e+3)) FROM ex"));
        assertEquals("1.50", answer("SELECT TO_VARCHAR(COALESCE(n, -1e0)) FROM ex"));
        assertEquals("1.50", answer("SELECT TO_VARCHAR(COALESCE(n, 1e20)) FROM ex"));
        assertEquals("1.6", answer("SELECT TO_VARCHAR(ABS(ROUND(1.55e0, 1))) FROM ex"),
            "two calls deep");
        assertEquals("NUMBER(10,2)", typeOf("COALESCE(n, 1e0)"));
        assertEquals("NUMBER(11,3)", typeOf("COALESCE(n, 1e-3)"), "the literal's scale widens the fold");
    }

    /** Every other clause takes it too — it is a number, not a select-list special case. */
    @Test
    public void everyClauseTakesIt() {
        assertEquals("3", answer("SELECT i FROM ex WHERE n = 1.5e0"));
        assertEquals("1", answer("SELECT TO_VARCHAR(CASE WHEN i = 3 THEN 1e0 ELSE 2e0 END) FROM ex"));
        assertEquals("3", answer("SELECT i FROM ex WHERE i IN (3e0, 4e0)"));
        assertEquals("4", answer("SELECT TO_VARCHAR(i + 1e0) FROM ex"));
        assertEquals("9", answer("SELECT TO_VARCHAR((1e0 + 2e0) * 3) FROM ex"));
        assertEquals("1", answer("SELECT COUNT(*) FROM ex GROUP BY i + 1e0"));
        assertEquals("3", answer("SELECT i FROM ex ORDER BY i + 1e0"));
        assertEquals("1", answer("SELECT COUNT(*) FROM ex HAVING COUNT(*) > 0e0"));
        assertEquals("1.00", answer("SELECT TO_VARCHAR(CAST(1e0 AS NUMBER(5,2))) FROM ex"));
        assertEquals("1", answer("SELECT 1e0 AS x FROM ex"), "an alias may follow it");
    }

    /** DML writes it, and a column default holds it. */
    @Test
    public void dmlAndDefaultsTakeItToo() {
        engine.execute("CREATE OR REPLACE TABLE exi (a NUMBER(10,2))");
        engine.execute("INSERT INTO exi VALUES (1e0)");
        engine.execute("INSERT INTO exi SELECT 2e0");
        assertEquals("1.00 | 2.00", answer("SELECT TO_VARCHAR(a) FROM exi ORDER BY a"));
        engine.execute("UPDATE exi SET a = 5e0 WHERE a = 1e0");
        assertEquals("2.00 | 5.00", answer("SELECT TO_VARCHAR(a) FROM exi ORDER BY a"));

        engine.execute("CREATE OR REPLACE TABLE exd (a NUMBER(10,2) DEFAULT 1e0)");
        engine.execute("INSERT INTO exd (a) VALUES (DEFAULT)");
        assertEquals("1.00", answer("SELECT TO_VARCHAR(a) FROM exd"));
    }

    /**
     * The naming is what the old split gave away: an unaliased item is named by its source text
     * upper-cased, so the literal names itself. Written with a SPACE it really is an alias, and that
     * reading has to survive.
     */
    @Test
    public void theColumnIsNamedAfterTheLiteral() {
        assertEquals("1E0", nameOf("SELECT 1e0 FROM ex"));
        assertEquals("1.5E0", nameOf("SELECT 1.5e0 FROM ex"));
        assertEquals("1E+3", nameOf("SELECT 1e+3 FROM ex"));
        assertEquals("E0", nameOf("SELECT 1 e0 FROM ex"), "a space makes it an alias again");
    }
}
