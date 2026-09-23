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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A text constant folds into the conversion that reads it through two more constructs: a SQL function's text
 * parameter, whose body is then typed and tagged as if the constant were written there, and a FROM-less
 * scalar subquery over it. A text the plan does not hold as a constant keeps the declared width. Every cell is
 * live-verified.
 */
public class TextConstantFoldTagTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE tcf_t (s VARCHAR, n NUMBER(5,2))");
        engine.execute("INSERT INTO tcf_t VALUES ('5', 1.5), ('6', 2.5)");
        engine.execute("CREATE OR REPLACE FUNCTION tcf_num(a VARCHAR) RETURNS NUMBER AS 'a::NUMBER'");
        engine.execute("CREATE OR REPLACE FUNCTION tcf_add(a VARCHAR) RETURNS NUMBER AS 'a + 1'");
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

    @Test
    public void aTextParameterCarriesItsConstantIntoTheBody() {
        assertEquals("NUMBER(38,0)[SB1]", answer("SELECT SYSTEM$TYPEOF(tcf_num('5'))"));
        assertEquals("NUMBER(38,0)[SB1]", answer("SELECT SYSTEM$TYPEOF(tcf_num('5' || '0'))"));
        assertEquals("NUMBER(38,0)[SB1]", answer("SELECT SYSTEM$TYPEOF(tcf_num(UPPER('5')))"));
        assertEquals("NUMBER(38,0)[SB1]", answer("SELECT SYSTEM$TYPEOF(tcf_num(NULL))"));
        assertEquals("NUMBER(38,0)[SB4]", answer("SELECT SYSTEM$TYPEOF(tcf_num('123456'))"));
        assertEquals("NUMBER(38,0)[SB1]", answer("SELECT SYSTEM$TYPEOF(tcf_num((SELECT '5')))"));
        assertEquals("NUMBER(19,0)[SB1]", answer("SELECT SYSTEM$TYPEOF(tcf_add('5'))"));
        assertEquals("5", answer("SELECT tcf_num('5')"));
        assertEquals("6", answer("SELECT tcf_add('5')"));
    }

    @Test
    public void aTextTheBodyCannotFoldKeepsTheDeclaredWidth() {
        assertEquals("NUMBER(38,0)[SB16]", answer("SELECT SYSTEM$TYPEOF(tcf_num(TRIM(' 5 ')))"));
        assertEquals("NUMBER(38,0)[SB16] | NUMBER(38,0)[SB16]", answer("SELECT SYSTEM$TYPEOF(tcf_num(n::VARCHAR)) FROM tcf_t"));
    }

    @Test
    public void aFromlessScalarSubqueryOfAConstantFolds() {
        assertEquals("NUMBER(38,0)[SB1]", answer("SELECT SYSTEM$TYPEOF((SELECT '5')::NUMBER)"));
        assertEquals("NUMBER(38,0)[SB1]", answer("SELECT SYSTEM$TYPEOF((SELECT '5' || '0')::NUMBER)"));
        assertEquals("NUMBER(38,0)[SB1]", answer("SELECT SYSTEM$TYPEOF((SELECT UPPER('5'))::NUMBER)"));
        assertEquals("NUMBER(38,0)[SB4]", answer("SELECT SYSTEM$TYPEOF((SELECT '123456')::NUMBER)"));
        assertEquals("NUMBER(38,0)[SB1]", answer("SELECT SYSTEM$TYPEOF(TO_NUMBER((SELECT '5')))"));
        assertEquals("NUMBER(38,0)[SB1]", answer("SELECT SYSTEM$TYPEOF((SELECT '5')::NUMBER + 0)"));
        assertEquals("NUMBER(38,0)[SB1]", answer("SELECT SYSTEM$TYPEOF((SELECT 5.5::FLOAT)::NUMBER)"));
        assertEquals("NUMBER(38,0)[SB1]", answer("SELECT SYSTEM$TYPEOF((SELECT (SELECT '5'))::NUMBER)"));
        assertEquals("NUMBER(38,0)[SB1]", answer("SELECT SYSTEM$TYPEOF((SELECT TO_VARIANT(5))::NUMBER)"));
        assertEquals("5", answer("SELECT (SELECT '5')::NUMBER"));
    }

    @Test
    public void aSubqueryTheBodyCannotFoldKeepsTheDeclaredWidth() {
        assertEquals("NUMBER(38,0)[SB16]", answer("SELECT SYSTEM$TYPEOF((SELECT TRIM(' 5 '))::NUMBER)"));
        assertEquals("NUMBER(38,0)[SB16]", answer("SELECT SYSTEM$TYPEOF((SELECT s FROM tcf_t LIMIT 1)::NUMBER)"));
        assertEquals("NUMBER(38,0)[SB16]", answer("SELECT SYSTEM$TYPEOF((SELECT '5' FROM tcf_t LIMIT 1)::NUMBER)"));
    }
}
