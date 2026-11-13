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

package dev.frostlake.executor;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

/**
 * Owns procedural scripting variable state and nested-scope semantics for {@link ProceduralExecutor}:
 * the variable map plus the scope stack used by BEGIN…END and control-flow blocks. Entering a scope
 * snapshots the current variables; exiting propagates updates to inherited variables and discards
 * any newly declared (or shadowed) within that scope.
 */
public class ScopeManager {

    private final Map<String, Object> variables = new HashMap<>();
    private final Stack<Map<String, Object>> scopeStack = new Stack<>();
    // Track which variable names were NEWLY DECLARED (not inherited) in each scope level.
    private final Stack<Set<String>> declaredInScopeStack = new Stack<>();
    // Whether each scope level is ISOLATED — inherits nothing and propagates nothing (a UDF body).
    private final Stack<Boolean> isolatedScopeStack = new Stack<>();
    // Whether each scope level is a BEGIN … END block nested in another, rather than the outermost one or a body.
    private final Stack<Boolean> nestedBlockStack = new Stack<>();
    // What each scope level's declarations tell of the account's writing their names out as binds (see BIND_*).
    private final Stack<Map<String, Integer>> bindRuleStack = new Stack<>();
    // The scope levels exception handlers run at, innermost last.
    private final Stack<Integer> handlerLevels = new Stack<>();

    /** A declaration made directly in a nested block or in a handler. */
    private static final int BIND_DIRECT = 1;

    /** A declaration of a name an enclosing scope declares too. */
    private static final int BIND_SHADOWS = 2;

    /** A declaration of a name an enclosing scope's DECLARE section declares. */
    private static final int BIND_SHADOWS_DECLARED = 4;

    /** A declaration a DECLARE section makes. */
    private static final int BIND_DECLARE_SECTION = 8;

    /** Live variable map — for raw record-field / session-variable / result-set access. */
    public Map<String, Object> variables() {
        return variables;
    }

    public void enterScope() {
        enterBlockScope(false);
    }

    /**
     * Enter a scope, telling whether it is a BEGIN … END block nested in another.
     *
     * @param nestedBlock whether the scope is a nested block's
     */
    public void enterBlockScope(final boolean nestedBlock) {
        scopeStack.push(new HashMap<>(variables));
        declaredInScopeStack.push(new HashSet<>());
        isolatedScopeStack.push(Boolean.FALSE);
        nestedBlockStack.push(Boolean.valueOf(nestedBlock));
        bindRuleStack.push(new HashMap<String, Integer>());
    }

    /**
     * Enter a scope that inherits NOTHING and propagates NOTHING — a fresh variable namespace whose exit
     * restores the caller's variables exactly. Used for a SQL UDF body: a function is its own execution
     * context, so it can neither read nor clobber the variables of a procedure that calls it
     * (live-verified — a UDF body naming a caller procedure's variable fails "invalid identifier", and a
     * UDF declaring a variable the caller also has leaves the caller's value untouched).
     */
    public void enterIsolatedScope() {
        scopeStack.push(new HashMap<>(variables));
        declaredInScopeStack.push(new HashSet<>());
        isolatedScopeStack.push(Boolean.TRUE);
        nestedBlockStack.push(Boolean.FALSE);
        bindRuleStack.push(new HashMap<String, Integer>());
        variables.clear();
    }

    public void exitScope() {
        if (!scopeStack.isEmpty()) {
            final Map<String, Object> parentSnapshot = scopeStack.pop();
            final Set<String> declaredHere = declaredInScopeStack.isEmpty()
                ? Collections.emptySet() : declaredInScopeStack.pop();
            final boolean isolated = !isolatedScopeStack.isEmpty() && isolatedScopeStack.pop();
            if (!nestedBlockStack.isEmpty()) {
                nestedBlockStack.pop();
            }
            if (!bindRuleStack.isEmpty()) {
                bindRuleStack.pop();
            }

            if (isolated) {
                variables.clear();
                variables.putAll(parentSnapshot);
                return;
            }

            // Propagate updates to variables inherited from parent (not re-declared here),
            // discard variables newly declared in this scope (including shadowed ones).
            final Map<String, Object> updatedVars = new HashMap<>(parentSnapshot);
            for (final Map.Entry<String, Object> entry : variables.entrySet()) {
                final String name = entry.getKey();
                if (parentSnapshot.containsKey(name) && !declaredHere.contains(name)) {
                    // Inherited and not shadowed — propagate updated value to parent
                    updatedVars.put(name, entry.getValue());
                }
                // Newly declared or shadowed variables — discard when scope exits
            }
            variables.clear();
            variables.putAll(updatedVars);
        }
    }

