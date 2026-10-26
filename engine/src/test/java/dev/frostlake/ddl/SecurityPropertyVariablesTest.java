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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A session variable is the value of any property of a network rule, a secret, a packages and an
 * authentication policy, and of a list's item, a session policy's role lists among them: read as its text,
 * a number refused by the variable's name, and a NULL or a Boolean judged by the property; a MODE no rule
 * type takes stands for INGRESS while the type judges it (all live-verified).
 */
public class SecurityPropertyVariablesTest extends MeasuredCellsTestSupport {

    @Test
    public void schemaKindsTakeVariables() {
        engine.execute("CREATE OR REPLACE TABLE tt691 (c TIME(3), d TIMESTAMP_NTZ(2))");
        engine.execute("INSERT INTO tt691 VALUES ('10:00:00.123', '2024-01-02 03:04:05.12')");
        assertAnswer("Statement executed successfully.", "SET cm = 'note691'");
        assertAnswer("Statement executed successfully.", "SET hp = 'example.com:443'");
        assertAnswer("Network rule NR691 is created.",
            "CREATE OR REPLACE NETWORK RULE nr691 TYPE = HOST_PORT MODE = EGRESS VALUE_LIST = ('example.com') "
                + "COMMENT = $cm");
        assertAnswer("Network rule NR691B is created.",
            "CREATE OR REPLACE NETWORK RULE nr691b TYPE = HOST_PORT MODE = EGRESS VALUE_LIST = ($hp)");
        assertAnswer("REFUSED The network rule type must be one of the snowflake supported network rule type.",
            "CREATE OR REPLACE NETWORK RULE nr691c TYPE = $cm MODE = EGRESS VALUE_LIST = ('example.com')");
        assertAnswer("Packages policy 'PP691' created successfully",
            "CREATE OR REPLACE PACKAGES POLICY pp691 LANGUAGE PYTHON ALLOWLIST = ('numpy') COMMENT = $cm");
        assertAnswer("Authentication policy 'AU691' created successfully",
            "CREATE OR REPLACE AUTHENTICATION POLICY au691 COMMENT = $cm");
        assertAnswer("Secret SE691 successfully created.",
            "CREATE OR REPLACE SECRET se691 TYPE = GENERIC_STRING SECRET_STRING = $cm");
        assertAnswer("Secret SE691B successfully created.",
            "CREATE OR REPLACE SECRET se691b TYPE = GENERIC_STRING SECRET_STRING = 'x' COMMENT = $cm");
        assertAnswer("Secret SE691C successfully created.",
            "CREATE OR REPLACE SECRET se691c TYPE = PASSWORD USERNAME = $cm PASSWORD = 'p'");
        assertAnswer("Statement executed successfully.", "ALTER SECRET se691b SET COMMENT = $cm");
        assertAnswer("Statement executed successfully.", "ALTER NETWORK RULE nr691 SET COMMENT = $cm");
    }

