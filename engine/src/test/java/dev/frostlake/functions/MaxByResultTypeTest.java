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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * MAX_BY and MIN_BY are typed as the value they answer, or as an ARRAY with a count: "The data type of the returned
 * value is the same as the data type of the input expression"; "If maximum_number_of_values_in_output_array is
 * specified, returns an ARRAY" (documented). They were typed as their nominal VARIANT, so the result columns read as
 * texts and a CTAS typed each column from its value: a DECFLOAT as NUMBER(38,0).
 */
public class MaxByResultTypeTest extends MeasuredCellsTestSupport {

    private void source() {
        engine.execute("CREATE TABLE mbr_src (n NUMBER(10,2), s VARCHAR(5), v VARIANT, d DECFLOAT, g INT)");
        engine.execute("INSERT INTO mbr_src SELECT 1.5, 'abc', PARSE_JSON('{\"a\":1}'), '-3e50', 1");
    }

    @Test
    public void eachIsTypedAsTheValueItAnswers() {
        source();
        assertShape("N:NUMBER(10,2), S:VARCHAR(5), V:VARIANT, M:VARCHAR(5), ARR:ARRAY",
            "SELECT MAX_BY(n, g) n, MAX_BY(s, g) s, MAX_BY(v, g) v, MIN_BY(s, g) m, MAX_BY(n, g, 2) arr FROM mbr_src");
    }

    @Test
    public void aTableBuiltOverThemKeepsTheValuesTypes() {
        source();
        engine.execute("CREATE TABLE mbr_m AS SELECT MAX_BY(n, g) n, MAX_BY(s, g) s, MAX_BY(v, g) v, MIN_BY(d, g) d "
            + "FROM mbr_src");
        assertShape("N:NUMBER(10,2), S:VARCHAR(5), V:VARIANT, D:DECFLOAT(38,0)", "SELECT n, s, v, d FROM mbr_m");
        assertAnswer("1", "SELECT v:a FROM mbr_m");
    }
}
