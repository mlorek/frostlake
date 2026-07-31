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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OperatorFunctionNames} is the one family feeding {@code SHOW FUNCTIONS} that cannot be
 * load-bearing for dispatch — the grammar implements these, not a name lookup — so its membership is a
 * curated claim: Snowflake gives the name to an operator, and Frostlake evaluates that operator. This
 * test re-checks the second half for <em>every</em> entry, so a name cannot be added to the listing
 * without the operator behind it actually working.
 *
 * <p>The first half was checked against a real account on every name below appears in the 926
 * its {@code SHOW BUILTIN FUNCTIONS} returns. The names deliberately left out are checked too —
 * {@code DIV} (listed there, but {@code SELECT DIV(10, 3)} answers "Unsupported feature 'DIV'" and
 * Frostlake cannot parse {@code 10 DIV 3}) and the interval MULTIPLY / DIVIDE entries.
 */
public class OperatorFunctionNamesTest extends BaseDatabaseTest {

    /** For each declared name, a statement exercising the operator form Frostlake implements it as. */
    private Map<String, String> operatorForms() {
        final Map<String, String> forms = new LinkedHashMap<String, String>();
        forms.put("+", "SELECT 1 + 2");
        forms.put("-", "SELECT 3 - 1");
        forms.put("*", "SELECT 2 * 3");
        forms.put("/", "SELECT 6 / 2");
        forms.put("%", "SELECT 7 % 3");
        forms.put("=", "SELECT 1 = 1");
        forms.put("!=", "SELECT 1 != 2");
        forms.put("<>", "SELECT 1 <> 2");
        forms.put("<", "SELECT 1 < 2");
        forms.put("<=", "SELECT 1 <= 2");
        forms.put(">", "SELECT 2 > 1");
        forms.put(">=", "SELECT 2 >= 1");
        forms.put("||", "SELECT 'a' || 'b'");
        forms.put("[]", "SELECT ARRAY_CONSTRUCT(1, 2)[0]");
        forms.put(":", "SELECT PARSE_JSON('{\"k\": 1}'):k");
        forms.put("IS NULL", "SELECT NULL IS NULL");
        forms.put("IS NOT NULL", "SELECT 1 IS NOT NULL");
        forms.put("AND", "SELECT TRUE AND FALSE");
        forms.put("OR", "SELECT TRUE OR FALSE");
        forms.put("NOT", "SELECT NOT TRUE");
        forms.put("BETWEEN", "SELECT 2 BETWEEN 1 AND 3");
        forms.put("IN", "SELECT 1 IN (1, 2)");
        forms.put("CASE", "SELECT CASE WHEN TRUE THEN 1 ELSE 2 END");
        forms.put("REGEXP", "SELECT 'a' REGEXP 'a'");
        forms.put("LIKE_ANY", "SELECT 'abc' LIKE ANY ('a%', 'b%')");
        forms.put("LIKE_ALL", "SELECT 'abc' LIKE ALL ('a%', '%c')");
        forms.put("ILIKE_ANY", "SELECT 'abc' ILIKE ANY ('A%')");
        forms.put("COUNT(*)", "SELECT COUNT(*) FROM operator_rows");
        forms.put("OBJECT_CONSTRUCT(*)", "SELECT OBJECT_CONSTRUCT(*) FROM operator_rows");
        forms.put("OBJECT_CONSTRUCT_KEEP_NULL(*)", "SELECT OBJECT_CONSTRUCT_KEEP_NULL(*) FROM operator_rows");
        forms.put("INTERVAL DAY TIME PLUS", "SELECT '2024-01-01'::TIMESTAMP_NTZ + INTERVAL '1 day'");
        forms.put("INTERVAL DAY TIME MINUS", "SELECT '2024-01-01'::TIMESTAMP_NTZ - INTERVAL '1 day'");
        forms.put("INTERVAL YEAR MONTH PLUS", "SELECT '2024-01-01'::DATE + INTERVAL '1 year'");
        forms.put("INTERVAL YEAR MONTH MINUS", "SELECT '2024-01-01'::DATE - INTERVAL '1 year'");
        return forms;
    }

    @Test
    public void everyDeclaredNameHasAWorkingOperatorForm() {
        final Map<String, String> forms = operatorForms();
        assertEquals(new TreeSet<String>(forms.keySet()), new TreeSet<String>(OperatorFunctionNames.names()),
            "every declared operator name needs a statement here, and vice versa");

        engine.execute("CREATE TABLE operator_rows (a INTEGER, b VARCHAR)");
        engine.execute("INSERT INTO operator_rows VALUES (1, 'x')");
        for (final Map.Entry<String, String> form : forms.entrySet()) {
            assertEquals(1, engine.executeQuery(form.getValue()).getRowCount(),
                "the operator form behind the listed name " + form.getKey() + " must evaluate: "
                    + form.getValue());
        }
    }

    @Test
    public void namesFrostlakeCannotEvaluateAreNotDeclared() {
        final Set<String> declared = OperatorFunctionNames.names();
        // Snowflake lists DIV, but neither engine can invoke it and Frostlake cannot even parse the
        // operator form, so listing it would advertise something that does not work.
        assertTrue(!declared.contains("DIV"), "DIV is not implemented in any form");
        // Interval scaling: Frostlake parses <datetime> +/- INTERVAL but not INTERVAL * n / INTERVAL / n.
        assertTrue(!declared.contains("INTERVAL DAY TIME MULTIPLY"), "interval scaling is not implemented");
        assertTrue(!declared.contains("INTERVAL DAY TIME DIVIDE"), "interval scaling is not implemented");
        assertTrue(!declared.contains("INTERVAL YEAR MONTH MULTIPLY"), "interval scaling is not implemented");
        assertTrue(!declared.contains("INTERVAL YEAR MONTH DIVIDE"), "interval scaling is not implemented");
        // ILIKE ALL is rejected by Snowflake itself ("Unknown function ILIKE_ALL") and by this grammar.
        assertTrue(!declared.contains("ILIKE_ALL"), "ILIKE ALL is not Snowflake syntax");

        Assumptions.assumeFalse(isLiveSnowflake(), "asserts Frostlake's grammar, not the account's");
        assertEquals(null, evaluated("SELECT 10 DIV 3"), "10 DIV 3 must stay a syntax error");
        assertEquals(null, evaluated("SELECT INTERVAL '1 day' * 2"), "interval scaling must stay unparsed");
    }

    /** The statement's first value, or null if it does not run at all. */
    private Object evaluated(final String sql) {
        try {
            return engine.executeQuery(sql).getRows().get(0).getValue(0);
        } catch (final RuntimeException e) {
            return null;
        }
    }
}
