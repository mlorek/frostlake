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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.frostlake.BaseDatabaseTest;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * CREATE ROLE asks CREATE ROLE on the account, which USERADMIN and the roles above it hold; DROP ROLE, DROP USER and
 * CREATE OR REPLACE ROLE ask OWNERSHIP of the role or user they remove; ALTER ACCOUNT SET or UNSET TAG asks MODIFY
 * on the account, as its other properties do (documented). Each refusal is the account's privilege sentence. Engine
 * only: GRANT, CREATE ROLE and the switch of user are not run on the live account.
 */
public class PrincipalDropPrivilegeTest extends BaseDatabaseTest {

    private String openerUser;

    private String opener;

    @Override
    protected void setupTest() {
        assumeFalse(isLiveSnowflake(), "GRANT, CREATE ROLE and CREATE USER are not run on the live account");
        openerUser = engine.getSessionContext().getCurrentUser();
        opener = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE ROLE pd_r");
        // The user puts no secondary role in use, so a refusal names the primary role alone.
        engine.execute("CREATE USER pd_u DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("GRANT ROLE pd_r TO USER pd_u");
        engine.execute("CREATE ROLE pd_victim");
        engine.execute("CREATE USER pd_victim_u");
        engine.execute("CREATE TAG pd_tag");
    }

    @Override
    protected void teardownTest() {
        if (isLiveSnowflake()) {
            return;
        }
        asOpener();
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private void runs(final String sql) {
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
    }

    /** The session as PD_U with only PD_R active: the engine's own user is asked for no account privilege. */
    private void asRole() {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("PD_U");
        engine.setCurrentRole("PD_R");
    }

    private void asOpener() {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser(openerUser);
        engine.setCurrentRole(opener);
    }

    private String account() {
        return String.valueOf(engine.executeQuery("SELECT CURRENT_ACCOUNT()").getRows().get(0).getValue(0));
    }

    private static String ownership(final String kind, final String name) {
        return "SQL access control error:\nInsufficient privileges to operate on " + kind.toLowerCase(Locale.ROOT)
            + " '" + name + "'. Your primary role PD_R must have OWNERSHIP granted on " + kind + " " + name + ".";
    }

    private static String onAccount(final String account, final String privilege) {
        return "SQL access control error:\nInsufficient privileges to operate on account '" + account
            + "'. Your primary role PD_R must have " + privilege + " granted on ACCOUNT " + account + ".";
    }

    @Test
    public void aRoleOrAUserAnotherRoleOwnsIsNotDroppedOrReplaced() {
        final String account = account();
        asRole();
        try {
            assertEquals(onAccount(account, "CREATE ROLE"), refusal("CREATE ROLE pd_new"));
            assertEquals(onAccount(account, "CREATE ROLE"), refusal("CREATE OR REPLACE ROLE pd_victim"));
            assertEquals(ownership("ROLE", "PD_VICTIM"), refusal("DROP ROLE pd_victim"));
            assertEquals(ownership("ROLE", "PD_VICTIM"), refusal("DROP ROLE IF EXISTS pd_victim"));
            assertEquals(ownership("USER", "PD_VICTIM_U"), refusal("DROP USER pd_victim_u"));
            assertEquals(onAccount(account, "MODIFY"), refusal("ALTER ACCOUNT SET TAG pd_tag = 'x'"));
            assertEquals(onAccount(account, "MODIFY"), refusal("ALTER ACCOUNT UNSET TAG pd_tag"));
        } finally {
            asOpener();
        }
    }

    @Test
    public void createRoleAndOwnershipLetARoleCreateReplaceAndDropRoles() {
        engine.execute("GRANT CREATE ROLE ON ACCOUNT TO ROLE pd_r");
        asRole();
        try {
            runs("CREATE ROLE pd_new");
            runs("CREATE OR REPLACE ROLE pd_new");
            runs("DROP ROLE pd_new");
            // A role it does not own is still another's to replace or drop.
            assertEquals(ownership("ROLE", "PD_VICTIM"), refusal("CREATE OR REPLACE ROLE pd_victim"));
            assertEquals(ownership("ROLE", "PD_VICTIM"), refusal("DROP ROLE pd_victim"));
        } finally {
            asOpener();
        }
        engine.execute("GRANT OWNERSHIP ON ROLE pd_victim TO ROLE pd_r");
        engine.execute("GRANT OWNERSHIP ON USER pd_victim_u TO ROLE pd_r");
        asRole();
        try {
            runs("DROP ROLE pd_victim");
            runs("DROP USER pd_victim_u");
        } finally {
            asOpener();
        }
    }
}