    /** Called when a variable is DECLARED (not just assigned) in the current scope. */
    public void markDeclaredInCurrentScope(final String name) {
        markDeclaredInCurrentScope(name, false);
    }

    /**
     * Called when a variable is DECLARED in the current scope, by a DECLARE section or otherwise, remembering what the
     * account's writing of the name as a bind turns on (see {@link #namesWrittenAsBinds}).
     *
     * @param name           the name
     * @param declareSection whether a DECLARE section declares it
     */
    public void markDeclaredInCurrentScope(final String name, final boolean declareSection) {
        if (declaredInScopeStack.isEmpty()) {
            return;
        }
        final String key = name.toUpperCase(Locale.ROOT);
        declaredInScopeStack.peek().add(key);
        final int level = bindRuleStack.size() - 1;
        final Integer enclosing = isolatedScopeStack.peek().booleanValue() ? null : declarationBelow(key, level);
        int rule = declareSection ? BIND_DECLARE_SECTION : 0;
        if (nestedBlockStack.peek().booleanValue()
                || !handlerLevels.isEmpty() && handlerLevels.peek().intValue() == level) {
            rule |= BIND_DIRECT;
        }
        if (enclosing != null) {
            rule |= (enclosing.intValue() & BIND_DECLARE_SECTION) != 0 ? BIND_SHADOWS_DECLARED : BIND_SHADOWS;
        }
        bindRuleStack.peek().put(key, Integer.valueOf(rule));
    }

    /** The rule of the innermost declaration of a name below a scope level, or null when none declares it there. */
    private Integer declarationBelow(final String key, final int level) {
        for (int i = level - 1; i >= 0; i--) {
            if (isolatedScopeStack.get(i + 1).booleanValue()) {
                return null;
            }
            final Integer rule = bindRuleStack.get(i).get(key);
            if (rule != null) {
                return rule;
            }
        }
        return null;
    }

    /** An exception handler starts running, in the current scope. */
    public void enterHandlerFrame() {
        handlerLevels.push(Integer.valueOf(bindRuleStack.size() - 1));
    }

    /** The exception handler that started last has ended. */
    public void exitHandlerFrame() {
        if (!handlerLevels.isEmpty()) {
            handlerLevels.pop();
        }
    }

    /**
     * The names of the current scope an anonymous block's expression writes out as binds, {@code (:x)} rather than
     * {@code (x)} (live-verified): one a nested BEGIN … END block or an exception handler declares, read directly
     * there rather than in a branch, a loop or a deeper block, and one whose declaration shadows a name an enclosing
     * scope declares, wherever it is read — but never one whose declaration shadows a name an enclosing DECLARE
     * section declares.
     *
     * @return the names, upper-cased
     */
    public Set<String> namesWrittenAsBinds() {
        final Set<String> names = new HashSet<String>();
        final int current = bindRuleStack.size() - 1;
        for (final String name : variables.keySet()) {
            for (int i = current; i >= 0; i--) {
                final Integer rule = bindRuleStack.get(i).get(name);
                if (rule == null) {
                    if (isolatedScopeStack.get(i).booleanValue()) {
                        break;
                    }
                    continue;
                }
                final int bits = rule.intValue();
                if ((bits & BIND_SHADOWS_DECLARED) == 0
                        && ((bits & BIND_SHADOWS) != 0 || (bits & BIND_DIRECT) != 0 && i == current)) {
                    names.add(name);
                }
                break;
            }
        }
        return names;
    }

    /**
     * The names DECLARED in the innermost scope, the ones its exit discards.
     *
     * @return a copy of the names, upper-cased; empty outside any scope
     */
    public Set<String> declaredInCurrentScope() {
        return declaredInScopeStack.isEmpty()
            ? Collections.<String>emptySet() : new HashSet<String>(declaredInScopeStack.peek());
    }

    public void setVariable(final String name, final Object value) {
        variables.put(name.toUpperCase(Locale.ROOT), value);
    }

    public Object getVariable(final String name) {
        return variables.get(name.toUpperCase(Locale.ROOT));
    }

    /**
     * Take a name out of the variables, so that it is no name at all again.
     *
     * @param name the name
     */
    public void removeVariable(final String name) {
        variables.remove(name.toUpperCase(Locale.ROOT));
    }

    public Map<String, Object> getAllVariables() {
        return new HashMap<>(variables);
    }
}
