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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The relations a join's merged relation is assembled from, and which of them an outer join extends
 * with NULLs: what the account's planner knows of a joined column's nullability beside its table's
 * statistics. A relation no outer join extends keeps what its statistics prove — live tags
 * {@code COALESCE(a.n, 3000.5)} over an inner, comma, cross or LEFT join, with {@code a} the kept side
 * and {@code a.n} holding no NULL, by {@code a.n}'s width alone. A relation an outer join extends may
 * answer NULL whatever its table holds: a LEFT join extends its right side, a RIGHT join every relation
 * on its left, a FULL join both, and an extended relation stays extended through every later join and
 * filter.
 *
 * <p>Relations are compared by identity: the FROM clause gives each alias its own instance.
 */
public final class JoinedRelations {

    private final List<Table> members;
    private final List<Table> nullExtended;

    private JoinedRelations(final List<Table> members, final List<Table> nullExtended) {
        this.members = members;
        this.nullExtended = nullExtended;
    }

    /**
     * The relations of {@code left} joined to {@code right}, either of which may itself be an earlier
     * join's merged relation.
     *
     * @param left              the join's left input
     * @param right             the join's right input
     * @param leftNullExtended  whether the join extends its left input with NULLs, as RIGHT and FULL do
     * @param rightNullExtended whether the join extends its right input with NULLs, as LEFT and FULL do
     * @return the merged relation's record
     */
    public static JoinedRelations joining(final Table left, final Table right,
                                          final boolean leftNullExtended, final boolean rightNullExtended) {
        final List<Table> members = new ArrayList<>();
        final List<Table> extended = new ArrayList<>();
        collect(left, leftNullExtended, members, extended);
        collect(right, rightNullExtended, members, extended);
        return new JoinedRelations(Collections.unmodifiableList(members), Collections.unmodifiableList(extended));
    }

    /** Adds one input's relations, each extended where this join extends the input or an earlier one did. */
    private static void collect(final Table input, final boolean extendedHere, final List<Table> members,
                                final List<Table> extended) {
        final JoinedRelations earlier = input.getJoinedRelations();
        if (earlier == null) {
            members.add(input);
            if (extendedHere) {
                extended.add(input);
            }
            return;
        }
        for (final Table member : earlier.members) {
            members.add(member);
            if (extendedHere || earlier.isNullExtended(member)) {
                extended.add(member);
            }
        }
    }

    /**
     * Whether {@code relation} is one of the joined relations.
     *
     * @param relation a relation of the FROM clause
     * @return true when this join assembles it
     */
    public boolean includes(final Table relation) {
        return indexOf(members, relation) >= 0;
    }

    /**
     * Whether an outer join extends {@code relation} with NULLs.
     *
     * @param relation a relation of the FROM clause
     * @return true when some outer join of the chain extends it
     */
    public boolean isNullExtended(final Table relation) {
        return indexOf(nullExtended, relation) >= 0;
    }

    /**
     * The first relation no outer join extends that carries {@code columnName}: the side a USING or
     * NATURAL key written bare reads, which for a RIGHT join is its right side — live tags
     * {@code COALESCE(k, 3000.5)} over {@code a RIGHT JOIN b USING (k)} by {@code b.k}'s width alone.
     *
     * @param columnName the key's name
     * @return the kept relation, or null when an outer join extends every relation carrying it
     */
    public Table keptRelationWith(final String columnName) {
        for (final Table member : members) {
            if (!isNullExtended(member) && member.hasColumn(columnName)) {
                return member;
            }
        }
        return null;
    }

    private static int indexOf(final List<Table> relations, final Table relation) {
        for (int i = 0; i < relations.size(); i++) {
            if (relations.get(i) == relation) {
                return i;
            }
        }
        return -1;
    }
}
