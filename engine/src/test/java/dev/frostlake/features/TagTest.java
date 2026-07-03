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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for TAG functionality (metadata management)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TagTest {
    private static final Logger logger = LoggerFactory.getLogger(TagTest.class);

    private DatabaseEngine engine;

    @BeforeAll
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
        logger.info("DatabaseEngine initialized for TAG tests");
    }

    @AfterAll
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    @Order(1)
    public void testCreateSimpleTag() {
        logger.info("Testing CREATE TAG");
        engine.execute("CREATE TAG cost_center");

        Tag tag = engine.getCatalog().getTag("cost_center");
        assertNotNull(tag);
        assertEquals("cost_center", tag.getName());
        assertFalse(tag.hasAllowedValues());
        assertFalse(tag.isMasking());
    }

    @Test
    @Order(2)
    public void testCreateTagWithAllowedValues() {
        logger.info("Testing CREATE TAG with ALLOWED_VALUES");
        engine.execute("CREATE TAG department ALLOWED_VALUES 'HR', 'IT', 'FINANCE', 'SALES'");

        Tag tag = engine.getCatalog().getTag("department");
        assertNotNull(tag);
        assertEquals("department", tag.getName());
        assertTrue(tag.hasAllowedValues());
        assertEquals(4, tag.getAllowedValues().size());
        assertTrue(tag.isValueAllowed("HR"));
        assertTrue(tag.isValueAllowed("IT"));
        assertFalse(tag.isValueAllowed("UNKNOWN"));
    }

    @Test
    @Order(3)
    public void testCreateTagWithMasking() {
        logger.info("Testing CREATE TAG with MASKING");
        engine.execute("CREATE TAG sensitive_data MASKING = TRUE");

        Tag tag = engine.getCatalog().getTag("sensitive_data");
        assertNotNull(tag);
        assertEquals("sensitive_data", tag.getName());
        assertTrue(tag.isMasking());
    }

    @Test
    @Order(4)
    public void testCreateTagWithComment() {
        logger.info("Testing CREATE TAG with COMMENT");
        engine.execute("CREATE TAG project_code COMMENT = 'Project identifier tag'");

        Tag tag = engine.getCatalog().getTag("project_code");
        assertNotNull(tag);
        assertEquals("project_code", tag.getName());
        assertEquals("Project identifier tag", tag.getComment());
    }

    @Test
    @Order(5)
    public void testCreateTagIfNotExists() {
        logger.info("Testing CREATE TAG IF NOT EXISTS");
        engine.execute("CREATE TAG IF NOT EXISTS cost_center");

        Tag tag = engine.getCatalog().getTag("cost_center");
        assertNotNull(tag);
    }

    @Test
    @Order(6)
    public void testCreateDuplicateTagFails() {
        logger.info("Testing duplicate CREATE TAG fails");
        assertThrows(RuntimeException.class, () -> {
            engine.execute("CREATE TAG cost_center");
        });
    }

    @Test
    @Order(7)
    public void testShowTags() {
        logger.info("Testing SHOW TAGS");
        ResultSet rs = engine.executeQuery("SHOW TAGS");

        assertNotNull(rs);
        assertTrue(rs.getRows().size() >= 4);

        boolean foundCostCenter = false;
        boolean foundDepartment = false;
        int nameIdx = rs.getColumnIndex("name");
        for (int i = 0; i < rs.getRows().size(); i++) {
            String tagName = (String) rs.getRows().get(i).getValue(nameIdx);
            if ("cost_center".equalsIgnoreCase(tagName)) {
                foundCostCenter = true;
            }
            if ("department".equalsIgnoreCase(tagName)) {
                foundDepartment = true;
            }
        }
        assertTrue(foundCostCenter);
        assertTrue(foundDepartment);
    }

    @Test
    @Order(8)
    public void testDescribeTag() {
        logger.info("Testing DESCRIBE TAG");
        ResultSet rs = engine.executeQuery("DESCRIBE TAG department");

        assertNotNull(rs);
        assertTrue(rs.getRows().size() > 0);

        String nameProperty = (String) rs.getRows().get(0).getValues().get(0);
        String nameValue = (String) rs.getRows().get(0).getValues().get(1);
        assertEquals("name", nameProperty);
        assertEquals("department", nameValue);
    }

    @Test
    @Order(9)
    public void testAlterTagAddAllowedValues() {
        logger.info("Testing ALTER TAG ADD ALLOWED_VALUES");
        engine.execute("ALTER TAG department ADD ALLOWED_VALUES 'MARKETING', 'OPERATIONS'");

        Tag tag = engine.getCatalog().getTag("department");
        assertEquals(6, tag.getAllowedValues().size());
        assertTrue(tag.isValueAllowed("MARKETING"));
        assertTrue(tag.isValueAllowed("OPERATIONS"));
    }

    @Test
    @Order(10)
    public void testAlterTagDropAllowedValues() {
        logger.info("Testing ALTER TAG DROP ALLOWED_VALUES");
        engine.execute("ALTER TAG department DROP ALLOWED_VALUES 'SALES'");

        Tag tag = engine.getCatalog().getTag("department");
        assertEquals(5, tag.getAllowedValues().size());
        assertFalse(tag.isValueAllowed("SALES"));
    }

    @Test
    @Order(11)
    public void testAlterTagUnsetAllowedValues() {
        logger.info("Testing ALTER TAG UNSET ALLOWED_VALUES");
        engine.execute("ALTER TAG department UNSET ALLOWED_VALUES");

        Tag tag = engine.getCatalog().getTag("department");
        assertFalse(tag.hasAllowedValues());
    }

    @Test
    @Order(12)
    public void testAlterTagSetComment() {
        logger.info("Testing ALTER TAG SET COMMENT");
        engine.execute("ALTER TAG cost_center SET COMMENT = 'Updated cost center tag'");

        Tag tag = engine.getCatalog().getTag("cost_center");
        assertEquals("Updated cost center tag", tag.getComment());
    }

    @Test
    @Order(13)
    public void testCommentOnTag() {
        logger.info("Testing COMMENT ON TAG");
        engine.execute("COMMENT ON TAG project_code IS 'Project tracking identifier'");

        Tag tag = engine.getCatalog().getTag("project_code");
        assertEquals("Project tracking identifier", tag.getComment());
    }

    @Test
    @Order(14)
    public void testCommentOnTagIfExists() {
        logger.info("Testing COMMENT IF EXISTS ON TAG");
        engine.execute("COMMENT IF EXISTS ON TAG nonexistent_tag IS 'This should not fail'");
    }

    @Test
    @Order(15)
    public void testAlterTagRename() {
        logger.info("Testing ALTER TAG RENAME");
        engine.execute("ALTER TAG project_code RENAME project_id");

        assertFalse(engine.getCatalog().hasTag("project_code"));
        assertTrue(engine.getCatalog().hasTag("project_id"));

        Tag tag = engine.getCatalog().getTag("project_id");
        assertEquals("project_id", tag.getName());
    }

    @Test
    @Order(16)
    public void testDropTag() {
        logger.info("Testing DROP TAG");
        engine.execute("DROP TAG sensitive_data");

        assertFalse(engine.getCatalog().hasTag("sensitive_data"));
    }

    @Test
    @Order(17)
    public void testDropTagIfExists() {
        logger.info("Testing DROP TAG IF EXISTS");
        engine.execute("DROP TAG IF EXISTS nonexistent_tag");
    }

    @Test
    @Order(18)
    public void testDropNonexistentTagFails() {
        logger.info("Testing DROP nonexistent TAG fails");
        assertThrows(RuntimeException.class, () -> {
            engine.execute("DROP TAG nonexistent_tag");
        });
    }

    @Test
    @Order(19)
    public void testDescribeNonexistentTagFails() {
        logger.info("Testing DESCRIBE nonexistent TAG fails");
        assertThrows(RuntimeException.class, () -> {
            engine.executeQuery("DESCRIBE TAG nonexistent_tag");
        });
    }

    @Test
    @Order(20)
    public void testAlterNonexistentTagFails() {
        logger.info("Testing ALTER nonexistent TAG fails");
        assertThrows(RuntimeException.class, () -> {
            engine.execute("ALTER TAG nonexistent_tag SET COMMENT = 'test'");
        });
    }

    @Test
    public void testAlterTagSetMasking() {
        logger.info("Testing ALTER TAG SET MASKING toggles the masking flag");
        engine.execute("CREATE TAG alter_mask_tag");
        assertFalse(engine.getCatalog().getTag("alter_mask_tag").isMasking());

        engine.execute("ALTER TAG alter_mask_tag SET MASKING = TRUE");
        assertTrue(engine.getCatalog().getTag("alter_mask_tag").isMasking());

        engine.execute("ALTER TAG alter_mask_tag SET MASKING = FALSE");
        assertFalse(engine.getCatalog().getTag("alter_mask_tag").isMasking());
    }
}
