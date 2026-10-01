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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A conversion of a CONSTANT into an exact NUMBER is folded while the statement compiles, and
 * SYSTEM$TYPEOF tags it by the value it folds to: a cast, TRY_CAST or TO_NUMBER-family call over a text,
 * FLOAT or VARIANT constant, including one built by a concatenation, CONCAT, UPPER or TO_VARIANT. The
 * same conversion over a column, over a call the plan leaves standing (LENGTH, TRIM, PARSE_JSON, SQRT, a
 * conditional) or through a text cast keeps the declared width, and so does one that fails. Every cell is
 * live-verified.
 */
public class ConstantConversionTagTest extends BaseDatabaseTest {

    @BeforeEach
    public void createTable() {
        engine.execute("CREATE TABLE t (n NUMBER(5,2), s VARCHAR(10), f FLOAT, g NUMBER(1,0))");
        engine.execute("INSERT INTO t VALUES (1.5, '5', 1.5, 1), (2.5, '70000', 2.5, 2)");
    }

    /** Every row's first cell, joined by a bar. */
    private String answer(final String sql) {
        final StringBuilder answer = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (answer.length() > 0) {
                answer.append(" | ");
            }
            answer.append(row.getValue(0));
        }
        return answer.toString();
    }

    private void assertAnswers(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    @Test
    public void aConvertedConstantIsTaggedByItsValue() {
        assertAnswers(new String[][] {
            {"SELECT SYSTEM$TYPEOF(LENGTH('abc')::NUMBER)", "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(LENGTH('abc')::NUMBER(10,2))", "NUMBER(10,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(ROUND(1.5)::NUMBER(10,2))", "NUMBER(10,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF((1 + 1)::NUMBER(10,2))", "NUMBER(10,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(1.5::NUMBER(10,2))", "NUMBER(10,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(UNIFORM(1, 10, RANDOM())::NUMBER)", "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(TO_NUMBER('5'))", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(CAST('5' AS NUMBER(10,2)))", "NUMBER(10,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF('5'::INT)", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(TRY_CAST('5' AS NUMBER))", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(PARSE_JSON('5')::NUMBER)", "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(1.5::FLOAT::NUMBER(10,2))", "NUMBER(10,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(SQRT(4)::NUMBER)", "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(s::NUMBER) FROM t", "NUMBER(38,0)[SB16] | NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(f::NUMBER) FROM t", "NUMBER(38,0)[SB16] | NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF('70000'::NUMBER)", "NUMBER(38,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF('5'::NUMBER + n) FROM t", "NUMBER(38,2)[SB2] | NUMBER(38,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(1.5::FLOAT::NUMBER)", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(2.5::FLOAT::NUMBER)", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(TO_VARIANT(70000)::NUMBER)", "NUMBER(38,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(ABS(-1))", "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(NULL::NUMBER(10,2))", "NUMBER(10,2)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(''::NUMBER)", "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(TRY_CAST('x' AS NUMBER))", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(TO_DECIMAL('5.5', 10, 2))", "NUMBER(10,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(g::VARCHAR::NUMBER) FROM t", "NUMBER(38,0)[SB16] | NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(-'5'::NUMBER)", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(CURRENT_DATE()::VARCHAR::DATE)", "DATE[SB4]"},
            {"SELECT SYSTEM$TYPEOF(('1' || '2')::NUMBER(10,2) * 3)", "NUMBER(11,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(1e2::NUMBER)", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(TRUE::NUMBER)", "NUMBER(2,0)[SB1]"},
        });
    }

    @Test
    public void theFoldStopsWhereThePlanKeepsACall() {
        assertAnswers(new String[][] {
            {"SELECT SYSTEM$TYPEOF(TRY_TO_NUMBER('5'))", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(TO_NUMERIC('5'))", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(TO_NUMBER('5.5', 10, 1))", "NUMBER(10,1)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(TRY_TO_DECIMAL('x', 10, 2))", "NUMBER(10,2)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(TO_NUMBER(1.5::FLOAT))", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(TO_NUMBER(TO_VARIANT(5)))", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(TO_NUMBER(LENGTH('abc')))", "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(TO_NUMBER(s)) FROM t", "NUMBER(38,0)[SB16] | NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(('5' || LENGTH('ab'))::NUMBER)", "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(CONCAT('1','2')::NUMBER)", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(UPPER('5')::NUMBER)", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(TRIM(' 5 ')::NUMBER)", "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, '5', '6')::NUMBER)", "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(COALESCE('5', '6')::NUMBER)", "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF((1.5::FLOAT + 1)::NUMBER)", "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(TO_VARIANT('5')::NUMBER)", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF('5'::VARIANT::NUMBER)", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(TO_CHAR(5)::NUMBER)", "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF($$5$$::NUMBER)", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(TRY_CAST('5' AS NUMBER(10,2)))", "NUMBER(10,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF('5'::DECIMAL(10,2))", "NUMBER(10,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF('1e3'::NUMBER)", "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(' 5 '::NUMBER)", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF('127.5'::NUMBER)", "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF('1000000000000'::NUMBER)", "NUMBER(38,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(127.5::FLOAT::NUMBER)", "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(-127.5::FLOAT::NUMBER)", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(('1' || '2' || '3')::NUMBER)", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(1.5::FLOAT::VARCHAR::NUMBER(10,2))", "NUMBER(10,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF((2 * 1.5::FLOAT)::NUMBER)", "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(CAST(CAST('5' AS VARCHAR) AS NUMBER))", "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(''::NUMBER + 1)", "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF('x'::NUMBER)", "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(NULL::VARCHAR::NUMBER)", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(TO_VARIANT(1.5)::NUMBER(10,2))", "NUMBER(10,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(PARSE_JSON('5')::NUMBER + 1)", "NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF('5'::NUMBER) FROM t", "NUMBER(38,0)[SB1] | NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(TO_BOOLEAN('true')::NUMBER)", "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(ZEROIFNULL('5'::NUMBER))", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(ABS('5'::NUMBER))", "NUMBER(38,0)[SB1]"},
        });
    }
}
