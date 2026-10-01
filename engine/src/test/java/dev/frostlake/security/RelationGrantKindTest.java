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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A grant on a relation belongs to the relation itself, whichever of TABLE or VIEW the statement names it with.
 * GRANT … ON VIEW t for a table t is the table's grant: SHOW GRANTS ON TABLE t and ON VIEW t both list it as a
 * TABLE grant, REVOKE … ON TABLE t takes it back, and SHOW GRANTS TO ROLE names it TABLE. A view named with
 * TABLE is granted as the view. USAGE on a relation is refused naming TABLE, whichever keyword and whatever
 * the relation. Every cell is live-verified.
 */
public class RelationGrantKindTest extends BaseDatabaseTest {

    private static final String ROLE = "RELATION_GRANT_KIND_ROLE";

    private static final String USAGE_REFUSAL = "Invalid object type 'TABLE' for privilege 'USAGE'.";

    @BeforeEach
    public void createObjects() {
        engine.execute("CREATE OR REPLACE DATABASE RELATION_GRANT_KIND_DB");
        engine.execute("CREATE OR REPLACE TABLE T (a INT)");
        engine.execute("CREATE OR REPLACE VIEW V AS SELECT a FROM T");
        engine.execute("CREATE OR REPLACE ROLE " + ROLE);
    }

    @AfterEach
    public void dropObjects() {
        engine.execute("DROP ROLE IF EXISTS " + ROLE);
        engine.execute("DROP DATABASE IF EXISTS RELATION_GRANT_KIND_DB");
    }

    /** The privilege, kind and name of each row the test role holds in a listing, sorted, a bar between rows. */
    private String held(final String sql) {
        return heldRows(sql, true);
    }

    /** The privilege and kind of each row the test role holds in a listing, sorted, a bar between rows. */
    private String heldKinds(final String sql) {
        return heldRows(sql, false);
    }

    private String heldRows(final String sql, final boolean withName) {
        final List<String> rows = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (!ROLE.equals(String.valueOf(row.getValue(5)))) {
                continue;
            }
            rows.add(row.getValue(1) + ", " + row.getValue(2) + (withName ? ", " + row.getValue(3) : ""));
        }
        Collections.sort(rows);
        return String.join(" | ", rows);
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), refused.getMessage());
    }

    @Test
    public void aTableNamedWithViewIsGrantedAsTheTable() {
        engine.execute("GRANT SELECT ON VIEW T TO ROLE " + ROLE);
        assertEquals("SELECT, TABLE, RELATION_GRANT_KIND_DB.PUBLIC.T", held("SHOW GRANTS ON TABLE T"));
        assertEquals("SELECT, TABLE, RELATION_GRANT_KIND_DB.PUBLIC.T", held("SHOW GRANTS ON VIEW T"));
        engine.execute("REVOKE SELECT ON TABLE T FROM ROLE " + ROLE);
        assertEquals("", held("SHOW GRANTS ON TABLE T"));

        engine.execute("GRANT INSERT ON VIEW T TO ROLE " + ROLE);
        engine.execute("GRANT REFERENCES ON TABLE T TO ROLE " + ROLE);
        assertEquals("INSERT, TABLE, RELATION_GRANT_KIND_DB.PUBLIC.T | REFERENCES, TABLE, RELATION_GRANT_KIND_DB.PUBLIC.T",
            held("SHOW GRANTS ON TABLE T"));
        engine.execute("REVOKE ALL ON VIEW T FROM ROLE " + ROLE);
        assertEquals("", held("SHOW GRANTS ON TABLE T"));
    }

    @Test
    public void aViewNamedWithTableIsGrantedAsTheView() {
        engine.execute("GRANT UPDATE ON TABLE V TO ROLE " + ROLE);
        assertEquals("UPDATE, VIEW, RELATION_GRANT_KIND_DB.PUBLIC.V", held("SHOW GRANTS ON VIEW V"));
        assertEquals("UPDATE, VIEW, RELATION_GRANT_KIND_DB.PUBLIC.V", held("SHOW GRANTS ON TABLE V"));
        engine.execute("REVOKE UPDATE ON VIEW V FROM ROLE " + ROLE);
        assertEquals("", held("SHOW GRANTS ON VIEW V"));
    }

    @Test
    public void theRolesListingNamesEachRelationsOwnKind() {
        engine.execute("GRANT SELECT ON VIEW T TO ROLE " + ROLE);
        engine.execute("GRANT INSERT ON TABLE V TO ROLE " + ROLE);
        assertEquals("INSERT, VIEW | SELECT, TABLE", heldKinds("SHOW GRANTS TO ROLE " + ROLE));
    }

    @Test
    public void usageOnARelationIsRefusedAsATable() {
        assertRefused("GRANT USAGE ON VIEW T TO ROLE " + ROLE, USAGE_REFUSAL);
        assertRefused("GRANT USAGE ON TABLE V TO ROLE " + ROLE, USAGE_REFUSAL);
        assertRefused("GRANT USAGE ON VIEW V TO ROLE " + ROLE, USAGE_REFUSAL);
        assertRefused("GRANT USAGE ON TABLE T TO ROLE " + ROLE, USAGE_REFUSAL);
    }
}
