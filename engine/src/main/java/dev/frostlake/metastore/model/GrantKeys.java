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

package dev.frostlake.metastore.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The bookkeeping of the privileges a grantee holds on objects (see {@link GrantLedger}): privileges keyed
 * {@code KIND:NAME}, and grantors, grant times and grant options keyed {@code KIND:NAME:PRIVILEGE}.
 */
final class GrantKeys {

    private GrantKeys() {
    }

    /** Moves or copies the privileges a {@link GrantRemap} reaches, with what is recorded about each. */
    static void remap(final Map<String, Set<Privilege>> privileges, final Map<String, Map<String, Set<Privilege>>> columns,
                      final Map<String, String> grantors, final Map<String, Instant> times, final Set<String> options,
                      final GrantRemap remap) {
        for (final Map.Entry<String, Set<Privilege>> entry : new ArrayList<>(privileges.entrySet())) {
            final String key = entry.getKey();
            final int split = key.indexOf(':');
            final String mapped = remap.mapped(key.substring(0, split), key.substring(split + 1));
            if (mapped == null) {
                continue;
            }
            final String newKey = key.substring(0, split + 1) + mapped;
            if (newKey.equals(key)) {
                continue;
            }
            final Set<Privilege> held = entry.getValue();
            for (final Privilege privilege : new ArrayList<>(held)) {
                if (remap.exceptsOwnership() && privilege == Privilege.OWNERSHIP) {
                    continue;
                }
                Set<Privilege> target = privileges.get(newKey);
                if (target == null) {
                    target = new HashSet<>();
                    privileges.put(newKey, target);
                }
                target.add(privilege);
                final String from = key + ":" + privilege.name();
                final String to = newKey + ":" + privilege.name();
                carry(grantors, from, to, remap.copies());
                carry(times, from, to, remap.copies());
                if (options != null && options.contains(from)) {
                    options.add(to);
                    if (!remap.copies()) {
                        options.remove(from);
                    }
                }
                if (!remap.copies()) {
                    held.remove(privilege);
                }
            }
            if (held.isEmpty()) {
                privileges.remove(key);
            }
        }
        for (final Map.Entry<String, Map<String, Set<Privilege>>> entry : new ArrayList<>(columns.entrySet())) {
            final String key = entry.getKey();
            final int split = key.indexOf(':');
            final String mapped = remap.mapped(key.substring(0, split), key.substring(split + 1));
            if (mapped == null) {
                continue;
            }
            final String newKey = key.substring(0, split + 1) + mapped;
            if (newKey.equals(key)) {
                continue;
            }
            Map<String, Set<Privilege>> target = columns.get(newKey);
            if (target == null) {
                target = new HashMap<>();
                columns.put(newKey, target);
            }
            for (final Map.Entry<String, Set<Privilege>> column : entry.getValue().entrySet()) {
                Set<Privilege> held = target.get(column.getKey());
                if (held == null) {
                    held = new HashSet<>();
                    target.put(column.getKey(), held);
                }
                held.addAll(column.getValue());
            }
            if (!remap.copies()) {
                columns.remove(key);
            }
        }
    }

    /** Takes back the privileges held on one object: all of them, or only OWNERSHIP. */
    static void purge(final Map<String, Set<Privilege>> privileges, final Map<String, Map<String, Set<Privilege>>> columns,
                      final Map<String, String> grantors, final Map<String, Instant> times, final Set<String> options,
                      final String key, final boolean ownershipOnly) {
        final Set<Privilege> held = privileges.get(key);
        if (held != null) {
            for (final Privilege privilege : new ArrayList<>(held)) {
                if (ownershipOnly && privilege != Privilege.OWNERSHIP) {
                    continue;
                }
                held.remove(privilege);
                final String withPrivilege = key + ":" + privilege.name();
                grantors.remove(withPrivilege);
                if (times != null) {
                    times.remove(withPrivilege);
                }
                if (options != null) {
                    options.remove(withPrivilege);
                }
            }
            if (held.isEmpty()) {
                privileges.remove(key);
            }
        }
        if (!ownershipOnly) {
            columns.remove(key);
        }
    }

    private static <V> void carry(final Map<String, V> recorded, final String from, final String to, final boolean copy) {
        if (recorded == null || !recorded.containsKey(from)) {
            return;
        }
        recorded.put(to, recorded.get(from));
        if (!copy) {
            recorded.remove(from);
        }
    }
}
