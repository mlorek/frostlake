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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tuple (row-constructor) IN semantics, which differ by right-hand shape. The tuple-ROW list form
 * {@code (a, b) IN ((1, 2), (3, 4))} applies full row-value three-valued logic — a NULL pair makes
 * a candidate row UNKNOWN unless another pair definitely mismatches. The SUBQUERY form is
 * two-valued — NULL never matches and never yields UNKNOWN. The FLAT list {@code (a, b) IN (1, 2)}
 * and a wrong-width row are compile-time type refusals.
 */
public class TupleInListTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    private long rows(final String sql) {
        return engine.executeQuery(sql).getRowCount();
    }

    @Test
    public void tupleListMatchIsTrue() {
        assertEquals(Boolean.TRUE, scalar("SELECT ((1,2) IN ((1,2),(3,4))) AS r"));
        assertEquals(1, rows("SELECT 1 WHERE (1,2) IN ((3,NULL),(1,2))"));
    }

    @Test
    public void tupleListMissIsFalse() {
        assertEquals(Boolean.FALSE, scalar("SELECT ((1,2) IN ((3,4))) AS r"));
    }

    @Test
    public void tupleListNullPairIsUnknown() {
        // (1,2) vs (1,NULL): the first pair matches, the second is NULL -> the row is UNKNOWN, and
        // with no TRUE row the whole IN (and its negation) is NULL.
        assertNull(scalar("SELECT ((1,2) IN ((1,NULL))) AS r"));
        assertNull(scalar("SELECT ((1,2) NOT IN ((1,NULL),(3,4))) AS r"));
        assertNull(scalar("SELECT ((NULL,2) IN ((1,2))) AS r"));
    }

    @Test
    public void tupleListDefiniteMismatchBeatsNullPair() {
        // (1,2) vs (3,NULL): 1<>3 makes the row definitely FALSE despite the NULL, so NOT IN is TRUE.
        assertEquals(Boolean.TRUE, scalar("SELECT ((1,2) NOT IN ((3,NULL),(5,6))) AS r"));
    }

    @Test
    public void tupleSubqueryIsTwoValued() {
        // The subquery form never yields UNKNOWN: a NULL on either side simply never matches.
        assertEquals(Boolean.FALSE, scalar("SELECT ((1,2) IN (SELECT 1, NULL)) AS r"));
        assertEquals(Boolean.FALSE, scalar("SELECT ((NULL,1) IN (SELECT 1, 1)) AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT ((1,2) IN (SELECT 1, 2)) AS r"));
        assertEquals(1, rows("SELECT 1 WHERE (1,2) NOT IN (SELECT 1, NULL)"));
        assertEquals(1, rows("SELECT 1 WHERE (NULL,1) NOT IN (SELECT 1, 1)"));
        assertEquals(0, rows("SELECT 1 WHERE (1,2) NOT IN (SELECT 1, 2)"));
    }

    @Test
    public void flatListIsATypeError() {
        // (1,2) IN (1,2,3,4) compares a ROW against scalars: refused, naming the ROW shape.
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT ((1,2) IN (1,2,3,4)) AS r");
            }
        });
        assertTrue(e.getMessage().contains("Invalid argument types for function 'IN'"),
            "unexpected message: " + e.getMessage());
    }

    @Test
    public void wrongWidthRowIsAConversionError() {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT ((1,2) IN ((1,2,3))) AS r");
            }
        });
        assertTrue(e.getMessage().contains("Can not convert parameter 'ROW(1, 2, 3)'"),
            "unexpected message: " + e.getMessage());
    }

    @Test
    public void scalarLeftWithTupleMemberIsRefused() {
        // 1 IN ((1,2)) mixes a scalar subject with a ROW member; both engines refuse it.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT (1 IN ((1,2))) AS r");
            }
        });
    }
}
