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

package dev.frostlake.types;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A unary plus is typed and tagged like a computation, not passed through. Over an exact NUMBER(p,s) it declares
 * NUMBER(min(38, s + max(p - s, 2)), s) — two integer digits at least, the scale kept — so {@code +1} is NUMBER(2,0),
 * {@code +1.5} NUMBER(3,1) and {@code +99} still NUMBER(2,0). Its storage tag keeps the operand's value only where the
 * plan holds that operand as a literal value — a literal, a signed one, a FROM-less subquery of one, a conversion
 * folded from a text constant, a NULL — and is the declared width everywhere else: over a column, a cast between
 * exact numbers, arithmetic or a function. Every cell is live-verified.
 */
public class UnaryPlusTypeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (a NUMBER(1,0), b NUMBER(3,2), c NUMBER(2,2), d NUMBER(38,37),"
            + " g NUMBER(37,37), h NUMBER(2,0), n NUMBER(5,2), i INT)");
        engine.execute("INSERT INTO t VALUES (1, 1.25, 0.25, 1.5, 0.5, 11, 1.5, 1),"
            + " (2, 2.25, 0.75, 2.5, 0.25, 12, 2.5, 2)");
    }

    /** The first row's first cell. */
    private String answer(final String sql) {
        for (final Row row : engine.executeQuery(sql).getRows()) {
            return String.valueOf(row.getValue(0));
        }
        return "no row";
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    /** The declared types of a result's columns, as NUMBER(p,s), in order. */
    private String columnTypes(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> types = new ArrayList<>();
        for (final ResultSetColumn column : rs.getColumns()) {
            final DataType type = column.getDataType();
            types.add(type instanceof NumericType
                ? type.getName() + "(" + ((NumericType) type).getPrecision() + "," + ((NumericType) type).getScale() + ")"
                : String.valueOf(type));
        }
        return String.join(", ", types);
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        return String.valueOf(refused.getMessage());
    }

    @Test
    public void aLiteralGainsIntegerDigitsUpToTwo() {
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(+1)", "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(+0)", "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(+99)", "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(+100)", "NUMBER(3,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(+1.5)", "NUMBER(3,1)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(+0.5)", "NUMBER(3,1)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(+.05)", "NUMBER(4,2)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(+9.99)", "NUMBER(4,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(+12.5)", "NUMBER(3,1)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(+123.45)", "NUMBER(5,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(+(1))", "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(+(+1))", "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(+(+(+1)))", "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(+(-1))", "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(-(+1))", "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(-1)", "NUMBER(1,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(+NULL)", "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(+(NULL))", "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(+1 + 1)", "NUMBER(3,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(+1 * 5)", "NUMBER(3,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(+0.1234567890123456789012345678901234567)", "NUMBER(38,37)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(+1.234567890123456789012345678901234567)", "NUMBER(38,36)[SB16]"},
        });
    }

    @Test
    public void aColumnGainsTheSameDigitsAndIsTaggedByItsWidth() {
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(+a) FROM t", "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(+b) FROM t", "NUMBER(4,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(+c) FROM t", "NUMBER(4,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(+d) FROM t", "NUMBER(38,37)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(+g) FROM t", "NUMBER(38,37)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(+h) FROM t", "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(+n) FROM t", "NUMBER(5,2)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(+(n)) FROM t", "NUMBER(5,2)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(+i) FROM t", "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(+(n + 1)) FROM t", "NUMBER(6,2)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(+ABS(n)) FROM t", "NUMBER(5,2)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(+n + 0) FROM t", "NUMBER(6,2)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(+x) FROM (SELECT 17::NUMBER(4,0) AS x)", "NUMBER(4,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(-n) FROM t", "NUMBER(5,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(n + 0) FROM t", "NUMBER(6,2)[SB2]"},
        });
    }

    @Test
    public void onlyAValueHeldAsALiteralKeepsItsInterval() {
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(+(-123.45))", "NUMBER(5,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(+(+123.45))", "NUMBER(5,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(+(SELECT 123.45))", "NUMBER(5,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(+'123.45'::NUMBER(5,2))", "NUMBER(5,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(+TO_NUMBER('123.45', 5, 2))", "NUMBER(5,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(+ZEROIFNULL(123.45))", "NUMBER(5,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(+CAST('12345' AS INT))", "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(+NULL::INT)", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(+(NULL + 1))", "NUMBER(19,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(+(123.45::NUMBER(5,2)))", "NUMBER(5,2)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(+TO_DECIMAL(123.45, 5, 2))", "NUMBER(5,2)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(+CAST(1 AS INT))", "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(+1::NUMBER(3,0))", "NUMBER(3,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(+(1.5 + 1.5))", "NUMBER(3,1)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(+(123.45 + 0))", "NUMBER(6,2)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(+(SELECT 123.45 + 0))", "NUMBER(6,2)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(+ABS(123.45))", "NUMBER(5,2)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(+IFF(TRUE, 123.45, 1))", "NUMBER(5,2)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(+COALESCE(123.45, 1))", "NUMBER(5,2)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(+LEAST(123.45, 200))", "NUMBER(5,2)[SB4]"},
        });
    }

    @Test
    public void otherFamiliesKeepTheirRules() {
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(+1.5::FLOAT)", "FLOAT[DOUBLE]"},
            {"SELECT SYSTEM$TYPEOF(+'5')", "FLOAT[DOUBLE]"},
            {"SELECT SYSTEM$TYPEOF(+TO_VARIANT(5))", "FLOAT[DOUBLE]"},
        });
        assertTrue(refusal("SELECT SYSTEM$TYPEOF(+TRUE)")
            .contains("Invalid argument types for function 'UNARY PLUS': (BOOLEAN)"));
        assertTrue(refusal("SELECT SYSTEM$TYPEOF(+CURRENT_DATE())")
            .contains("Invalid argument types for function 'UNARY PLUS': (DATE)"));
    }

    @Test
    public void theResultColumnAndATableCarryTheWidenedType() {
        assertEquals("NUMBER(2,0), NUMBER(3,1), NUMBER(2,0), NUMBER(5,2)",
            columnTypes("SELECT +1 AS a, +1.5 AS b, +(+1) AS e, +n AS g FROM t"));
        engine.execute("CREATE TABLE cu AS SELECT +a AS a1, +b AS b1, +c AS c1, +g AS g1, +0.5 AS f1, +NULL AS n1,"
            + " +1 AS o1 FROM t");
        assertEquals("A1:NUMBER:2:0 B1:NUMBER:4:2 C1:NUMBER:4:2 G1:NUMBER:38:37 F1:NUMBER:3:1 N1:NUMBER:2:0"
                + " O1:NUMBER:2:0",
            answer("SELECT LISTAGG(column_name || ':' || data_type || ':' || numeric_precision || ':' || numeric_scale,"
                + " ' ') WITHIN GROUP (ORDER BY ordinal_position) FROM information_schema.columns"
                + " WHERE table_name = 'CU'"));
    }

    @Test
    public void aWidenedLiteralStillFitsANarrowerColumn() {
        engine.execute("CREATE TABLE n1 (c NUMBER(1,0))");
        engine.execute("INSERT INTO n1 VALUES (+5)");
        engine.execute("INSERT INTO n1 SELECT +5");
        engine.execute("INSERT INTO n1 VALUES (+(1 + 1))");
        assertEquals("2", answer("SELECT MIN(c) FROM n1"));
        assertEquals("5", answer("SELECT MAX(c) FROM n1"));
    }
}
