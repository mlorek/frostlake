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
package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A tag's properties: PROPAGATE and ON_CONFLICT on CREATE TAG and ALTER TAG … SET, the ALTER TAG … UNSET of each
 * property, CREATE OR ALTER TAG, and ALTER TAG … RENAME TO a qualified name, which moves the tag.
 */
public class TagPropertiesTest extends BaseDatabaseTest {

    private String show(final String tag, final String column) {
        final ResultSet rs = engine.executeQuery("SHOW TAGS IN SCHEMA test_db.test_schema");
        return cell(rs, soleRowWhere(rs, "name", tag), column);
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return refused.getMessage();
    }

    @Test
    public void propagationIsRecordedAndShown() {
        // A conflict value on a tag with allowed values must be one of them.
        assertEquals("Invalid on_conflict strategy: On Conflict value must be part of Allowed Values if set",
            refusal("CREATE TAG p1 ALLOWED_VALUES 'a', 'b' PROPAGATE = ON_DEPENDENCY ON_CONFLICT = 'mixed' "
                + "COMMENT = 'c'"));
        engine.executeQuery("CREATE TAG p1 ALLOWED_VALUES 'a', 'b' PROPAGATE = ON_DEPENDENCY ON_CONFLICT = 'b' "
            + "COMMENT = 'c'");
        assertEquals("ON_DEPENDENCY", show("P1", "propagate"));
        assertEquals("b", show("P1", "on_conflict"));
        engine.executeQuery("CREATE TAG p2");
        assertEquals("NONE", show("P2", "propagate"));
        assertNull(show("P2", "on_conflict"));
        assertTrue(refusal("CREATE TAG p3 PROPAGATE = SOMETIMES").contains("PROPAGATE"));
    }

    @Test
    public void alterTagSetsAndUnsetsEachProperty() {
        engine.executeQuery("CREATE TAG a1");
        // SET ALLOWED_VALUES stands alone: a property after its list is a syntax error.
        assertTrue(refusal("ALTER TAG a1 SET ALLOWED_VALUES 'x', 'y' COMMENT = 'set'").contains("syntax error"));
        engine.executeQuery("ALTER TAG a1 SET ALLOWED_VALUES 'x', 'y'");
        engine.executeQuery("ALTER TAG a1 SET PROPAGATE = ON_DATA_MOVEMENT ON_CONFLICT = ALLOWED_VALUES_SEQUENCE "
            + "COMMENT = 'set'");
        assertEquals("[\"x\",\"y\"]", show("A1", "allowed_values"));
        assertEquals("ON_DATA_MOVEMENT", show("A1", "propagate"));
        assertEquals("ALLOWED_VALUES_SEQUENCE", show("A1", "on_conflict"));
        assertEquals("set", show("A1", "comment"));
        engine.executeQuery("ALTER TAG a1 UNSET ON_CONFLICT");
        assertNull(show("A1", "on_conflict"));
        assertEquals("ON_DATA_MOVEMENT", show("A1", "propagate"));
        engine.executeQuery("ALTER TAG a1 UNSET PROPAGATE");
        assertEquals("NONE", show("A1", "propagate"));
        engine.executeQuery("ALTER TAG a1 UNSET COMMENT");
        assertEquals("", show("A1", "comment"));
        engine.executeQuery("ALTER TAG a1 UNSET ALLOWED_VALUES");
        assertNull(show("A1", "allowed_values"));
        engine.executeQuery("ALTER TAG a1 SET COMMENT = 'again'");
        assertEquals("again", show("A1", "comment"));
    }

    @Test
    public void createOrAlterTagCreatesThenReshapes() {
        assertEquals("Tag C1 successfully created.", String.valueOf(engine.executeQuery(
            "CREATE OR ALTER TAG c1 ALLOWED_VALUES 'a' COMMENT = 'first'").getRows().get(0).getValue(0)));
        engine.executeQuery("ALTER TAG c1 SET PROPAGATE = ON_DEPENDENCY");
        assertEquals("Statement executed successfully.", String.valueOf(engine.executeQuery(
            "CREATE OR ALTER TAG c1 ALLOWED_VALUES 'b', 'c'").getRows().get(0).getValue(0)));
        assertEquals("[\"b\",\"c\"]", show("C1", "allowed_values"));
        assertEquals("", show("C1", "comment"), "a property the statement leaves out is unset");
        assertEquals("NONE", show("C1", "propagate"), "the statement describes the whole tag, propagation included");
        engine.executeQuery("CREATE OR ALTER TAG c1 PROPAGATE = ON_DEPENDENCY ON_CONFLICT = 'z' COMMENT = 'x'");
        assertEquals("ON_DEPENDENCY", show("C1", "propagate"));
        assertEquals("z", show("C1", "on_conflict"));
        assertNull(show("C1", "allowed_values"));
    }

    @Test
    public void renameToAQualifiedNameMovesTheTag() {
        engine.executeQuery("CREATE TAG r1 COMMENT = 'moving'");
        engine.executeQuery("CREATE SCHEMA elsewhere");
        engine.executeQuery("ALTER TAG test_db.test_schema.r1 RENAME TO test_db.elsewhere.r2");
        final ResultSet moved = engine.executeQuery("SHOW TAGS IN SCHEMA test_db.elsewhere");
        assertEquals("moving", cell(moved, soleRowWhere(moved, "name", "R2"), "comment"));
        assertEquals(0, engine.executeQuery("SHOW TAGS LIKE 'R1' IN SCHEMA test_db.test_schema").getRows().size());
        engine.executeQuery("ALTER TAG test_db.elsewhere.r2 RENAME TO r3");
        assertEquals(1, engine.executeQuery("SHOW TAGS LIKE 'R3' IN SCHEMA test_db.elsewhere").getRows().size());
    }
}
