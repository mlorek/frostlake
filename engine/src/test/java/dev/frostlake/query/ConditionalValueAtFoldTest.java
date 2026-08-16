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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A conditional's VALUE is presented at the type its branches FOLD to, chosen branch or not: live
 * plans every branch as a CAST to the fold, so the unselected division in {@code COALESCE(n, 1/0)}
 * still widens the answer to NUMBER(14,6) and 1.00 comes back as 1.000000; a FLOAT fold hands back a
 * double; a text branch under a numeric fold is the number it spells — refused when it spells none
 * and CHOSEN, never read when unchosen; DECODE and NVL2 evaluate only the branch they return; NULLIF
 * presents at its own declared width. Live-verified cell by cell.
 */
public class ConditionalValueAtFoldTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE cv (n NUMBER(10,2), i NUMBER(5,0), f FLOAT, t VARCHAR(10),"
            + " x VARCHAR(10), e VARCHAR(10), n52 NUMBER(5,2), n2010 NUMBER(20,10), z NUMBER(10,2))");
        engine.execute("INSERT INTO cv VALUES (1.00, 1, 1.5, '1.5', 'abc', '', 1.25, 1.5, 0)");
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder();
            while (rs.next()) {
                for (int i = 0; i < rs.getColumns().size(); i++) {
                    all.append(i > 0 ? ", " : "").append(String.valueOf(rs.getValue(i)));
                }
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return "REFUSED " + String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String valueAndType(final String expression) {
        return answer("SELECT " + expression + ", SPLIT_PART(SYSTEM$TYPEOF(" + expression + "), '[', 1) FROM cv");
    }

    @Test
    public void theUnselectedBranchStillWidensTheFold() {
        assertEquals("1.000000, NUMBER(14,6)", valueAndType("COALESCE(n, 1/0)"));
        assertEquals("1.000000, NUMBER(14,6)", valueAndType("NVL(n, 1/0)"));
        assertEquals("1.000000, NUMBER(14,6)", valueAndType("IFNULL(n, 1/0)"));
        assertEquals("1.000000, NUMBER(7,6)", valueAndType("IFF(TRUE, 1, 1/0)"));
        assertEquals("1.000000, NUMBER(7,6)", valueAndType("CASE WHEN TRUE THEN 1 ELSE 1/0 END"));
        assertEquals("1.000000, NUMBER(14,6)", valueAndType("CASE i WHEN 1 THEN n ELSE 1/0 END"));
        assertEquals("1.000000, NUMBER(9,6)", valueAndType("IFF(n > 0, 1, 1/n)"));
        assertEquals("1.000000, NUMBER(14,6)", valueAndType("COALESCE(n, 1/3)"));
        assertEquals("1.000000, NUMBER(14,6)", valueAndType("COALESCE(n, i/2)"));
        assertEquals("1.000000, NUMBER(14,6)", valueAndType("COALESCE(n, i, 1/3)"));
        assertEquals("0.500000, NUMBER(14,6)", valueAndType("IFF(TRUE, 1/2, n)"));
        assertEquals("1.00000000, NUMBER(16,8)", valueAndType("IFF(TRUE, n, n/3)"));
        assertEquals("0.000000, NUMBER(14,6)", valueAndType("IFF(TRUE, z, 1/0)"));
        assertEquals("1.000000, NUMBER(11,6)", valueAndType("COALESCE(i, 1/3)"));
        assertEquals("1.0000000000, NUMBER(20,10)", valueAndType("COALESCE(n, n2010)"));
        assertEquals("1.000, NUMBER(11,3)", valueAndType("COALESCE(n, 1.555)"));
        assertEquals("0.333333, NUMBER(14,6)", valueAndType("LEAST(n, 1/3)"));
    }

    @Test
    public void aNarrowerBranchIsPresentedAtTheFoldToo() {
        assertEquals("1.00, NUMBER(10,2)", valueAndType("COALESCE(i, n)"));
        assertEquals("1.00, NUMBER(10,2)", valueAndType("COALESCE(n, i)"));
        assertEquals("1.00, NUMBER(10,2)", valueAndType("IFF(TRUE, i, n)"));
        assertEquals("1.00, NUMBER(10,2)", valueAndType("IFF(FALSE, i, n)"));
        assertEquals("1.25, NUMBER(10,2)", valueAndType("COALESCE(n52, n)"));
        assertEquals("1.00, NUMBER(10,2)", valueAndType("COALESCE(1, n)"));
        assertEquals("1.00, NUMBER(10,2)", valueAndType("COALESCE(n, 1.5)"));
        assertEquals("1.50, NUMBER(10,2)", valueAndType("IFF(TRUE, '1.5', n)"));
        assertEquals("1.00, NUMBER(10,2)", valueAndType("IFF(TRUE, n, '1.5')"));
        assertEquals("1.00, NUMBER(10,2)", valueAndType("GREATEST(n, i)"));
        assertEquals("1.00, NUMBER(10,2)", valueAndType("IFF(TRUE, n, NULL)"));
        assertEquals("1.00, NUMBER(10,2)", valueAndType("COALESCE(NULL, n)"));
    }

    @Test
    public void aFloatFoldHandsBackADouble() {
        assertEquals("1, FLOAT", answer("SELECT TO_VARCHAR(COALESCE(n, f)), SPLIT_PART(SYSTEM$TYPEOF(COALESCE(n, f)), '[', 1) FROM cv"));
        assertEquals("1.5, FLOAT", answer("SELECT TO_VARCHAR(COALESCE(f, n)), SPLIT_PART(SYSTEM$TYPEOF(COALESCE(f, n)), '[', 1) FROM cv"));
        assertEquals("1, FLOAT", answer("SELECT TO_VARCHAR(IFF(TRUE, n, f)), SPLIT_PART(SYSTEM$TYPEOF(IFF(TRUE, n, f)), '[', 1) FROM cv"));
        assertEquals("1, FLOAT", answer("SELECT TO_VARCHAR(IFF(TRUE, i, f)), SPLIT_PART(SYSTEM$TYPEOF(IFF(TRUE, i, f)), '[', 1) FROM cv"));
        assertEquals("1.25, FLOAT", answer("SELECT TO_VARCHAR(IFF(TRUE, n52, f)), SPLIT_PART(SYSTEM$TYPEOF(IFF(TRUE, n52, f)), '[', 1) FROM cv"));
        assertEquals("1, FLOAT", answer("SELECT TO_VARCHAR(COALESCE(i, f)), SPLIT_PART(SYSTEM$TYPEOF(COALESCE(i, f)), '[', 1) FROM cv"));
        assertEquals("1.5, FLOAT", answer("SELECT TO_VARCHAR(COALESCE(t, f)), SPLIT_PART(SYSTEM$TYPEOF(COALESCE(t, f)), '[', 1) FROM cv"));
        assertEquals("1.5, FLOAT", answer("SELECT TO_VARCHAR(GREATEST(n, f)), SPLIT_PART(SYSTEM$TYPEOF(GREATEST(n, f)), '[', 1) FROM cv"));
        assertEquals("1.5, FLOAT", answer("SELECT TO_VARCHAR(COALESCE(f, 1/0)), SPLIT_PART(SYSTEM$TYPEOF(COALESCE(f, 1/0)), '[', 1) FROM cv"));
    }

    @Test
    public void aTextBranchIsTheNumberItSpellsWhenChosen() {
        assertEquals("1.50000, NUMBER(18,5)", valueAndType("COALESCE(t, n)"));
        assertEquals("1.50000, NUMBER(18,5)", valueAndType("IFF(TRUE, t, n)"));
        assertEquals("1.00000, NUMBER(18,5)", valueAndType("COALESCE(n, t)"));
        assertEquals("1.00000, NUMBER(18,5)", valueAndType("IFF(TRUE, n, t)"));
        assertEquals("1.50000, NUMBER(18,5)", valueAndType("COALESCE(t, i)"));
        assertEquals("1.00000, NUMBER(18,5)", valueAndType("COALESCE(i, t)"));
        assertEquals("1.50000, NUMBER(18,5)", valueAndType("GREATEST(t, n)"));
        assertEquals("1.00000, NUMBER(18,5)", valueAndType("LEAST(n, t)"));
        assertEquals("1.50000, NUMBER(18,5)", valueAndType("DECODE(i, 2, n, t)"));
        assertEquals("REFUSED Numeric value 'abc' is not recognized", answer("SELECT COALESCE(x, n) FROM cv"));
        assertEquals("REFUSED Numeric value 'abc' is not recognized", answer("SELECT IFF(TRUE, x, n) FROM cv"));
        assertEquals("REFUSED Numeric value '' is not recognized", answer("SELECT COALESCE(e, n) FROM cv"));
        assertEquals("1.00000", answer("SELECT COALESCE(n, x) FROM cv"));
        assertEquals("1.00000", answer("SELECT IFF(FALSE, x, n) FROM cv"));
    }

    @Test
    public void decodeAndNvl2EvaluateOnlyTheBranchTheyReturn() {
        assertEquals("1.000000, NUMBER(14,6)", valueAndType("DECODE(i, 1, n, 1/0)"));
        assertEquals("1.000000, NUMBER(14,6)", valueAndType("DECODE(i, 2, 1/0, n)"));
        assertEquals("1.00", answer("SELECT DECODE(i, 1, n, 1/0, 2) FROM cv"));
        assertEquals("1.000000, NUMBER(14,6)", valueAndType("NVL2(NULL, 1/0, n)"));
        assertEquals("1.000000, NUMBER(14,6)", valueAndType("NVL2(n, n, 1/0)"));
        assertEquals("REFUSED Division by zero", answer("SELECT NVL2(n, 1/0, n) FROM cv"));
    }

    @Test
    public void nullifPresentsAtItsDeclaredWidth() {
        assertEquals("1.000000, NUMBER(14,6)", valueAndType("NULLIF(n, 1/3)"));
        assertEquals("1.5, VARCHAR(134217728)", valueAndType("NULLIF(t, n)"));
        assertEquals("1.00, NUMBER(10,2)", valueAndType("NULLIF(n, t)"));
        assertEquals("1.00, NUMBER(10,2)", valueAndType("NULLIF(n, f)"));
        assertEquals("null, NUMBER(10,2)", valueAndType("NULLIF(n, i)"));
        assertEquals("1.25, NUMBER(10,2)", valueAndType("NULLIF(n52, n)"));
    }
}
