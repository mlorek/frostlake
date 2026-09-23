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

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.Taggable;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies a creation-time {@code [WITH] TAG (name = 'value', …)} clause to the object being created,
 * so the tags it names reach TAG_REFERENCES. The clause hangs off several DDL rules, so the search
 * walks the statement's parse tree rather than naming one accessor.
 */
final class InlineTags {

    private InlineTags() {
    }

    /**
     * Apply the object's own tag clause to {@code target}. The caller passes only the contexts that
     * belong to the object itself — a CREATE TABLE's tail options, say — because a column's tag
     * clause sits in the same statement and belongs to the column, not the table.
     */
    static void applyFrom(final Taggable target, final List<? extends ParseTree> ownContexts,
                          final QueryExecutor queryExecutor) {
        if (ownContexts == null) {
            return;
        }
        for (final ParseTree context : ownContexts) {
            final FrostlakeParser.TagListContext tags = firstTagList(context);
            if (tags != null) {
                apply(target, tags, queryExecutor);
            }
        }
    }

    /**
     * Apply one tag clause's assignments to {@code target}, keyed by the tag's simple name. A tag given twice
     * must be given the same value both times; two values are refused, naming them in the order written.
     */
    static void apply(final Taggable target, final FrostlakeParser.TagListContext tags,
                      final QueryExecutor queryExecutor) {
        final Map<String, List<String>> valuesByTag = new LinkedHashMap<>();
        final Map<String, String> simpleNames = new LinkedHashMap<>();
        for (final FrostlakeParser.TagAssignmentContext assignment : tags.tagAssignment()) {
            final QualifiedName tagName = QualifiedName.of(
                ParseTreeText.qualifiedNameParts(assignment.qualifiedName()));
            final String value = TagValues.text(assignment.qualifiedName(), assignment.tagValue(), queryExecutor);
            List<String> values = valuesByTag.get(tagName.toString());
            if (values == null) {
                values = new ArrayList<>();
                valuesByTag.put(tagName.toString(), values);
                simpleNames.put(tagName.toString(), tagName.last());
            }
            values.add(value);
        }
        for (final Map.Entry<String, List<String>> tag : valuesByTag.entrySet()) {
            final List<String> values = tag.getValue();
            for (final String value : values) {
                if (!value.equals(values.get(0))) {
                    throw new RuntimeException("Same tag " + simpleNames.get(tag.getKey())
                        + " with multiple values provided, values = " + String.join(", ", values));
                }
            }
        }
        for (final Map.Entry<String, List<String>> tag : valuesByTag.entrySet()) {
            target.setTag(simpleNames.get(tag.getKey()), tag.getValue().get(0));
        }
    }

    /** The first tag clause in the subtree, or null when the statement carries none. */
    private static FrostlakeParser.TagListContext firstTagList(final ParseTree node) {
        if (node instanceof FrostlakeParser.TagListContext) {
            return (FrostlakeParser.TagListContext) node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final FrostlakeParser.TagListContext found = firstTagList(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
