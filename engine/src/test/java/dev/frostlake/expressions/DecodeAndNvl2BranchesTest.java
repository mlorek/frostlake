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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DECODE and NVL2 are CONDITIONALS, and their result type comes from their branches like any other's.
 * Frostlake reported the registry's nominal VARIANT for both, because neither was recognised as one —
 * and neither could simply join the list, since their arguments are not all branches.
 *
 * <ul>
 *   <li>{@code NVL2(test, ifNotNull, ifNull)} — the first argument is TESTED, not returned. Live proves
 *       it: {@code NVL2(s100, b4, b100)} declares BINARY(100), so the VARCHAR test contributes
 *       nothing.</li>
 *   <li>{@code DECODE(expr, search1, result1, …, [default])} — the RESULTS are every second argument
 *       from the third, and the trailing default exists only when the argument count is EVEN. Live
 *       ignores the expression and the searches the same way.</li>
 * </ul>
 */
public class DecodeAndNvl2BranchesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE dn (b4 BINARY(4), b100 BINARY(100), s100 VARCHAR(100),"
            + " n NUMBER)");
        engine.execute("INSERT INTO dn SELECT TO_BINARY('41424344'), TO_BINARY('4142'), 'abc', 1");
    }

    private String declaredType(final String expression) {
        engine.execute("CREATE OR REPLACE VIEW dn_v AS SELECT " + expression + " AS c FROM dn");
        final ResultSet rs = engine.executeQuery("SHOW COLUMNS IN TABLE test_db.test_schema.dn_v");
        rs.next();
        return String.valueOf(rs.getValue("data_type"));
    }

    /** Both take the widest branch, exactly as IFF and COALESCE do. */
    @Test
    public void bothTakeTheirWidestBranch() {
        assertEquals("""
            {"type":"BINARY","length":100,"byteLength":100,"nullable":true,"fixed":true}""",
            declaredType("NVL2(b4, b4, b100)"));
        assertEquals("""
            {"type":"BINARY","length":100,"byteLength":100,"nullable":true,"fixed":true}""",
            declaredType("DECODE(n, 1, b4, b100)"));
    }

    /** NVL2's first argument is tested, not returned — its type must not reach the result. */
    @Test
    public void nvl2IgnoresItsTestArgument() {
        assertEquals("""
            {"type":"BINARY","length":100,"byteLength":100,"nullable":true,"fixed":true}""",
            declaredType("NVL2(s100, b4, b100)"));
    }

    /** DECODE ignores its expression AND its search values, whatever family they are. */
    @Test
    public void decodeIgnoresItsExpressionAndSearches() {
        assertEquals("""
            {"type":"BINARY","length":100,"byteLength":100,"nullable":true,"fixed":true}""",
            declaredType("DECODE(s100, 'x', b4, b100)"));
    }

    /** With an ODD argument count there is no default, and the single result stands alone. */
    @Test
    public void decodeWithoutADefaultTakesItsResultsAlone() {
        assertEquals("""
            {"type":"BINARY","length":4,"byteLength":4,"nullable":true,"fixed":true}""",
            declaredType("DECODE(n, 1, b4)"));
        assertEquals("""
            {"type":"BINARY","length":100,"byteLength":100,"nullable":true,"fixed":true}""",
            declaredType("DECODE(n, 1, b4, 2, b100)"));
    }

    /** And a mixed pair is refused, in the same words the other conditionals use. */
    @Test
    public void aMixedPairIsRefused() {
        for (final String expression
                : new String[]{"NVL2(b4, b4, s100)", "DECODE(n, 1, b4, s100)"}) {
            String message = "accepted";
            try {
                engine.execute("CREATE OR REPLACE VIEW dn_bad AS SELECT " + expression
                    + " AS c FROM dn");
            } catch (final RuntimeException refused) {
                message = refused.getMessage().replace('\n', ' ');
            }
            assertTrue(message.contains("Can not convert parameter 'DN.S100' of type [VARCHAR(100)]"
                + " into expected type [BINARY(4)]"), expression + " gave: " + message);
        }
    }

    /** Neither reports the registry's nominal VARIANT any more, which is what they used to answer. */
    @Test
    public void neitherIsReportedAsVariant() {
        assertTrue(!declaredType("NVL2(b4, b4, b100)").contains("VARIANT"));
        assertTrue(!declaredType("DECODE(n, 1, b4, b100)").contains("VARIANT"));
    }
}
