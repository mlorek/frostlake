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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TO_VARCHAR is a registered synonym of TO_CHAR (previously it resolved to "unknown function").
 * Format-string handling for both is a separate, still-pending item.
 */
public class ToVarcharTest extends BaseDatabaseTest {

    private String str(final String sql) {
        final Object v = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return v == null ? null : v.toString();
    }

    @Test
    public void numberToString() {
        assertEquals("42", str("SELECT TO_VARCHAR(42)"));
    }

    @Test
    public void behavesLikeToChar() {
        assertEquals(str("SELECT TO_CHAR('hello')"), str("SELECT TO_VARCHAR('hello')"));
    }
}
