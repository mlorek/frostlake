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
 * A tag's ON_CONFLICT rule is held to the tag it would leave: it needs PROPAGATE, the allowed-values sequence needs
 * allowed values, and a value must be one of the allowed values when the tag has some. A refused statement leaves
 * the catalog as it was. PROPAGATE, ON_CONFLICT and COMMENT follow the allowed values in any order, each once.
 */
public class TagConflictRuleTest extends BaseDatabaseTest {

    private static final String NEEDS_PROPAGATE =
        "Invalid on_conflict strategy: On Conflict can only be set when the PROPAGATE property is set";
    private static final String NEEDS_VALUES = "Invalid on_conflict strategy: On conflict as allowed_values_sequence "
        + "requires allowed values to be added to the Tag";
    private static final String NOT_ALLOWED =
        "Invalid on_conflict strategy: On Conflict value must be part of Allowed Values if set";
    private static final String SEQUENCE_DROP = "Invalid on_conflict strategy: Cannot drop allowed values with "
        + "on_conflict strategy = allowed_values_sequence as it requires allowed values to added for the tag";
    private static final String TWICE = "duplicate property '";

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private String show(final String name, final String column) {
        final ResultSet rs = engine.executeQuery("SHOW TAGS IN SCHEMA test_db.test_schema");
        return cell(rs, soleRowWhere(rs, "name", name), column);
    }

    private int tagsNamed(final String name) {
        return engine.executeQuery("SHOW TAGS LIKE '" + name + "' IN SCHEMA test_db.test_schema").getRowCount();
    }

    @Test
    public void aCreateRefusesARuleTheTagWouldNotTake() {
        assertTrue(refusal("CREATE TAG tcr_1 ON_CONFLICT = 'x'").contains(NEEDS_PROPAGATE));
        assertTrue(refusal("CREATE TAG tcr_1 COMMENT = 'c' ON_CONFLICT = 'y'").contains(NEEDS_PROPAGATE));
        assertTrue(refusal("CREATE TAG tcr_1 PROPAGATE = ON_DATA_MOVEMENT ON_CONFLICT = ALLOWED_VALUES_SEQUENCE")
            .contains(NEEDS_VALUES));
        assertTrue(refusal("CREATE TAG tcr_1 ALLOWED_VALUES 'a', 'b' PROPAGATE = ON_DEPENDENCY ON_CONFLICT = 'mixed'")
            .contains(NOT_ALLOWED));
        assertTrue(refusal("CREATE TAG tcr_1 PROPAGATE = BOGUS").contains("invalid value 'BOGUS' for property "
            + "'PROPAGATE'"));
        assertEquals(0, tagsNamed("TCR_1"), "a refused CREATE TAG leaves no tag");
        engine.executeQuery("CREATE TAG tcr_1 PROPAGATE = ON_DATA_MOVEMENT ON_CONFLICT = 'zz'");
        assertEquals("zz", show("TCR_1", "on_conflict"));
    }

    @Test
    public void anAlterRefusesARuleTheTagWouldNotTake() {
        engine.executeQuery("CREATE TAG tcr_2 ALLOWED_VALUES 'a', 'b' PROPAGATE = ON_DEPENDENCY"
            + " ON_CONFLICT = ALLOWED_VALUES_SEQUENCE");
        assertTrue(refusal("ALTER TAG tcr_2 SET ON_CONFLICT = 'q'").contains(NOT_ALLOWED));
        assertTrue(refusal("ALTER TAG tcr_2 SET PROPAGATE = ON_DATA_MOVEMENT ON_CONFLICT = 'w'").contains(NOT_ALLOWED));
        assertEquals("ON_DEPENDENCY", show("TCR_2", "propagate"), "a refused SET changes nothing");
        assertTrue(refusal("ALTER TAG tcr_2 UNSET ALLOWED_VALUES").contains(NEEDS_VALUES));
        engine.executeQuery("ALTER TAG tcr_2 UNSET ON_CONFLICT");
        engine.executeQuery("ALTER TAG tcr_2 UNSET PROPAGATE");
        assertEquals("NONE", show("TCR_2", "propagate"));
        assertTrue(refusal("ALTER TAG tcr_2 SET ON_CONFLICT = 'a'").contains(NEEDS_PROPAGATE));

        engine.executeQuery("CREATE TAG tcr_3 PROPAGATE = ON_DEPENDENCY ON_CONFLICT = 'free'");
        engine.executeQuery("ALTER TAG tcr_3 SET ON_CONFLICT = 'other'");
        assertTrue(refusal("ALTER TAG tcr_3 SET ALLOWED_VALUES 'k'").contains(NOT_ALLOWED));
        assertEquals("other", show("TCR_3", "on_conflict"));
    }

