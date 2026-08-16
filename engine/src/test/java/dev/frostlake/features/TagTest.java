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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TAG DDL, asserted through the SQL surface — {@code SHOW TAGS} cells (name, allowed_values as a
 * ", "-joined list, comment) — so every check runs against whichever engine executed the DDL,
 * embedded or live. Each test creates the tags it reads, so there is no cross-test ordering.
 *
 * <p>{@code MASKING} is not a tag property: Snowflake refuses it at compile time with
 * {@code invalid property 'MASKING' for 'TAG'}, live-verified, and so does this engine.
 */
public class TagTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(TagTest.class);

    private static final String DESCRIBE_TAG_EDITION_GATED =
        "DESCRIBE TAG answers \"Unsupported feature 'TAG'\" on a Standard-edition account, so its "
        + "shape is asserted embedded only";

    /** One SHOW TAGS cell for the given tag, or null when the cell carries no value. */
    private String tagCell(final String tag, final String column) {
        final ResultSet rs = engine.executeQuery("SHOW TAGS LIKE '" + tag + "'");
        return cell(rs, soleRowWhere(rs, "name", tag.toUpperCase()), column);
    }

    private int tagCount(final String name) {
        return engine.executeQuery("SHOW TAGS LIKE '" + name + "'").getRowCount();
    }

    @Test
    public void testCreateSimpleTag() {
        logger.info("Testing CREATE TAG");
        engine.execute("CREATE TAG cost_center");

        assertEquals("COST_CENTER", tagCell("cost_center", "name"));
        assertNull(tagCell("cost_center", "allowed_values"), "no ALLOWED_VALUES were declared");
    }

    @Test
    public void testCreateTagWithAllowedValues() {
        logger.info("Testing CREATE TAG with ALLOWED_VALUES");
        engine.execute("CREATE TAG department ALLOWED_VALUES 'HR', 'IT', 'FINANCE', 'SALES'");

        assertEquals("DEPARTMENT", tagCell("department", "name"));
        assertEquals("[\"HR\",\"IT\",\"FINANCE\",\"SALES\"]", tagCell("department", "allowed_values"));
    }

    @Test
    public void testCreateTagWithMaskingIsRefused() {
        logger.info("Testing CREATE TAG … MASKING is refused as an invalid property");
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TAG sensitive_data MASKING = TRUE");
            }
        });
        assertTrue(e.getMessage().contains("invalid property 'MASKING' for 'TAG'"), e.getMessage());

        // The refusal is a compilation error, so no tag is left behind.
        assertEquals(0, tagCount("sensitive_data"));
    }

    @Test
    public void testAlterTagSetMaskingIsRefused() {
        logger.info("Testing ALTER TAG … SET MASKING is refused as an invalid property");
        engine.execute("CREATE TAG alter_mask_tag");

        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TAG alter_mask_tag SET MASKING = TRUE");
            }
        });
        assertTrue(e.getMessage().contains("invalid property 'MASKING' for 'TAG'"), e.getMessage());
    }

    @Test
    public void testCreateTagWithComment() {
        logger.info("Testing CREATE TAG with COMMENT");
        engine.execute("CREATE TAG project_code COMMENT = 'Project identifier tag'");

        assertEquals("PROJECT_CODE", tagCell("project_code", "name"));
        assertEquals("Project identifier tag", tagCell("project_code", "comment"));
    }

    @Test
    public void testCreateTagIfNotExists() {
        logger.info("Testing CREATE TAG IF NOT EXISTS");
        engine.execute("CREATE TAG cost_center");
        engine.execute("CREATE TAG IF NOT EXISTS cost_center");

        assertEquals(1, tagCount("cost_center"));
    }

    @Test
    public void testCreateDuplicateTagFails() {
        logger.info("Testing duplicate CREATE TAG fails");
        engine.execute("CREATE TAG cost_center");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TAG cost_center");
            }
        });
    }

    @Test
    public void testShowTags() {
        logger.info("Testing SHOW TAGS");
        engine.execute("CREATE TAG cost_center");
        engine.execute("CREATE TAG department");

        final ResultSet rs = engine.executeQuery("SHOW TAGS");
        soleRowWhere(rs, "name", "COST_CENTER");
        soleRowWhere(rs, "name", "DEPARTMENT");
    }

    @Test
    public void testShowTagsCarriesDatabaseAndSchema() {
        logger.info("Testing SHOW TAGS names the tag's container");
        engine.execute("CREATE TAG department ALLOWED_VALUES 'HR', 'IT'");

        final ResultSet rs = engine.executeQuery("SHOW TAGS LIKE 'department'");
        final Row tag = soleRowWhere(rs, "name", "DEPARTMENT");
        assertEquals("TEST_DB", cell(rs, tag, "database_name"));
        assertEquals("TEST_SCHEMA", cell(rs, tag, "schema_name"));
    }

    @Test
    public void testAlterTagAddAllowedValues() {
        logger.info("Testing ALTER TAG ADD ALLOWED_VALUES");
        engine.execute("CREATE TAG department ALLOWED_VALUES 'HR', 'IT', 'FINANCE', 'SALES'");
        engine.execute("ALTER TAG department ADD ALLOWED_VALUES 'MARKETING', 'OPERATIONS'");

        assertEquals("[\"HR\",\"IT\",\"FINANCE\",\"SALES\",\"MARKETING\",\"OPERATIONS\"]",
            tagCell("department", "allowed_values"));
    }

    @Test
    public void testAlterTagDropAllowedValues() {
        logger.info("Testing ALTER TAG DROP ALLOWED_VALUES");
        engine.execute("CREATE TAG department ALLOWED_VALUES 'HR', 'IT', 'FINANCE', 'SALES'");
        engine.execute("ALTER TAG department DROP ALLOWED_VALUES 'SALES'");

        assertEquals("[\"HR\",\"IT\",\"FINANCE\"]", tagCell("department", "allowed_values"));
    }

    @Test
    public void testAlterTagUnsetAllowedValues() {
        logger.info("Testing ALTER TAG UNSET ALLOWED_VALUES");
        engine.execute("CREATE TAG department ALLOWED_VALUES 'HR', 'IT'");
        engine.execute("ALTER TAG department UNSET ALLOWED_VALUES");

        assertNull(tagCell("department", "allowed_values"));
    }

    @Test
    public void testAlterTagSetComment() {
        logger.info("Testing ALTER TAG SET COMMENT");
        engine.execute("CREATE TAG cost_center");
        engine.execute("ALTER TAG cost_center SET COMMENT = 'Updated cost center tag'");

        assertEquals("Updated cost center tag", tagCell("cost_center", "comment"));
    }

    @Test
    public void testCommentOnTag() {
        logger.info("Testing COMMENT ON TAG");
        engine.execute("CREATE TAG project_code");
        engine.execute("COMMENT ON TAG project_code IS 'Project tracking identifier'");

        assertEquals("Project tracking identifier", tagCell("project_code", "comment"));
    }

    @Test
    public void testCommentOnTagIfExists() {
        logger.info("Testing COMMENT IF EXISTS ON TAG");
        engine.execute("COMMENT IF EXISTS ON TAG nonexistent_tag IS 'This should not fail'");
    }

    @Test
    public void testAlterTagRename() {
        logger.info("Testing ALTER TAG RENAME");
        engine.execute("CREATE TAG project_code");
        engine.execute("ALTER TAG project_code RENAME TO project_id");

        assertEquals(0, tagCount("project_code"));
        assertEquals(1, tagCount("project_id"));
        assertEquals("PROJECT_ID", tagCell("project_id", "name"));
    }

    @Test
    public void testDropTag() {
        logger.info("Testing DROP TAG");
        engine.execute("CREATE TAG sensitive_data");
        engine.execute("DROP TAG sensitive_data");

        assertEquals(0, tagCount("sensitive_data"));
    }

    @Test
    public void testDropTagIfExists() {
        logger.info("Testing DROP TAG IF EXISTS");
        engine.execute("DROP TAG IF EXISTS nonexistent_tag");
    }

    @Test
    public void testDropNonexistentTagFails() {
        logger.info("Testing DROP nonexistent TAG fails");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP TAG nonexistent_tag");
            }
        });
    }

    @Test
    public void testAlterNonexistentTagFails() {
        logger.info("Testing ALTER nonexistent TAG fails");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TAG nonexistent_tag SET COMMENT = 'test'");
            }
        });
    }

    @Test
    public void testDescribeTag() {
        Assumptions.assumeFalse(isLiveSnowflake(), DESCRIBE_TAG_EDITION_GATED);

        logger.info("Testing DESCRIBE TAG");
        engine.execute("CREATE TAG department ALLOWED_VALUES 'HR', 'IT'");
        final ResultSet rs = engine.executeQuery("DESCRIBE TAG department");

        assertEquals("name", cell(rs, rs.getRows().get(0), "property"));
        assertEquals("DEPARTMENT", cell(rs, rs.getRows().get(0), "value"));
    }

    @Test
    public void testDescribeNonexistentTagFails() {
        Assumptions.assumeFalse(isLiveSnowflake(), DESCRIBE_TAG_EDITION_GATED);

        logger.info("Testing DESCRIBE nonexistent TAG fails");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("DESCRIBE TAG nonexistent_tag");
            }
        });
    }
}
