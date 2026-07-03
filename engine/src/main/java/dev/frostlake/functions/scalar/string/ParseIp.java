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

package dev.frostlake.functions.scalar.string;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.VariantType;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigInteger;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * PARSE_IP(ip_address, type [, permissive]) — parses an IPv4/IPv6 address (optionally in CIDR
 * {@code addr/prefix} form) into a VARIANT OBJECT, matching Snowflake. {@code type} is {@code 'INET'}
 * or {@code 'CIDR'} (case-insensitive) and is echoed lowercased as {@code ip_type}.
 *
 * <p>For an IPv4 address the keys are, in order: {@code family} (4), {@code host} (the address without
 * the prefix), {@code ip_fields} (a 4-element array — the 32-bit address then three zeros), {@code
 * ip_type}, {@code ipv4} (the address as a 32-bit integer), {@code ipv4_range_end}, {@code
 * ipv4_range_start} (the CIDR range as 32-bit integers), {@code netmask_prefix_length} (defaulting to
 * 32 when no prefix is given), and {@code snowflake$type} ({@code "ip_address"}). Example:
 * {@code PARSE_IP('192.168.242.188/24','INET')} yields {@code ipv4} 3232297660 with range
 * 3232297472..3232297727.
 *
 * <p>IPv6 support is minimal: an address containing {@code ':'} yields {@code family} 6 with {@code
 * host}, {@code ip_type}, {@code netmask_prefix_length} (default 128), {@code snowflake$type}, and
 * best-effort {@code hex_ipv6} / {@code hex_ipv6_range_start} / {@code hex_ipv6_range_end} (a 32-char
 * lowercase hex rendering of the 128-bit value / range — the exact Snowflake hex format is
 * undocumented).
 *
 * <p>A malformed address (or bad {@code type}) raises; when {@code permissive} is 1 it instead returns
 * an object with only an {@code error} key. NULL address yields NULL.
 */
public class ParseIp extends BuiltInFunction {

    public ParseIp() { super("PARSE_IP", VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final boolean permissive = args.size() > 2 && args.get(2) != null
            && ((Number) args.get(2)).intValue() != 0;
        final String type = args.get(1) == null ? "inet" : args.get(1).toString().trim().toLowerCase();
        if (!type.equals("inet") && !type.equals("cidr")) {
            return onError("invalid type '" + args.get(1) + "' (expected INET or CIDR)", permissive);
        }
        final String input = args.get(0).toString().trim();
        try {
            if (input.indexOf(':') >= 0) {
                return parseIpv6(input, type);
            }
            return parseIpv4(input, type);
        } catch (final IllegalArgumentException e) {
            return onError(e.getMessage(), permissive);
        }
    }

    private String parseIpv4(final String input, final String type) {
        final int slash = input.indexOf('/');
        final String addrPart = slash < 0 ? input : input.substring(0, slash);
        int prefix = 32;
        if (slash >= 0) {
            final String prefixStr = input.substring(slash + 1).trim();
            try {
                prefix = Integer.parseInt(prefixStr);
            } catch (final NumberFormatException e) {
                throw new IllegalArgumentException("invalid netmask prefix '" + prefixStr + "'");
            }
            if (prefix < 0 || prefix > 32) {
                throw new IllegalArgumentException("IPv4 netmask prefix out of range: " + prefix);
            }
        }
        final String[] octets = addrPart.split("\\.", -1);
        if (octets.length != 4) {
            throw new IllegalArgumentException("invalid IPv4 address '" + addrPart + "'");
        }
        long ipv4 = 0L;
        for (int i = 0; i < 4; i++) {
            final int octet;
            try {
                octet = Integer.parseInt(octets[i].trim());
            } catch (final NumberFormatException e) {
                throw new IllegalArgumentException("invalid IPv4 octet '" + octets[i] + "'");
            }
            if (octet < 0 || octet > 255) {
                throw new IllegalArgumentException("IPv4 octet out of range: " + octet);
            }
            ipv4 = (ipv4 << 8) | octet;
        }
        final int hostBits = 32 - prefix;
        final long mask = hostBits >= 32 ? 0L : (0xFFFFFFFFL << hostBits) & 0xFFFFFFFFL;
        final long rangeStart = ipv4 & mask;
        final long rangeEnd = rangeStart | (~mask & 0xFFFFFFFFL);

        final ObjectNode obj = ArrayFunctionHelper.MAPPER.createObjectNode();
        obj.put("family", 4);
        obj.put("host", addrPart);
        final ArrayNode fields = obj.putArray("ip_fields");
        fields.add(ipv4);
        fields.add(0);
        fields.add(0);
        fields.add(0);
        obj.put("ip_type", type);
        obj.put("ipv4", ipv4);
        obj.put("ipv4_range_end", rangeEnd);
        obj.put("ipv4_range_start", rangeStart);
        obj.put("netmask_prefix_length", prefix);
        obj.put("snowflake$type", "ip_address");
        return obj.toString();
    }

    private String parseIpv6(final String input, final String type) {
        final int slash = input.indexOf('/');
        final String addrPart = slash < 0 ? input : input.substring(0, slash);
        int prefix = 128;
        if (slash >= 0) {
            final String prefixStr = input.substring(slash + 1).trim();
            try {
                prefix = Integer.parseInt(prefixStr);
            } catch (final NumberFormatException e) {
                throw new IllegalArgumentException("invalid netmask prefix '" + prefixStr + "'");
            }
            if (prefix < 0 || prefix > 128) {
                throw new IllegalArgumentException("IPv6 netmask prefix out of range: " + prefix);
            }
        }
        final byte[] bytes;
        try {
            final InetAddress addr = InetAddress.getByName(addrPart);
            if (!(addr instanceof Inet6Address)) {
                throw new IllegalArgumentException("not an IPv6 address '" + addrPart + "'");
            }
            bytes = addr.getAddress();
        } catch (final UnknownHostException e) {
            throw new IllegalArgumentException("invalid IPv6 address '" + addrPart + "'");
        }
        BigInteger value = BigInteger.ZERO;
        for (int i = 0; i < bytes.length; i++) {
            value = value.shiftLeft(8).or(BigInteger.valueOf(bytes[i] & 0xFF));
        }
        final BigInteger fullMask = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);
        final int hostBits = 128 - prefix;
        final BigInteger mask = hostBits >= 128 ? BigInteger.ZERO
            : fullMask.shiftLeft(hostBits).and(fullMask);
        final BigInteger rangeStart = value.and(mask);
        final BigInteger rangeEnd = rangeStart.or(mask.xor(fullMask));

        final ObjectNode obj = ArrayFunctionHelper.MAPPER.createObjectNode();
        obj.put("family", 6);
        obj.put("hex_ipv6", toHex128(value));
        obj.put("hex_ipv6_range_end", toHex128(rangeEnd));
        obj.put("hex_ipv6_range_start", toHex128(rangeStart));
        obj.put("host", addrPart);
        obj.put("ip_type", type);
        obj.put("netmask_prefix_length", prefix);
        obj.put("snowflake$type", "ip_address");
        return obj.toString();
    }

    private static String toHex128(final BigInteger value) {
        return String.format("%032x", value);
    }

    private Object onError(final String message, final boolean permissive) {
        if (permissive) {
            final ObjectNode err = ArrayFunctionHelper.MAPPER.createObjectNode();
            err.put("error", message);
            return err.toString();
        }
        throw new RuntimeException("Error parsing IP address: " + message);
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 3; }
}
