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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PARSE_IP(ip_address, type [, permissive]) — parses an IP address into a VARIANT OBJECT. */
public class ParseIpTest extends BaseDatabaseTest {

    private String scalar(final String sql) {
        final Object v = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return v == null ? null : v.toString();
    }

    @Test
    public void parsesDocumentedIpv4Cidr() {
        // Matches the object shown in the Snowflake PARSE_IP documentation for '192.168.242.188/24'.
        assertEquals(
            "{\"family\":4,\"host\":\"192.168.242.188\",\"ip_fields\":[3232297660,0,0,0],"
                + "\"ip_type\":\"inet\",\"ipv4\":3232297660,\"ipv4_range_end\":3232297727,"
                + "\"ipv4_range_start\":3232297472,\"netmask_prefix_length\":24,"
                + "\"snowflake$type\":\"ip_address\"}",
            scalar("SELECT PARSE_IP('192.168.242.188/24', 'INET')"));
    }

    @Test
    public void plainAddressHasNullNetmaskAndNoRange() {
        // Live-verified: a bare address is NOT /32 — the netmask is null and no range is reported.
        assertEquals(
            "{\"family\":4,\"host\":\"10.0.0.5\",\"ip_fields\":[167772165,0,0,0],"
                + "\"ip_type\":\"inet\",\"ipv4\":167772165,"
                + "\"netmask_prefix_length\":null,\"snowflake$type\":\"ip_address\"}",
            scalar("SELECT PARSE_IP('10.0.0.5', 'INET')"));
    }

    @Test
    public void typeArgumentIsEchoedAsIpType() {
        // The 'CIDR' type is echoed lowercased in ip_type. With CIDR the address must BE the
        // network address (host bits zero) — live-verified.
        assertEquals(
            "{\"family\":4,\"host\":\"192.168.242.0\",\"ip_fields\":[3232297472,0,0,0],"
                + "\"ip_type\":\"cidr\",\"ipv4\":3232297472,\"ipv4_range_end\":3232297727,"
                + "\"ipv4_range_start\":3232297472,\"netmask_prefix_length\":24,"
                + "\"snowflake$type\":\"ip_address\"}",
            scalar("SELECT PARSE_IP('192.168.242.0/24', 'CIDR')"));
    }

    @Test
    public void cidrRejectsHostBitsAndOversizedPrefixes() {
        // Live-verified messages: host bits set beyond the prefix, and a prefix past /32.
        final RuntimeException hostBits = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT PARSE_IP('192.168.242.188/24', 'CIDR')");
            }
        });
        assertTrue(hostBits.getMessage().contains("Address does not conform to cidr format"),
            hostBits.getMessage());
        final RuntimeException tooLarge = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT PARSE_IP('192.168.242.0/33', 'CIDR')");
            }
        });
        assertTrue(tooLarge.getMessage().contains("Network range prefix is too large"),
            tooLarge.getMessage());
    }

    @Test
    public void cidrWithoutAPrefixHasNullNetmaskAndNoRange() {
        // Live-verified shape: netmask_prefix_length is null and no range members are reported.
        assertEquals(
            "{\"family\":4,\"host\":\"192.168.242.188\",\"ip_fields\":[3232297660,0,0,0],"
                + "\"ip_type\":\"cidr\",\"ipv4\":3232297660,"
                + "\"netmask_prefix_length\":null,\"snowflake$type\":\"ip_address\"}",
            scalar("SELECT PARSE_IP('192.168.242.188', 'CIDR')"));
    }

    @Test
    public void inetAcceptsHostBitsWithinThePrefix() {
        assertEquals(
            "{\"family\":4,\"host\":\"192.168.242.188\",\"ip_fields\":[3232297660,0,0,0],"
                + "\"ip_type\":\"inet\",\"ipv4\":3232297660,\"ipv4_range_end\":3232297727,"
                + "\"ipv4_range_start\":3232297472,\"netmask_prefix_length\":24,"
                + "\"snowflake$type\":\"ip_address\"}",
            scalar("SELECT PARSE_IP('192.168.242.188/24', 'INET')"));
    }

    @Test
    public void permissiveReturnsErrorObjectOnBadInput() {
        assertEquals("{\"error\":\"Invalid IPv4 address. One or more fields is higher than 255.\"}",
            scalar("SELECT PARSE_IP('999.1.1.1', 'INET', 1)"));
    }

    @Test
    public void nonPermissiveBadInputThrows() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT PARSE_IP('not.an.ip', 'INET')");
            }
        });
    }

    @Test
    public void ipv6YieldsFamily6() {
        // IPv6 support is minimal: family 6 with the host echoed and a 128-bit hex rendering.
        final String out = scalar("SELECT PARSE_IP('2001:db8::1', 'INET')");
        assertTrue(out.contains("\"family\":6"), out);
        assertTrue(out.contains("\"host\":\"2001:db8::1\""), out);
        assertTrue(out.contains("\"hex_ipv6\":"), out);
    }

    @Test
    public void nullInputYieldsNull() {
        assertNull(scalar("SELECT PARSE_IP(NULL, 'INET')"));
    }
}
