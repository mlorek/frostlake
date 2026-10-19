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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.NameKeys;
import dev.frostlake.metastore.SqlObject;
import dev.frostlake.metastore.StatementSession;
import dev.frostlake.types.DataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The functions or the procedures a schema holds, by name and signature. A routine is held under its canonical
 * name — an unquoted name upper-cased, a quoted one as written — and resolved by that name EXACTLY, so "a" and A are
 * two routines, each reached only by its own spelling (live-verified); {@link #get} alone also answers a name in
 * another case, for the engine's Java callers (see {@link NameKeys#keyFor}). A name holds any number of overloads,
 * told apart by their parameters' types (see {@link RoutineSignatures}). Another session's temporary routine is no
 * routine here, and a permanent overload hides a temporary one of its signature (live-verified).
 *
 * @param <R> the kind of routine
 */
public final class OverloadStore<R extends SqlObject & Routine> {

    private final Map<String, List<R>> byName = new ConcurrentHashMap<>();
    private final Schema home;
    private final String kind;

    /**
     * An empty store.
     *
     * @param home the schema the routines are in, which spells a missing one's name in full
     * @param kind the kind as a missing routine's refusal names it: {@code Function} or {@code Procedure}
     */
    OverloadStore(final Schema home, final String kind) {
        this.home = home;
        this.kind = kind;
    }

    /**
     * Holds a new overload. A signature an overload of the name already carries is refused as taken (see
     * {@link RoutineSignatures#refuseSignatureHeld}), and so is one that differs from an overload of its lifetime by
     * a structured field's NOT NULL alone.
     *
     * @param routine the routine
     */
    public void add(final R routine) {
        final List<R> overloads = overloadsOf(routine.getName());
        for (final R existing : overloads) {
            if (!RoutineSignatures.sameSignature(existing.getParameters(), routine.getParameters())) {
                continue;
            }
            if (RoutineSignatures.exactSignature(existing.getParameters(), routine.getParameters())) {
                RoutineSignatures.refuseSignatureHeld(routine, existing);
            } else if (RoutineSignatures.sameLifetime(routine, existing)) {
                throw RoutineSignatures.notNullOnlyTwin(routine);
            }
        }
        overloads.add(routine);
    }

    /**
     * Holds an overload as it was held where it is copied from, judging nothing.
     *
     * @param routine the routine
     */
    void hold(final R routine) {
        overloadsOf(routine.getName()).add(routine);
    }

    /** The overloads of a name, made when it has none. */
    private List<R> overloadsOf(final String name) {
        // putIfAbsent rather than get/put: the map is concurrent, and losing a race here would drop an overload
        // registered by another thread.
        List<R> overloads = byName.get(name);
        if (overloads == null) {
            overloads = new ArrayList<>();
            final List<R> raced = byName.putIfAbsent(name, overloads);
            if (raced != null) {
                overloads = raced;
            }
        }
        return overloads;
    }

    /**
     * Removes this very overload, and its name with it when no other overload carries that name.
     *
     * @param routine the routine
     * @return whether it was held here
     */
    public boolean remove(final R routine) {
        final List<R> overloads = byName.get(routine.getName());
        if (overloads == null) {
            return false;
        }
        boolean removed = false;
        // By identity: routines compare equal by name alone, so remove(Object) would take a sibling.
        for (int i = 0; i < overloads.size(); i++) {
            if (overloads.get(i) == routine) {
                overloads.remove(i);
                removed = true;
                break;
            }
        }
        if (overloads.isEmpty()) {
            byName.remove(routine.getName());
        }
        return removed;
    }

    /**
     * The one overload a name reaches, folding case as {@link NameKeys#keyFor} does; a name nothing carries does not
     * exist, and one with several overloads is refused for want of a signature.
     *
     * @param name the name as the caller spells it
     * @return the overload
     */
    public R get(final String name) {
        final List<R> overloads = overloads(NameKeys.keyFor(byName, name));
        if (overloads.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.doesNotExist(kind, home.qualifiedName(name)));
        }
        if (overloads.size() > 1) {
            throw new RuntimeException(kind + " " + name + " has multiple overloads. Use get" + kind
                + "BySignature() with parameter types.");
        }
        return overloads.get(0);
    }

    /**
     * The overload whose parameters match these argument types by type family; a name or a signature
     * nothing carries does not exist.
     *
     * @param name          the routine's exact canonical name
     * @param argumentTypes the signature written after it
     * @return the overload
     */
    public R getBySignature(final String name, final List<DataType> argumentTypes) {
        for (final R routine : overloads(name)) {
            if (RoutineSignatures.matches(routine.getParameters(), argumentTypes)) {
                return routine;
            }
        }
        throw new RuntimeException(SqlCompilationError.doesNotExist(kind, home.qualifiedName(name)));
    }

    /**
     * The overload a statement names by the signature it writes: its parameters match by type family, and a type
     * written with a length, a precision or a scale matches only a parameter declared with the same (see
     * {@link RoutineSignatures#matchesWritten}).
     *
     * @param name          the routine's exact canonical name
     * @param argumentTypes the signature written after it, each type with the parameters written on it
     * @return the overload
     */
    public R getByWrittenSignature(final String name, final List<DataType> argumentTypes) {
        for (final R routine : overloads(name)) {
            if (RoutineSignatures.matchesWritten(routine.getParameters(), argumentTypes)) {
                return routine;
            }
        }
        throw new RuntimeException(SqlCompilationError.doesNotExist(kind, home.qualifiedName(name)));
    }

    /**
     * Every overload this session reaches (see {@link #reached}).
     *
     * @return the overloads
     */
    public List<R> values() {
        final List<R> all = new ArrayList<>();
        for (final List<R> overloads : byName.values()) {
            all.addAll(reached(new ArrayList<>(overloads)));
        }
        return all;
    }

    /**
     * Every overload, each session's temporary ones included, for what outlives or owns them all.
     *
     * @return the overloads
     */
    public List<R> all() {
        final List<R> all = new ArrayList<>();
        for (final List<R> overloads : byName.values()) {
            all.addAll(overloads);
        }
        return all;
    }

    /**
     * Every overload carrying exactly this canonical name this session reaches.
     *
     * @param name the canonical name
     * @return the overloads; none when the name carries none
     */
    public List<R> overloads(final String name) {
        final List<R> overloads = name == null ? null : byName.get(name);
        return overloads != null ? reached(new ArrayList<>(overloads)) : new ArrayList<>();
    }

    /**
     * Whether a new routine of that lifetime would find this signature taken (see {@link #add}): a temporary routine
     * by this session's own temporary one, a permanent routine by a permanent one. A temporary routine over a
     * permanent one is refused for its signature when it is added.
     *
     * @param name       the routine's canonical name
     * @param parameters the new signature
     * @param temporary  whether the new routine is temporary
     * @return whether the signature is taken
     */
    public boolean holdsSignature(final String name, final List<Parameter> parameters, final boolean temporary) {
        for (final R existing : overloads(name)) {
            if (existing.isTemporary() == temporary
                    && RoutineSignatures.exactSignature(existing.getParameters(), parameters)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether an overload of that name already carries this signature, a structured field's NOT NULL aside (see
     * {@link #add}).
     *
     * @param name       the routine's canonical name
     * @param parameters the signature
     * @return whether one does
     */
    public boolean holdsSignature(final String name, final List<Parameter> parameters) {
        for (final R existing : overloads(name)) {
            if (RoutineSignatures.sameSignature(existing.getParameters(), parameters)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Refuses a signature that an overload the name already has would compete with for some call: the two take a
     * common number of arguments and agree in the types of that many leading parameters, so only optional arguments
     * tell them apart (live-verified). {@code f(a NUMBER)} beside {@code f(a NUMBER, b NUMBER DEFAULT 0)} is refused,
     * in either order, as are {@code f()} beside {@code f(o NUMBER DEFAULT 0)} and two overloads whose optional
     * arguments alone differ in type; {@code f(a NUMBER, b VARCHAR)} beside {@code f(a NUMBER, b NUMBER DEFAULT 0)}
     * stands, a call of two arguments telling them apart by type. An overload of exactly this signature is not judged
     * here: it is the one a CREATE OR REPLACE replaces, or the name already taken.
     *
     * @param name       the routine's canonical name
     * @param parameters the new signature
     */
    public void requireUnambiguous(final String name, final List<Parameter> parameters) {
        for (final R existing : overloads(name)) {
            if (RoutineSignatures.competes(existing.getParameters(), parameters)) {
                throw new RuntimeException(RoutineSignatures.ambiguousOverload(kind.toUpperCase(Locale.ROOT),
                    name));
            }
        }
    }

    /**
     * The overloads this session reaches of those carrying one name: the permanent ones and its own temporary ones.
     * Another session's temporary routine is no routine here, and a permanent routine created after a temporary one
     * of its signature is the one every session calls, its creator's included (live-verified).
     */
    private List<R> reached(final List<R> overloads) {
        final List<R> reached = new ArrayList<>();
        for (final R overload : overloads) {
            if (!overload.isTemporary()) {
                reached.add(overload);
                continue;
            }
            boolean hidden = !Objects.equals(overload.getCreatedInSession(), StatementSession.current());
            for (final R other : overloads) {
                hidden = hidden || !other.isTemporary()
                    && RoutineSignatures.exactSignature(other.getParameters(), overload.getParameters());
            }
            if (!hidden) {
                reached.add(overload);
            }
        }
        return reached;
    }
}
