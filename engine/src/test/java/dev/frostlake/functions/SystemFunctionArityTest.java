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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SYSTEM$ family's ARGUMENT COUNTS. These functions live outside the registry, so the registry's
 * declared bounds never saw them and a wrong count fell through to the implementation —
 * {@code SYSTEM$TYPEOF()} answered "NULL[LOB]". Live refuses them in the ordinary arity vocabulary,
 * at the call's own position:
 *
 * <pre>
 *   SYSTEM$TYPEOF()               not enough arguments for function [SYSTEM$TYPEOF()], expected 1, got 0
 *   SYSTEM$TYPEOF(1, 2)           too many arguments for function [SYSTEM$TYPEOF(1, 2)] expected 1, got 2
 *   SYSTEM$WAIT(0, 'SECONDS', 3)  … expected 2, got 3
 *   SYSTEM$GET_TAG('t')           … expected 3, got 1
 * </pre>
 *
 * <p>★ ONLY MEASURED BOUNDS ARE ENFORCED — {@link SystemFunctionArity} holds them, and a name with no
 * entry stays unchecked rather than guessed at. SYSTEM$ALLOWLIST(1) is live's account-privilege
 * parameter sentence, not an arity, and is deliberately not reproduced.
 *
 * <p>★ SYSTEM$STREAM_HAS_DATA HAS ITS OWN GRAMMAR SLOT, which used to demand exactly one argument and
 * made the wrong counts SYNTAX errors. The slot now takes any count and the wrong ones reach the same
 * arity sentence as the rest of the family.
 */
public class SystemFunctionArityTest extends BaseDatabaseTest {

    /** One statement's refusal, or its first value. */
    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            rs.next();
            return "ACCEPTED " + String.valueOf(rs.getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String tooFew(final String echo, final int expected, final int got) {
        return "SQL compilation error: error line 1 at position 7|not enough arguments for function ["
            + echo + "], expected " + expected + ", got " + got;
    }

    private static String tooMany(final String echo, final int expected, final int got) {
        return "SQL compilation error: error line 1 at position 7|too many arguments for function ["
            + echo + "] expected " + expected + ", got " + got;
    }

    @Test
    void typeofRefusesBothDirections() {
        assertEquals(tooFew("SYSTEM$TYPEOF()", 1, 0), outcome("SELECT SYSTEM$TYPEOF()"));
        assertEquals(tooMany("SYSTEM$TYPEOF(1, 2)", 1, 2), outcome("SELECT SYSTEM$TYPEOF(1, 2)"));
    }

    @Test
    void theFamilySpeaksTheSameVocabulary() {
        assertEquals(tooFew("SYSTEM$WAIT()", 1, 0), outcome("SELECT SYSTEM$WAIT()"));
        assertEquals(tooMany("SYSTEM$WAIT(0, 'SECONDS', 3)", 2, 3),
            outcome("SELECT SYSTEM$WAIT(0, 'SECONDS', 3)"));
        assertEquals(tooFew("SYSTEM$CANCEL_QUERY()", 1, 0), outcome("SELECT SYSTEM$CANCEL_QUERY()"));
        assertEquals(tooFew("SYSTEM$PIPE_STATUS()", 1, 0), outcome("SELECT SYSTEM$PIPE_STATUS()"));
        assertEquals(tooFew("SYSTEM$CLUSTERING_DEPTH()", 1, 0),
            outcome("SELECT SYSTEM$CLUSTERING_DEPTH()"));
        assertEquals(tooFew("SYSTEM$ABORT_SESSION()", 1, 0), outcome("SELECT SYSTEM$ABORT_SESSION()"));
        assertEquals(tooFew("SYSTEM$LAST_CHANGE_COMMIT_TIME()", 1, 0),
            outcome("SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME()"));
        assertEquals(tooFew("SYSTEM$GET_TAG('t')", 3, 1), outcome("SELECT SYSTEM$GET_TAG('t')"));
    }

    @Test
    void streamHasDataLeftItsSyntaxErrorBehind() {
        assertEquals(tooFew("SYSTEM$STREAM_HAS_DATA()", 1, 0),
            outcome("SELECT SYSTEM$STREAM_HAS_DATA()"));
        assertEquals(tooMany("SYSTEM$STREAM_HAS_DATA('a', 'b')", 1, 2),
            outcome("SELECT SYSTEM$STREAM_HAS_DATA('a', 'b')"));
    }

    @Test
    void theLegalCountsStillAnswer() {
        // The VALUE for a legal call belongs to its own surfaces (the declared-type read and the
        // storage tag), so only the acceptance is pinned here.
        assertTrue(outcome("SELECT SYSTEM$TYPEOF(1)").startsWith("ACCEPTED"));
        // The two engines word the wait's answer differently ("waited" against "waited 0 seconds"),
        // which is a value surface — only the acceptance is this test's business.
        assertTrue(outcome("SELECT SYSTEM$WAIT(0)").startsWith("ACCEPTED waited"));
        // A one-argument call over a stream that does not exist is that function's OWN refusal — the
        // valid-stream-name sentence, already live-verified — and must not become an arity one.
        assertTrue(outcome("SELECT SYSTEM$STREAM_HAS_DATA('nosuchstream')")
            .contains("must be a valid stream name"));
    }
}
