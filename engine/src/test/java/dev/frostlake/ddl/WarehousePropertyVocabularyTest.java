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

/**
 * The enumerated warehouse properties, each of which refuses an unknown value with its OWN sentence.
 *
 * <p>★ THREE PHRASINGS FOR THE SAME KIND OF MISTAKE, one per property — measured, not inferred:
 *
 * <pre>
 *   WAREHOUSE_SIZE   = 'HUGE'     invalid type of property 'HUGE' for 'WAREHOUSE_SIZE'
 *   SCALING_POLICY   = 'NOSUCH'   invalid value 'NOSUCH' for property 'SCALING_POLICY'
 *   WAREHOUSE_TYPE   = 'NOSUCH'   invalid property 'NOSUCH' for 'WAREHOUSE_TYPE'
 * </pre>
 *
 * They cannot be shared, and a family sentence would be wrong for two of the three.
 *
 * <p>★ SCALING_POLICY COULD NOT BE PARSED AT ALL in its quoted form, so
 * {@code SCALING_POLICY = 'ECONOMY'} — the spelling a real script writes — was refused as a syntax
 * error. That is the direction that breaks working SQL, and it is why this is more than a wording gap.
 *
 * <p>★ BOTH PROPERTIES TAKE A BARE WORD OR A QUOTED STRING, IN ANY CASE, and both normalise: SHOW
 * WAREHOUSES reports {@code ECONOMY} and {@code STANDARD} upper-cased whatever was written, exactly as
 * the size does.
 *
 * <p>★ ALTER SHARES CREATE'S SENTENCE for both, so the vocabulary is one rule reached from two
 * statements rather than a per-statement check.
 */
public class WarehousePropertyVocabularyTest extends BaseDatabaseTest {

    /** Create a warehouse with the clause and report the outcome, then drop it. */
    private String create(final String clause) {
        final String outcome;
        try {
            engine.execute("CREATE OR REPLACE WAREHOUSE fl_wpv_wh " + clause);
            outcome = "ACCEPTED";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        } finally {
            try {
                engine.execute("DROP WAREHOUSE IF EXISTS fl_wpv_wh");
            } catch (final RuntimeException ignored) {
                // A refused CREATE leaves nothing to drop; the outcome above is what is under test.
            }
        }
        return outcome;
    }

    /** Create with the clause, read one SHOW column back, then drop. */
    private String shown(final String clause, final String column) {
        try {
            engine.execute("CREATE OR REPLACE WAREHOUSE fl_wpv_wh2 " + clause);
            final ResultSet rs = engine.executeQuery("SHOW WAREHOUSES LIKE 'FL_WPV_WH2'");
            String value = "NO ROW";
            while (rs.next()) {
                value = String.valueOf(rs.getValue(column));
            }
            return value;
        } finally {
            try {
                engine.execute("DROP WAREHOUSE IF EXISTS fl_wpv_wh2");
            } catch (final RuntimeException ignored) {
                // Cleanup only.
            }
        }
    }

    /** ★ THE QUOTED SCALING_POLICY, which used to be a syntax error. */
    @Test
    public void scalingPolicyTakesItsQuotedForm() {
        assertEquals("ACCEPTED", create("SCALING_POLICY = 'STANDARD'"));
        assertEquals("ACCEPTED", create("SCALING_POLICY = 'ECONOMY'"));
        assertEquals("ACCEPTED", create("SCALING_POLICY = 'economy'"));
        assertEquals("ACCEPTED", create("SCALING_POLICY = ECONOMY"), "the bare word still parses");
    }

    /** ★ SCALING_POLICY's own sentence: "invalid value … for property …". */
    @Test
    public void scalingPolicyRefusesAValueOutsideItsVocabulary() {
        assertEquals("SQL compilation error:|invalid value 'NOSUCH' for property 'SCALING_POLICY'",
            create("SCALING_POLICY = 'NOSUCH'"));
    }

    /** WAREHOUSE_TYPE's vocabulary, bare and quoted. */
    @Test
    public void warehouseTypeTakesItsVocabulary() {
        assertEquals("ACCEPTED", create("WAREHOUSE_TYPE = 'STANDARD'"));
        assertEquals("ACCEPTED", create("WAREHOUSE_TYPE = 'SNOWPARK-OPTIMIZED'"));
        assertEquals("ACCEPTED", create("WAREHOUSE_TYPE = 'standard'"));
        assertEquals("ACCEPTED", create("WAREHOUSE_TYPE = STANDARD"));
    }

    /** ★ WAREHOUSE_TYPE's own sentence, which is neither of the other two. */
    @Test
    public void warehouseTypeRefusesAValueOutsideItsVocabulary() {
        assertEquals("SQL compilation error:|invalid property 'NOSUCH' for 'WAREHOUSE_TYPE'",
            create("WAREHOUSE_TYPE = 'NOSUCH'"));
    }

    /** ★ SHOW normalises both to upper case, whatever case was written. */
    @Test
    public void showReportsBothUpperCased() {
        assertEquals("ECONOMY", shown("SCALING_POLICY = 'economy'", "scaling_policy"));
        assertEquals("STANDARD", shown("WAREHOUSE_TYPE = 'standard'", "type"));
    }

    /** ★ ALTER shares CREATE's sentence for both properties. */
    @Test
    public void thealterFormSharesTheSameSentences() {
        engine.execute("CREATE OR REPLACE WAREHOUSE fl_wpv_wh3");
        try {
            String outcome;
            try {
                engine.execute("ALTER WAREHOUSE fl_wpv_wh3 SET SCALING_POLICY = 'NOSUCH'");
                outcome = "ACCEPTED";
            } catch (final RuntimeException refused) {
                outcome = String.valueOf(refused.getMessage()).replace('\n', '|');
            }
            assertEquals("SQL compilation error:|invalid value 'NOSUCH' for property 'SCALING_POLICY'",
                outcome);
            try {
                engine.execute("ALTER WAREHOUSE fl_wpv_wh3 SET WAREHOUSE_TYPE = 'NOSUCH'");
                outcome = "ACCEPTED";
            } catch (final RuntimeException refused) {
                outcome = String.valueOf(refused.getMessage()).replace('\n', '|');
            }
            assertEquals("SQL compilation error:|invalid property 'NOSUCH' for 'WAREHOUSE_TYPE'",
                outcome);
            engine.execute("ALTER WAREHOUSE fl_wpv_wh3 SET SCALING_POLICY = 'ECONOMY'");
        } finally {
            engine.execute("DROP WAREHOUSE IF EXISTS fl_wpv_wh3");
        }
    }

    /** WAREHOUSE_SIZE's sentence, which #423 landed, must not move. */
    @Test
    public void thesizeSentenceIsUntouched() {
        assertEquals("SQL compilation error:|invalid type of property 'HUGE' for 'WAREHOUSE_SIZE'",
            create("WAREHOUSE_SIZE = 'HUGE'"));
        assertEquals("ACCEPTED", create("WAREHOUSE_SIZE = 'XSMALL'"));
    }
}