    @Test
    public void valuesByType() {
        assertAnswer("Statement executed successfully.",
            "SET (cm, hp, ty, md, n, b, pk, pk2, am, me, ct, ip, gs) = ('note692', 'example.com:443', 'HOST_PORT', "
                + "'EGRESS', 5, TRUE, 'numpy', 'pandas', 'PASSWORD', 'OPTIONAL', 'SNOWFLAKE_UI', '10.0.0.1', "
                + "'GENERIC_STRING')");
        assertAnswer("Statement executed successfully.", "SET nul = NULL");
        assertAnswer("Network rule NR692 is created.",
            "CREATE OR REPLACE NETWORK RULE nr692 TYPE = $ty MODE = $md VALUE_LIST = ($hp) COMMENT = $cm");
        engine.execute("SHOW NETWORK RULES LIKE 'NR692'");
        assertAnswer("NR692|HOST_PORT|EGRESS|note692|1",
            "SELECT \"name\", \"type\", \"mode\", \"comment\", \"entries_in_valuelist\" FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        engine.execute("DESCRIBE NETWORK RULE nr692");
        assertAnswer("NR692|HOST_PORT|EGRESS|example.com:443|note692",
            "SELECT \"name\", \"type\", \"mode\", \"value_list\", \"comment\" FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("REFUSED SQL compilation error: | invalid value '$n' for property 'VALUE_LIST'",
            "CREATE OR REPLACE NETWORK RULE nr692c TYPE = HOST_PORT MODE = EGRESS VALUE_LIST = ($n)");
        assertAnswer("REFUSED SQL compilation error: | invalid value '$b' for property 'VALUE_LIST'",
            "CREATE OR REPLACE NETWORK RULE nr692c TYPE = HOST_PORT MODE = EGRESS VALUE_LIST = ($b)");
        assertAnswer("REFUSED SQL compilation error: | invalid value '$nul' for property 'VALUE_LIST'",
            "CREATE OR REPLACE NETWORK RULE nr692c TYPE = HOST_PORT MODE = EGRESS VALUE_LIST = ($nul)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 83 | Session variable '$NOSUCH692' does "
                + "not exist",
            "CREATE OR REPLACE NETWORK RULE nr692c TYPE = HOST_PORT MODE = EGRESS VALUE_LIST = ($nosuch692)");
        assertAnswer("Network rule NR692C is created.",
            "CREATE OR REPLACE NETWORK RULE nr692c TYPE = HOST_PORT MODE = EGRESS VALUE_LIST = ($hp, $hp)");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n' for 'COMMENT'",
            "CREATE OR REPLACE NETWORK RULE nr692c TYPE = HOST_PORT MODE = EGRESS VALUE_LIST = ('a.com') COMMENT = $n");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n' for 'TYPE'",
            "CREATE OR REPLACE NETWORK RULE nr692c TYPE = $n MODE = EGRESS VALUE_LIST = ('a.com')");
        assertAnswer("REFUSED SQL compilation error: The network rule mode INGRESS is not supported by the network "
                + "rule type HOST_PORT. The network rule mode must be one of [EGRESS, "
                + "DATA_CONNECTIVITY_PROXY_EGRESS]. | ",
            "CREATE OR REPLACE NETWORK RULE nr692c TYPE = HOST_PORT MODE = $cm VALUE_LIST = ('a.com')");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n' for 'MODE'",
            "CREATE OR REPLACE NETWORK RULE nr692c TYPE = HOST_PORT MODE = $n VALUE_LIST = ('a.com')");
        assertAnswer("Network rule NR692D is created.",
            "CREATE OR REPLACE NETWORK RULE nr692d TYPE = IPV4 MODE = INGRESS VALUE_LIST = ($ip)");
        engine.execute("DESCRIBE NETWORK RULE nr692");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n' for 'COMMENT'",
            "ALTER NETWORK RULE nr692 SET COMMENT = $n");
        assertAnswer("Statement executed successfully.", "ALTER NETWORK RULE nr692 SET COMMENT = $nul");
        engine.execute("SHOW NETWORK RULES LIKE 'NR692'");
        assertAnswer("NR692|", "SELECT \"name\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("Statement executed successfully.", "ALTER NETWORK RULE nr692 SET COMMENT = $hp");
        engine.execute("SHOW NETWORK RULES LIKE 'NR692'");
        assertAnswer("NR692|example.com:443", "SELECT \"name\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("Secret SE692 successfully created.",
            "CREATE OR REPLACE SECRET se692 TYPE = GENERIC_STRING SECRET_STRING = $cm COMMENT = $cm");
        engine.execute("SHOW SECRETS LIKE 'SE692'");
        assertAnswer("SE692|note692|GENERIC_STRING",
            "SELECT \"name\", \"comment\", \"secret_type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("Secret SE692B successfully created.",
            "CREATE OR REPLACE SECRET se692b TYPE = PASSWORD USERNAME = $cm PASSWORD = $cm");
        engine.execute("DESCRIBE SECRET se692b");
        assertAnswer("SE692B|PASSWORD|note692|null",
            "SELECT \"name\", \"secret_type\", \"username\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n' for 'SECRET_STRING'",
            "CREATE OR REPLACE SECRET se692c TYPE = GENERIC_STRING SECRET_STRING = $n");
        assertAnswer("REFUSED SQL compilation error: | invalid value 'null' for property 'SECRET_STRING'",
            "CREATE OR REPLACE SECRET se692c TYPE = GENERIC_STRING SECRET_STRING = $b");
        assertAnswer("REFUSED SQL compilation error: | invalid value 'null' for property 'SECRET_STRING'",
            "CREATE OR REPLACE SECRET se692c TYPE = GENERIC_STRING SECRET_STRING = $nul");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n' for 'COMMENT'",
            "CREATE OR REPLACE SECRET se692c TYPE = GENERIC_STRING SECRET_STRING = 'x' COMMENT = $n");
        assertAnswer("Secret SE692C successfully created.",
            "CREATE OR REPLACE SECRET se692c TYPE = GENERIC_STRING SECRET_STRING = 'x' COMMENT = $nul");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n' for 'USERNAME'",
            "CREATE OR REPLACE SECRET se692c TYPE = PASSWORD USERNAME = $n PASSWORD = 'p'");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n' for 'PASSWORD'",
            "CREATE OR REPLACE SECRET se692c TYPE = PASSWORD USERNAME = 'u' PASSWORD = $n");
        assertAnswer("Secret SE692C successfully created.",
            "CREATE OR REPLACE SECRET se692c TYPE = $gs SECRET_STRING = 'x'");
        assertAnswer("Statement executed successfully.", "ALTER SECRET se692 SET SECRET_STRING = $hp");
        assertAnswer("Statement executed successfully.", "ALTER SECRET se692b SET USERNAME = $hp");
        assertAnswer("Statement executed successfully.", "ALTER SECRET se692b SET PASSWORD = $hp");
        engine.execute("DESCRIBE SECRET se692b");
        assertAnswer("example.com:443", "SELECT \"username\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n' for 'COMMENT'",
            "ALTER SECRET se692 SET COMMENT = $n");
        assertAnswer("Statement executed successfully.", "ALTER SECRET se692 SET COMMENT = $nul");
        engine.execute("SHOW SECRETS LIKE 'SE692'");
        assertAnswer("SE692|null", "SELECT \"name\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("Packages policy 'PP692' created successfully",
            "CREATE OR REPLACE PACKAGES POLICY pp692 LANGUAGE PYTHON ALLOWLIST = ($pk) BLOCKLIST = ($pk2) COMMENT = "
                + "$cm");
        engine.execute("DESCRIBE PACKAGES POLICY pp692");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n' for 'COMMENT'",
            "CREATE OR REPLACE PACKAGES POLICY pp692b LANGUAGE PYTHON COMMENT = $n");
        assertAnswer("REFUSED SQL compilation error: | invalid value '$n' for property 'ALLOWLIST'",
            "CREATE OR REPLACE PACKAGES POLICY pp692b LANGUAGE PYTHON ALLOWLIST = ($n)");
        assertAnswer("Packages policy 'PP692B' created successfully",
            "CREATE OR REPLACE PACKAGES POLICY pp692b LANGUAGE PYTHON ADDITIONAL_CREATION_BLOCKLIST = ($pk)");
        assertAnswer("Statement executed successfully.", "ALTER PACKAGES POLICY pp692 SET COMMENT = $hp");
        assertAnswer("Statement executed successfully.", "ALTER PACKAGES POLICY pp692 SET ALLOWLIST = ($pk2)");
        engine.execute("DESCRIBE PACKAGES POLICY pp692");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n' for 'COMMENT'",
            "CREATE OR REPLACE AUTHENTICATION POLICY au692 COMMENT = $n");
        assertAnswer("Authentication policy 'AU692' created successfully",
            "CREATE OR REPLACE AUTHENTICATION POLICY au692 COMMENT = $nul");
        assertAnswer("Authentication policy 'AU692B' created successfully",
            "CREATE OR REPLACE AUTHENTICATION POLICY au692b AUTHENTICATION_METHODS = ($am)");
        assertAnswer("Authentication policy 'AU692C' created successfully",
            "CREATE OR REPLACE AUTHENTICATION POLICY au692c MFA_ENROLLMENT = $me");
        assertAnswer("Authentication policy 'AU692D' created successfully",
            "CREATE OR REPLACE AUTHENTICATION POLICY au692d CLIENT_TYPES = ($ct)");
        engine.execute("DESCRIBE AUTHENTICATION POLICY au692b");
        assertAnswer("Authentication policy 'AU692' created successfully",
            "CREATE OR REPLACE AUTHENTICATION POLICY au692 COMMENT = $cm");
        assertAnswer("Statement executed successfully.", "ALTER AUTHENTICATION POLICY au692 SET COMMENT = $hp");
        assertAnswer("Statement executed successfully.", "ALTER AUTHENTICATION POLICY au692 SET MFA_ENROLLMENT = $me");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n' for 'COMMENT'",
            "ALTER AUTHENTICATION POLICY au692 SET COMMENT = $n");
        engine.execute("SHOW AUTHENTICATION POLICIES LIKE 'AU692'");
        assertAnswer("AU692|example.com:443", "SELECT \"name\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }

    @Test
    public void spellingsAndNothing() {
        assertAnswer("Statement executed successfully.",
            "SET (cm, md2, ty2, md3, n, b, r1) = ('note693', 'egress', 'host_port', 'INGRESS', 5, TRUE, 'PUBLIC')");
        assertAnswer("Statement executed successfully.", "SET nul = NULL");
        assertAnswer("REFUSED SQL compilation error: The network rule mode INGRESS is not supported by the network "
                + "rule type HOST_PORT. The network rule mode must be one of [EGRESS, "
                + "DATA_CONNECTIVITY_PROXY_EGRESS]. | ",
            "CREATE OR REPLACE NETWORK RULE nr693 TYPE = HOST_PORT MODE = 'note693' VALUE_LIST = ('example.com')");
        assertAnswer("REFUSED SQL compilation error: The network rule mode INGRESS is not supported by the network "
                + "rule type HOST_PORT. The network rule mode must be one of [EGRESS, "
                + "DATA_CONNECTIVITY_PROXY_EGRESS]. | ",
            "CREATE OR REPLACE NETWORK RULE nr693 TYPE = HOST_PORT MODE = note693 VALUE_LIST = ('example.com')");
        assertAnswer("Network rule NR693 is created.",
            "CREATE OR REPLACE NETWORK RULE nr693 TYPE = HOST_PORT MODE = 'EGRESS' VALUE_LIST = ('example.com')");
        assertAnswer("Network rule NR693 is created.",
            "CREATE OR REPLACE NETWORK RULE nr693 TYPE = HOST_PORT MODE = $md2 VALUE_LIST = ('example.com')");
        assertAnswer("Network rule NR693 is created.",
            "CREATE OR REPLACE NETWORK RULE nr693 TYPE = 'HOST_PORT' MODE = EGRESS VALUE_LIST = ('example.com')");
        assertAnswer("Network rule NR693 is created.",
            "CREATE OR REPLACE NETWORK RULE nr693 TYPE = $ty2 MODE = EGRESS VALUE_LIST = ('example.com')");
        assertAnswer("REFUSED The network rule mode must be one of the snowflake supported network rule mode.",
            "CREATE OR REPLACE NETWORK RULE nr693b TYPE = IPV4 MODE = $cm VALUE_LIST = ('10.0.0.1')");
        engine.execute("SHOW NETWORK RULES LIKE 'NR693B'");
        assertAnswer("", "SELECT \"name\", \"type\", \"mode\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("REFUSED The network rule mode must be one of the snowflake supported network rule mode.",
            "CREATE OR REPLACE NETWORK RULE nr693b TYPE = IPV4 MODE = 'note693' VALUE_LIST = ('10.0.0.1')");
        assertAnswer("REFUSED The network rule mode must be one of the snowflake supported network rule mode.",
            "CREATE OR REPLACE NETWORK RULE nr693b TYPE = IPV4 MODE = note693 VALUE_LIST = ('10.0.0.1')");
        assertAnswer("Network rule NR693B is created.",
            "CREATE OR REPLACE NETWORK RULE nr693b TYPE = IPV4 MODE = $md3 VALUE_LIST = ('10.0.0.1')");
        assertAnswer("REFUSED The network rule type must be one of the snowflake supported network rule type.",
            "CREATE OR REPLACE NETWORK RULE nr693 TYPE = $b MODE = EGRESS VALUE_LIST = ('example.com')");
        assertAnswer("REFUSED The network rule type must be one of the snowflake supported network rule type.",
            "CREATE OR REPLACE NETWORK RULE nr693 TYPE = $nul MODE = EGRESS VALUE_LIST = ('example.com')");
        assertAnswer("REFUSED SQL compilation error: The network rule mode INGRESS is not supported by the network "
                + "rule type HOST_PORT. The network rule mode must be one of [EGRESS, "
                + "DATA_CONNECTIVITY_PROXY_EGRESS]. | ",
            "CREATE OR REPLACE NETWORK RULE nr693 TYPE = HOST_PORT MODE = $md3 VALUE_LIST = ('example.com')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 44 | Session variable '$NOSUCH693' does "
                + "not exist",
            "CREATE OR REPLACE NETWORK RULE nr693 TYPE = $nosuch693 MODE = EGRESS VALUE_LIST = ('example.com')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 107 | Session variable '$NOSUCH693' "
                + "does not exist",
            "CREATE OR REPLACE NETWORK RULE nr693 TYPE = HOST_PORT MODE = EGRESS VALUE_LIST = ('example.com') "
                + "COMMENT = $nosuch693");
        assertAnswer("Network rule NR693 is created.",
            "CREATE OR REPLACE NETWORK RULE nr693 TYPE = HOST_PORT MODE = EGRESS VALUE_LIST = ('example.com') "
                + "COMMENT = $cm");
        engine.execute("SHOW NETWORK RULES LIKE 'NR693'");
        assertAnswer("NR693|HOST_PORT|EGRESS|note693",
            "SELECT \"name\", \"type\", \"mode\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("REFUSED SQL compilation error: | invalid value 'null' for property 'USERNAME'",
            "CREATE OR REPLACE SECRET se693 TYPE = PASSWORD USERNAME = $b PASSWORD = 'p'");
        assertAnswer("REFUSED SQL compilation error: | invalid value 'null' for property 'USERNAME'",
            "CREATE OR REPLACE SECRET se693 TYPE = PASSWORD USERNAME = $nul PASSWORD = 'p'");
        assertAnswer("REFUSED SQL compilation error: | invalid value 'null' for property 'PASSWORD'",
            "CREATE OR REPLACE SECRET se693 TYPE = PASSWORD USERNAME = 'u' PASSWORD = $b");
        assertAnswer("REFUSED SQL compilation error: | invalid value 'null' for property 'PASSWORD'",
            "CREATE OR REPLACE SECRET se693 TYPE = PASSWORD USERNAME = 'u' PASSWORD = $nul");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n' for 'TYPE'",
            "CREATE OR REPLACE SECRET se693 TYPE = $n SECRET_STRING = 'x'");
        assertAnswer("REFUSED SQL compilation error: | invalid value 'note693' for property 'TYPE'",
            "CREATE OR REPLACE SECRET se693 TYPE = $cm SECRET_STRING = 'x'");
        assertAnswer("Secret SE693 successfully created.",
            "CREATE OR REPLACE SECRET se693 TYPE = 'GENERIC_STRING' SECRET_STRING = 'x'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 69 | Session variable '$NOSUCH693' does "
                + "not exist",
            "CREATE OR REPLACE SECRET se693 TYPE = GENERIC_STRING SECRET_STRING = $nosuch693");
        assertAnswer("Secret SE693 successfully created.",
            "CREATE OR REPLACE SECRET se693 TYPE = GENERIC_STRING SECRET_STRING = 'x'");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n' for 'SECRET_STRING'",
            "ALTER SECRET se693 SET SECRET_STRING = $n");
        assertAnswer("REFUSED SQL compilation error: | invalid value 'null' for property 'SECRET_STRING'",
            "ALTER SECRET se693 SET SECRET_STRING = $nul");
        assertAnswer("Statement executed successfully.", "ALTER SECRET se693 SET COMMENT = $cm");
        engine.execute("SHOW SECRETS LIKE 'SE693'");
        assertAnswer("SE693|note693|GENERIC_STRING",
            "SELECT \"name\", \"comment\", \"secret_type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("Authentication policy 'AU693' created successfully",
            "CREATE OR REPLACE AUTHENTICATION POLICY au693 MFA_ENROLLMENT = $b");
        assertAnswer("Authentication policy 'AU693' created successfully",
            "CREATE OR REPLACE AUTHENTICATION POLICY au693 MFA_ENROLLMENT = $nul");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n' for 'MFA_ENROLLMENT'",
            "CREATE OR REPLACE AUTHENTICATION POLICY au693 MFA_ENROLLMENT = $n");
        assertAnswer("REFUSED SQL Compilation Error: note693 invalid for MFA_ENROLLMENT. ",
            "CREATE OR REPLACE AUTHENTICATION POLICY au693 MFA_ENROLLMENT = $cm");
        assertAnswer("REFUSED SQL Compilation Error: NOTE693 invalid for Authentication Methods. ",
            "CREATE OR REPLACE AUTHENTICATION POLICY au693 AUTHENTICATION_METHODS = ($cm)");
        assertAnswer("REFUSED SQL compilation error: | invalid value '$n' for property 'AUTHENTICATION_METHODS'",
            "CREATE OR REPLACE AUTHENTICATION POLICY au693 AUTHENTICATION_METHODS = ($n)");
        assertAnswer("REFUSED SQL compilation error: | invalid value '$nul' for property 'CLIENT_TYPES'",
            "CREATE OR REPLACE AUTHENTICATION POLICY au693 CLIENT_TYPES = ($nul)");
        assertAnswer("REFUSED SQL Compilation Error: NOTE693 invalid for Authentication Methods. ",
            "CREATE OR REPLACE AUTHENTICATION POLICY au693 AUTHENTICATION_METHODS = ('PASSWORD', $cm)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 72 | Session variable '$NOSUCH693' does "
                + "not exist",
            "CREATE OR REPLACE AUTHENTICATION POLICY au693 AUTHENTICATION_METHODS = ($nosuch693)");
        assertAnswer("REFUSED SQL compilation error: | invalid value '$b' for property 'BLOCKLIST'",
            "CREATE OR REPLACE PACKAGES POLICY pp693 LANGUAGE PYTHON BLOCKLIST = ($b)");
        assertAnswer("REFUSED SQL compilation error: | invalid value '$nul' for property 'ALLOWLIST'",
            "CREATE OR REPLACE PACKAGES POLICY pp693 LANGUAGE PYTHON ALLOWLIST = ($nul)");
        assertAnswer("Packages policy 'PP693' created successfully",
            "CREATE OR REPLACE PACKAGES POLICY pp693 LANGUAGE PYTHON ALLOWLIST = ($cm, 'numpy')");
        assertAnswer("Session policy 'SP693' created successfully.",
            "CREATE OR REPLACE SESSION POLICY sp693 ALLOWED_SECONDARY_ROLES = ($r1)");
        assertAnswer("Session policy 'SP693' created successfully.",
            "CREATE OR REPLACE SESSION POLICY sp693 BLOCKED_SECONDARY_ROLES = ($r1)");
        assertAnswer("REFUSED SQL compilation error: | invalid value '$n' for property 'ALLOWED_SECONDARY_ROLES'",
            "CREATE OR REPLACE SESSION POLICY sp693 ALLOWED_SECONDARY_ROLES = ($n)");
        assertAnswer("REFUSED Invalid value specified for property 'PASSWORD_MIN_LENGTH'",
            "CREATE OR REPLACE PASSWORD POLICY pw693 PASSWORD_MIN_LENGTH = ($cm)");
        assertAnswer("Network rule NR693B is created.",
            "CREATE OR REPLACE NETWORK RULE nr693b TYPE = IPV4 MODE = INGRESS VALUE_LIST = ('10.0.0.1')");
        assertAnswer("REFUSED SQL compilation error: | invalid value '$n' for property 'VALUE_LIST'",
            "ALTER NETWORK RULE nr693b SET VALUE_LIST = ($n)");
        assertAnswer("REFUSED SQL compilation error: | invalid value '[note693]' for property 'VALUE_LIST'",
            "ALTER NETWORK RULE nr693b SET VALUE_LIST = ($cm)");
        assertAnswer("REFUSED SQL compilation error: | invalid property 'TYPE' for 'NETWORK_RULE'",
            "ALTER NETWORK RULE nr693b SET TYPE = $cm");
        assertAnswer("REFUSED SQL compilation error: | invalid property 'MODE' for 'NETWORK_RULE'",
            "ALTER NETWORK RULE nr693b SET MODE = $cm");
    }

    @Test
    public void unknownModesAndLeftOutChoices() {
        assertAnswer("Statement executed successfully.", "SET (cm, b) = ('note694', TRUE)");
        assertAnswer("Statement executed successfully.", "SET nul = NULL");
        assertAnswer("REFUSED SQL compilation error: The network rule mode INGRESS is not supported by the network "
                + "rule type PRIVATE_HOST_PORT. The network rule mode must be one of [EGRESS]. | ",
            "CREATE OR REPLACE NETWORK RULE nr694 TYPE = PRIVATE_HOST_PORT MODE = note694 VALUE_LIST = "
                + "('example.com')");
        assertAnswer("REFUSED The network rule mode must be one of the snowflake supported network rule mode.",
            "CREATE OR REPLACE NETWORK RULE nr694 TYPE = AWSVPCEID MODE = note694 VALUE_LIST = "
                + "('vpce-0123456789abcdef0')");
        assertAnswer("REFUSED The network rule mode must be one of the snowflake supported network rule mode.",
            "CREATE OR REPLACE NETWORK RULE nr694 TYPE = IPV6 MODE = note694 VALUE_LIST = ('::1')");
        assertAnswer("REFUSED SQL compilation error: The network rule mode INGRESS is not supported by the network "
                + "rule type HOST_PORT. The network rule mode must be one of [EGRESS, "
                + "DATA_CONNECTIVITY_PROXY_EGRESS]. | ",
            "CREATE OR REPLACE NETWORK RULE nr694 TYPE = HOST_PORT  VALUE_LIST = ('example.com')");
        assertAnswer("REFUSED SQL compilation error: The network rule mode INTERNAL_STAGE is not supported by the "
                + "network rule type HOST_PORT. The network rule mode must be one of [EGRESS, "
                + "DATA_CONNECTIVITY_PROXY_EGRESS]. | ",
            "CREATE OR REPLACE NETWORK RULE nr694 TYPE = HOST_PORT MODE = INTERNAL_STAGE VALUE_LIST = ('example.com')");
        assertAnswer("REFUSED The network rule type must be one of the snowflake supported network rule type.",
            "CREATE OR REPLACE NETWORK RULE nr694 TYPE = note694 MODE = EGRESS VALUE_LIST = ('example.com')");
        assertAnswer("REFUSED The network rule type must be one of the snowflake supported network rule type.",
            "CREATE OR REPLACE NETWORK RULE nr694 TYPE = note694 MODE = note694 VALUE_LIST = ('example.com')");
        assertAnswer("REFUSED SQL compilation error: The network rule mode INGRESS is not supported by the network "
                + "rule type HOST_PORT. The network rule mode must be one of [EGRESS, "
                + "DATA_CONNECTIVITY_PROXY_EGRESS]. | ",
            "CREATE OR REPLACE NETWORK RULE nr694 TYPE = HOST_PORT MODE = $cm VALUE_LIST = ('example.com')");
        assertAnswer("Network rule NR694 is created.",
            "CREATE OR REPLACE NETWORK RULE nr694 TYPE = HOST_PORT MODE = EGRESS VALUE_LIST = ('example.com') "
                + "COMMENT = $b");
        engine.execute("SHOW NETWORK RULES LIKE 'NR694'");
        assertAnswer("NR694|", "SELECT \"name\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("Authentication policy 'AU694' created successfully",
            "CREATE OR REPLACE AUTHENTICATION POLICY au694 MFA_ENROLLMENT = $nul COMMENT = $b");
        engine.execute("DESCRIBE AUTHENTICATION POLICY au694");
        assertAnswer("COMMENT|null ;; MFA_ENROLLMENT|OPTIONAL",
            "SELECT \"property\", \"value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"property\" IN "
                + "('MFA_ENROLLMENT', 'COMMENT')");
        assertAnswer("Authentication policy 'AU694' created successfully",
            "CREATE OR REPLACE AUTHENTICATION POLICY au694 MFA_ENROLLMENT = $b");
        engine.execute("DESCRIBE AUTHENTICATION POLICY au694");
        assertAnswer("COMMENT|null ;; MFA_ENROLLMENT|OPTIONAL",
            "SELECT \"property\", \"value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"property\" IN "
                + "('MFA_ENROLLMENT', 'COMMENT')");
        assertAnswer("Authentication policy 'AU694' created successfully",
            "CREATE OR REPLACE AUTHENTICATION POLICY au694 MFA_ENROLLMENT = 'OPTIONAL'");
        assertAnswer("Statement executed successfully.", "ALTER AUTHENTICATION POLICY au694 SET MFA_ENROLLMENT = $nul");
        engine.execute("DESCRIBE AUTHENTICATION POLICY au694");
        assertAnswer("COMMENT|null ;; MFA_ENROLLMENT|OPTIONAL",
            "SELECT \"property\", \"value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"property\" IN "
                + "('MFA_ENROLLMENT', 'COMMENT')");
        assertAnswer("Authentication policy 'AU694B' created successfully",
            "CREATE OR REPLACE AUTHENTICATION POLICY au694b");
    }
}
