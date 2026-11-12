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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * CREATE USER reads the ingress policy's bypass and refuses it on the account, whatever the session holds, and no user
 * is made (live-verified).
 */
public class CreateUserBypassTest extends BaseDatabaseTest {

    @Test
    public void theIngressPolicyBypassIsRefusedOnTheAccount() {
        assumeFalse(isLiveSnowflake(), "CREATE USER is not run on the live account");
        final String account = String.valueOf(engine.executeQuery("SELECT CURRENT_ACCOUNT()").getRows().get(0)
            .getValue(0));
        final String refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE USER u3076 MINS_TO_BYPASS_INGRESS_POLICY = 5");
            }
        }).getMessage();
        assertEquals("SQL access control error:\nInsufficient privileges to operate on account '" + account + "'.",
            refused);
        assertFalse(engine.getCatalog().hasUserExact("U3076"));
    }
}
