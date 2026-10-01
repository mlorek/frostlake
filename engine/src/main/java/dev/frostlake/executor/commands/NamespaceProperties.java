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

import dev.frostlake.executor.ContainerParameterCatalog;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.metastore.model.ObjectParameters;
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The properties a CREATE DATABASE or CREATE SCHEMA writes, read from the statement's property list in any order:
 * the retention (judged by the caller), the comment, inline tags, WITH MANAGED ACCESS, and the other container
 * parameters by name. A name that is neither a parameter of the container's scope nor one of the few documented
 * CREATE properties that are not parameters is refused as an invalid property, as ALTER refuses it.
 */
final class NamespaceProperties {

    /** Documented CREATE properties that SHOW PARAMETERS does not list. */
    private static final Set<String> CREATE_ONLY = new HashSet<>(Arrays.asList(
        "CATALOG_SYNC_NAMESPACE_MODE", "CATALOG_SYNC_NAMESPACE_FLATTEN_DELIMITER", "CLASSIFICATION_PROFILE",
        "OBJECT_VISIBILITY"));

    private final List<FrostlakeParser.CommentClauseContext> comments = new ArrayList<>();
    private final List<FrostlakeParser.TagListContext> tags = new ArrayList<>();
    private final Map<String, String> parameters = new LinkedHashMap<>();
    private String retention;
    private boolean managedAccess;

    private NamespaceProperties() {
    }

    /** The properties of a CREATE DATABASE. */
    static NamespaceProperties ofDatabase(final List<FrostlakeParser.DatabasePropertyContext> properties) {
        final NamespaceProperties read = new NamespaceProperties();
        for (final FrostlakeParser.DatabasePropertyContext property : properties) {
            read.read(property, "DATABASE");
        }
        return read;
    }

    /** The properties of a CREATE SCHEMA. */
    static NamespaceProperties ofSchema(final List<FrostlakeParser.SchemaPropertyContext> properties) {
        final NamespaceProperties read = new NamespaceProperties();
        for (final FrostlakeParser.SchemaPropertyContext property : properties) {
            if (property.MANAGED() != null) {
                read.managedAccess = true;
            } else {
                read.read(property.databaseProperty(), "SCHEMA");
            }
        }
        return read;
    }

    private void read(final FrostlakeParser.DatabasePropertyContext property, final String kind) {
        if (property.DATA_RETENTION_TIME_IN_DAYS() != null && property.INTEGER_LITERAL() != null) {
            retention = (property.MINUS() != null ? "-" : "") + property.INTEGER_LITERAL().getText();
        } else if (property.commentClause() != null) {
            comments.add(property.commentClause());
        } else if (property.tagList() != null) {
            tags.add(property.tagList());
        } else if (property.optionKey() != null) {
            final String raw = property.optionKey().getText();
            final String key = ParameterRegistry.canonical(raw);
            if ("DATA_RETENTION_TIME_IN_DAYS".equals(key)) {
                throw new RuntimeException(SqlCompilationError.invalidValueForParameter(
                    property.copyOptionValue().getText(), key));
            }
            final boolean known = "SCHEMA".equals(kind)
                ? ParameterRegistry.isSchemaParameter(key) || ContainerParameterCatalog.isSchemaParameter(key)
                : ParameterRegistry.isDatabaseParameter(key) || ContainerParameterCatalog.isDatabaseParameter(key);
            if (!known && !CREATE_ONLY.contains(key)) {
                throw ParameterRegistry.invalidProperty(ParameterRegistry.spell(raw), kind);
            }
            parameters.put(key, render(property.copyOptionValue()));
        }
    }

    /** The retention as written, or null when the statement sets none. */
    String retention() {
        return retention;
    }

    /** The comment clauses, in the order written. */
    List<FrostlakeParser.CommentClauseContext> comments() {
        return comments;
    }

    /** The inline tag lists, in the order written. */
    List<FrostlakeParser.TagListContext> tags() {
        return tags;
    }

    /** Whether the statement asks for a managed access schema. */
    boolean managedAccess() {
        return managedAccess;
    }

    /** Sets every named parameter on the created container. */
    void applyParameters(final ObjectParameters target) {
        for (final Map.Entry<String, String> parameter : parameters.entrySet()) {
            target.set(parameter.getKey(), parameter.getValue());
        }
    }

    /**
     * A parameter value as SHOW PARAMETERS reports it: a string's content, a flag as {@code true} or
     * {@code false}, a number as written, a name as it resolves.
     */
    static String render(final FrostlakeParser.CopyOptionValueContext value) {
        if (value.STRING_LITERAL() != null) {
            return SqlStringLiterals.decode(value.STRING_LITERAL().getText());
        }
        if (value.booleanValue() != null) {
            return value.booleanValue().TRUE() != null ? "true" : "false";
        }
        if (value.INTEGER_LITERAL() != null) {
            return (value.MINUS() != null ? "-" : "") + value.INTEGER_LITERAL().getText();
        }
        if (value.qualifiedName() != null) {
            return SqlIdentifiers.canonicalText(value.qualifiedName().getText());
        }
        return value.getText().toUpperCase(Locale.ROOT);
    }
}
