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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * The names the exception handlers running now may not read: what their blocks' BEGIN sections declared with LET.
 *
 * <p>A variable, a cursor or a RESULTSET that a block declares in its BEGIN section is not passed to the block's
 * handlers; one a handler uses belongs in the DECLARE section. The block compiles all the same, and a handler
 * that never touches the name runs. Reading it is what fails — in an expression, as a bind of a statement, as the
 * cursor an OPEN or a FOR names, in a block nested in the handler — and only when the read is reached: one on a
 * branch not taken costs nothing. A handler that assigns the name first, or declares it again, reads its own
 * value from then on, while an assignment the BEGIN section made changes nothing (live-verified).
 *
 * <p>The read ends the body (see {@link HandlerLetAbort}).
 */
public final class HandlerLets {

    /** The unread names of each running handler, the innermost handler's first. */
    private final Deque<Set<String>> unread = new ArrayDeque<Set<String>>();
    /** The statement each running handler is at, the innermost handler's first; null before its first. */
    private final Deque<List<ParseTree>> running = new ArrayDeque<List<ParseTree>>();

    /**
     * A handler starts.
     *
     * @param lets the names its block's BEGIN section declares with LET, upper-cased
     */
    public void enter(final Set<String> lets) {
        unread.push(new HashSet<String>(lets));
        running.push(new ArrayList<ParseTree>());
    }

    /** The innermost handler has ended. */
    public void exit() {
        unread.pop();
        running.pop();
    }

    /**
     * The innermost handler runs a statement of its own.
     *
     * @param statement the statement
     */
    public void at(final ParseTree statement) {
        final List<ParseTree> current = running.peek();
        if (current != null) {
            current.clear();
            current.add(statement);
        }
    }

    /**
     * A name was assigned or declared: a running handler reads it from now on.
     *
     * @param name the name
     */
    public void written(final String name) {
        if (unread.isEmpty()) {
            return;
        }
        final String canonical = name.toUpperCase(Locale.ROOT);
        for (final Set<String> names : unread) {
            names.remove(canonical);
        }
    }

    /**
     * Refuse the read of a name no running handler may read.
     *
     * @param name     the name read
     * @param line     the line the name is written on, or 0 when the reader does not know it
     * @param position the name's position on that line
     */
    public void requireReadable(final String name, final int line, final int position) {
        if (unread.isEmpty() || name == null) {
            return;
        }
        final String canonical = name.toUpperCase(Locale.ROOT);
        for (final Set<String> names : unread) {
            if (!names.contains(canonical)) {
                continue;
            }
            if (line > 0) {
                throw new HandlerLetAbort(canonical, line, position);
            }
            for (final List<ParseTree> current : running) {
                final Token written = current.isEmpty() ? null : firstNaming(current.get(0), canonical, new Token[1]);
                if (written != null) {
                    throw new HandlerLetAbort(canonical, written.getLine(), written.getCharPositionInLine());
                }
            }
            throw new HandlerLetAbort(canonical);
        }
    }

    /**
     * Take the running handlers out, for a body that runs as a frame of its own: its names are no handler's.
     *
     * @return what was taken out, to be handed back to {@link #restore}
     */
    public HandlerLets setAside() {
        final HandlerLets aside = new HandlerLets();
        aside.unread.addAll(unread);
        aside.running.addAll(running);
        unread.clear();
        running.clear();
        return aside;
    }

    /**
     * Put back what {@link #setAside} took out.
     *
     * @param aside the value that call returned
     */
    public void restore(final HandlerLets aside) {
        unread.clear();
        running.clear();
        unread.addAll(aside.unread);
        running.addAll(aside.running);
    }

    /** The first word of a statement that spells the name, a bind's colon standing in for the name it opens. */
    private static Token firstNaming(final ParseTree tree, final String canonical, final Token[] previous) {
        if (tree instanceof TerminalNode) {
            final Token symbol = ((TerminalNode) tree).getSymbol();
            final Token before = previous[0];
            previous[0] = symbol;
            if (!canonical.equals(symbol.getText().toUpperCase(Locale.ROOT))) {
                return null;
            }
            return before != null && ":".equals(before.getText()) && before.getStopIndex() + 1 == symbol.getStartIndex()
                ? before : symbol;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            final Token found = firstNaming(tree.getChild(i), canonical, previous);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
