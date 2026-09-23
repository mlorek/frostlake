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

import dev.frostlake.executor.StatementClock;
import dev.frostlake.metastore.SqlObject;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An integration: an account-level object holding the configuration of a third-party service — an API gateway,
 * an Iceberg catalog, an e-mail, webhook or queue notification channel. Frostlake keeps the configuration and
 * never calls the service. Its properties are kept as written, keyed by their upper-case names, apart from the
 * enabled flag and the comment every kind shares.
 */
public class Integration extends SqlObject {

    private final IntegrationKind kind;
    private boolean enabled;
    private final Map<String, PropertyValue> properties = new LinkedHashMap<>();

    /**
     * @param name the integration's canonical name
     * @param kind its kind
     */
    public Integration(final String name, final IntegrationKind kind) {
        this(name, kind, StatementClock.instant());
    }

    /**
     * An integration created at a given moment, as a restored snapshot brings it back.
     *
     * @param name the integration's canonical name
     * @param kind its kind
     * @param createdTime when it was created
     */
    public Integration(final String name, final IntegrationKind kind, final Instant createdTime) {
        super(name, createdTime);
        this.kind = kind;
    }

    @Override
    public String getObjectType() {
        return "INTEGRATION";
    }

    /** The integration's kind. */
    public IntegrationKind getKind() {
        return kind;
    }

    /** Whether the integration is enabled. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Enables or disables the integration. */
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    /** The kind-specific properties, keyed by upper-case name, in the order they were set. */
    public Map<String, PropertyValue> getProperties() {
        return properties;
    }

    /** One property, or null when it is not set. */
    public PropertyValue property(final String key) {
        return properties.get(key);
    }

    /** One scalar property's text, or null. */
    public String text(final String key) {
        final PropertyValue value = properties.get(key);
        return value == null ? null : value.getText();
    }

    /**
     * The type SHOW INTEGRATIONS reports: {@code EXTERNAL_API} for an API integration, {@code CATALOG} for a
     * catalog integration, and for a notification integration its TYPE, a queue's followed by its provider.
     */
    public String getType() {
        switch (kind) {
            case API:
                return "EXTERNAL_API";
            case NOTIFICATION:
                final String type = text("TYPE");
                if ("QUEUE".equals(type) && text("NOTIFICATION_PROVIDER") != null) {
                    return "QUEUE - " + text("NOTIFICATION_PROVIDER");
                }
                return type;
            case STORAGE:
                return "EXTERNAL_STAGE";
            case EXTERNAL_ACCESS:
                return "EXTERNAL_ACCESS";
            default:
                return kind.getWords();
        }
    }
}
