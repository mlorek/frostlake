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

package dev.frostlake.security;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The masking-policy DDL surface a table reaches it through: the MODIFY spelling of the column
 * attachment, and the existence check on DROP.
 *
 * <pre>
 *   ALTER TABLE rt MODIFY COLUMN g SET MASKING POLICY nosuchpolicy
 *   DROP MASKING POLICY nosuchpolicy
 *       Masking policy 'TEST_DB.TEST_SCHEMA.NOSUCHPOLICY' does not exist or not authorized.
 * </pre>
 *
 * <p>★ MODIFY AND ALTER ARE ONE ACTION. Live runs and refuses the two spellings identically, so the
 * grammar carries them as one alternative and the handler dispatches on either word.
 *
 * <p>★ THE DROP NAMES THE POLICY FULLY QUALIFIED AND UPPER-CASED, with a full stop — the
 * "&lt;Kind&gt; '&lt;NAME&gt;' does not exist or not authorized." family the sibling policies already
 * speak. IF EXISTS forgives it, and the row-access spelling behaves the same.
 */
public class MaskingPolicyDdlSurfaceTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE rt (g VARCHAR(10))");
        engine.execute("CREATE OR REPLACE MASKING POLICY realmask AS (val VARCHAR)"
            + " RETURNS VARCHAR -> '***'");
    }

    /** One statement's refusal, or "ACCEPTED". */
    private String outcome(final String sql) {
        try {
            engine.execute(sql);
            return "ACCEPTED";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static final String MISSING =
        "SQL compilation error:|Masking policy 'TEST_DB.TEST_SCHEMA.NOSUCHPOLICY' does not exist"
            + " or not authorized.";

    @Test
    void theModifySpellingIsTheAlterAction() {
        assertEquals(MISSING,
            outcome("ALTER TABLE rt MODIFY COLUMN g SET MASKING POLICY nosuchpolicy"));
        assertEquals(MISSING,
            outcome("ALTER TABLE rt ALTER COLUMN g SET MASKING POLICY nosuchpolicy"));
        assertEquals("ACCEPTED", outcome("ALTER TABLE rt MODIFY COLUMN g SET MASKING POLICY realmask"));
        assertEquals("ACCEPTED", outcome("ALTER TABLE rt MODIFY COLUMN g UNSET MASKING POLICY"));
    }

    @Test
    void droppingAMissingPolicyRefusesWithTheQualifiedName() {
        assertEquals(MISSING, outcome("DROP MASKING POLICY nosuchpolicy"));
        assertEquals("ACCEPTED", outcome("DROP MASKING POLICY IF EXISTS nosuchpolicy"));
        assertEquals("ACCEPTED", outcome("DROP MASKING POLICY realmask"));
    }

    @Test
    void theRowAccessSpellingBehavesTheSame() {
        assertEquals("SQL compilation error:|Row access policy 'TEST_DB.TEST_SCHEMA.NOSUCHRAP'"
                + " does not exist or not authorized.",
            outcome("DROP ROW ACCESS POLICY nosuchrap"));
        assertEquals("ACCEPTED", outcome("DROP ROW ACCESS POLICY IF EXISTS nosuchrap"));
    }
}
