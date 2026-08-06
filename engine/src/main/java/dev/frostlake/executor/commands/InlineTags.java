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
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.Taggable;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;

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
    static void applyFrom(final Taggable target, final List<? extends ParseTree> ownContexts) {
        if (ownContexts == null) {
            return;
        }
        for (final ParseTree context : ownContexts) {
            final FrostlakeParser.TagListContext tags = firstTagList(context);
            if (tags != null) {
                apply(target, tags);
            }
        }
    }

    /** Apply one tag clause's assignments to {@code target}, keyed by the tag's simple name. */
    static void apply(final Taggable target, final FrostlakeParser.TagListContext tags) {
        for (final FrostlakeParser.TagAssignmentContext assignment : tags.tagAssignment()) {
            final QualifiedName tagName = QualifiedName.of(
                ParseTreeText.qualifiedNameParts(assignment.qualifiedName()));
            target.setTag(tagName.last(),
                SqlStringLiterals.decode(assignment.STRING_LITERAL().getText()));
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
