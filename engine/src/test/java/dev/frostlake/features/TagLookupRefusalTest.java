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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What SYSTEM$GET_TAG and TAG_REFERENCES answer when the tag, the object or an argument is not there: the tag
 * and the object each have to exist, and each miss is refused in the words of its kind.
 */
public class TagLookupRefusalTest extends BaseDatabaseTest {

    private static final String PREFIX = "SQL compilation error:\n";

    @BeforeEach
    public void aTagAndATable() {
        engine.execute("CREATE TAG gt1");
        engine.execute("CREATE TABLE pt (a INT)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private String getTag(final String arguments) {
        return refusal("SELECT SYSTEM$GET_TAG(" + arguments + ")");
    }

    private String references(final String arguments) {
        return refusal("SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES(" + arguments + "))");
    }

    @Test
    public void aTagThatIsNotSetAnswersNull() {
        assertNull(engine.executeQuery("SELECT SYSTEM$GET_TAG('gt1', 'pt', 'TABLE')").getRows().get(0).getValue(0));
        assertNull(engine.executeQuery("SELECT SYSTEM$GET_TAG('gt1', 'pt', 'table')").getRows().get(0).getValue(0));
        assertNull(engine.executeQuery("SELECT SYSTEM$GET_TAG('gt1', 'test_schema', 'SCHEMA')").getRows().get(0)
            .getValue(0));
    }

    @Test
    public void aMissingTagIsRefusedByItsNameAsWrittenOrInFull() {
        assertEquals(hinted(PREFIX + "Tag 'NOSUCH_TAG_ZZ' does not exist or not authorized."),
            getTag("'nosuch_tag_zz', 'pt', 'TABLE'"));
        assertEquals(hinted(PREFIX + "Tag 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized."),
            getTag("'test_schema.nosuch', 'pt', 'TABLE'"));
        // The tag is looked for before the object.
        assertEquals(hinted(PREFIX + "Tag 'NOSUCH_TAG_ZZ' does not exist or not authorized."),
            getTag("'nosuch_tag_zz', 'test_db.test_schema.nosuch_pt', 'TABLE'"));
        assertEquals(hinted(PREFIX + "Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized."),
            getTag("'nosuch_s.cc', 'pt', 'TABLE'"));
        assertEquals(hinted(PREFIX + "Database 'NOSUCH_D' does not exist or not authorized."),
            getTag("'nosuch_d.s.cc', 'pt', 'TABLE'"));
    }

    @Test
    public void aMissingObjectIsRefusedInTheWordsOfItsKind() {
        assertEquals(hinted(PREFIX + "Table 'NOSUCH_PT' does not exist or not authorized."),
            getTag("'gt1', 'nosuch_pt', 'TABLE'"));
        assertEquals(hinted(PREFIX + "Table 'TEST_DB.TEST_SCHEMA.NOSUCH_PT' does not exist or not authorized."),
            getTag("'gt1', 'test_db.test_schema.nosuch_pt', 'TABLE'"));
        assertEquals(hinted(PREFIX + "Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized."),
            getTag("'gt1', 'nosuch_s.pt', 'TABLE'"));
        assertEquals(PREFIX + "Column 'TEST_DB.TEST_SCHEMA.PT.ZZ' does not exist or not authorized.",
            getTag("'gt1', 'pt.zz', 'COLUMN'"));
        assertEquals(hinted(PREFIX + "Warehouse 'NOSUCH_WH_ZZ' does not exist or not authorized."),
            getTag("'gt1', 'nosuch_wh_zz', 'WAREHOUSE'"));
        assertEquals("Unknown domain: NOPE.", getTag("'gt1', 'pt', 'NOPE'"));
        assertEquals("SQL compilation error: Invalid value VIEW for argument OBJECT_TYPE. Please use object type TABLE"
            + " for all kinds of table-like objects.", getTag("'gt1', 'pt', 'VIEW'"));
    }

    @Test
    public void anArgumentHasToBeAConstantThatIsNotNull() {
        assertEquals(PREFIX + "argument 0 to function SYSTEM$GET_TAG needs to be constant, found 'null'",
            getTag("NULL, 'pt', 'TABLE'"));
        assertEquals(PREFIX + "argument 1 to function SYSTEM$GET_TAG needs to be constant, found 'null'",
            getTag("'gt1', NULL, 'TABLE'"));
        assertEquals(PREFIX + "argument 2 to function SYSTEM$GET_TAG needs to be constant, found 'null'",
            getTag("'gt1', 'pt', NULL"));
    }

    @Test
    public void tagReferencesRefusesAMissingObjectTheSameWay() {
        assertEquals(hinted(PREFIX + "Table 'NOSUCH_PT' does not exist or not authorized."),
            references("'nosuch_pt', 'TABLE'"));
        assertEquals(hinted(PREFIX + "Table 'TEST_DB.TEST_SCHEMA.NOSUCH_PT' does not exist or not authorized."),
            references("'test_schema.nosuch_pt', 'TABLE'"));
        assertEquals(hinted(PREFIX + "Table 'TEST_DB.TEST_SCHEMA.NOSUCH_PT' does not exist or not authorized."),
            references("'test_db.test_schema.nosuch_pt', 'TABLE'"));
        assertEquals(hinted(PREFIX + "Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized."),
            references("'nosuch_s.pt', 'TABLE'"));
        assertEquals(hinted(PREFIX + "Database 'NOSUCH_D' does not exist or not authorized."),
            references("'nosuch_d.s.pt', 'TABLE'"));
        assertEquals(PREFIX + "Column 'TEST_DB.TEST_SCHEMA.NOSUCH_PT.A' does not exist or not authorized.",
            references("'nosuch_pt.a', 'COLUMN'"));
        assertEquals(PREFIX + "Column 'TEST_DB.TEST_SCHEMA.PT.ZZ' does not exist or not authorized.",
            references("'pt.zz', 'COLUMN'"));
        assertEquals(PREFIX + "Column 'TEST_DB.TEST_SCHEMA.A' does not exist or not authorized.",
            references("'a', 'COLUMN'"));
        assertEquals("Unknown domain: NOPE.", references("'pt', 'NOPE'"));
        assertEquals("SQL compilation error: Invalid value VIEW for argument OBJECT_TYPE. Please use object type TABLE"
            + " for all kinds of table-like objects.", references("'pt', 'VIEW'"));
    }

    @Test
    public void tagReferencesNamesTheArgumentThatIsMissing() {
        assertEquals("SQL compilation error: error line 1 at position 419\n"
            + "missing required argument [DOMAIN] for function [TAG_REFERENCES]", references("'pt'"));
        assertEquals("SQL compilation error: error line 1 at position 419\n"
            + "missing required argument [DOMAIN] for function [TAG_REFERENCES]", references("'pt', NULL"));
        assertEquals("SQL compilation error: error line 1 at position 419\n"
            + "missing required argument [OBJECT_NAME] for function [TAG_REFERENCES]", references("NULL, 'TABLE'"));
    }
}
