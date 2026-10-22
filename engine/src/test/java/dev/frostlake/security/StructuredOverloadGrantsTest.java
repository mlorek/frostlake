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
import dev.frostlake.LiveSnowflake;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Privileges on overloads that differ by a structured parameter's shape alone: each overload keeps its own record,
 * named with its shape, so a grant reaches the overloads its signature names one by one and a DROP of one leaves the
 * others' privileges. Engine-only: the live account takes no GRANT from this suite.
 */
public class StructuredOverloadGrantsTest extends BaseDatabaseTest {

    private List<String> grantedFunctions(final String role) {
        final ResultSet rs = engine.executeQuery("SHOW GRANTS TO ROLE " + role);
        final List<String> names = new ArrayList<String>();
        for (int r = 0; r < rs.getRowCount(); r++) {
            final List<Object> row = rs.getRows().get(r).getValues();
            if ("FUNCTION".equals(row.get(2))) {
                names.add(row.get(1) + " " + row.get(3));
            }
        }
        return names;
    }

    @Test
    public void eachShapeKeepsItsOwnPrivileges() {
        assumeFalse(LiveSnowflake.enabled(), "the live account takes no GRANT from this suite");
        engine.execute("CREATE OR REPLACE FUNCTION f1(x OBJECT(a INT)) RETURNS INT AS '1'");
        engine.execute("CREATE OR REPLACE FUNCTION f1(x OBJECT(b INT)) RETURNS INT AS '2'");
        engine.execute("CREATE OR REPLACE FUNCTION f1(x OBJECT) RETURNS INT AS '3'");
        engine.execute("CREATE ROLE IF NOT EXISTS r_shape");
        engine.execute("GRANT USAGE ON FUNCTION f1(OBJECT) TO ROLE r_shape");
        assertEquals("[USAGE TEST_DB.TEST_SCHEMA.F1(OBJECT(a NUMBER)), USAGE TEST_DB.TEST_SCHEMA.F1(OBJECT(b NUMBER)), "
            + "USAGE TEST_DB.TEST_SCHEMA.F1(OBJECT)]", grantedFunctions("r_shape").toString());
        engine.execute("DROP FUNCTION f1(OBJECT(a INT))");
        assertEquals("[USAGE TEST_DB.TEST_SCHEMA.F1(OBJECT(b NUMBER)), USAGE TEST_DB.TEST_SCHEMA.F1(OBJECT)]",
            grantedFunctions("r_shape").toString());
        engine.execute("REVOKE USAGE ON FUNCTION f1(OBJECT) FROM ROLE r_shape");
        assertEquals("[]", grantedFunctions("r_shape").toString());
    }

    @Test
    public void arrayAndMapShapesKeepTheirOwnPrivileges() {
        assumeFalse(LiveSnowflake.enabled(), "the live account takes no GRANT from this suite");
        engine.execute("CREATE OR REPLACE FUNCTION g1(x ARRAY(INT)) RETURNS INT AS '1'");
        engine.execute("CREATE OR REPLACE FUNCTION g1(x ARRAY(VARCHAR)) RETURNS INT AS '2'");
        engine.execute("CREATE ROLE IF NOT EXISTS r_shape2");
        engine.execute("GRANT USAGE ON FUNCTION g1(ARRAY) TO ROLE r_shape2");
        assertEquals("[USAGE TEST_DB.TEST_SCHEMA.G1(ARRAY(NUMBER)), USAGE TEST_DB.TEST_SCHEMA.G1(ARRAY(VARCHAR))]",
            grantedFunctions("r_shape2").toString());
        engine.execute("DROP FUNCTION g1(ARRAY(INT))");
        assertEquals("[USAGE TEST_DB.TEST_SCHEMA.G1(ARRAY(VARCHAR))]", grantedFunctions("r_shape2").toString());
    }
}