    @Test
    public void aPropertyWrittenTwiceIsRefusedBeforeAnythingElse() {
        // A second ON_CONFLICT is a duplicate whether the first stands beside PROPAGATE or apart from it, and the
        // refusal comes before the value of any property is judged.
        assertTrue(refusal("CREATE TAG tcr_4 PROPAGATE = ON_DEPENDENCY ON_CONFLICT = 'a' ON_CONFLICT = 'b'")
            .contains(TWICE + "ON_CONFLICT';"));
        assertTrue(refusal("CREATE TAG tcr_4 PROPAGATE = ON_DEPENDENCY ON_CONFLICT = 'a' COMMENT = 'c'"
            + " ON_CONFLICT = 'b'").contains(TWICE + "ON_CONFLICT';"));
        assertTrue(refusal("CREATE TAG tcr_4 ON_CONFLICT = 'a' PROPAGATE = ON_DEPENDENCY ON_CONFLICT = 'b'")
            .contains(TWICE + "ON_CONFLICT';"));
        assertTrue(refusal("CREATE TAG tcr_4 PROPAGATE = BOGUS ON_CONFLICT = 'a' ON_CONFLICT = 'b'")
            .contains(TWICE + "ON_CONFLICT';"));
        assertTrue(refusal("CREATE TAG tcr_4 PROPAGATE = ON_DEPENDENCY ON_CONFLICT = 'a' PROPAGATE = ON_DATA_MOVEMENT")
            .contains(TWICE + "PROPAGATE';"));
        assertTrue(refusal("CREATE TAG tcr_4 COMMENT = 'a' COMMENT = 'b'").contains(TWICE + "COMMENT';"));
        assertTrue(refusal("CREATE OR ALTER TAG tcr_4 PROPAGATE = ON_DEPENDENCY ON_CONFLICT = 'a' ON_CONFLICT = 'b'")
            .contains(TWICE + "ON_CONFLICT';"));
        assertEquals(0, tagsNamed("TCR_4"), "a refused CREATE TAG leaves no tag");

        engine.executeQuery("CREATE TAG tcr_4 PROPAGATE = ON_DEPENDENCY");
        assertTrue(refusal("CREATE TAG IF NOT EXISTS tcr_4 ON_CONFLICT = 'a' ON_CONFLICT = 'b'")
            .contains(TWICE + "ON_CONFLICT';"));
        assertTrue(refusal("ALTER TAG tcr_4 SET ON_CONFLICT = 'x' ON_CONFLICT = 'y'")
            .contains(TWICE + "ON_CONFLICT';"));
        assertTrue(refusal("ALTER TAG tcr_4 SET ON_CONFLICT = 'x1' PROPAGATE = ON_DATA_MOVEMENT ON_CONFLICT = 'x2'")
            .contains(TWICE + "ON_CONFLICT';"));
        assertTrue(refusal("ALTER TAG tcr_4 SET PROPAGATE = ON_DEPENDENCY PROPAGATE = ON_DATA_MOVEMENT")
            .contains(TWICE + "PROPAGATE';"));
        assertTrue(refusal("ALTER TAG tcr_4 SET COMMENT = 'e' COMMENT = 'f'").contains(TWICE + "COMMENT';"));
        assertNull(show("TCR_4", "on_conflict"), "a refused SET changes nothing");
        // Before the tag is looked up, and whatever IF EXISTS forgives.
        assertTrue(refusal("ALTER TAG tcr_nosuch SET ON_CONFLICT = 'x' ON_CONFLICT = 'y'")
            .contains(TWICE + "ON_CONFLICT';"));
        assertTrue(refusal("ALTER TAG IF EXISTS tcr_nosuch SET ON_CONFLICT = 'x' ON_CONFLICT = 'y'")
            .contains(TWICE + "ON_CONFLICT';"));
    }

