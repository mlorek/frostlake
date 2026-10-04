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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A named file format object (CREATE FILE FORMAT) — a reusable set of parsing/formatting options (TYPE plus
 * format-specific options like FIELD_DELIMITER, SKIP_HEADER) that COPY statements reference via FORMAT_NAME.
 */
public class FileFormat {

    /** The option holding the strings a load reads as NULL. */
    private static final String NULL_IF = "NULL_IF";

    /** Leads and separates NULL_IF's values when a comma cannot tell them apart. */
    private static final char VALUE_SEPARATOR = '\u001F';

    private String name;
    private String type;
    private final Map<String, String> options;
    private String comment;
    // The creating statement's instant, which a replayed statement reproduces (see SqlObject).
    private Instant createdTime = StatementClock.instant();
    /** When the last DDL after the CREATE ran, or null while the CREATE is the last; see {@link #getLastDdlTime}. */
    private Instant lastDdlTime;
    private String owner = "SYSADMIN";

    public FileFormat(final String name, final String type) {
        this.name = name;
        this.type = type != null ? type.toUpperCase() : "CSV";
        this.options = new LinkedHashMap<>();
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type != null ? type.toUpperCase() : null;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public void setOption(final String key, final String value) {
        if (key != null) {
            options.put(key.toUpperCase(), value);
        }
    }

    public String getOption(final String key) {
        return key == null ? null : options.get(key.toUpperCase());
    }

    /**
     * NULL_IF's values, each exactly as written, or null when the format sets none. They are kept in the NULL_IF
     * option joined by commas; a list whose values could not be told apart that way — one holding a comma, or a
     * single empty string, which the comma-joined text cannot tell from an empty list — is kept behind a
     * leading unit-separator character, which then also separates the values.
     *
     * @return the values in the order written, or null
     */
    public List<String> getNullIfValues() {
        final String stored = getOption(NULL_IF);
        if (stored == null) {
            return null;
        }
        final List<String> values = new ArrayList<>();
        if (stored.isEmpty()) {
            return values;
        }
        final boolean separated = stored.charAt(0) == VALUE_SEPARATOR;
        final String joined = separated ? stored.substring(1) : stored;
        int start = 0;
        for (int i = 0; i <= joined.length(); i++) {
            if (i == joined.length() || joined.charAt(i) == (separated ? VALUE_SEPARATOR : ',')) {
                values.add(joined.substring(start, i));
                start = i + 1;
            }
        }
        return values;
    }

    /**
     * Set NULL_IF's values, kept as {@link #getNullIfValues} reads them back.
     *
     * @param values the values in the order written
     */
    public void setNullIfValues(final List<String> values) {
        boolean ambiguous = values.size() == 1 && values.get(0).isEmpty();
        for (final String value : values) {
            ambiguous = ambiguous || value.indexOf(',') >= 0;
        }
        final StringBuilder stored = new StringBuilder();
        if (ambiguous) {
            stored.append(VALUE_SEPARATOR);
        }
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                stored.append(ambiguous ? VALUE_SEPARATOR : ',');
            }
            stored.append(values.get(i));
        }
        setOption(NULL_IF, stored.toString());
    }

    /** When this file format was created, as SHOW FILE FORMATS reports it. */
    public Instant getCreatedTime() {
        return createdTime;
    }

    /**
     * Put back when this object was created, as a snapshot recorded it, so an engine clone or a restore reports the
     * original creation time. A null, from a snapshot that never recorded it, keeps the time it was rebuilt.
     *
     * @param created the recorded creation time, or null
     */
    public void restoreCreatedTime(final Instant created) {
        if (created != null) {
            this.createdTime = created;
        }
    }

    /**
     * When the last DDL statement ran on this file format, which is INFORMATION_SCHEMA.FILE_FORMATS' LAST_ALTERED: its
     * CREATE until an ALTER or a COMMENT ON moves it, as {@link dev.frostlake.metastore.SqlObject#getLastDdlTime} is for
     * the objects that extend it.
     *
     * @return the instant
     */
    public Instant getLastDdlTime() {
        return lastDdlTime != null ? lastDdlTime : createdTime;
    }

    /** A DDL statement changed this file format: its LAST_ALTERED moves to the statement's instant. */
    public void markDdl() {
        lastDdlTime = StatementClock.instant();
    }

    /**
     * Put back when the last DDL ran on this file format, as a snapshot recorded it; a null, from a snapshot that never
     * recorded it, leaves LAST_ALTERED at the creation.
     *
     * @param lastDdl the recorded last DDL, or null
     */
    public void restoreLastDdlTime(final Instant lastDdl) {
        if (lastDdl != null) {
            this.lastDdlTime = lastDdl;
        }
    }

    /** The role that owns this file format. */
    public String getOwner() {
        return owner;
    }

    public void setOwner(final String owner) {
        this.owner = owner;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(final String comment) {
        this.comment = comment;
    }
}
