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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a network rule's TYPE lets its MODE and its VALUE_LIST be (live-verified): each type takes its own modes,
 * told back in the refusal under the type's own spelling, and reads its values its own way, echoing the ones that
 * fail. A HOST_PORT rule's hosts are judged by their shape here — the account also resolves them, so the names
 * below are ones that resolve.
 */
public class NetworkRuleValuesTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    private void taken(final String tail) {
        engine.execute("CREATE OR REPLACE NETWORK RULE nv_rule " + tail);
    }

    private String refused(final String tail) {
        return refusal("CREATE OR REPLACE NETWORK RULE nv_rule " + tail);
    }

    private static String invalid(final String values) {
        return "invalid value '[" + values + "]' for property 'VALUE_LIST'";
    }

    @Test
    public void aTypeTakesItsOwnModes() {
        final String sentence = " is not supported by the network rule type ";
        assertTrue(refused("TYPE = HOST_PORT VALUE_LIST = ()").contains("The network rule mode INGRESS" + sentence
            + "HOST_PORT. The network rule mode must be one of [EGRESS, DATA_CONNECTIVITY_PROXY_EGRESS]."),
            "a mode left out is INGRESS, which a host rule does not take");
        assertTrue(refused("TYPE = AWSVPCEID MODE = EGRESS VALUE_LIST = ()").contains("The network rule mode EGRESS"
            + sentence + "AwsVpceId. The network rule mode must be one of [INGRESS, INTERNAL_STAGE,"
            + " SNOWFLAKE_MANAGED_STORAGE_VOLUME]."));
        assertTrue(refused("TYPE = IPV4 MODE = INTERNAL_STAGE VALUE_LIST = ()").contains("IPv4. The network rule mode"
            + " must be one of [INGRESS, POSTGRES_INGRESS, POSTGRES_EGRESS]."));
        assertTrue(refused("TYPE = IPV6 MODE = EGRESS VALUE_LIST = ()").contains("IPv6. The network rule mode must be"
            + " one of [INGRESS]."));
        assertTrue(refused("TYPE = AZURELINKID MODE = EGRESS VALUE_LIST = ()").contains("AzureLinkId. The network"
            + " rule mode must be one of [INGRESS]."));
        assertTrue(refused("TYPE = GCPPSCID MODE = EGRESS VALUE_LIST = ()").contains("GcpPscId. The network rule"
            + " mode must be one of [INGRESS]."));
        assertTrue(refused("TYPE = COMPUTE_POOL MODE = EGRESS VALUE_LIST = ()").contains("COMPUTE_POOL. The network"
            + " rule mode must be one of [INGRESS]."));
        assertTrue(refused("TYPE = PRIVATE_HOST_PORT VALUE_LIST = ()").contains("PRIVATE_HOST_PORT. The network rule"
            + " mode must be one of [EGRESS]."));
        assertEquals("The network rule mode must be one of the snowflake supported network rule mode.",
            refused("TYPE = IPV4 MODE = NOSUCH VALUE_LIST = ()"));
        taken("TYPE = HOST_PORT MODE = DATA_CONNECTIVITY_PROXY_EGRESS VALUE_LIST = ()");
        taken("TYPE = AWSVPCEID MODE = INTERNAL_STAGE VALUE_LIST = ()");
        taken("TYPE = IPV4 MODE = POSTGRES_INGRESS VALUE_LIST = ()");
    }

    @Test
    public void anAddressIsFourNumbersAndAPrefix() {
        for (final String address : new String[] {"1.1.1.1", "10.0.0.0/8", "0.0.0.0/0", "10.0.0.1/8", " 1.1.1.1",
            "01.1.1.1", "+1.1.1.1", "1.1.1.1/", "1.1.1.1/032", "1.1.1.1 /8", "255.255.255.255/32"}) {
            taken("TYPE = IPV4 VALUE_LIST = ('" + address + "')");
        }
        for (final String address : new String[] {"999.1.1.1", "1.1.1.256", "abc", "1.1.1", "1.1.1.1.1", "1..1.1",
            "1.1.1.1/33", "1.1.1.1/-1", "1.1.1.1/8/8", "1.1.1.1/a", "0x1.1.1.1", "1.1.1.-1", "::1", "1.1.1.1:80"}) {
            assertTrue(refused("TYPE = IPV4 VALUE_LIST = ('" + address + "')").contains(invalid(address)), address);
        }
        assertTrue(refused("TYPE = IPV4 VALUE_LIST = ('')").contains(invalid("")));
        taken("TYPE = IPV4 VALUE_LIST = ()");
        taken("TYPE = IPV4 VALUE_LIST = ('1.1.1.1', '1.1.1.1')");
        for (final String address : new String[] {"::1", "2001:db8::/32", "2001:db8::1", "::/0"}) {
            taken("TYPE = IPV6 VALUE_LIST = ('" + address + "')");
        }
        for (final String address : new String[] {"abc", "1.1.1.1", "::1/129"}) {
            assertTrue(refused("TYPE = IPV6 VALUE_LIST = ('" + address + "')").contains(invalid(address)), address);
        }
    }

    @Test
    public void onlyTheValuesThatFailAreEchoed() {
        assertTrue(refused("TYPE = IPV4 VALUE_LIST = ('bad1', '1.1.1.1', 'bad2')").contains(invalid("bad1, bad2")));
        assertTrue(refused("TYPE = IPV4 VALUE_LIST = ('1,1,1,1')").contains(invalid("1, 1, 1, 1")),
            "a comma inside a value separates several");
        assertTrue(refused("TYPE = AWSVPCEID VALUE_LIST = ('vpce-12345678', 'x')").contains(invalid("x")));
        assertTrue(refused("TYPE = HOST_PORT MODE = EGRESS VALUE_LIST = ('example.com', 'foo.invalid')").contains(
            invalid("foo.invalid") + ", Reason: One or more values might be an unresolvable host name. Verify all"
                + " hosts are resolvable, or use a wildcard pattern."));
    }

    @Test
    public void anEndpointOrALinkIsAnId() {
        taken("TYPE = AWSVPCEID VALUE_LIST = ('vpce-0fa383eb170331202')");
        taken("TYPE = AWSVPCEID VALUE_LIST = ('vpce-12345678')");
        for (final String id : new String[] {"vpce-1", "vpce-0FA383EB170331202", "VPCE-0fa383eb170331202",
            "vpce-0fa383eb17033120", "vpce-0fa383eb1703312022", "abc"}) {
            assertTrue(refused("TYPE = AWSVPCEID VALUE_LIST = ('" + id + "')").contains(invalid(id)), id);
        }
        for (final String id : new String[] {"123456789", "0", "0012", "1234567890123456789"}) {
            taken("TYPE = AZURELINKID VALUE_LIST = ('" + id + "')");
        }
        for (final String id : new String[] {"abc123", "-1", "1.5", "12345678901234567890"}) {
            assertTrue(refused("TYPE = AZURELINKID VALUE_LIST = ('" + id + "')").contains(invalid(id)), id);
        }
        taken("TYPE = GCPPSCID VALUE_LIST = ('12345678901234567890')");
        for (final String id : new String[] {"abc", "-1", "1.5"}) {
            assertTrue(refused("TYPE = GCPPSCID VALUE_LIST = ('" + id + "')").contains(invalid(id)), id);
        }
    }

    @Test
    public void aHostIsANameOrAPublicAddressWithAPort() {
        for (final String host : new String[] {"example.com", "example.com:443", "EXAMPLE.COM", "example.com.",
            "example.com:", "example.com:0", "google.com:65535", " google.com", "*.example.com", "*.co.uk",
            "sub.*.example.com", "ex*.example.com", "1.2.3.4", "1.2.3.4:80", "0.0.0.0:443"}) {
            taken("TYPE = HOST_PORT MODE = EGRESS VALUE_LIST = ('" + host + "')");
        }
        for (final String host : new String[] {"foo.invalid", "localhost", "example", "http://example.com",
            "example.com:65536", "example.com:abc", "google.com:00080", "google.com: 80", "exa mple.com",
            "a_b.example.com", ".example.com", "example..com", "-a.example.com", "*", "*.com", "*example.com",
            "a.*.com", "*.*.example.com", "*.example.*", "10.0.0.1", "127.0.0.1", "256.2.3.4", "1.2.3.4.5",
            "1.2.3.4/8", "::1"}) {
            assertTrue(refused("TYPE = HOST_PORT MODE = EGRESS VALUE_LIST = ('" + host + "')").contains(invalid(host)),
                host);
        }
    }

    @Test
    public void alterAndCreateOrAlterJudgeTheValuesToo() {
        engine.execute("CREATE NETWORK RULE nv_set TYPE = IPV4 VALUE_LIST = ('1.1.1.1')");
        assertTrue(refusal("ALTER NETWORK RULE nv_set SET VALUE_LIST = ('bad')").contains(invalid("bad")));
        assertTrue(refusal("CREATE OR ALTER NETWORK RULE nv_set TYPE = IPV4 VALUE_LIST = ('bad')").contains(
            invalid("bad")));
        engine.execute("ALTER NETWORK RULE nv_set SET VALUE_LIST = ('10.0.0.0/8', '2.2.2.2')");
        assertEquals("10.0.0.0/8,2.2.2.2", engine.executeQuery(
            "DESC NETWORK RULE nv_set ->> SELECT \"value_list\" FROM $1").getRows().get(0).getValue(0));
    }
}
