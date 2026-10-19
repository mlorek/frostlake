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

import org.junit.jupiter.api.Test;

/**
 * A star argument's qualifier - OBJECT_CONSTRUCT(q.*), ARRAY_CONSTRUCT(q.*) - names an alias or a relation exactly,
 * as a star item's does: a quoted part names itself and an unquoted one its upper-cased self, so "LC".* and lc.*
 * reach no table "lc", and the refusal echoes the qualifier quoted where its name needs it (all live-verified).
 */
public class StarArgumentQualifierCaseTest extends MeasuredCellsTestSupport {

    @Test
    public void aStarArgumentsQualifierNamesARelationExactly() {
        engine.execute("CREATE OR REPLACE TABLE \"lc\" (a INT)");
        engine.execute("INSERT INTO \"lc\" VALUES (1)");
        assertAnswer("REFUSED SQL compilation error: | Object 'LC' does not exist or not authorized.",
            "SELECT \"LC\".* FROM \"lc\"");
        assertAnswer("REFUSED SQL compilation error: | Object 'LC' does not exist or not authorized.",
            "SELECT OBJECT_CONSTRUCT(\"LC\".*) FROM \"lc\"");
        assertAnswer("REFUSED SQL compilation error: | Object '\"q\"' does not exist or not authorized.",
            "SELECT \"q\".* FROM \"lc\"");
        assertAnswer("REFUSED SQL compilation error: | Object '\"q\"' does not exist or not authorized.",
            "SELECT OBJECT_CONSTRUCT(\"q\".*) FROM \"lc\"");
        assertAnswer("REFUSED SQL compilation error: | Object 'LC' does not exist or not authorized.",
            "SELECT ARRAY_CONSTRUCT(\"LC\".*) FROM \"lc\"");
        assertAnswer("1", "SELECT \"lc\".* FROM \"lc\"");
        assertAnswer("{\"A\":1}", "SELECT OBJECT_CONSTRUCT(\"lc\".*) FROM \"lc\"");
        assertAnswer("REFUSED SQL compilation error: | Object 'LC' does not exist or not authorized.",
            "SELECT lc.* FROM \"lc\"");
        assertAnswer("REFUSED SQL compilation error: | Object 'LC' does not exist or not authorized.",
            "SELECT OBJECT_CONSTRUCT(lc.*) FROM \"lc\"");
        assertAnswer("{\"A\":1}", "SELECT OBJECT_CONSTRUCT(x.*) FROM \"lc\" x");
        assertAnswer("{\"A\":1}", "SELECT OBJECT_CONSTRUCT(X.*) FROM \"lc\" x");
        assertAnswer("{\"A\":1}", "SELECT OBJECT_CONSTRUCT(\"X\".*) FROM \"lc\" x");
        assertAnswer("REFUSED SQL compilation error: | Object '\"x\"' does not exist or not authorized.",
            "SELECT OBJECT_CONSTRUCT(\"x\".*) FROM \"lc\" x");
    }
}
