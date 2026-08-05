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

    /** Live variable map — for raw record-field / session-variable / result-set access. */
    public Map<String, Object> variables() {
        return variables;
    }

    public void enterScope() {
        scopeStack.push(new HashMap<>(variables));
        declaredInScopeStack.push(new HashSet<>());
        isolatedScopeStack.push(Boolean.FALSE);
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
        variables.clear();
    }

    public void exitScope() {
        if (!scopeStack.isEmpty()) {
            Map<String, Object> parentSnapshot = scopeStack.pop();
            Set<String> declaredHere = declaredInScopeStack.isEmpty()
                ? Collections.emptySet() : declaredInScopeStack.pop();
            boolean isolated = !isolatedScopeStack.isEmpty() && isolatedScopeStack.pop();

            if (isolated) {
                variables.clear();
                variables.putAll(parentSnapshot);
                return;
            }

            // Propagate updates to variables inherited from parent (not re-declared here),
            // discard variables newly declared in this scope (including shadowed ones).
            Map<String, Object> updatedVars = new HashMap<>(parentSnapshot);
            for (final Map.Entry<String, Object> entry : variables.entrySet()) {
                String name = entry.getKey();
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
        if (!declaredInScopeStack.isEmpty()) {
            declaredInScopeStack.peek().add(name.toUpperCase());
        }
    }

    public void setVariable(final String name, final Object value) {
        variables.put(name.toUpperCase(), value);
    }

    public Object getVariable(final String name) {
        return variables.get(name.toUpperCase());
    }

    public Map<String, Object> getAllVariables() {
        return new HashMap<>(variables);
    }
}
