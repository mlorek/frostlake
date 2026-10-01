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

package dev.frostlake.metastore;

import dev.frostlake.metastore.model.Integration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The account's integrations, keyed by canonical name: one namespace across every kind. */
public final class IntegrationRegistry {

    private final Map<String, Integration> integrations = new ConcurrentHashMap<>();

    /** The integration of that canonical name, or null. */
    public Integration find(final String name) {
        return integrations.get(name);
    }

    /** Adds or replaces an integration. */
    public void put(final Integration integration) {
        integrations.put(integration.getName(), integration);
    }

    /** Removes the integration of that name; answers it, or null when there was none. */
    public Integration remove(final String name) {
        return integrations.remove(name);
    }

    /** Every integration, ordered by name. */
    public List<Integration> all() {
        final List<Integration> out = new ArrayList<>(integrations.values());
        out.sort(new Comparator<Integration>() {
            @Override
            public int compare(final Integration a, final Integration b) {
                return a.getName().compareTo(b.getName());
            }
        });
        return out;
    }
}
