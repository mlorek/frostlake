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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.SqlCompilationError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * What a network rule's TYPE lets its MODE and its VALUE_LIST be (live-verified). Each type takes its own modes,
 * told back in the refusal under the type's own spelling, and reads its values its own way; the values that fail
 * are echoed, the ones that pass are not. A value is trimmed, and a comma inside one separates several.
 *
 * <pre>
 *   IPV4                INGRESS, POSTGRES_INGRESS, POSTGRES_EGRESS   a.b.c.d or a.b.c.d/n, each number 0 to 255, n 0 to 32
 *   IPV6                INGRESS                                      an IPv6 address, or one with a /n of 0 to 128
 *   AWSVPCEID           INGRESS, INTERNAL_STAGE                      vpce- and 8 or 17 lower-case hex digits
 *   AZURELINKID         INGRESS                                      a whole number
 *   GCPPSCID            INGRESS                                      a whole number
 *   COMPUTE_POOL        INGRESS                                      not judged
 *   HOST_PORT           EGRESS, DATA_CONNECTIVITY_PROXY_EGRESS       host[:port], see {@link #isHostPort}
 *   PRIVATE_HOST_PORT   EGRESS                                       none taken: every value is refused
 * </pre>
 *
 * <p>The account resolves a HOST_PORT rule's hosts when the rule is made and refuses one it cannot resolve. With no
 * resolver at hand the name's shape is read instead, so a well-formed name nothing answers to is taken.
 */
final class NetworkRuleChecks {

    private static final String INGRESS = "INGRESS";

    private static final Map<String, List<String>> MODES = new HashMap<>();
    private static final Map<String, String> TYPE_SPELLINGS = new HashMap<>();
    private static final List<String> KNOWN_MODES = Collections.unmodifiableList(Arrays.asList("INGRESS",
        "INTERNAL_STAGE", "SNOWFLAKE_MANAGED_STORAGE_VOLUME", "EGRESS", "POSTGRES_INGRESS", "POSTGRES_EGRESS",
        "DATA_CONNECTIVITY_PROXY_EGRESS"));

    private static final Pattern VPC_ENDPOINT = Pattern.compile("vpce-([0-9a-f]{8}|[0-9a-f]{17})");
    private static final Pattern DIGITS = Pattern.compile("[0-9]+");
    private static final Pattern HEX_GROUP = Pattern.compile("[0-9A-Fa-f]{1,4}");

    /** One DNS label: letters and digits, with hyphens inside it only. */
    private static final Pattern HOST_LABEL = Pattern.compile("[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?");

    /** A label holding the one wildcard a host pattern may carry. */
    private static final Pattern WILDCARD_LABEL = Pattern.compile("[A-Za-z0-9-]*\\*[A-Za-z0-9-]*");

    /** The top-level domains reserved never to resolve. */
    private static final List<String> RESERVED_TOP_LEVEL_DOMAINS = Collections.unmodifiableList(Arrays.asList(
        "example", "invalid", "localhost", "test"));

    static {
        type("IPV4", "IPv4", "INGRESS", "POSTGRES_INGRESS", "POSTGRES_EGRESS");
        type("IPV6", "IPv6", "INGRESS");
        type("AWSVPCEID", "AwsVpceId", "INGRESS", "INTERNAL_STAGE", "SNOWFLAKE_MANAGED_STORAGE_VOLUME");
        type("AZURELINKID", "AzureLinkId", "INGRESS");
        type("GCPPSCID", "GcpPscId", "INGRESS");
        type("COMPUTE_POOL", "COMPUTE_POOL", "INGRESS");
        type("HOST_PORT", "HOST_PORT", "EGRESS", "DATA_CONNECTIVITY_PROXY_EGRESS");
        type("PRIVATE_HOST_PORT", "PRIVATE_HOST_PORT", "EGRESS");
    }

    private NetworkRuleChecks() {
    }

    private static void type(final String type, final String spelling, final String... modes) {
        MODES.put(type, Collections.unmodifiableList(Arrays.asList(modes)));
        TYPE_SPELLINGS.put(type, spelling);
    }

    /**
     * Whether a word names a mode at all.
     *
     * @param mode the word, upper-cased
     * @return true for a mode some type takes
     */
    static boolean isMode(final String mode) {
        return KNOWN_MODES.contains(mode);
    }

    /** The refusal of a MODE no type takes. */
    static RuntimeException unknownMode() {
        return new RuntimeException("The network rule mode must be one of the snowflake supported network rule mode.");
    }

    /** The refusal of a TYPE that is none of the account's. */
    static RuntimeException unknownType() {
        return new RuntimeException("The network rule type must be one of the snowflake supported network rule type.");
    }

    /**
     * Refuses a MODE no type takes. Such a word stands for INGRESS while the rule's type judges its mode, so a type
     * that takes no INGRESS refuses it in INGRESS's name first (live-verified): {@code TYPE = HOST_PORT MODE = nope}
     * is refused as {@code The network rule mode INGRESS is not supported by the network rule type HOST_PORT. …},
     * {@code TYPE = IPV4 MODE = nope} as no mode at all.
     *
     * @param type the rule's TYPE, or null where the statement gives none
     * @param mode its MODE, upper-cased
     */
    static void requireKnownMode(final String type, final String mode) {
        if (isMode(mode)) {
            return;
        }
        final List<String> taken = type == null ? null : MODES.get(type);
        if (taken != null && !taken.contains(INGRESS)) {
            requireMode(type, INGRESS);
        }
        throw unknownMode();
    }

    /**
     * Refuses a mode the type does not take.
     *
     * @param type the rule's TYPE
     * @param mode its MODE
     */
    static void requireMode(final String type, final String mode) {
        final List<String> taken = MODES.get(type);
        if (taken == null) {
            return;
        }
        if (!taken.contains(mode)) {
            throw new RuntimeException(SqlCompilationError.PREFIX + " The network rule mode " + mode
                + " is not supported by the network rule type " + TYPE_SPELLINGS.get(type)
                + ". The network rule mode must be one of " + taken + ".\n");
        }
        if ("AWSVPCEID".equals(type) && "SNOWFLAKE_MANAGED_STORAGE_VOLUME".equals(mode)) {
            // Told among the type's modes, and refused as one nothing takes.
            throw unknownMode();
        }
    }

    /**
     * Refuses a value list holding a value the type cannot read, echoing the values that fail.
     *
     * @param type   the rule's TYPE
     * @param values its VALUE_LIST
     */
    static void requireValues(final String type, final List<String> values) {
        final List<String> failing = new ArrayList<>();
        for (final String written : values) {
            final String[] parts = written.split(",", -1);
            for (final String part : parts) {
                final String value = part.trim();
                if (!reads(type, value)) {
                    failing.add(value);
                }
            }
        }
        if (failing.isEmpty()) {
            return;
        }
        final String refusal = "invalid value '" + failing + "' for property 'VALUE_LIST'";
        throw new RuntimeException(SqlCompilationError.of("HOST_PORT".equals(type) ? refusal
            + ", Reason: One or more values might be an unresolvable host name. Verify all hosts are resolvable, or"
            + " use a wildcard pattern." : refusal));
    }

    private static boolean reads(final String type, final String value) {
        switch (type) {
            case "IPV4":
                return isIpv4Range(value);
            case "IPV6":
                return isIpv6Range(value);
            case "AWSVPCEID":
                return VPC_ENDPOINT.matcher(value).matches();
            case "AZURELINKID":
                return DIGITS.matcher(value).matches() && fits(value, false);
            case "GCPPSCID":
                return DIGITS.matcher(value).matches() && fits(value, true);
            case "HOST_PORT":
                return isHostPort(value);
            case "PRIVATE_HOST_PORT":
                // A host and port, a bare host, a private-link host and a wildcard were all refused.
                return false;
            default:
                return true;
        }
    }

    /**
     * How many values a value list holds, as SHOW NETWORK RULES counts them: a comma inside a value separates
     * several (live-verified).
     *
     * @param values the VALUE_LIST as written
     * @return the count
     */
    static long entries(final List<String> values) {
        long count = 0;
        for (final String written : values) {
            count += written.split(",", -1).length;
        }
        return count;
    }

    /** Whether a run of digits is a 64-bit number: a signed one, or an unsigned one. */
    private static boolean fits(final String digits, final boolean unsigned) {
        try {
            if (unsigned) {
                Long.parseUnsignedLong(digits);
            } else {
                Long.parseLong(digits);
            }
            return true;
        } catch (final NumberFormatException tooLarge) {
            return false;
        }
    }

    /** A whole number in a range, read as leniently as the account reads it: a leading + and leading zeros pass. */
    private static boolean isNumberUpTo(final String text, final int most) {
        try {
            final int number = Integer.parseInt(text.trim());
            return number >= 0 && number <= most && !text.trim().startsWith("-");
        } catch (final NumberFormatException noNumber) {
            return false;
        }
    }

    /** {@code a.b.c.d}, or {@code a.b.c.d/n}: four numbers of 0 to 255 and a prefix of 0 to 32, which may be empty. */
    private static boolean isIpv4Range(final String value) {
        final String[] halves = value.split("/");
        if (halves.length == 0 || halves.length > 2) {
            return false;
        }
        if (halves.length == 2 && !isNumberUpTo(halves[1], 32)) {
            return false;
        }
        return isIpv4(halves[0].trim());
    }

    private static boolean isIpv4(final String address) {
        final String[] numbers = address.split("\\.");
        if (numbers.length != 4) {
            return false;
        }
        for (final String number : numbers) {
            if (!isNumberUpTo(number, 255)) {
                return false;
            }
        }
        return true;
    }

    /** An IPv6 address, or one with a prefix of 0 to 128. */
    private static boolean isIpv6Range(final String value) {
        final String[] halves = value.split("/");
        if (halves.length == 0 || halves.length > 2) {
            return false;
        }
        if (halves.length == 2 && !isNumberUpTo(halves[1], 128)) {
            return false;
        }
        return isIpv6(halves[0].trim());
    }

    private static boolean isIpv6(final String address) {
        if (address.indexOf(':') < 0) {
            return false;
        }
        final int gap = address.indexOf("::");
        if (gap >= 0 && address.indexOf("::", gap + 1) >= 0) {
            return false;
        }
        final List<String> groups = new ArrayList<>();
        if (gap >= 0) {
            groups.addAll(groupsOf(address.substring(0, gap)));
            groups.addAll(groupsOf(address.substring(gap + 2)));
        } else {
            groups.addAll(groupsOf(address));
        }
        int width = 0;
        for (int i = 0; i < groups.size(); i++) {
            final String group = groups.get(i);
            if (i == groups.size() - 1 && group.indexOf('.') >= 0 && isIpv4(group)) {
                width += 2;
            } else if (HEX_GROUP.matcher(group).matches()) {
                width++;
            } else {
                return false;
            }
        }
        return gap >= 0 ? width < 8 : width == 8;
    }

    private static List<String> groupsOf(final String text) {
        return text.isEmpty() ? new ArrayList<String>() : Arrays.asList(text.split(":", -1));
    }

    /**
     * Whether a value names a host and, after a colon, a port the account would read: a port is empty or a number of
     * 0 to 65535 written without leading zeros; a host is a public IPv4 address — no loopback, private or
     * link-local one — or a name of two or more DNS labels, whose last is not numeric and is no top-level domain
     * reserved never to resolve. One label of a name may hold a wildcard, when two labels follow it.
     *
     * @param value the value, trimmed
     * @return true when it reads as a host and a port
     */
    static boolean isHostPort(final String value) {
        String host = value;
        final int colon = value.indexOf(':');
        if (colon >= 0) {
            final String port = value.substring(colon + 1);
            if (!port.isEmpty() && (!DIGITS.matcher(port).matches() || port.length() > 5
                    || port.length() > 1 && port.charAt(0) == '0' || Integer.parseInt(port) > 65535)) {
                return false;
            }
            host = value.substring(0, colon);
        }
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        final String[] labels = host.split("\\.", -1);
        if (labels.length < 2) {
            return false;
        }
        if (isIpv4(host) && labels.length == 4 && DIGITS.matcher(labels[3]).matches()) {
            return isPublicAddress(labels);
        }
        int wildcard = -1;
        for (int i = 0; i < labels.length; i++) {
            if (labels[i].indexOf('*') >= 0) {
                if (wildcard >= 0 || !WILDCARD_LABEL.matcher(labels[i]).matches()) {
                    return false;
                }
                wildcard = i;
            } else if (!HOST_LABEL.matcher(labels[i]).matches()) {
                return false;
            }
        }
        if (wildcard >= 0 && labels.length - 1 - wildcard < 2) {
            return false;
        }
        final String topLevel = labels[labels.length - 1].toLowerCase(Locale.ROOT);
        return !DIGITS.matcher(topLevel).matches() && !RESERVED_TOP_LEVEL_DOMAINS.contains(topLevel);
    }

    /** Whether an IPv4 address is one a rule may name: not a loopback, a private or a link-local one. */
    private static boolean isPublicAddress(final String[] numbers) {
        final int first = Integer.parseInt(numbers[0].trim());
        final int second = Integer.parseInt(numbers[1].trim());
        if (first == 10 || first == 127) {
            return false;
        }
        if (first == 172 && second >= 16 && second <= 31) {
            return false;
        }
        return !(first == 192 && second == 168) && !(first == 169 && second == 254);
    }
}
