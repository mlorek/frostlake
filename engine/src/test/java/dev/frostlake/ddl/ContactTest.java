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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CONTACT object and the attachment that gives it a job: {@code ALTER TABLE … SET CONTACT
 * <purpose> = <contact>}. Both halves are measured against live.
 *
 * <p>Two spellings matter and both are the opposite of what the surrounding syntax suggests: the
 * attachment takes NO parentheses (live refuses the parenthesized form outright), and
 * EMAIL_DISTRIBUTION_LIST takes exactly ONE address despite its name. The purposes are a closed set
 * of three, validated on the UNSET form too.
 */
public class ContactTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE c_t (id INT)");
        engine.execute("CREATE CONTACT c1");
        engine.execute("CREATE CONTACT c2 COMMENT = 'the second'");
    }

    private ResultSet contacts() {
        return engine.executeQuery("SHOW CONTACTS LIKE 'c2'");
    }

    @Test
    public void showListsAContactWithItsComment() {
        final ResultSet listed = contacts();
        assertEquals("the second", cell(listed, soleRowWhere(listed, "name", "C2"), "comment"));
        assertEquals("TEST_SCHEMA", cell(listed, soleRowWhere(listed, "name", "C2"), "schema_name"));
    }

    @Test
    public void creatingOneTwiceIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE CONTACT c1");
            }
        });
        assertEquals("SQL compilation error:\nObject 'C1' already exists.", ex.getMessage());
    }

    @Test
    public void ifNotExistsAndOrReplaceBothSurviveTheSecondCreate() {
        engine.execute("CREATE CONTACT IF NOT EXISTS c1");
        engine.execute("CREATE OR REPLACE CONTACT c1 COMMENT = 'replaced'");
        final ResultSet listed = engine.executeQuery("SHOW CONTACTS LIKE 'c1'");
        assertEquals("replaced", cell(listed, soleRowWhere(listed, "name", "C1"), "comment"));
    }

    @Test
    public void urlAndOneEmailAddressAreProperties() {
        engine.execute("CREATE CONTACT c3 URL = 'https://example.com'");
        engine.execute("CREATE CONTACT c7 EMAIL_DISTRIBUTION_LIST = 'someone@example.com'");
        final ResultSet listed = engine.executeQuery("SHOW CONTACTS");
        assertEquals("https://example.com", cell(listed, soleRowWhere(listed, "name", "C3"), "url"));
        assertEquals("someone@example.com",
            cell(listed, soleRowWhere(listed, "name", "C7"), "email_distribution_list"));
    }

    /** A contact is reached one way or the other — an address and a URL together are refused. */
    @Test
    public void anAddressAndAUrlTogetherAreRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE CONTACT c8 URL = 'https://example.com'"
                    + " EMAIL_DISTRIBUTION_LIST = 'someone@example.com'");
            }
        });
        assertEquals("Email, url or users/email_list, only one could be set.", ex.getMessage());
    }

    /** A dotted domain with a real top level, and one address only. */
    @Test
    public void anAddressThatIsNotOneIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE CONTACT c4 EMAIL_DISTRIBUTION_LIST = 'a@b.c'");
            }
        });
        assertEquals("Invalid email address(es): [a@b.c].", ex.getMessage());
    }

    @Test
    public void aListOfAddressesIsRefusedDespiteThePropertyName() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE CONTACT c5 EMAIL_DISTRIBUTION_LIST = 'a@b.com, c@d.com'");
            }
        });
        assertEquals("Invalid email address(es): [a@b.com, c@d.com].", ex.getMessage());
    }

    @Test
    public void aPropertyTheObjectDoesNotHaveIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE CONTACT c6 EMAIL = 'someone@example.com'");
            }
        });
        assertEquals("SQL compilation error:\ninvalid property 'EMAIL' for 'CONTACT'", ex.getMessage());
    }

    @Test
    public void theCommentCanBeChangedLater() {
        engine.execute("ALTER CONTACT c1 SET COMMENT = 'later'");
        final ResultSet listed = engine.executeQuery("SHOW CONTACTS LIKE 'c1'");
        assertEquals("later", cell(listed, soleRowWhere(listed, "name", "C1"), "comment"));
    }

    @Test
    public void attachingTakesNoParentheses() {
        engine.execute("ALTER TABLE c_t SET CONTACT SUPPORT = c1");
        engine.execute("ALTER TABLE c_t SET CONTACT SUPPORT = c1, STEWARD = c2");
        engine.execute("ALTER TABLE c_t SET CONTACT ACCESS_APPROVAL = c2");
        engine.execute("ALTER TABLE c_t UNSET CONTACT SUPPORT");
        engine.execute("ALTER TABLE c_t UNSET CONTACT STEWARD, ACCESS_APPROVAL");
    }

    @Test
    public void theParenthesizedAttachmentIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE c_t SET CONTACT (SUPPORT = c1)");
            }
        });
        assertTrue(ex.getMessage().contains("syntax error"), ex.getMessage());
    }

    @Test
    public void aQuotedPurposeIsAccepted() {
        engine.execute("ALTER TABLE c_t SET CONTACT 'support' = c1");
    }

    @Test
    public void aPurposeOutsideTheThreeIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE c_t SET CONTACT APPROVER = c1");
            }
        });
        assertEquals("SQL compilation error: Purpose type APPROVER is not valid,"
            + " please use access_approval, steward and support.", ex.getMessage());
    }

    /** UNSET validates the purpose before it looks at what is attached. */
    @Test
    public void unsettingAPurposeOutsideTheThreeIsRefusedToo() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE c_t UNSET CONTACT APPROVER");
            }
        });
        assertEquals("SQL compilation error: Purpose type APPROVER is not valid,"
            + " please use access_approval, steward and support.", ex.getMessage());
    }

    @Test
    public void unsettingAPurposeThatWasNeverSetIsANoOp() {
        engine.execute("ALTER TABLE c_t UNSET CONTACT ACCESS_APPROVAL");
    }

    @Test
    public void attachingAContactThatDoesNotExistIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE c_t SET CONTACT SUPPORT = no_such_contact");
            }
        });
        assertEquals("SQL compilation error:\nContact 'TEST_DB.TEST_SCHEMA.NO_SUCH_CONTACT'"
            + " does not exist or not authorized.", ex.getMessage());
    }

    @Test
    public void droppingOneThatIsGoneIsRefusedUnlessForgiven() {
        engine.execute("DROP CONTACT c2");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP CONTACT c2");
            }
        });
        assertEquals("SQL compilation error:\nContact 'TEST_DB.TEST_SCHEMA.C2'"
            + " does not exist or not authorized.", ex.getMessage());
        engine.execute("DROP CONTACT IF EXISTS c2");
    }

    /** CONTACT is not a reserved word: tables and columns may be called it. */
    @Test
    public void contactRemainsUsableAsAName() {
        engine.execute("CREATE TABLE contact (contact INT, contacts INT, support INT, steward INT)");
        engine.execute("INSERT INTO contact VALUES (1, 2, 3, 4)");
        assertEquals("1", engine.executeQuery("SELECT contact FROM contact")
            .getRows().get(0).getValue(0).toString());
    }
}