    @Test
    public void thePropertiesAfterTheAllowedValuesComeInAnyOrder() {
        engine.executeQuery("CREATE TAG tcr_5 ON_CONFLICT = 'a' COMMENT = 'c' PROPAGATE = ON_DEPENDENCY");
        assertEquals("a", show("TCR_5", "on_conflict"));
        assertEquals("c", show("TCR_5", "comment"));
        engine.executeQuery("CREATE TAG tcr_6 COMMENT = 'c' PROPAGATE = ON_DEPENDENCY");
        assertEquals("ON_DEPENDENCY", show("TCR_6", "propagate"));
        engine.executeQuery("CREATE TAG tcr_7 ALLOWED_VALUES 'a' COMMENT = 'c' PROPAGATE = ON_DEPENDENCY"
            + " ON_CONFLICT = 'a'");
        assertEquals("a", show("TCR_7", "on_conflict"));
        engine.executeQuery("CREATE OR ALTER TAG tcr_8 ON_CONFLICT = 'a' COMMENT = 'c' PROPAGATE = ON_DEPENDENCY");
        assertEquals("a", show("TCR_8", "on_conflict"));
        engine.executeQuery("CREATE TAG tcr_9 COMMENT = $$dollar$$");
        assertEquals("dollar", show("TCR_9", "comment"));
        // ALLOWED_VALUES comes before every other property.
        assertTrue(refusal("CREATE TAG tcr_10 COMMENT = 'c' ALLOWED_VALUES 'a'").contains("syntax error"));
        assertTrue(refusal("CREATE TAG tcr_10 PROPAGATE = ON_DEPENDENCY ALLOWED_VALUES 'a'").contains("syntax error"));
        assertTrue(refusal("CREATE TAG tcr_10 ALLOWED_VALUES 'a' ALLOWED_VALUES 'b'").contains("syntax error"));
        assertEquals(0, tagsNamed("TCR_10"));
        engine.executeQuery("ALTER TAG tcr_6 SET COMMENT = 'd' ON_CONFLICT = 'z'");
        engine.executeQuery("ALTER TAG tcr_6 SET COMMENT = $$e$$");
        assertEquals("z", show("TCR_6", "on_conflict"));
        assertEquals("e", show("TCR_6", "comment"));
    }

    @Test
    public void aPropagationSetAloneKeepsTheRule() {
        engine.executeQuery("CREATE TAG tcr_11 PROPAGATE = ON_DEPENDENCY ON_CONFLICT = 'q'");
        engine.executeQuery("ALTER TAG tcr_11 SET PROPAGATE = ON_DATA_MOVEMENT");
        assertEquals("ON_DATA_MOVEMENT", show("TCR_11", "propagate"));
        assertEquals("q", show("TCR_11", "on_conflict"));
        engine.executeQuery("ALTER TAG tcr_11 SET ON_CONFLICT = 'c1' PROPAGATE = ON_DEPENDENCY");
        engine.executeQuery("ALTER TAG tcr_11 SET PROPAGATE = ON_DATA_MOVEMENT COMMENT = 'x'");
        assertEquals("c1", show("TCR_11", "on_conflict"));
        engine.executeQuery("ALTER TAG tcr_11 SET PROPAGATE = ON_DEPENDENCY ON_CONFLICT = 'r'");
        assertEquals("r", show("TCR_11", "on_conflict"));
        engine.executeQuery("ALTER TAG tcr_11 UNSET PROPAGATE");
        assertEquals("NONE", show("TCR_11", "propagate"));
        assertNull(show("TCR_11", "on_conflict"), "the rule goes with the propagation");

        // IF EXISTS forgives the tag's absence, not a refusal of the statement itself.
        engine.executeQuery("ALTER TAG IF EXISTS tcr_nosuch SET ON_CONFLICT = 'x'");
        engine.executeQuery("ALTER TAG IF EXISTS tcr_nosuch ADD ALLOWED_VALUES 'a'");
        engine.executeQuery("CREATE TAG tcr_12");
        assertTrue(refusal("ALTER TAG IF EXISTS tcr_12 SET ON_CONFLICT = 'x'").contains(NEEDS_PROPAGATE));
        assertTrue(refusal("ALTER TAG IF EXISTS tcr_12 SET PROPAGATE = BOGUS")
            .contains("invalid value 'BOGUS' for property 'PROPAGATE'"));
        // A mode that is none of the three is refused before the tag is looked for; the rule's check comes after.
        assertTrue(refusal("ALTER TAG IF EXISTS tcr_nosuch SET PROPAGATE = BOGUS")
            .contains("invalid value 'BOGUS' for property 'PROPAGATE'"));
        assertTrue(refusal("ALTER TAG tcr_nosuch SET PROPAGATE = BOGUS")
            .contains("invalid value 'BOGUS' for property 'PROPAGATE'"));
        assertTrue(refusal("ALTER TAG tcr_nosuch SET ON_CONFLICT = 'x'").contains("does not exist or not authorized"));
    }

