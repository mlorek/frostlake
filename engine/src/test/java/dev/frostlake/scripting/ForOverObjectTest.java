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

package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A FOR over a value loops over an OBJECT once per key, the variable holding NULL each time, and over an ARRAY's
 * elements; either way the variable is a VARIANT, {@code SYSTEM$TYPEOF(r)} answering VARIANT[LOB] (live-verified).
 */
public class ForOverObjectTest extends MeasuredCellsTestSupport {

    @Test
    public void anObjectIsLoopedOverOncePerKey() {
        assertCells(new String[][] {
            {"DECLARE o OBJECT DEFAULT OBJECT_CONSTRUCT('b', 1, 'a', 2); x VARCHAR DEFAULT ''; BEGIN FOR r IN o "
                + "DO x := x || r::VARCHAR || ','; END FOR; RETURN x; END;",
                "null"},
            {"DECLARE o OBJECT DEFAULT OBJECT_CONSTRUCT('b', 1, 'a', 2); x VARCHAR DEFAULT ''; BEGIN FOR r IN o "
                + "DO x := x || TO_VARCHAR(r) || ','; END FOR; RETURN x; END;",
                "null"},
            {"DECLARE o OBJECT DEFAULT OBJECT_CONSTRUCT('b', 1, 'a', 2); x VARCHAR DEFAULT ''; BEGIN FOR r IN o "
                + "DO x := x || 'i'; END FOR; RETURN x; END;",
                "ii"},
            {"DECLARE o OBJECT DEFAULT OBJECT_CONSTRUCT('b', 1, 'a', 2); x VARCHAR DEFAULT ''; BEGIN FOR r IN o "
                + "DO x := SYSTEM$TYPEOF(r); END FOR; RETURN x; END;",
                "VARIANT[LOB]"},
            {"DECLARE o OBJECT DEFAULT OBJECT_CONSTRUCT('b', 1, 'a', 2); x VARCHAR DEFAULT ''; BEGIN FOR r IN o "
                + "DO x := x || r || ','; END FOR; RETURN x; END;",
                "null"},
            {"DECLARE x VARCHAR DEFAULT ''; BEGIN FOR r IN (OBJECT_CONSTRUCT('b', 1, 'a', 2)) DO x := x || 'i'; "
                + "END FOR; RETURN x; END;",
                "ii"},
            {"DECLARE x VARCHAR DEFAULT ''; BEGIN FOR r IN (OBJECT_CONSTRUCT()) DO x := x || 'i'; END FOR; "
                + "RETURN x; END;",
                ""},
            {"DECLARE x VARCHAR DEFAULT ''; BEGIN FOR r IN (ARRAY_CONSTRUCT('p', 'q')) DO x := "
                + "SYSTEM$TYPEOF(r); END FOR; RETURN x; END;",
                "VARIANT[LOB]"},
            {"DECLARE x VARCHAR DEFAULT ''; BEGIN FOR r IN (ARRAY_CONSTRUCT(1, 2)) DO x := x || r::VARCHAR; END "
                + "FOR; RETURN x; END;",
                "12"},
            {"DECLARE x VARCHAR DEFAULT ''; BEGIN FOR r IN (ARRAY_CONSTRUCT(1, 2)) DO x := SYSTEM$TYPEOF(r); "
                + "END FOR; RETURN x; END;",
                "VARIANT[LOB]"},
            {"DECLARE x VARCHAR DEFAULT ''; BEGIN FOR r IN (ARRAY_CONSTRUCT(OBJECT_CONSTRUCT('k', 1))) DO x := "
                + "x || r::VARCHAR; END FOR; RETURN x; END;",
                "{\"k\":1}"},
            {"DECLARE x VARCHAR DEFAULT ''; BEGIN FOR r IN (PARSE_JSON('{\"b\": 1, \"a\": 2}')) DO x := x || "
                + "'i'; END FOR; RETURN x; END;",
                "ii"},
        });
    }
}
