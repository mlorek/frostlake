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
import java.util.Collection;
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
 *
 * <p>The record also holds the relations' NAME SCOPES. An ON, comma, CROSS, LATERAL or ASOF join keeps
 * its relations apart, so a bare name two of them carry is ambiguous; a USING or NATURAL join merges
 * every relation of its left input and of its right input into ONE relation, in which a bare name reads
 * its left-most copy — live answers {@code K1.b} for a bare {@code b} over
 * {@code K1 JOIN K2 ON TRUE JOIN K3 USING (k)}. Each scope carries the column positions a positional
 * reference counts: the merged keys, then the left input's columns without its key copies unless the
 * join extends the left with NULLs, then the right input's the same way (live: {@code $1..$5} over
 * {@code a(k, v) FULL JOIN b(k, w) USING (k)} read the merged key, {@code a.k}, {@code v}, {@code b.k}
 * and {@code w}, where an inner join has only the key, {@code v} and {@code w}).
 */
public final class JoinedRelations {

    private final List<Table> members;
    private final List<Table> nullExtended;
    /** The relations of each name scope. */
    private final List<List<Table>> scopes;
    /** The column positions of each name scope, index-aligned with {@link #scopes}. */
    private final List<List<RelationSlot>> layouts;

    private JoinedRelations(final List<Table> members, final List<Table> nullExtended,
                            final List<List<Table>> scopes, final List<List<RelationSlot>> layouts) {
        this.members = members;
        this.nullExtended = nullExtended;
        this.scopes = scopes;
        this.layouts = layouts;
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
        final List<List<Table>> scopes = new ArrayList<>();
        final List<List<RelationSlot>> layouts = new ArrayList<>();
        collectScopes(left, scopes, layouts);
        collectScopes(right, scopes, layouts);
        return new JoinedRelations(Collections.unmodifiableList(members), Collections.unmodifiableList(extended),
            scopes, layouts);
    }

    /**
     * The relations of a USING or NATURAL join of {@code left} to {@code right}: as {@link #joining}
     * records them, with every name scope of the left input — but those of {@code apart} — and of the
     * right input merged into one.
     *
     * @param left              the join's left input
     * @param right             the join's right input
     * @param leftNullExtended  whether the join extends its left input with NULLs, as RIGHT and FULL do
     * @param rightNullExtended whether the join extends its right input with NULLs, as LEFT and FULL do
     * @param keyNames          the join's key columns, in the order the join lists them
     * @param apart             relations of the left input the join does not merge: those of another
     *                          comma-separated item of the FROM clause
     * @return the merged relation's record
     */
    public static JoinedRelations merging(final Table left, final Table right, final boolean leftNullExtended,
                                          final boolean rightNullExtended, final Collection<String> keyNames,
                                          final List<Table> apart) {
        final JoinedRelations joined = joining(left, right, leftNullExtended, rightNullExtended);
        final List<List<Table>> leftScopes = new ArrayList<>();
        final List<List<RelationSlot>> leftLayouts = new ArrayList<>();
        collectScopes(left, leftScopes, leftLayouts);
        final List<List<Table>> rightScopes = new ArrayList<>();
        final List<List<RelationSlot>> rightLayouts = new ArrayList<>();
        collectScopes(right, rightScopes, rightLayouts);
        final List<List<Table>> scopes = new ArrayList<>();
        final List<List<RelationSlot>> layouts = new ArrayList<>();
        final List<Table> mergedMembers = new ArrayList<>();
        final List<RelationSlot> leftLayout = new ArrayList<>();
        for (int i = 0; i < leftScopes.size(); i++) {
            if (anyOf(leftScopes.get(i), apart)) {
                scopes.add(leftScopes.get(i));
                layouts.add(leftLayouts.get(i));
            } else {
                mergedMembers.addAll(leftScopes.get(i));
                leftLayout.addAll(leftLayouts.get(i));
            }
        }
        final List<RelationSlot> rightLayout = new ArrayList<>();
        for (int i = 0; i < rightScopes.size(); i++) {
            mergedMembers.addAll(rightScopes.get(i));
            rightLayout.addAll(rightLayouts.get(i));
        }
        final List<RelationSlot> layout = new ArrayList<>();
        for (final String keyName : keyNames) {
            final RelationSlot leftCopy = named(leftLayout, keyName);
            final RelationSlot rightCopy = named(rightLayout, keyName);
            final List<RelationSlot> copies = new ArrayList<>();
            if (leftCopy != null) {
                copies.add(leftCopy);
                if (!leftNullExtended) {
                    leftLayout.remove(leftCopy);
                }
            }
            if (rightCopy != null) {
                copies.add(rightCopy);
                if (!rightNullExtended) {
                    rightLayout.remove(rightCopy);
                }
            }
            if (!copies.isEmpty()) {
                layout.add(RelationSlot.mergedKey(keyName, copies));
            }
        }
        layout.addAll(leftLayout);
        layout.addAll(rightLayout);
        scopes.add(Collections.unmodifiableList(mergedMembers));
        layouts.add(Collections.unmodifiableList(layout));
        return new JoinedRelations(joined.members, joined.nullExtended, scopes, layouts);
    }

    /** Adds one input's name scopes: an earlier join's, or the input alone with its own columns. */
    private static void collectScopes(final Table input, final List<List<Table>> scopes,
                                      final List<List<RelationSlot>> layouts) {
        final JoinedRelations earlier = input.getJoinedRelations();
        if (earlier != null) {
            scopes.addAll(earlier.scopes);
            layouts.addAll(earlier.layouts);
            return;
        }
        final List<RelationSlot> layout = new ArrayList<>();
        // A staged-file query offers its fields by position, never the METADATA$ columns after them.
        final int positional = input.getStagePositions() == null ? input.getColumns().size()
            : input.getStagePositions().fields();
        for (int i = 0; i < positional; i++) {
            layout.add(RelationSlot.column(input, i));
        }
        scopes.add(Collections.singletonList(input));
        layouts.add(Collections.unmodifiableList(layout));
    }

    /** The first slot of {@code layout} named exactly {@code name}, or null. */
    private static RelationSlot named(final List<RelationSlot> layout, final String name) {
        for (final RelationSlot slot : layout) {
            if (slot.getName().equals(name)) {
                return slot;
            }
        }
        return null;
    }

    /** Whether any of {@code relations} is one of {@code candidates}, by identity. */
    private static boolean anyOf(final List<Table> relations, final List<Table> candidates) {
        if (candidates == null) {
            return false;
        }
        for (final Table relation : relations) {
            if (indexOf(candidates, relation) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** @return how many name scopes the joined relations form */
    public int scopeCount() {
        return scopes.size();
    }

    /**
     * The name scope a relation belongs to.
     *
     * @param relation a relation of the FROM clause
     * @return the scope's index, or -1 when this join does not assemble the relation
     */
    public int scopeOf(final Table relation) {
        for (int i = 0; i < scopes.size(); i++) {
            if (indexOf(scopes.get(i), relation) >= 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The relations of one name scope.
     *
     * @param scope the scope's index
     * @return its relations, in combined-row order
     */
    public List<Table> scopeMembers(final int scope) {
        return scopes.get(scope);
    }

    /**
     * The column positions one name scope offers a positional reference.
     *
     * @param scope the scope's index
     * @return its slots, first position first
     */
    public List<RelationSlot> scopeLayout(final int scope) {
        return layouts.get(scope);
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