    @Test
    public void addingOrDroppingValuesKeepsTheRuleTrue() {
        engine.executeQuery("CREATE TAG tcr_13 ALLOWED_VALUES 'a', 'b' PROPAGATE = ON_DEPENDENCY ON_CONFLICT = 'b'");
        assertTrue(refusal("ALTER TAG tcr_13 DROP ALLOWED_VALUES 'b'").contains(NOT_ALLOWED));
        engine.executeQuery("ALTER TAG tcr_13 DROP ALLOWED_VALUES 'a'");
        engine.executeQuery("ALTER TAG tcr_13 DROP ALLOWED_VALUES 'nothere'");
        engine.executeQuery("ALTER TAG tcr_13 ADD ALLOWED_VALUES 'c'");
        engine.executeQuery("ALTER TAG tcr_13 ADD ALLOWED_VALUES 'c'");
        assertEquals("[\"b\",\"c\"]", show("TCR_13", "allowed_values"));

        // Once the last value is dropped there is no list left to hold the rule to.
        engine.executeQuery("CREATE TAG tcr_14 ALLOWED_VALUES 'b' PROPAGATE = ON_DEPENDENCY ON_CONFLICT = 'b'");
        engine.executeQuery("ALTER TAG tcr_14 DROP ALLOWED_VALUES 'b'");
        assertEquals("b", show("TCR_14", "on_conflict"));

        // A rule written before any value: the values added must include it.
        engine.executeQuery("CREATE TAG tcr_15 PROPAGATE = ON_DEPENDENCY ON_CONFLICT = 'zz'");
        assertTrue(refusal("ALTER TAG tcr_15 ADD ALLOWED_VALUES 'k'").contains(NOT_ALLOWED));
        assertNull(show("TCR_15", "allowed_values"), "a refused ADD changes nothing");
        engine.executeQuery("ALTER TAG tcr_15 ADD ALLOWED_VALUES 'zz', 'k'");
        assertEquals("[\"zz\",\"k\"]", show("TCR_15", "allowed_values"));

        // The allowed-values sequence keeps at least one value.
        engine.executeQuery("CREATE TAG tcr_16 ALLOWED_VALUES 'a', 'b' PROPAGATE = ON_DEPENDENCY"
            + " ON_CONFLICT = ALLOWED_VALUES_SEQUENCE");
        engine.executeQuery("ALTER TAG tcr_16 DROP ALLOWED_VALUES 'b'");
        engine.executeQuery("ALTER TAG tcr_16 DROP ALLOWED_VALUES 'zzz'");
        assertTrue(refusal("ALTER TAG tcr_16 DROP ALLOWED_VALUES 'a'").contains(SEQUENCE_DROP));
        engine.executeQuery("ALTER TAG tcr_16 ADD ALLOWED_VALUES 'c'");
        assertEquals("[\"a\",\"c\"]", show("TCR_16", "allowed_values"));
    }
}
