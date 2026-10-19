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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What NETWORK_POLICY does among a user's properties, on an engine of its own, since a user is no object the
 * suite makes on an account: a CREATE USER and an ALTER USER ... SET list attach the policy beside their other
 * properties, an UNSET list and NETWORK_POLICY = NULL take it off, and a policy that is not there refuses the
 * statement before anything of it is applied. The forms themselves are measured over a missing user
 * (UserNetworkPolicyPropertyTest).
 */
public class UserNetworkPolicyAmongPropertiesTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE NETWORK POLICY np_one ALLOWED_IP_LIST = ('10.0.0.0/8')");
        engine.execute("CREATE NETWORK POLICY np_two ALLOWED_IP_LIST = ('10.1.0.0/16')");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** The policy SHOW PARAMETERS reports for the user, or null when its level is not the user's. */
    private String policyOf(final String user) {
        final ResultSet rs = engine.executeQuery("SHOW PARAMETERS LIKE 'NETWORK_POLICY' IN USER " + user);
        assertEquals(1, rs.getRowCount());
        final Object level = rs.getRows().get(0).getValue(rs.getColumnIndex("level"));
        final Object value = rs.getRows().get(0).getValue(rs.getColumnIndex("value"));
        return "USER".equals(level) ? String.valueOf(value) : null;
    }

    private String commentOf(final String user) {
        final ResultSet rs = engine.executeQuery("SHOW USERS LIKE '" + user + "'");
        final Object comment = rs.getRows().get(0).getValue(rs.getColumnIndex("comment"));
        return comment == null || comment.toString().isEmpty() ? null : comment.toString();
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    @Test
    public void createUserAttachesThePolicyItNames() {
        engine.execute("CREATE USER np_u1 COMMENT = 'made' NETWORK_POLICY = np_one");
        assertEquals("NP_ONE", policyOf("np_u1"));
        assertEquals("made", commentOf("np_u1"));
        engine.execute("CREATE USER np_u2");
        assertNull(policyOf("np_u2"));
    }

    @Test
    public void aSetListAttachesItBesideTheOtherProperties() {
        engine.execute("CREATE USER np_u1");
        engine.execute("ALTER USER np_u1 SET COMMENT = 'c', NETWORK_POLICY = np_one");
        assertEquals("NP_ONE", policyOf("np_u1"));
        assertEquals("c", commentOf("np_u1"));
        engine.execute("ALTER USER np_u1 SET NETWORK_POLICY = 'NP_TWO' FIRST_NAME = 'f'");
        assertEquals("NP_TWO", policyOf("np_u1"));
        // The statement of one property still stands.
        engine.execute("ALTER USER np_u1 SET NETWORK_POLICY = np_one");
        assertEquals("NP_ONE", policyOf("np_u1"));
    }

    @Test
    public void anUnsetListAndNullTakeItOff() {
        engine.execute("CREATE USER np_u1 NETWORK_POLICY = np_one COMMENT = 'c'");
        engine.execute("ALTER USER np_u1 UNSET NETWORK_POLICY, COMMENT");
        assertNull(policyOf("np_u1"));
        assertNull(commentOf("np_u1"));
        engine.execute("ALTER USER np_u1 SET NETWORK_POLICY = np_two");
        engine.execute("ALTER USER np_u1 SET COMMENT = 'd' NETWORK_POLICY = NULL");
        assertNull(policyOf("np_u1"));
        assertEquals("d", commentOf("np_u1"));
    }

    @Test
    public void aMissingPolicyRefusesTheWholeStatement() {
        engine.execute("CREATE USER np_u1 COMMENT = 'kept'");
        assertTrue(refusal("ALTER USER np_u1 SET COMMENT = 'changed' NETWORK_POLICY = no_such_policy")
            .contains("Network policy NO_SUCH_POLICY does not exist or not authorized."));
        assertEquals("kept", commentOf("np_u1"));
        assertNull(policyOf("np_u1"));
        // A string is the policy's name exactly: 'np_one' is no policy NP_ONE.
        assertTrue(refusal("ALTER USER np_u1 SET COMMENT = 'changed', NETWORK_POLICY = 'np_one'")
            .contains("Network policy np_one does not exist or not authorized."));
        assertTrue(refusal("CREATE USER np_u3 NETWORK_POLICY = no_such_policy")
            .contains("Network policy NO_SUCH_POLICY does not exist or not authorized."));
        assertFalse(engine.getCatalog().hasUser("NP_U3"));
    }

    @Test
    public void theAttachmentGoesWithTheUser() {
        engine.execute("CREATE USER np_u1 NETWORK_POLICY = np_one");
        // A renamed user keeps its policy under its new name.
        engine.execute("ALTER USER np_u1 RENAME TO np_u9");
        assertEquals("NP_ONE", policyOf("np_u9"));
        assertTrue(refusal("DROP NETWORK POLICY np_one").contains("attached to USER with name NP_U9"));
        // A replaced user is a new one, and a dropped user holds nothing: the policy may be dropped.
        engine.execute("CREATE OR REPLACE USER np_u9");
        assertNull(policyOf("np_u9"));
        engine.execute("ALTER USER np_u9 SET NETWORK_POLICY = np_two");
        engine.execute("DROP USER np_u9");
        engine.execute("DROP NETWORK POLICY np_one");
        engine.execute("DROP NETWORK POLICY np_two");
    }
}
