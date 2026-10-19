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
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.TagAssignments;
import dev.frostlake.metastore.Taggable;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.parser.FrostlakeParser;

/**
 * An ALTER's SET TAG and UNSET TAG actions over one object. Each tag named has to exist, for either action: one
 * that is missing and named bare is refused by that name alone, one named by a path by its full path; a value has
 * to be one the tag allows.
 */
final class TagActions {

    private TagActions() {
    }

    /**
     * Sets each tag of a SET TAG action on an object.
     *
     * @param target the object
     * @param set the action
     * @param catalog the catalog the tags live in
     * @param queryExecutor the executor whose session variables a {@code $name} value reads
     */
    static void set(final Taggable target, final FrostlakeParser.TagSetContext set, final Catalog catalog,
                    final QueryExecutor queryExecutor) {
        for (final FrostlakeParser.TagAssignContext assign : set.tagAssign()) {
            final Tag tag = tag(assign.qualifiedName(), catalog);
            final String value = TagValues.text(assign.qualifiedName(), assign.tagValue(), queryExecutor);
            TagValues.requireAllowed(tag, value);
            TagAssignments.set(target, tag, value);
        }
    }

    /**
     * Takes each tag of an UNSET TAG action off an object.
     *
     * @param target the object
     * @param unset the action
     * @param catalog the catalog the tags live in
     */
    static void unset(final Taggable target, final FrostlakeParser.TagUnsetContext unset, final Catalog catalog) {
        for (final FrostlakeParser.QualifiedNameContext name : unset.qualifiedName()) {
            TagAssignments.unset(target, tag(name, catalog));
        }
    }

    /**
     * The tag a SET TAG or an UNSET TAG names, which has to exist.
     *
     * @param name the tag's name as written
     * @param catalog the catalog the tags live in
     * @return the tag
     */
    static Tag tag(final FrostlakeParser.QualifiedNameContext name, final Catalog catalog) {
        final String tagName = ParseTreeText.getQualifiedName(name);
        final QualifiedName written = QualifiedName.parse(tagName);
        if (written.size() == 1 && catalog.getCurrentDatabase() != null && catalog.getCurrentSchema() != null
                && !catalog.hasTag(tagName)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Tag", written.last()));
        }
        return catalog.getTag(tagName);
    }
}
