grammar Frostlake;

// The word IDENTIFIER leaves the lexer as one of three tokens, see the lexer members below.
tokens { KW_IDENTIFIER_REF, KW_IDENTIFIER_OPEN }

@lexer::header {
import java.util.ArrayList;
import java.util.List;
}

@lexer::members {
    /** Tokens already read past an IDENTIFIER keyword to classify it, handed out before the lexer reads on. */
    private final List<Token> tokensAhead = new ArrayList<Token>();

    /**
     * The word IDENTIFIER followed by a parenthesis is KW_IDENTIFIER_REF when a whole object reference follows
     * it — a string, a variable, a bind variable or an integer, then the closing parenthesis — and
     * KW_IDENTIFIER_OPEN when anything else does. Live reads the two apart wherever a name may be followed by
     * a parenthesis of its own: CREATE TABLE IDENTIFIER('n' || 'x') (a INT) names a table IDENTIFIER whose
     * column list is refused at the string, and CREATE TABLE identifier (a INT) creates that table, while a
     * FROM clause refuses IDENTIFIER('t' || '1') at the '||'. Anywhere else the word stays KW_IDENTIFIER.
     */
    @Override
    public Token nextToken() {
        final Token token = tokensAhead.isEmpty() ? super.nextToken() : tokensAhead.remove(0);
        if (token.getType() == KW_IDENTIFIER && token instanceof WritableToken) {
            ((WritableToken) token).setType(identifierKeywordType());
        }
        return token;
    }

    @Override
    public void reset() {
        tokensAhead.clear();
        super.reset();
    }

    private int identifierKeywordType() {
        if (typeAhead(0) != LPAREN) {
            return KW_IDENTIFIER;
        }
        final int argument = typeAhead(1);
        final int closing;
        if (argument == COLON) {
            // A bind variable's name is never quoted there.
            closing = typeAhead(2) == RPAREN || typeAhead(2) == Token.EOF || typeAhead(2) == QUOTED_IDENTIFIER
                ? Token.EOF : typeAhead(3);
        } else if (argument == STRING_LITERAL || argument == DOLLAR_QUOTED_STRING || argument == INTEGER_LITERAL
                || argument == SESSION_VAR_REF || argument == QUESTION) {
            closing = typeAhead(2);
        } else {
            closing = Token.EOF;
        }
        // The two are the grammar's own token types, declared for the parser; the lexer has no rule for them.
        return closing == RPAREN ? FrostlakeParser.KW_IDENTIFIER_REF : FrostlakeParser.KW_IDENTIFIER_OPEN;
    }

    /** The type of the token {@code index} places past the keyword, reading on as far as it needs. */
    private int typeAhead(final int index) {
        while (tokensAhead.size() <= index) {
            if (!tokensAhead.isEmpty() && tokensAhead.get(tokensAhead.size() - 1).getType() == Token.EOF) {
                return Token.EOF;
            }
            tokensAhead.add(super.nextToken());
        }
        return tokensAhead.get(index).getType();
    }
}

@parser::members {
    /** Whether a name may stand bare after SHOW GRANTS ON: any name but a lone kind word live reserves. Asked once
     *  the name is read, so the refusal lands on the token after it, where live reports it. */
    private boolean bareGrantsNameAllowed(final QualifiedNameContext name) {
        if (name.getStart() != name.getStop()) {
            return true;
        }
        final int word = name.getStart().getType();
        return word != DATABASE && word != SCHEMA && word != TABLE && word != VIEW;
    }

    /** Which unreserved words may serve as a BARE (AS-less) alias HERE, given what follows them.
     *  Two words need the question asked, and both because the alias reading and a clause reading are
     *  each viable to plain lookahead:
     *
     *  <p>EXCEPT is an alias only when the next token cannot start a set-operation right-hand side —
     *  `SELECT 1 except` / `FROM t except` are aliases, `... EXCEPT SELECT ...` stays a set-op
     *  (statements need no semicolon between them, so both parses reach EOF).
     *
     *  <p>LIMIT is an alias except when NULL follows it, where live reads the CLAUSE: `FROM t LIMIT
     *  NULL` answers every row. The alias survives everywhere else — `FROM t LIMIT`, `FROM t LIMIT
     *  ORDER BY a` and `FROM t LIMIT LIMIT 2` are all aliases on both engines — so only that one
     *  pairing is taken away. Asking it here rather than in a new decision is deliberate: this
     *  predicate is already hoisted into the alias choice, and the count-versus-alias question is
     *  exactly the kind that has cost this grammar dearly when given a decision of its own.
     *
     *  <p>Comments do not hide the NULL: the lexer skips them, so `LIMIT /*…*&#47; NULL` reads the same
     *  as the plain spelling. Every other word passes unchanged. */
    /** Whether a null treatment may stand INSIDE the parentheses here. The account takes it there for
     *  FIRST_VALUE and LAST_VALUE only — `LAG(v IGNORE NULLS)` and `NTH_VALUE(v, 1 IGNORE NULLS)` are
     *  syntax errors at the IGNORE, while every one of them takes the treatment AFTER the closing
     *  parenthesis (live-verified). The name is read back off the token stream rather than passed in,
     *  so the predicate costs nothing where it does not apply. */
    private boolean insideNullTreatmentAllowed() {
        for (int back = 1; back < 40; back++) {
            final Token token = _input.LT(-back);
            if (token == null || token.getType() == Token.EOF) {
                return false;
            }
            if (token.getType() == LPAREN) {
                final Token name = _input.LT(-back - 1);
                if (name == null) {
                    return false;
                }
                final String spelled = name.getText().toUpperCase();
                return spelled.equals("FIRST_VALUE") || spelled.equals("LAST_VALUE");
            }
        }
        return false;
    }

    /** Whether an interval literal's qualifier goes on to a trailing field: a TO with a singular field after it.
     *  Anything else leaves the TO unread, so the refusal lands on it, where live reports it. */
    private boolean intervalRangeFollows() {
        if (_input.LT(1).getType() != TO) {
            return false;
        }
        switch (_input.LT(2).getType()) {
            case YEAR:
            case MONTH:
            case DAY:
            case HOUR:
            case MINUTE:
            case SECOND:
                return true;
            default:
                return false;
        }
    }

    private boolean bareAliasAllowed() {
        final int here = _input.LT(1).getType();
        if (here == LIMIT) {
            return _input.LT(2).getType() != NULL;
        }
        if (here != EXCEPT) {
            return true;
        }
        final int next = _input.LT(2).getType();
        return next != SELECT && next != LPAREN && next != WITH && next != ALL && next != DISTINCT;
    }

    /** Whether a token may BEGIN a column reference. THREE of the five words a NAME position admits do
     *  not reach an expression: CASE, CAST and WHEN each lead their own construct there, and live
     *  refuses `SELECT case FROM t` as a syntax error EVEN WHEN a column of that name exists — defining
     *  one and referencing it are different vocabularies. TRY_CAST is the same story and was this
     *  guard's original occupant.
     *
     *  <p>CONSTRAINT and DEFAULT are NOT in that group, measured: live PARSES both here and answers
     *  "invalid identifier", which is the resolver talking, not the parser. They differ from each other
     *  further down — a CONSTRAINT column RESOLVES once it exists, while an unquoted DEFAULT never does
     *  (see ExpressionAstBuilder, where that one is refused).
     *
     *  <p>A predicate here is hoisted into every decision that can reach this alternative, which is why
     *  the qualified STAR takes starQualifiedName rather than qualifiedName: sharing the rule let the
     *  hoisted predicate eliminate `case.*` along with the bare reference. */
    private boolean expressionLeadingName() {
        switch (_input.LT(1).getType()) {
            case TRY_CAST:
                return false;
            case CASE:
            case CAST:
            case WHEN:
                // QUALIFYING is not REFERENCING: `case.a` names a column of the table CASE and live
                // reads it, while the bare `case` is where the construct starts. A star over such a
                // table expands to exactly these qualified references, so refusing them outright
                // would break `SELECT case.* FROM case` on the way back out.
                return _input.LT(2).getType() == DOT;
            default:
                return !callOfParenthesisedArgument();
        }
    }

    /**
     * Refuses a {@code TABLE(<operand>)} whose operand live cannot read there. It reads the name a string, a
     * dollar-quoted string, a session variable or a bind spells, or a table function's call, and refuses
     * everything else where the reading stops: at the operator of {@code 'a' || 'b'}, at the first token of a
     * number, a NULL or a parenthesis, and at the ')' after a bare name — that one followed by a second line
     * at the token after it, as live's recovery gives (live-verified).
     */
    private void refuseUnreadableTableOperand(final TableSourceContext source) {
        final ExpressionContext operand = source.expression();
        if (operand == null || source.TABLE() == null) {
            return;
        }
        // A table function's arguments take no quantifier: TABLE(SPLIT_TO_TABLE(ALL 'a,b', ',')) and
        // TABLE(FLATTEN(DISTINCT v)) are syntax errors at the ALL or the DISTINCT (live-verified).
        final Token quantifier = callQuantifier(operand);
        if (quantifier != null) {
            notifyErrorListeners(quantifier, "unexpected '" + quantifier.getText() + "'",
                new RefusedWordFault(this, source, true));
            return;
        }
        if (readsAsTableOperand(operand)) {
            return;
        }
        if (operand instanceof QualifiedNameExprContext) {
            // A bare name could still open a call, so the reading stops at the ')' — and live's recovery
            // names the token after it on a second line.
            final Token close = _input.get(operand.getStop().getTokenIndex() + 1);
            notifyErrorListeners(close, "unexpected '" + close.getText() + "'",
                new IdentifierReferenceFault(this, source, false));
            final Token after = _input.get(close.getTokenIndex() + 1);
            notifyErrorListeners(after, "unexpected '" + after.getText() + "'",
                new IdentifierReferenceFault(this, source, true));
            return;
        }
        final Token fault = operand.getChildCount() > 1 && operand.getChild(0) instanceof ParserRuleContext
            ? _input.get(((ParserRuleContext) operand.getChild(0)).getStop().getTokenIndex() + 1)
            : operand.getStart();
        notifyErrorListeners(fault, "unexpected '" + fault.getText() + "'",
            new IdentifierReferenceFault(this, source, true));
    }

    /** The DISTINCT or ALL a call operand is written with before its arguments, or null. */
    private Token callQuantifier(final ExpressionContext operand) {
        TerminalNode written = null;
        if (operand instanceof FunctionCallExprContext) {
            final FunctionCallExprContext call = (FunctionCallExprContext) operand;
            written = call.DISTINCT() != null ? call.DISTINCT() : call.ALL();
        } else if (operand instanceof FunctionCallNamedArgsExprContext) {
            final FunctionCallNamedArgsExprContext call = (FunctionCallNamedArgsExprContext) operand;
            written = call.DISTINCT() != null ? call.DISTINCT() : call.ALL();
        } else if (operand instanceof FunctionCallMixedArgsExprContext) {
            final FunctionCallMixedArgsExprContext call = (FunctionCallMixedArgsExprContext) operand;
            written = call.DISTINCT() != null ? call.DISTINCT() : call.ALL();
        } else if (operand instanceof FunctionCallStarExprContext) {
            written = ((FunctionCallStarExprContext) operand).DISTINCT();
        }
        return written == null ? null : written.getSymbol();
    }

    /** Whether an operand names a relation the way live reads one there, or calls a table function. */
    private boolean readsAsTableOperand(final ExpressionContext operand) {
        if (operand instanceof SessionVarExprContext || operand instanceof BindVarExprContext
                || operand instanceof FunctionCallExprContext || operand instanceof FunctionCallStarExprContext
                || operand instanceof FunctionCallMixedArgsExprContext
                || operand instanceof FunctionCallNamedArgsExprContext
                || operand instanceof SystemUserTaskCancelExprContext) {
            return true;
        }
        if (!(operand instanceof LiteralExprContext)) {
            return false;
        }
        final int written = operand.getStart().getType();
        return written == STRING_LITERAL || written == DOLLAR_QUOTED_STRING;
    }

    /**
     * Refuses an IDENTIFIER() reference that is not whole, as live does: at the first token after the opening
     * parenthesis that a whole reference cannot take — {@code 'a' || ''} at the '||', {@code UPPER('a')} at
     * UPPER, an empty pair at its ')' — and, when a comma stands after that token inside the parentheses,
     * once more at the first ')' after the comma, which ends the statement's report:
     * {@code IDENTIFIER(CONCAT('a', 'b'))} is CONCAT, then CONCAT's own ')' — except after DROP and DESCRIBE, where
     * the one line ends the report ({@code commaLine} false).
     */
    private void refuseOpenedIdentifierReference(final ParserRuleContext reference, final Token open,
                                                 final Token closing, final boolean commaLine) {
        final int close = closing.getTokenIndex();
        final Token fault = _input.get(openedReferenceFault(open.getTokenIndex()));
        notifyErrorListeners(fault, "unexpected '" + fault.getText() + "'",
            new IdentifierReferenceFault(this, reference, !commaLine));
        for (int comma = fault.getTokenIndex(); commaLine && comma < close; comma++) {
            if (_input.get(comma).getType() != COMMA) {
                continue;
            }
            for (int paren = comma + 1; paren <= close; paren++) {
                if (_input.get(paren).getType() == RPAREN) {
                    notifyErrorListeners(_input.get(paren), "unexpected ')'",
                        new IdentifierReferenceFault(this, reference, true));
                    return;
                }
            }
        }
    }

    /**
     * Refuses a word live's lexer keeps as a keyword where a class name stands, at the word: SHOW T and DROP T y
     * are syntax errors at the T. After DROP live names the token after the word as well, when that token could
     * be a name or is a parenthesis — DROP T y is 'T' then 'y', DROP T (x) 'T' then '(', while DROP T 1 and DROP T;
     * are 'T' alone — and nothing more of the statement. The kinds live drops that Frostlake has no statements for
     * pass: DROP ALERT a misses the alert. A word opening a two-word kind is refused at the word after it instead,
     * unless that word completes the kind: DROP EXTERNAL y is 'y', while DROP EXTERNAL TABLE x misses the external
     * table; and after DROP the plural listing words are keywords too, DROP SECRETS s being 'SECRETS' then 's'
     * (live-verified).
     */
    private void refuseKeywordClassName(final ClassNameContext name) {
        final Token word = name.getStart();
        final boolean drop = name.getParent() instanceof DropClassStatementContext;
        if (drop && word.getType() == ALERT) {
            // DROP ALERT is a statement of its own, so what follows ALERT is judged as the alert's name.
            final Token next = _input.LT(1);
            notifyErrorListeners(next, "unexpected '" + next.getText() + "'", new RefusedWordFault(this, name, true));
            return;
        }
        final boolean keyword = !ClassNameWords.isClassWord(word) || drop && ClassNameWords.isListingWord(word);
        if (!keyword || drop && UnmodelledDropKind.of(word) != null) {
            return;
        }
        final Token next = _input.LT(1);
        if (drop && ClassNameWords.opensDropKind(word)) {
            if (!ClassNameWords.completesDropKind(word, next)) {
                notifyErrorListeners(next, "unexpected '" + next.getText() + "'", new RefusedWordFault(this, name, true));
            }
            return;
        }
        final boolean stacks = drop && (next.getType() == LPAREN || next.getType() == RPAREN
            || getATN().nextTokens(getATN().ruleToStartState[RULE_identifier]).contains(next.getType()));
        notifyErrorListeners(word, "unexpected '" + word.getText() + "'", new RefusedWordFault(this, name, !stacks));
        if (stacks) {
            notifyErrorListeners(next, "unexpected '" + next.getText() + "'", new RefusedWordFault(this, name, true));
        }
    }

    /**
     * Refuses what live refuses in a signature's item or parameter once it is read: TRUE or FALSE as the word, at
     * the word — DROP TABLE t1 (TRUE) is 'TRUE' alone, while a DESCRIBE whose FIRST item it is names the token after
     * it too, DESCRIBE TABLE t1 (TRUE) being 'TRUE' then ')' — and parameters after a quoted word, at their '(':
     * DESCRIBE TABLE t1 ("a"(1)) (live-verified).
     */
    private void refuseSignatureWord(final ParserRuleContext item, final Token word, final boolean isItem) {
        if (word.getType() == TRUE || word.getType() == FALSE) {
            final boolean firstOfDescribe = isItem && item.getParent() instanceof ObjectSignatureContext
                && ((ObjectSignatureContext) item.getParent()).objectSignatureItem(0) == item
                && item.getParent().getParent() instanceof DescribeStatementContext;
            int next = word.getTokenIndex() + 1;
            while (_input.get(next).getChannel() != Token.DEFAULT_CHANNEL) {
                next++;
            }
            final Token after = _input.get(next);
            notifyErrorListeners(word, "unexpected '" + word.getText() + "'",
                new RefusedWordFault(this, item, !firstOfDescribe));
            if (firstOfDescribe) {
                notifyErrorListeners(after, "unexpected '" + after.getText() + "'", new RefusedWordFault(this, item, true));
            }
            return;
        }
        final ObjectSignatureParametersContext parameters = item instanceof ObjectSignatureItemContext
            ? ((ObjectSignatureItemContext) item).objectSignatureParameters()
            : ((ObjectSignatureParameterContext) item).objectSignatureParameters();
        if (word.getType() == QUOTED_IDENTIFIER && parameters != null) {
            notifyErrorListeners(parameters.LPAREN().getSymbol(), "unexpected '('", new RefusedWordFault(this, item, true));
        }
    }

    /**
     * Refuses an argument of an overload's type list written with its name, at its type, as the one line of the
     * statement: the list takes types alone (live-verified). The type is named when the token before it is the
     * argument's name rather than the list's '(' or ','.
     */
    private void refuseNamedArgument(final Token type) {
        int before = type.getTokenIndex() - 1;
        while (before > 0 && _input.get(before).getChannel() != Token.DEFAULT_CHANNEL) {
            before--;
        }
        final int previous = _input.get(before).getType();
        if (previous != LPAREN && previous != COMMA) {
            notifyErrorListeners(type, "unexpected '" + type.getText() + "'", new RefusedWordFault(this, _ctx, true));
        }
    }

    /** The index of the first token after the parenthesis at {@code open} that a whole reference cannot take. */
    private int openedReferenceFault(final int open) {
        final int argument = _input.get(open + 1).getType();
        if (argument == COLON) {
            final int name = _input.get(open + 2).getType();
            final boolean named = name == INTEGER_LITERAL || name != QUOTED_IDENTIFIER
                && getATN().nextTokens(getATN().ruleToStartState[RULE_identifier]).contains(name);
            return named ? open + 3 : open + 2;
        }
        if (argument == STRING_LITERAL || argument == DOLLAR_QUOTED_STRING || argument == INTEGER_LITERAL
                || argument == SESSION_VAR_REF || argument == QUESTION) {
            return open + 2;
        }
        return open + 1;
    }

    /** Whether the name starting here is called with an argument that opens a parenthesis of its own, as in
     *  {@code ABS((SELECT -1))}, or with a subquery written without one, as in {@code ABS(SELECT -1)}. Such a
     *  name is a call, never a column. A statement's closing semicolon is optional here, so the same text also
     *  reads as two statements, {@code SELECT ABS} and {@code ((SELECT -1))}. ANTLR settles that ambiguity for
     *  the lower alternative, this bare name, and the second statement was then refused at its parenthesis.
     *  The outer-join marker {@code c(+)} keeps its reading: a plus follows its parenthesis. */
    private boolean callOfParenthesisedArgument() {
        int last = 1;
        while (_input.LT(last + 1).getType() == DOT) {
            last += 2;
        }
        if (_input.LT(last + 1).getType() != LPAREN) {
            return false;
        }
        final int argumentStart = _input.LT(last + 2).getType();
        return argumentStart == LPAREN || argumentStart == SELECT || argumentStart == WITH;
    }

    /** Whether a function argument here may be a subquery written without parentheses of its own. Only the
     *  call's FIRST argument may be one, and its select list then runs to the closing parenthesis, so a comma
     *  after it adds a select item rather than an argument: live reads {@code COALESCE(SELECT 1, 2)} as one
     *  two-column argument and refuses {@code CONCAT('a', SELECT 'b')} as a syntax error. The subquery must
     *  start with SELECT or WITH, so an argument opening a parenthesis never has two readings. */
    private boolean bareSubqueryArgument() {
        final int first = _input.LT(1).getType();
        return _input.LT(-1).getType() == LPAREN && (first == SELECT || first == WITH);
    }

    /** Whether a token may lead a table name in the FROM position. A join keyword there is still a
     *  KEYWORD when the name is BARE — live reads `FROM asof` as the start of an ASOF JOIN and refuses
     *  it at end of input — but names a container when a dot follows, `FROM asof.t` reaching
     *  "Schema 'ASOF' does not exist". Both spellings measured, for all eleven words. */
    private boolean tableNameLeadAllowed() {
        switch (_input.LT(1).getType()) {
            case ASOF:
            case CROSS:
            case FULL:
            case INNER:
            case JOIN:
            case LATERAL:
            case LEFT:
            case MATCH_CONDITION:
            case NATURAL:
            case RIGHT:
            case USING:
                return _input.LT(2).getType() == DOT;
            default:
                return true;
        }
    }

    /** The words that may name a COLUMN but may NOT be a bare (AS-less) table alias, measured word by
     *  word against a live account over a RAW connection. Every one of them LEADS something in the FROM
     *  clause — a join, a lateral, a match condition — so a bare alias spelling it would swallow the
     *  clause it starts, and live refuses the qualified `<w>.x` form with it.
     *
     *  <p>The list once held four more — BREAK, CONTINUE, RAISE and RETURN — described as statement
     *  keywords live refuses here even though nothing in a FROM clause begins with them. It does not:
     *  all four are ordinary aliases, and `SELECT break.x FROM st break` RESOLVES. They were excluded
     *  because the test harness split `SELECT x FROM st break` into two statements before submitting
     *  it, so the account was asked about a bare `break` and refused that instead. */
    private boolean bareTableAliasAllowed() {
        switch (_input.LT(1).getType()) {
            case ASOF: case CROSS: case FULL: case INNER: case JOIN:
            case LATERAL: case LEFT: case MATCH_CONDITION: case NATURAL:
            case RIGHT: case USING:
                return false;
            default:
                return true;
        }
    }
}

// Parser Rules - Simplified for demonstration
sqlScript
    : (flowChain SEMI?)+ EOF
    ;

// One statement chain and nothing after it: the syntax-error listener parses a statement's own text with it to learn
// whether a word can continue that statement. No other rule refers to it.
singleFlowChain
    : flowChain EOF
    ;

// The flow operator: stmt ->> stmt ->> stmt. Each stage may read a PRIOR stage's result via a
// $n table reference, where n counts BACKWARD ($1 = the immediately preceding statement).
// The chain's result is the LAST statement's result.
flowChain
    : statement (FLOW_ARROW statement)*
    ;

statement
    : ddlStatement
    | dmlStatement
    | explainStatement
    | transactionStatement
    | listStatement
    | getStatement
    | putStatement
    | removeStatement
    | sessionSetStatement
    | sessionUnsetStatement
    | proceduralStatement
    // After the procedural statements: a query block takes an INTO clause too, so a statement written
    // SELECT … INTO … reads both ways, and the tie goes to the earlier alternative — the block's own SELECT …
    // INTO. A query this alternative alone can read (a set operation, a parenthesised query) keeps its INTO
    // clause for the compiler to refuse.
    | queryStatement
    // Before the class listing, which reads SHOW SECRETS as a refused class name.
    | securityObjectListing
    | showStatement
    | showClassStatement
    | describeStatement
    | securityStatement
    | taskStatement
    | accessControlStatement
    | containerServicesStatement
    ;

ddlStatement
    // First, so that ALTER USER u UNSET NETWORK_POLICY reads as the attachment it is rather than as the
    // user's catch-all UNSET property.
    : securityObjectStatement
    | createStatement
    | notebookStatement
    | streamlitStatement
    | dropStatement
    | dropClassStatement
    | undropStatement
    | alterStatement
    | useStatement
    | commentStatement
    | truncateStatement
    | integrationStatement
    | externalVolumeStatement
    ;

// NOTEBOOK and STREAMLIT objects: catalog metadata for an app's files, settings and versions. Nothing runs them:
// EXECUTE NOTEBOOK answers as a run that succeeded, and the Git actions (PUSH, PULL) refuse as they do for an app
// whose versions come from no Git repository.
notebookStatement
    : CREATE or_replace? NOTEBOOK if_not_exists? qualifiedName (FROM STRING_LITERAL)? notebookOption* SEMI?
    | ALTER NOTEBOOK if_exists? qualifiedName RENAME TO qualifiedName SEMI?
    | ALTER NOTEBOOK if_exists? qualifiedName SET notebookSetOption+ SEMI?
    | ALTER NOTEBOOK if_exists? qualifiedName UNSET notebookUnsetProperty (COMMA notebookUnsetProperty)* SEMI?
    | ALTER NOTEBOOK qualifiedName appVersionAction SEMI?
    | EXECUTE NOTEBOOK qualifiedName (LPAREN (expression (COMMA expression)*)? RPAREN)? SEMI?
    | DROP NOTEBOOK if_exists? dropInstanceName dropBehavior? SEMI?
    | UNDROP NOTEBOOK qualifiedName SEMI?
    ;

notebookOption
    : MAIN_FILE EQ STRING_LITERAL
    | COMMENT EQ STRING_LITERAL
    | QUERY_WAREHOUSE EQ identifier
    | IDLE_AUTO_SHUTDOWN_TIME_SECONDS EQ INTEGER_LITERAL
    | RUNTIME_NAME EQ STRING_LITERAL
    | COMPUTE_POOL EQ STRING_LITERAL
    | WAREHOUSE EQ identifier
    | SECRETS EQ appSecretList
    ;

notebookSetOption
    : COMMENT EQ STRING_LITERAL
    | QUERY_WAREHOUSE EQ identifier
    | IDLE_AUTO_SHUTDOWN_TIME_SECONDS EQ INTEGER_LITERAL
    | SECRETS EQ appSecretList
    ;

notebookUnsetProperty
    : QUERY_WAREHOUSE
    | COMMENT
    | SECRETS
    | IDLE_AUTO_SHUTDOWN_TIME_SECONDS
    ;

// The version and Git actions notebooks and Streamlit apps share. Each takes its parameters as name = value pairs,
// and the handler refuses a name the action does not take, or a value it cannot, as the account does.
appVersionAction
    : ADD LIVE VERSION appVersionAlias? FROM LAST appActionParameter*
    | ADD VERSION if_not_exists? appVersionAlias? FROM (STRING_LITERAL | stageRef) appActionParameter*
    | COMMIT appActionParameter*
    | ABORT
    | PUSH (TO STRING_LITERAL)? appActionParameter*
    | PULL appActionParameter*
    ;

// A version's alias, written as a name live's lexer keeps as no keyword of its own (FIRST, LAST, LIVE, VERSION or
// COMMENT written bare are refused where they stand), or quoted; the handler refuses the aliases the account keeps
// for itself.
appVersionAlias
    : {ClassNameWords.isPlainName(_input.LT(1))}? identifier
    ;

appActionParameter
    : (identifier | PASSWORD) EQ appActionValue
    ;

// A parameter's value as written: the handler takes a string or a name and refuses a number or a boolean as an
// invalid value, a compilation error rather than a syntax error.
appActionValue
    : STRING_LITERAL
    | DOLLAR_QUOTED_STRING
    | MINUS? (INTEGER_LITERAL | FLOAT_LITERAL)
    | booleanValue
    | qualifiedName
    ;

// SECRETS = ('<variable>' = <secret>, …). Only the first entry parses with its variable written as a bare word,
// which the handler refuses as the account does; a bare word further on is a syntax error there as well.
appSecretList
    : LPAREN (appSecret (COMMA appSecretEntry)*)? RPAREN
    ;

appSecret
    : (STRING_LITERAL | identifier) EQ appSecretValue
    ;

appSecretEntry
    : STRING_LITERAL EQ appSecretValue
    ;

appSecretValue
    : qualifiedName
    | STRING_LITERAL
    ;

streamlitStatement
    : CREATE or_replace? STREAMLIT if_not_exists? qualifiedName (FROM STRING_LITERAL)? streamlitOption* SEMI?
    | ALTER STREAMLIT if_exists? qualifiedName SET streamlitOption+ SEMI?
    | ALTER STREAMLIT if_exists? qualifiedName UNSET streamlitUnsetProperty (COMMA streamlitUnsetProperty)* SEMI?
    | ALTER STREAMLIT if_exists? qualifiedName RENAME TO qualifiedName SEMI?
    | ALTER STREAMLIT qualifiedName appVersionAction SEMI?
    | DROP STREAMLIT if_exists? dropInstanceName dropBehavior? SEMI?
    | UNDROP STREAMLIT qualifiedName SEMI?
    ;

streamlitOption
    : ROOT_LOCATION EQ STRING_LITERAL
    | MAIN_FILE EQ STRING_LITERAL
    | QUERY_WAREHOUSE EQ identifier
    | RUNTIME_NAME EQ STRING_LITERAL
    | COMPUTE_POOL EQ identifier
    | COMMENT EQ STRING_LITERAL
    | TITLE EQ STRING_LITERAL
    | IMPORTS EQ LPAREN (STRING_LITERAL (COMMA STRING_LITERAL)*)? RPAREN
    | EXTERNAL_ACCESS_INTEGRATIONS EQ LPAREN (identifier (COMMA identifier)*)? RPAREN
    | SECRETS EQ appSecretList
    ;

streamlitUnsetProperty
    : EXTERNAL_ACCESS_INTEGRATIONS
    | QUERY_WAREHOUSE
    | TITLE
    | COMMENT
    | SECRETS
    ;

// Network rules, network policies, password policies and secrets. Their properties are read by name and checked
// against each kind's documented set by the handler, which refuses any other as an invalid property of the
// object's type. Attaching a password policy or a network policy to the account or to a user is recorded and never
// enforced: the engine authenticates no one.
securityObjectStatement
    : CREATE (or_replace | or_alter)? NETWORK RULE if_not_exists? qualifiedName securityProperty* SEMI?
    | CREATE (or_replace | or_alter)? NETWORK POLICY if_not_exists? identifier securityProperty* SEMI?
    | CREATE or_replace? PASSWORD POLICY if_not_exists? qualifiedName securityProperty* SEMI?
    | CREATE or_replace? SECRET if_not_exists? qualifiedName securityProperty* SEMI?
    | ALTER NETWORK RULE if_exists? qualifiedName (securityPropertyChange | RENAME TO qualifiedName) SEMI?
    | ALTER NETWORK POLICY if_exists? identifier (securityPropertyChange | RENAME TO identifier
      | (ADD | REMOVE) optionKey EQ securityPropertyValue | tagSet | tagUnset) SEMI?
    | ALTER PASSWORD POLICY if_exists? qualifiedName (securityPropertyChange | RENAME TO qualifiedName | tagSet
      | tagUnset) SEMI?
    | ALTER SECRET if_exists? qualifiedName securityPropertyChange SEMI?
    | DROP NETWORK RULE if_exists? qualifiedName dropBehavior? SEMI?
    | DROP NETWORK POLICY if_exists? identifier dropBehavior? SEMI?
    | DROP PASSWORD POLICY if_exists? qualifiedName dropBehavior? SEMI?
    | DROP SECRET if_exists? qualifiedName dropBehavior? SEMI?
    | ALTER (ACCOUNT | USER if_exists? identifier) SET PASSWORD POLICY qualifiedName FORCE? SEMI?
    | ALTER (ACCOUNT | USER if_exists? identifier) UNSET PASSWORD POLICY SEMI?
    | ALTER (ACCOUNT | USER if_exists? identifier) SET NETWORK_POLICY EQ (identifier | STRING_LITERAL) SEMI?
    | ALTER (ACCOUNT | USER if_exists? identifier) UNSET NETWORK_POLICY SEMI?
    ;

securityProperty
    : optionKey EQ securityPropertyValue
    ;

securityPropertyValue
    : STRING_LITERAL
    | MINUS? INTEGER_LITERAL
    | identifier
    | LPAREN (STRING_LITERAL (COMMA STRING_LITERAL)*)? RPAREN
    ;

// SET takes its properties separated by blanks or commas alike.
securityPropertyChange
    : SET securityProperty (COMMA? securityProperty)*
    | UNSET optionKey (COMMA optionKey)*
    ;

// The listings and descriptions of those four kinds, each with the modifiers the account takes: SHOW NETWORK
// POLICIES takes a LIKE and SHOW SECRETS a STARTS WITH and a LIMIT, which their pages leave out.
securityObjectListing
    : SHOW NETWORK RULES (LIKE STRING_LITERAL)? securityListingScope? (STARTS WITH STRING_LITERAL)? (LIMIT INTEGER_LITERAL (FROM STRING_LITERAL)?)? SEMI?
    | SHOW NETWORK POLICIES (LIKE STRING_LITERAL)? SEMI?
    | SHOW PASSWORD POLICIES (LIKE STRING_LITERAL)? (securityListingScope | ON (ACCOUNT | USER identifier))? (STARTS WITH STRING_LITERAL)? (LIMIT INTEGER_LITERAL)? SEMI?
    | SHOW SECRETS (LIKE STRING_LITERAL)? securityListingScope? (STARTS WITH STRING_LITERAL)? (LIMIT INTEGER_LITERAL (FROM STRING_LITERAL)?)? SEMI?
    | (DESCRIBE | DESC) NETWORK RULE qualifiedName SEMI?
    | (DESCRIBE | DESC) NETWORK POLICY identifier SEMI?
    | (DESCRIBE | DESC) PASSWORD POLICY qualifiedName SEMI?
    | (DESCRIBE | DESC) SECRET qualifiedName SEMI?
    ;

securityListingScope
    : IN (ACCOUNT | DATABASE qualifiedName? | SCHEMA qualifiedName? | qualifiedName)
    ;

undropStatement
    : UNDROP TABLE qualifiedName SEMI?
    | UNDROP ICEBERG TABLE qualifiedName SEMI?
    | UNDROP SCHEMA qualifiedName SEMI?
    | UNDROP DATABASE identifier SEMI?
    | UNDROP TAG qualifiedName SEMI?
    | UNDROP DYNAMIC TABLE qualifiedName SEMI?
    ;

createStatement
    // TRANSIENT is the only modifier a DATABASE takes: live-verified, `CREATE TEMPORARY DATABASE d` is
    // a syntax error ON THE WORD DATABASE, so TEMPORARY must NOT be accepted here.
    : CREATE or_replace? TRANSIENT? DATABASE if_not_exists? identifier (CLONE identifier timeTravelClause?)? databaseProperty* SEMI?
    // A SCHEMA takes MORE spellings than it supports, and the difference is the whole point: live
    // PARSES `CREATE TEMPORARY/TEMP/VOLATILE SCHEMA` and then refuses each with "Unsupported feature
    // '<WORD> SCHEMA'." — a message that can only exist if the word was read. LOCAL and GLOBAL are the
    // boundary: `CREATE LOCAL TEMPORARY SCHEMA` IS a syntax error, so the pair below is deliberately
    // not the (LOCAL | GLOBAL)? group the TABLE alternative uses.
    | CREATE or_replace? (TRANSIENT | TEMPORARY | TEMP | VOLATILE)? SCHEMA if_not_exists? qualifiedName (CLONE qualifiedName timeTravelClause?)? schemaProperty* SEMI?
    // A table must say what its columns ARE: an explicit column list, CLONE, LIKE, or CTAS. A body-less
    // `CREATE TABLE t`, `CREATE TABLE t TAG (…)` or `CREATE TABLE t CLUSTER BY (…)` is a syntax error in
    // Snowflake (live-verified), so the shape group below is NOT optional.
    // ICEBERG names a Snowflake-managed Iceberg table, stored as an ordinary table beside its Iceberg metadata
    // (EXTERNAL_VOLUME, CATALOG, BASE_LOCATION, … arrive as table options); only TRANSIENT may precede it.
    | CREATE (or_replace | or_alter)? (TRANSIENT ICEBERG? | (LOCAL | GLOBAL)? (TEMPORARY | TEMP) | VOLATILE | HYBRID | ICEBERG)? TABLE if_not_exists? objectName tableTailOption* (LPAREN columnList RPAREN tableTailOption* (AS selectStatement)? | CLONE qualifiedName timeTravelClause? | LIKE qualifiedName | USING TEMPLATE selectStatement | columnListOptional? AS selectStatement) tableTailOption* SEMI?
    // An event table's columns are the fixed OpenTelemetry set, so it is written without a column list.
    | CREATE or_replace? EVENT TABLE if_not_exists? objectName tableTailOption* SEMI?
    // TEMPORARY/TEMP/VOLATILE views, live-measured, including the keyword ORDER: SECURE comes
    // BEFORE the temporary keyword on a view (`CREATE SECURE TEMPORARY VIEW` parses,
    // `CREATE TEMPORARY SECURE VIEW` is a syntax error) — the opposite of a function, below.
    // RECURSIVE stands where the temporariness would, and excludes it: `CREATE SECURE RECURSIVE VIEW`
    // parses, `CREATE TEMPORARY RECURSIVE VIEW` is a syntax error at RECURSIVE (live-verified).
    | CREATE (or_replace | or_alter)? SECURE? (RECURSIVE | (LOCAL | GLOBAL)? (TEMPORARY | TEMP | VOLATILE))? VIEW if_not_exists? objectName copyGrants? viewProperty* (LPAREN viewColumnList RPAREN)? copyGrants? viewProperty* rowAccessPolicyClause? commentClause? tagList? AS selectStatement SEMI?
    | CREATE or_replace? SECURE? MATERIALIZED VIEW if_not_exists? objectName copyGrants? (LPAREN viewColumnList RPAREN)? copyGrants? commentClause? tagList? AS selectStatement SEMI?
    // COMMENT is one of the pre-AS options; a trailing COMMENT after the query is a syntax error (live-verified).
    // A clone takes the source's definition and data; only the target lag and the warehouse may be given anew.
    | CREATE or_replace? TRANSIENT? DYNAMIC TABLE if_not_exists? qualifiedName (CLONE qualifiedName timeTravelClause? copyGrants? dynamicTableCloneOption* | (LPAREN identifierList RPAREN)? dynamicTableOptions (LPAREN identifierList RPAREN)? AS selectStatement) SEMI?
    // The tags come before COPY GRANTS, WITH optional; the AT | BEFORE point follows the source's name
    // directly, ahead of the options (live-verified).
    | CREATE or_replace? STREAM if_not_exists? qualifiedName tagList? copyGrants? ON streamSourceKind qualifiedName streamPoint? streamOptions? commentClause? SEMI?
    // A clone takes the source's definition and its current offset.
    | CREATE or_replace? STREAM if_not_exists? qualifiedName CLONE qualifiedName copyGrants? SEMI?
    // EXECUTE AS USER follows the options and the AFTER list and precedes WHEN (live-verified).
    | CREATE or_replace? TASK if_not_exists? qualifiedName warehouseClause? taskOptions? afterClause? commentClause? taskExecuteAs? (WHEN booleanExpr)? AS taskBody commentClause? SEMI?
    | CREATE or_replace? PIPE if_not_exists? qualifiedName pipeOptions? AS copyStatement commentClause? SEMI?
    | CREATE or_replace? SEQUENCE if_not_exists? qualifiedName (CLONE qualifiedName | WITH? sequenceOptions? commentClause?) SEMI?
    | CREATE or_replace? WAREHOUSE if_not_exists? identifier warehouseProperties? commentClause? SEMI?
    // CREATE COMPUTE POOL: MIN_NODES, MAX_NODES and INSTANCE_FAMILY are required, checked by the
    // handler so the missing-option report matches a real account's.
    | CREATE COMPUTE POOL if_not_exists? identifier (FOR APPLICATION identifier)? computePoolOption* tagList? SEMI?
    // CREATE CORTEX SEARCH SERVICE: WAREHOUSE and TARGET_LAG are required and ON must follow the name
    // (live-verified: `CREATE CORTEX SEARCH SERVICE ON body s` is a syntax error, while a missing
    // WAREHOUSE or TARGET_LAG is a compilation error the handler reports). The options are order-free,
    // and the parentheses around the defining query are optional.
    | CREATE or_replace? CORTEX SEARCH SERVICE if_not_exists? qualifiedName ON identifier
      (ATTRIBUTES identifierList)? cortexSearchOption* AS (LPAREN selectStatement RPAREN | selectStatement) SEMI?
    | CREATE or_replace? (TEMPORARY | TEMP)? STAGE if_not_exists? qualifiedName stageProperties? tagList? commentClause? SEMI?
    | CREATE or_replace? (TEMPORARY | TEMP)? FILE FORMAT if_not_exists? qualifiedName copyFormatOption* commentClause? SEMI?
    // ALLOWED_VALUES comes first; PROPAGATE, ON_CONFLICT and COMMENT follow in any order, and the handler refuses
    // one written twice, and an ON_CONFLICT unless the tag propagates.
    | CREATE or_replace? TAG if_not_exists? qualifiedName tagProperties? tagSetProperty* SEMI?
    // CREATE OR ALTER TAG reads the properties CREATE TAG reads, and describes the whole tag.
    | CREATE or_alter TAG qualifiedName tagProperties? tagSetProperty* SEMI?
    // An alert: a condition evaluated on a schedule and an action run when it returns rows, or a clone of one.
    | CREATE (or_replace | or_alter)? ALERT if_not_exists? qualifiedName alertDefinition SEMI?
    // A function takes the temporary keyword BEFORE SECURE and admits no LOCAL/GLOBAL prefix —
    // both live-measured, and both the reverse of the view rule above. Its options take EXECUTE AS last, as a
    // procedure's do, for the handler to refuse: a function has no invocation type.
    | CREATE or_replace? (TEMPORARY | TEMP | VOLATILE)? SECURE? FUNCTION if_not_exists? qualifiedName LPAREN parameterList? RPAREN RETURNS returnType functionOption* (executeAsClause (functionOption | executeAsClause)*)? (AS bodyDefinition)? SEMI?
    // A procedure takes a function's options, then EXECUTE AS last. An option or a second EXECUTE AS after it is
    // read here so the handler refuses it as the syntax error the account reports at that word.
    | CREATE or_replace? (TEMPORARY | TEMP | VOLATILE)? PROCEDURE if_not_exists? qualifiedName LPAREN parameterList? RPAREN RETURNS returnType functionOption* (executeAsClause (functionOption | executeAsClause)*)? (AS bodyDefinition)?  SEMI?
    | CREATE or_replace? USER if_not_exists? identifier userProperties? SEMI?
    | CREATE or_replace? ROLE if_not_exists? identifier commentClause? SEMI?
    | CREATE or_replace? MASKING POLICY if_not_exists? qualifiedName AS LPAREN parameterList RPAREN RETURNS dataTypeName typeParameters? THIN_ARROW (bodyDefinition | booleanExpr) commentClause? SEMI?
    | CREATE or_replace? ROW ACCESS POLICY if_not_exists? qualifiedName AS LPAREN parameterList RPAREN RETURNS BOOLEAN THIN_ARROW (bodyDefinition | booleanExpr) commentClause? SEMI?
    | CREATE or_replace? CONTACT if_not_exists? qualifiedName contactProperty* SEMI?
    | CREATE or_replace? PROJECTION POLICY if_not_exists? qualifiedName AS LPAREN parameterList? RPAREN RETURNS (dataTypeName | identifier) THIN_ARROW (bodyDefinition | booleanExpr) commentClause? SEMI?
    | CREATE or_replace? AGGREGATION POLICY if_not_exists? qualifiedName AS LPAREN parameterList? RPAREN RETURNS (dataTypeName | identifier) THIN_ARROW (bodyDefinition | booleanExpr) commentClause? SEMI?
    | CREATE or_replace? JOIN POLICY if_not_exists? qualifiedName AS LPAREN parameterList? RPAREN RETURNS (dataTypeName | identifier) THIN_ARROW (bodyDefinition | booleanExpr) commentClause? SEMI?
    ;

// Integrations: account objects holding a third-party service's configuration. Frostlake stores them and never
// calls out. CREATE names one of the three kinds modelled; the other statements take any documented kind, or none.
integrationStatement
    : CREATE or_replace? createdIntegrationKind INTEGRATION if_not_exists? identifier objectProperty* SEMI?
    | ALTER integrationKind? INTEGRATION if_exists? identifier integrationAction SEMI?
    | DROP integrationKind? INTEGRATION if_exists? identifier SEMI?
    ;

createdIntegrationKind
    : API
    | CATALOG
    | NOTIFICATION
    ;

integrationKind
    : API
    | CATALOG
    | NOTIFICATION
    | STORAGE
    | SECURITY
    | EXTERNAL ACCESS
    ;

// The tag forms come first: TAG may also be read as a property name.
integrationAction
    : tagSet
    | tagUnset
    | SET objectProperty+
    | UNSET optionKey (COMMA optionKey)*
    ;

// External volumes: account objects naming the cloud storage locations Iceberg tables are written to.
externalVolumeStatement
    : CREATE or_replace? EXTERNAL VOLUME if_not_exists? identifier objectProperty* SEMI?
    | ALTER EXTERNAL VOLUME if_exists? identifier externalVolumeAction SEMI?
    | DROP EXTERNAL VOLUME if_exists? identifier SEMI?
    | UNDROP EXTERNAL VOLUME identifier SEMI?
    ;

// ADD STORAGE_LOCATION = (…), REMOVE STORAGE_LOCATION '<name>', UPDATE STORAGE_LOCATION = '<name>' CREDENTIALS = (…)
// and SET ALLOW_WRITES / COMMENT; the handler checks the property names.
externalVolumeAction
    : SET objectProperty+
    | ADD objectProperty
    | REMOVE optionKey STRING_LITERAL
    | UPDATE objectProperty+
    ;

// A property of an object whose property set is checked per object kind by its handler: a literal, a word, a list
// of values, a parenthesised list of nested properties, or a parenthesised map of string pairs.
objectProperty
    : optionKey EQ objectPropertyValue
    ;

objectPropertyValue
    : STRING_LITERAL
    | MINUS? INTEGER_LITERAL
    | booleanValue
    | ALL
    | qualifiedName
    | LPAREN RPAREN
    | LPAREN objectProperty (COMMA? objectProperty)* RPAREN
    | LPAREN STRING_LITERAL EQ STRING_LITERAL (COMMA STRING_LITERAL EQ STRING_LITERAL)* RPAREN
    | LPAREN objectPropertyValue (COMMA objectPropertyValue)* RPAREN
    ;

// ALTER ICEBERG TABLE's own actions; the ones it shares with a table are read by tableAction.
icebergTableAction
    : REFRESH STRING_LITERAL?
    | CONVERT TO MANAGED objectProperty*
    ;

// CREATE CONTACT's properties, all string-valued: COMMENT, URL and EMAIL_DISTRIBUTION_LIST (which
// takes ONE address, live-verified, despite the plural name). Routed through optionKey so none of
// the property names has to become a keyword.
contactProperty
    : optionKey EQ STRING_LITERAL
    ;

// A contact is attached to an object for a PURPOSE. Live takes three, and takes them quoted too.
contactPurpose
    : identifier
    | STRING_LITERAL
    ;

contactAssignment
    : contactPurpose EQ qualifiedName
    ;

// The '=' is positional (live-verified): an OBJECT-level comment REQUIRES it — CREATE TABLE t (x INT)
// COMMENT 'x' is a syntax error at the string — while a COLUMN-level comment FORBIDS it — id INT
// COMMENT = 'x' is a syntax error at the '='. The two rules below carry that split.
commentClause
    : COMMENT EQ (STRING_LITERAL | DOLLAR_QUOTED_STRING)
    ;

columnCommentClause
    : COMMENT (STRING_LITERAL | DOLLAR_QUOTED_STRING)
    ;

executeAsClause
    : EXECUTE AS (OWNER | CALLER)
    ;

nullHandlingClause
    : CALLED ON NULL INPUT
    | RETURNS NULL ON NULL INPUT
    | STRICT
    ;

volatilityClause
    : IMMUTABLE
    | VOLATILE
    ;

functionOption
    : languageClause
    | runtimeVersionClause
    | packagesClause
    | importsClause
    | handlerClause
    | nullHandlingClause
    | volatilityClause
    | MEMOIZABLE
    | commentClause
    // A service function: the service and endpoint a call would be sent to, and its batch size.
    | SERVICE EQ qualifiedName
    | ENDPOINT EQ (identifier | STRING_LITERAL)
    | MAX_BATCH_ROWS EQ INTEGER_LITERAL
    ;

importsClause
    : IMPORTS EQ LPAREN stringLiteralList? RPAREN
    ;

collateClause
    : COLLATE (STRING_LITERAL | DOLLAR_QUOTED_STRING)
    ;

// What may follow LIKE's ESCAPE: a bare literal, NULL, or a session variable — never an expression
// built from one. A parenthesis, a cast, a concatenation, a number, a keyword and a column name are
// all syntax errors at the token itself (live-verified).
escapeOperand
    : STRING_LITERAL
    | DOLLAR_QUOTED_STRING
    | NULL
    | SESSION_VAR_REF
    ;

clusterByClause
    : CLUSTER BY LPAREN expressionList RPAREN
    ;

// Any unquoted word names a language; one the account does not know is refused by name while compiling.
languageClause
    : LANGUAGE (JAVASCRIPT | JAVA | SQL | PYTHON | SCALA | IDENTIFIER)
    ;

runtimeVersionClause
    : RUNTIME_VERSION EQ (STRING_LITERAL | FLOAT_LITERAL | INTEGER_LITERAL)
    ;

handlerClause
    : HANDLER EQ STRING_LITERAL
    ;

packagesClause
    : PACKAGES EQ LPAREN stringLiteralList? RPAREN
    ;

// The properties and parameters CREATE DATABASE takes, in any order: the retention, the comment, inline
// tags, and any other database parameter by name (the handler refuses a name that is none).
databaseProperty
    : DATA_RETENTION_TIME_IN_DAYS EQ MINUS? INTEGER_LITERAL
    | commentClause
    | tagList
    | optionKey EQ copyOptionValue
    ;

// CREATE SCHEMA takes the database's properties and WITH MANAGED ACCESS.
schemaProperty
    : WITH MANAGED ACCESS
    | databaseProperty
    ;

// CASCADE / RESTRICT is taken after every DROP (live-verified).
// Every kind reads its name as an objectName, so IDENTIFIER('<name>') names the object a DROP drops —
// live takes it for all of them, IF EXISTS and a session variable included. FUNCTION and PROCEDURE are
// left on the plain name: their REQUIRED signature's parens would sit against the call's own.
dropStatement
    : DROP DATABASE if_exists? objectName dropBehavior? SEMI?
    | DROP SCHEMA if_exists? objectName objectSignature? dropBehavior? SEMI?
    | DROP TABLE if_exists? (objectName | openedValueReference) objectSignature? dropBehavior? SEMI?
    | DROP ICEBERG TABLE if_exists? (objectName | openedValueReference) dropBehavior? SEMI?
    | DROP VIEW if_exists? objectName objectSignature? dropBehavior? SEMI?
    | DROP MATERIALIZED VIEW if_exists? objectName objectSignature? dropBehavior? SEMI?
    | DROP DYNAMIC TABLE if_exists? objectName dropBehavior? SEMI?
    | DROP STREAM if_exists? objectName dropBehavior? SEMI?
    | DROP TASK if_exists? objectName dropBehavior? SEMI?
    | DROP PIPE if_exists? objectName dropBehavior? SEMI?
    | DROP SEQUENCE if_exists? objectName dropBehavior? SEMI?
    | DROP WAREHOUSE if_exists? objectName dropBehavior? SEMI?
    | DROP COMPUTE POOL if_exists? objectName dropBehavior? SEMI?
    | DROP CORTEX SEARCH SERVICE if_exists? objectName dropBehavior? SEMI?
    | DROP STAGE if_exists? objectName dropBehavior? SEMI?
    | DROP FILE FORMAT if_exists? objectName dropBehavior? SEMI?
    | DROP TAG if_exists? objectName dropBehavior? SEMI?
    | DROP ALERT if_exists? objectName dropBehavior? SEMI?
    | DROP FUNCTION if_exists? qualifiedName LPAREN dataTypeList? RPAREN dropBehavior? SEMI?   // the signature is REQUIRED (live-verified)
    | DROP PROCEDURE if_exists? qualifiedName LPAREN dataTypeList? RPAREN dropBehavior? SEMI?   // the signature is REQUIRED (live-verified)
    | DROP USER if_exists? objectName dropBehavior? SEMI?
    | DROP ROLE if_exists? objectName dropBehavior? SEMI?
    | DROP MASKING POLICY if_exists? objectName dropBehavior? SEMI?
    | DROP ROW ACCESS POLICY if_exists? objectName dropBehavior? SEMI?
    | DROP CONTACT if_exists? objectName dropBehavior? SEMI?
    | DROP PROJECTION POLICY if_exists? objectName dropBehavior? SEMI?
    | DROP AGGREGATION POLICY if_exists? objectName dropBehavior? SEMI?
    | DROP JOIN POLICY if_exists? objectName dropBehavior? SEMI?
    ;

// An instance of a class, DROP <class> <instance>. No class is modelled, so the class is refused as missing. The
// two-word kinds live drops that Frostlake has no statements for — EXTERNAL TABLE, FAILOVER GROUP and REPLICATION
// GROUP — read their second word here as well, and an external table's name may carry a signature, which changes
// nothing (UnmodelledDropKind, live-verified).
dropClassStatement
    : DROP className ({ClassNameWords.completesDropKind(_input.LT(-1), _input.LT(1))}? dropKindWord=(TABLE | GROUP)
        if_exists? dropInstanceName objectSignature? | if_exists? dropInstanceName) dropBehavior? SEMI?
    ;

dropInstanceName
    : objectName
    | CASCADE
    | RESTRICT
    ;

alterStatement
    : ALTER DATABASE if_exists? identifier databaseAction SEMI?
    | ALTER SCHEMA if_exists? qualifiedName schemaAction SEMI?
    | ALTER TABLE if_exists? (objectName | openedIdentifierReference) tableAction SEMI?
    | ALTER ICEBERG TABLE if_exists? (objectName | openedIdentifierReference) (icebergTableAction | tableAction) SEMI?
    | ALTER VIEW if_exists? (objectName | openedIdentifierReference) viewAction SEMI?
    | ALTER MATERIALIZED VIEW if_exists? (objectName | openedIdentifierReference) materializedViewAction SEMI?
    | ALTER DYNAMIC TABLE if_exists? qualifiedName dynamicTableAction SEMI?
    | ALTER STREAM if_exists? qualifiedName streamAction SEMI?
    | ALTER TASK if_exists? qualifiedName taskAction SEMI?
    | ALTER PIPE if_exists? qualifiedName pipeAction SEMI?
    | ALTER SEQUENCE if_exists? qualifiedName sequenceAction SEMI?
    | ALTER WAREHOUSE if_exists? identifier warehouseAction SEMI?
    | ALTER COMPUTE POOL if_exists? identifier computePoolAction SEMI?
    | ALTER CORTEX SEARCH SERVICE if_exists? qualifiedName cortexSearchAction SEMI?
    | ALTER STAGE if_exists? qualifiedName stageAction SEMI?
    | ALTER FILE FORMAT if_exists? qualifiedName fileFormatAction SEMI?
    | ALTER TAG if_exists? qualifiedName tagAction SEMI?
    | ALTER ALERT if_exists? qualifiedName alertAction SEMI?
    | ALTER MASKING POLICY if_exists? qualifiedName policyAction SEMI?
    | ALTER CONTACT if_exists? qualifiedName (SET commentClause | UNSET COMMENT) SEMI?
    | ALTER PROJECTION POLICY if_exists? qualifiedName policyAction SEMI?
    | ALTER AGGREGATION POLICY if_exists? qualifiedName policyAction SEMI?
    | ALTER JOIN POLICY if_exists? qualifiedName policyAction SEMI?
    | ALTER ROW ACCESS POLICY if_exists? qualifiedName policyAction SEMI?
    | ALTER USER if_exists? identifier userAction SEMI?
    | ALTER ROLE if_exists? identifier roleAction SEMI?
    // The signature is required: its absence parses so the handler can refuse it at the action, in live's one line.
    | ALTER FUNCTION if_exists? qualifiedName (LPAREN routineSignature? RPAREN)? routineAlterAction SEMI?
    | ALTER PROCEDURE if_exists? qualifiedName (LPAREN routineSignature? RPAREN)? routineAlterAction SEMI?
    | ALTER SESSION sessionAction SEMI?
    ;

useStatement
    : USE DATABASE objectName SEMI?
    | USE SCHEMA objectName SEMI?
    | USE WAREHOUSE objectName SEMI?
    | USE ROLE objectName SEMI?
    | USE SECONDARY ROLES (ALL | identifier (COMMA identifier)*) SEMI?
    | USE objectName SEMI?
    ;

// An object name that may be given literally or resolved dynamically via IDENTIFIER('<name>') / IDENTIFIER($var).
// Where it names what a statement creates, fills, changes or uses, the word IDENTIFIER without a whole reference
// after it is the object's own name, so the statement goes on to refuse what follows the word: live refuses
// UPDATE IDENTIFIER('t' || '1') SET a = 1 at the parenthesis and INSERT INTO IDENTIFIER('t' || '1') VALUES (1)
// at the string, where the column list begins.
objectName
    : KW_IDENTIFIER_REF LPAREN identifierArgument RPAREN
    | qualifiedName
    | KW_IDENTIFIER_OPEN
    ;

// A name a statement reads only as a WHOLE reference: an IDENTIFIER() the lexer found broken is no name at
// all there, and the alternative holding it fails where it stands — ALTER TABLE t RENAME TO IDENTIFIER('t' ||
// '2') is refused at RENAME (live-verified).
wholeObjectName
    : KW_IDENTIFIER_REF LPAREN identifierArgument RPAREN
    | qualifiedName
    ;

// The signature a DROP or a DESCRIBE may write after an object's name — the shape a routine's arguments take,
// which live reads there for every object and ignores: DESCRIBE TABLE t1 (x), (VARCHAR(10)), () and (a, b) all
// describe T1, while (1), ('a'), ((a)), (a b) and (a,) are syntax errors (live-verified).
objectSignature
    : LPAREN (objectSignatureItem (COMMA objectSignatureItem)*)? RPAREN
    ;

// One item of that signature: a type's word with the parameters a type carries, or a word — qualified or not — with
// parameters of its own: numbers and words, qualified or not, each word with parameters again, so DESCRIBE TABLE
// t1 (a(b, c(1))), (a.b.c), (a(b.c.e)) and IDENTIFIER(UPPER(t1)) describe T1 and the table IDENTIFIER. Live reads no
// more there — DESCRIBE TABLE t1 (a b), DROP TABLE t3 (x INT) and (a()) are syntax errors at the second word and the
// ')' — and TRUE, FALSE and a quoted word's parameters are refused where they stand (see refuseSignatureWord); which
// words and shapes the account takes in each position, and the lines it reports for the rest, SignatureRecovery reads
// after the parse (all live-verified).
objectSignatureItem
    : dataTypeName typeParameters?
    | word=identifier (DOT identifier)* objectSignatureParameters? {refuseSignatureWord($ctx, $word.start, true);}
    ;

objectSignatureParameters
    : LPAREN objectSignatureParameter (COMMA objectSignatureParameter)* RPAREN
    ;

objectSignatureParameter
    : MINUS? INTEGER_LITERAL
    | word=identifier (DOT identifier)* objectSignatureParameters? {refuseSignatureWord($ctx, $word.start, false);}
    ;

// An IDENTIFIER() reference the lexer found not whole (KW_IDENTIFIER_OPEN), where a statement reads it as a
// reference all the same: a FROM clause, an expression, DROP and DESCRIBE. Its parentheses are read whole and
// refused by the parser itself at the token live names (see refuseOpenedIdentifierReference), so the parse goes
// on after them and a fault further along the statement is still reported where it stands.
openedIdentifierReference
    : KW_IDENTIFIER_OPEN LPAREN openedIdentifierContent RPAREN {refuseOpenedIdentifierReference($ctx, $LPAREN, $RPAREN, true);}
    ;

// After DROP and DESCRIBE, an IDENTIFIER() the lexer found not whole reads as a reference only when its content
// opens as a whole reference's does — a string, a variable, a bind or an integer, refused where the reading
// stops, once: DROP TABLE IDENTIFIER('t' || '1', 2) is the '||' alone. Anything else makes the word IDENTIFIER the object's own
// name and the parentheses its signature, refused inside them: DROP TABLE IDENTIFIER(UPPER('t1')) at the string
// and DESCRIBE TABLE IDENTIFIER(CONCAT('t', '1')) at both strings (live-verified).
openedValueReference
    : KW_IDENTIFIER_OPEN LPAREN openedReferenceValue openedIdentifierContent RPAREN
      {refuseOpenedIdentifierReference($ctx, $LPAREN, $RPAREN, false);}
    ;

openedReferenceValue
    : STRING_LITERAL
    | DOLLAR_QUOTED_STRING
    | INTEGER_LITERAL
    | SESSION_VAR_REF
    | QUESTION
    | COLON
    ;

openedIdentifierContent
    : (~(LPAREN | RPAREN) | LPAREN openedIdentifierContent RPAREN)*
    ;

// What IDENTIFIER() takes: a string literal, a session variable, a bind variable and an integer (which then
// names no object). Any other expression is a syntax error at its first token that does not fit — live refuses
// IDENTIFIER('t' || '1') at the '||', IDENTIFIER(UPPER('t1')) at UPPER and IDENTIFIER(TRUE) at TRUE. A bind
// variable's name is never quoted there: the lexer leaves IDENTIFIER(:"tn") incomplete, refused at the name.
identifierArgument
    : STRING_LITERAL
    | DOLLAR_QUOTED_STRING
    | INTEGER_LITERAL
    | SESSION_VAR_REF
    | COLON (identifier | INTEGER_LITERAL)
    | QUESTION
    ;

commentStatement
    : COMMENT if_exists? ON DATABASE identifier IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON SCHEMA qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON TABLE qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON DYNAMIC TABLE qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON COLUMN qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON VIEW qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON MATERIALIZED VIEW qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON FUNCTION qualifiedName LPAREN dataTypeList? RPAREN IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON PROCEDURE qualifiedName LPAREN dataTypeList? RPAREN IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON STREAM qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON TASK qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON WAREHOUSE identifier IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON STAGE qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON TAG qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON USER identifier IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON ROLE identifier IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON SEQUENCE qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON PIPE qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON MASKING POLICY qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON ROW ACCESS POLICY qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON FILE FORMAT qualifiedName IS STRING_LITERAL SEMI?
    ;

truncateStatement
    : TRUNCATE TABLE? if_exists? objectName SEMI?
    ;

securityStatement
    : grantDatabaseRoleStatement
    | revokeDatabaseRoleStatement
    | grantStatement
    | revokeStatement
    ;

taskStatement
    : EXECUTE TASK qualifiedName taskExecuteOption? SEMI?
    | EXECUTE ALERT qualifiedName SEMI?
    ;

// A retry of the last failed graph run or of a named one, or a run with a configuration merged over the
// task's own.
taskExecuteOption
    : RETRY taskRetryTarget
    | USING CONFIG EQ taskConfigValue
    ;

// GRAPH and RUN stay plain words, read off the tokens ahead.
taskRetryTarget
    : LAST
    | {ClauseWords.isWord(_input.LT(1), "GRAPH") && ClauseWords.isWord(_input.LT(2), "RUN")}? identifier identifier GROUP STRING_LITERAL
    ;

taskConfigValue
    : STRING_LITERAL
    | DOLLAR_QUOTED_STRING
    ;

// The user a task runs as, named directly or through IDENTIFIER().
taskExecuteAs
    : EXECUTE AS USER (identifier | KW_IDENTIFIER_REF LPAREN identifierArgument RPAREN)
    ;

grantStatement
    : GRANT ROLE identifier TO (USER | ROLE) identifier SEMI?  // GRANT ROLE role_name TO USER/ROLE target_name
    // An ACCOUNT-level privilege names its scope: live-verified 2026-08-02, `GRANT CREATE DATABASE TO
    // ROLE r` is a syntax error on a real account ("unexpected 'TO'") while `GRANT CREATE DATABASE ON
    // ACCOUNT TO ROLE r` succeeds. Must precede the generic `privilegeList ON ACCOUNT` alternative so
    // two-word privileges (CREATE DATABASE, MONITOR USAGE, APPLY TAG, …) bind as one globalPrivilege.
    | GRANT globalPrivilegeList ON ACCOUNT TO ROLE identifier withGrantOption? SEMI?  // GRANT global_privs ON ACCOUNT TO ROLE role_name
    // The COPY / REVOKE CURRENT GRANTS tail parses after any object grant; the handler refuses it beside a
    // privilege other than OWNERSHIP, in live's own sentence.
    | GRANT privilegeList ON objectType qualifiedName (LPAREN identifierList? RPAREN)? TO ((USER | ROLE) identifier | databaseRoleGrantee) currentGrants? withGrantOption? SEMI?  // GRANT privs ON type name [(col1, col2)] TO USER/ROLE target_name
    | GRANT OWNERSHIP ON objectType qualifiedName TO (USER | ROLE) identifier currentGrants? SEMI?  // GRANT OWNERSHIP ON type name TO USER/ROLE target_name
    | GRANT privilegeList ON ACCOUNT TO (USER | ROLE) identifier withGrantOption? SEMI?  // GRANT privs ON ACCOUNT TO USER/ROLE target_name
    | GRANT privilegeList ON ALL bulkObjectType IN bulkScope TO ((USER | ROLE) identifier | databaseRoleGrantee) currentGrants? withGrantOption? SEMI?  // GRANT privs ON ALL <types> IN <scope>
    | GRANT privilegeList ON FUTURE bulkObjectType IN bulkScope TO ((USER | ROLE) identifier | databaseRoleGrantee) currentGrants? withGrantOption? SEMI?  // GRANT privs ON FUTURE <types> IN <scope>
    ;

// What an ownership transfer does with the grants its object carries. COPY CURRENT GRANTS reads word by word;
// REVOKE CURRENT GRANTS is one token, so a REVOKE that does not open it is read as it always was.
currentGrants
    : COPY CURRENT GRANTS
    | REVOKE_CURRENT_GRANTS
    ;

// A grantee that is a database role, named with its database or relative to the current one.
databaseRoleGrantee
    : DATABASE ROLE qualifiedName
    ;

// A privilege granted WITH GRANT OPTION may be granted on by its grantee.
withGrantOption
    : WITH GRANT OPTION
    ;

// REVOKE GRANT OPTION FOR takes back only the right to grant the privilege on, leaving the privilege.
grantOptionFor
    : GRANT OPTION FOR
    ;

// What a REVOKE does with the grants its grantee made of the privilege in turn.
revokeMode
    : RESTRICT
    | CASCADE
    ;

// GRANT and REVOKE of a database role: to and from an account role, another database role or a user. A REVOKE
// takes one RESTRICT or CASCADE; a GRANT takes neither.
grantDatabaseRoleStatement
    : GRANT DATABASE ROLE qualifiedName TO ((USER | ROLE) identifier | databaseRoleGrantee) SEMI?
    ;

revokeDatabaseRoleStatement
    : REVOKE DATABASE ROLE qualifiedName FROM ((USER | ROLE) identifier | databaseRoleGrantee) revokeMode? SEMI?
    ;

bulkObjectType
    : SCHEMAS
    | TABLES
    | VIEWS
    | FUNCTIONS
    | PROCEDURES
    | STAGES
    | SEQUENCES
    | TASKS
    | STREAMS
    | PIPES
    | DYNAMIC TABLES
    | EVENT TABLES
    | EXTERNAL TABLES
    | MATERIALIZED VIEWS
    | FILE FORMATS
    | ALERTS
    ;

// A bulk grant reaches a DATABASE or a SCHEMA and nothing wider: `IN ACCOUNT` is a syntax error on the
// word ACCOUNT, for ON ALL and ON FUTURE alike and whatever privilege is named (live-verified).
bulkScope
    : DATABASE qualifiedName
    | SCHEMA qualifiedName
    ;

revokeStatement
    : REVOKE ROLE identifier FROM (USER | ROLE) identifier revokeMode? SEMI?  // REVOKE ROLE role_name FROM USER/ROLE target_name
    // Same ACCOUNT scoping as GRANT — live 2026-08-02: `REVOKE CREATE DATABASE FROM ROLE r` is a
    // syntax error ("unexpected 'FROM'"), `REVOKE CREATE DATABASE ON ACCOUNT FROM ROLE r` succeeds.
    | REVOKE grantOptionFor? globalPrivilegeList ON ACCOUNT FROM ROLE identifier revokeMode? SEMI?  // REVOKE global_privs ON ACCOUNT FROM ROLE role_name
    | REVOKE grantOptionFor? privilegeList ON objectType qualifiedName (LPAREN identifierList? RPAREN)? FROM ((USER | ROLE) identifier | databaseRoleGrantee) revokeMode? SEMI?  // REVOKE privs ON type name [(col1, col2)] FROM USER/ROLE target_name
    | REVOKE OWNERSHIP ON objectType qualifiedName FROM (USER | ROLE) identifier SEMI?  // REVOKE OWNERSHIP ON type name FROM USER/ROLE target_name
    | REVOKE grantOptionFor? privilegeList ON ACCOUNT FROM (USER | ROLE) identifier revokeMode? SEMI?  // REVOKE privs ON ACCOUNT FROM USER/ROLE target_name
    | REVOKE grantOptionFor? privilegeList ON ALL bulkObjectType IN bulkScope FROM ((USER | ROLE) identifier | databaseRoleGrantee) revokeMode? SEMI?
    | REVOKE grantOptionFor? privilegeList ON FUTURE bulkObjectType IN bulkScope FROM ((USER | ROLE) identifier | databaseRoleGrantee) revokeMode? SEMI?
    ;

privilegeList
    : privilege (COMMA privilege)*
    | ALL PRIVILEGES?
    ;

globalPrivilegeList
    : globalPrivilege (COMMA globalPrivilege)*
    ;

globalPrivilege
    : CREATE ACCOUNT
    | CREATE DATABASE
    | CREATE INTEGRATION
    | CREATE NETWORK POLICY
    | CREATE ROLE
    | CREATE USER
    | CREATE WAREHOUSE
    | APPLY MASKING POLICY
    | APPLY ROW ACCESS POLICY
    | APPLY SESSION POLICY
    | APPLY TAG
    | EXECUTE TASK
    | IMPORT SHARE
    | MANAGE GRANTS
    | MONITOR EXECUTION
    | MONITOR USAGE
    ;

privilege
    : privilegeLead privilegeWord*
    ;

// The word a privilege STARTS with is a closed set — live answers an unknown one with a syntax error on
// that very word ("GRANT NO SUCH PRIVILEGE ON SCHEMA s" stops at NO) — while what follows it is not: the
// account grants CREATE AGENT, CREATE DBT PROJECT, CREATE ZEROCOPY CONNECTOR and sixty more whose kind
// words it never enumerated for us. So the lead is listed and the tail is read as words.
privilegeLead
    : SELECT | INSERT | UPDATE | DELETE | TRUNCATE | CREATE | DROP | ALTER | MODIFY | USAGE
    | OPERATE | MONITOR | READ | WRITE | EXECUTE | REFERENCES | OWNERSHIP | APPLY | APPLYBUDGET
    | REBUILD | EVOLVE | ADD | VIEW | IMPORTED | USE_ANY_ROLE
    ;

// A word a privilege's name may carry after its lead. A word Snowflake spells that this grammar has no
// token for — AGENT, DBT, ZEROCOPY — arrives as IDENTIFIER and is covered by the first alternative.
privilegeWord
    : IDENTIFIER | ACCESS | ACCOUNT | ADD | AGGREGATION | ALERT | ALTER | ANY | APPLICATION | APPLY
    | APPLYBUDGET | ARTIFACT | AUTO | CLASS | CONTACT | CORTEX | CREATE | DATA | DATABASE | DELETE | DROP
    | DYNAMIC | ERROR | EVENT | EVOLVE | EXECUTE | EXECUTION | EXTERNAL | FILE | FORMAT | FUNCTION | GRANTS
    | GROUP | HYBRID | ICEBERG | IMAGE | IMPORT | IMPORTED | INSERT | INTEGRATION | JOIN | MANAGE | MANAGED
    | MASKING | MATERIALIZED | METRIC | MODIFY | MONITOR | NETWORK | NOTEBOOK | OPERATE | OPTIMIZATION
    | OWNERSHIP | PACKAGES | PASSWORD | PIPE | POLICY | PRIVILEGES | PROCEDURE | PROJECTION | READ | REBUILD
    | REFERENCES | REPOSITORY | RESOURCE | ROLE | ROW | RULE | SCHEMA | SEARCH | SECRET | SELECT | SEQUENCE
    | SERVICE | SESSION | SET | SHARE | STAGE | STORAGE | STREAM | STREAMLIT | TABLE | TAG | TASK
    | TEMPORARY | TRUNCATE | TYPE | UPDATE | USAGE | USE | USER | VIEW | WAREHOUSE | WRITE
    ;

objectType
    : securableKind
    // Live spells these kinds in words. The one-token spellings parse here only so that GRANT and REVOKE refuse them
    // in live's sentence (Object type or Class 'FILE_FORMAT' does not exist or not authorized.).
    | FILE_FORMAT
    | RESOURCE_MONITOR
    | MASKING_POLICY
    | ROW_ACCESS_POLICY
    | SESSION_POLICY
    ;

securableKind
    : DATABASE
    | SCHEMA
    | TABLE
    | VIEW
    | WAREHOUSE
    | STAGE
    | STREAM
    | TASK
    | SEQUENCE
    | PIPE
    | PROCEDURE
    | FUNCTION
    | FILE FORMAT
    | INTEGRATION
    | RESOURCE MONITOR
    | MASKING POLICY
    | ROW ACCESS POLICY
    | SESSION POLICY
    | TAG
    | NETWORK POLICY
    | NETWORK RULE
    | PASSWORD POLICY
    | SECRET
    ;

// What SHOW GRANTS ON names: a kind and its object, or a bare name, which live reads as a table or a view. A kind word
// live reserves is never a bare name (see bareGrantsNameAllowed), so SHOW GRANTS ON DATABASE alone stays a syntax
// error at its end.
showGrantsTarget
    : securableKind qualifiedName (LPAREN dataTypeList? RPAREN)?
    | bare=qualifiedName {bareGrantsNameAllowed($bare.ctx)}?
    ;

userProperties
    : userProperty+
    ;

userProperty
    : PASSWORD EQ STRING_LITERAL
    | DEFAULT_ROLE EQ (identifier | STRING_LITERAL)
    | DEFAULT_WAREHOUSE EQ (identifier | STRING_LITERAL)
    | DEFAULT_NAMESPACE EQ (qualifiedName | STRING_LITERAL)
    | DEFAULT_SECONDARY_ROLES EQ LPAREN stringLiteralList? RPAREN
    | LOGIN_NAME EQ (identifier | STRING_LITERAL)
    | DISPLAY_NAME EQ (identifier | STRING_LITERAL)
    | FIRST_NAME EQ STRING_LITERAL
    | MIDDLE_NAME EQ STRING_LITERAL
    | LAST_NAME EQ STRING_LITERAL
    | EMAIL EQ STRING_LITERAL
    | MUST_CHANGE_PASSWORD EQ booleanValue
    | DISABLED EQ booleanValue
    | TYPE EQ (identifier | STRING_LITERAL)
    | COMMENT EQ STRING_LITERAL
    | DAYS_TO_EXPIRY EQ userPropertyValue
    | MINS_TO_UNLOCK EQ userPropertyValue
    | MINS_TO_BYPASS_MFA EQ userPropertyValue
    | RSA_PUBLIC_KEY EQ userPropertyValue
    | RSA_PUBLIC_KEY_2 EQ userPropertyValue
    ;

// The value of a countdown or public-key property, taken as written: the handler reads the forms each property
// takes and refuses any other as an invalid value, a compilation error rather than a syntax error.
userPropertyValue
    : MINUS? (INTEGER_LITERAL | FLOAT_LITERAL)
    | STRING_LITERAL
    | DOLLAR_QUOTED_STRING
    | NULL
    | booleanValue
    | identifier
    ;

streamOptions
    : streamOption (streamOption)*
    ;

streamOption
    : APPEND_ONLY EQ booleanValue
    | SHOW_INITIAL_ROWS EQ booleanValue
    | {ClauseWords.isWord(_input.LT(1), "INSERT_ONLY")}? identifier EQ booleanValue
    ;

// The object a stream tracks.
streamSourceKind
    : TABLE
    | VIEW
    | EXTERNAL TABLE
    | STAGE
    | DYNAMIC TABLE
    | EVENT TABLE
    ;

// The point a stream starts from.
streamPoint
    : (AT_KEYWORD | BEFORE) LPAREN timeTravelPoint RPAREN
    ;

parameterList
    : parameterDef (COMMA parameterDef)*
    ;

parameterDef
    : identifier dataTypeName typeParameters? ((DEFAULT | COLON_EQ) expression)?
    ;

// The argument types naming one overload. An argument may be written with its name, as a CREATE writes it, and a
// plain word may stand for a type: both parse so the statement can refuse them as live does, the named argument
// at its type while the list is read — before any later fault, DROP FUNCTION f1(x INT DEFAULT 1) being 'INT'
// alone (see refuseNamedArgument) — and the word as an unsupported data type (RoutineSignatureForm).
dataTypeList
    : (identifier? type=dataTypeName {refuseNamedArgument($type.start);} typeParameters? | identifier)
      (COMMA (identifier? type=dataTypeName {refuseNamedArgument($type.start);} typeParameters? | identifier))*
    ;

// GET_DDL's routine argument, name(TYPES), read whole: a text that is not exactly this names no routine.
routineReference
    : qualifiedName LPAREN dataTypeList? RPAREN EOF
    ;

// An ALTER FUNCTION / PROCEDURE signature: argument types, or arguments written as a CREATE writes them. Only
// RENAME TO takes a named one; the handler refuses it under any other action (live-verified).
routineSignature
    : routineSignatureItem (COMMA routineSignatureItem)*
    ;

routineSignatureItem
    : dataTypeName typeParameters?
    | identifier dataTypeName typeParameters? ((DEFAULT | COLON_EQ) expression)?
    | identifier   // a plain word where a type stands, refused as an unsupported data type
    ;

returnType
    : TABLE LPAREN columnList? RPAREN   // Table function or procedure; a procedure may declare no columns
    | dataTypeName typeParameters? (NOT? NULL)?   // Scalar function; [NOT] NULL is informational
    ;

bodyDefinition
    : STRING_LITERAL              // Single-quoted string
    | DOLLAR_QUOTED_STRING        // Dollar-quoted string ($$...$$)
    | beginEndBlock               // Unquoted scripting body: CREATE PROCEDURE ... AS BEGIN...END / AS DECLARE...END
    ;

taskBody
    : sqlStatement                // Raw SQL statement
    | callStatement               // CALL <procedure>(...) — a task that just invokes a stored procedure
    | executeImmediateStatement   // EXECUTE IMMEDIATE expression
    | beginEndBlock               // A Snowflake Scripting block, with or without its DECLARE section
                                  // (live-verified). The block is written UNQUOTED, exactly like the
                                  // unquoted procedure body — a $$-quoted one is a syntax error there.
    ;

warehouseClause
    : WAREHOUSE EQ (STRING_LITERAL | identifier | SESSION_VAR_REF)
    ;

scheduleClause
    : SCHEDULE EQ STRING_LITERAL
    ;

afterClause
    : AFTER qualifiedName (COMMA qualifiedName)*
    ;

// Options may be separated by commas (live-verified).
taskOptions
    : taskOption (COMMA? taskOption)*
    ;

taskOption
    : ALLOW_OVERLAPPING_EXECUTION EQ booleanValue
    | USER_TASK_TIMEOUT_MS EQ INTEGER_LITERAL
    | SUSPEND_TASK_AFTER_NUM_FAILURES EQ INTEGER_LITERAL
    | TASK_AUTO_RETRY_ATTEMPTS EQ INTEGER_LITERAL
    | USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE EQ STRING_LITERAL
    // A warehouse size takes either spelling here — live accepts both the quoted 'MEDIUM' and the
    // bare MEDIUM (the sibling USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE is quoted-only).
    | SERVERLESS_TASK_MAX_STATEMENT_SIZE EQ (identifier | STRING_LITERAL)
    | TARGET_COMPLETION_INTERVAL EQ STRING_LITERAL
    | USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS EQ INTEGER_LITERAL
    | ERROR_INTEGRATION EQ identifier
    | COMMENT EQ? STRING_LITERAL
    | scheduleClause
    // Every other property — CONFIG, OVERLAP_POLICY, FINALIZE, SUCCESS_INTEGRATION, LOG_LEVEL,
    // SERVERLESS_TASK_MIN_STATEMENT_SIZE and the session parameters. The handler reads the name and
    // validates the value, refusing a name no task carries the way live does.
    | identifier EQ taskPropertyValue
    ;

taskPropertyValue
    : STRING_LITERAL
    | DOLLAR_QUOTED_STRING
    | MINUS? (INTEGER_LITERAL | FLOAT_LITERAL)
    | booleanValue
    | qualifiedName
    // A parenthesised list of constants, one value to the account (the handler reads it).
    | LPAREN (taskListItem (COMMA taskListItem)*)? RPAREN
    ;

taskListItem
    : STRING_LITERAL
    | DOLLAR_QUOTED_STRING
    | MINUS? (INTEGER_LITERAL | FLOAT_LITERAL)
    | booleanValue
    | qualifiedName
    ;

pipeOptions
    : pipeOption+
    ;

pipeOption
    : AUTO_INGEST EQ booleanValue
    | AWS_SNS_TOPIC EQ STRING_LITERAL
    | ERROR_INTEGRATION EQ (STRING_LITERAL | identifier)
    | INTEGRATION EQ STRING_LITERAL
    | COMMENT EQ STRING_LITERAL
    ;

copyIntoStatement
    : COPY INTO qualifiedName copyIntoTableClause* SEMI?
    | COPY INTO stageRef copyIntoStageClause* SEMI?
    | COPY INTO STRING_LITERAL copyIntoStageClause* SEMI?
    ;

copyIntoTableClause
    : LPAREN identifierList RPAREN
    | FROM copySource
    | FILE_FORMAT EQ LPAREN copyFormatOptions RPAREN
    | FILES EQ LPAREN stringLiteralList RPAREN
    | PATTERN EQ STRING_LITERAL
    | VALIDATION_MODE EQ copyOptionValue
    | ON_ERROR EQ copyOptionValue
    | SIZE_LIMIT EQ MINUS? INTEGER_LITERAL
    | PURGE EQ copyOptionValue
    | FORCE EQ copyOptionValue
    | MATCH_BY_COLUMN_NAME EQ copyOptionValue
    | optionKey EQ parenOptionList
    | identifier EQ copyOptionValue
    ;

copyIntoStageClause
    : FROM copyTableSource
    | FILE_FORMAT EQ LPAREN copyFormatOptions RPAREN
    | PARTITION BY expression
    | HEADER (EQ booleanValue)?
    | OVERWRITE EQ copyOptionValue
    | SINGLE EQ copyOptionValue
    | MAX_FILE_SIZE EQ MINUS? INTEGER_LITERAL
    | VALIDATION_MODE EQ copyOptionValue
    | optionKey EQ parenOptionList
    | identifier EQ copyOptionValue
    ;

copySource
    : stageRef
    | STRING_LITERAL
    | LPAREN copyTransformation RPAREN
    ;

// COPY load transformation: a projection over the staged file's positional columns ($1, $2, …) FROM @stage.
// Only the projection + stage source are modelled (no WHERE/JOIN/aggregates), matching Snowflake's COPY transform.
copyTransformation
    : SELECT copyTransformItem (COMMA copyTransformItem)* FROM stageRef
    ;

copyTransformItem
    : expression (AS identifier)?
    ;

// A stage reference: a named stage (@stage), the current user's stage (@~), or a table stage (@%table),
// each with an optional path beneath it. Any part of the name may be left empty — @..st, @db..st, @db...st,
// @.%t, @....st — and it parses, so that the name is refused as live refuses it (StageReferenceShape).
// A stage reference's NAME runs to the first '/', and a DOT always separates name parts — never a path.
// The account reads @st.x and @st.csv alike as the stage X (or CSV) in the schema ST, and @st. as a
// stage with no name in that schema, which is why a trailing run of dots is part of the name here and
// resolves through the empty parts it leaves behind.
stageRef
    : AT DOT* identifier (DOT+ identifier)* DOT* stagePath?
    | AT TILDE stagePath?
    | AT DOT* (identifier DOT+)* PERCENT (identifier | TABLE) (DOT+ identifier)* DOT* stagePath?
    ;

// The optional trailing SLASH matches Snowflake, where unload targets are conventionally written as
// directories: COPY INTO @stage/some/dir/ FROM (query).
// A path begins at a '/', and only there. Inside a segment a dot is an ordinary character, so a file
// name carries one and '..' is a segment like any other — the account does NOT normalise it away, and
// a COPY writes through it.
stagePath
    // TO joins the segment words because paths like @~/some/path/to/file.csv are routine and `to`
    // lexes as the keyword token; a full path-aware lexer mode is not worth the complexity.
    : (SLASH stagePathSegment?)+
    ;

stagePathSegment
    : (identifier | INTEGER_LITERAL | TO | SOME | ANY | ALL | DOT)+
    ;

copyTableSource
    : qualifiedName
    | LPAREN selectStatement RPAREN
    ;

stringLiteralList
    : STRING_LITERAL (COMMA STRING_LITERAL)*
    ;

copyFormatOptions
    : copyFormatOption (COMMA? copyFormatOption)*
    ;

copyFormatOption
    : TYPE EQ copyOptionValue
    | FIELD_DELIMITER EQ copyOptionValue
    | SKIP_HEADER EQ MINUS? INTEGER_LITERAL
    | DATE_FORMAT EQ copyOptionValue
    | COMPRESSION EQ copyOptionValue
    | RECORD_DELIMITER EQ copyOptionValue
    | ESCAPE EQ copyOptionValue
    // A parenthesized string list, e.g. NULL_IF = ('\\N', 'NULL', '') — must precede the scalar catch-all.
    | identifier EQ LPAREN stringLiteralList? RPAREN
    | identifier EQ copyOptionValue
    ;

// A copy option value: a quoted string, an unquoted enum identifier (TYPE = CSV, COMPRESSION = GZIP),
// the keyword-valued enums CONTINUE/AUTO, an integer, or a boolean — matching Snowflake's unquoted forms.
copyOptionValue
    : STRING_LITERAL
    | MINUS? INTEGER_LITERAL
    | booleanValue
    | CONTINUE
    | AUTO
    // ERROR_LOGGING's value is the BAREWORD DEFAULT — live refuses the quoted spelling — and the
    // keyword is no identifier, so it joins CONTINUE and AUTO as an admitted bare value. Every
    // consumer validates its own values in Java, so widening here does not widen what they accept.
    | DEFAULT
    | qualifiedName
    ;

copyStatement
    : COPY INTO qualifiedName copyRestOfStatement
    ;

copyRestOfStatement
    : ~SEMI*
    ;

warehouseProperties
    : WITH? warehouseProperty (warehouseProperty)*
    ;

warehouseProperty
    : WAREHOUSE_TYPE EQ (STANDARD | ADAPTIVE | STRING_LITERAL)
    | WAREHOUSE_SIZE EQ (STRING_LITERAL | identifier | SESSION_VAR_REF)
    | AUTO_SUSPEND EQ MINUS? INTEGER_LITERAL
    | AUTO_RESUME EQ booleanValue
    | MIN_CLUSTER_COUNT EQ INTEGER_LITERAL
    | MAX_CLUSTER_COUNT EQ INTEGER_LITERAL
    | SCALING_POLICY EQ (STANDARD | ECONOMY | STRING_LITERAL)
    | INITIALLY_SUSPENDED EQ booleanValue
    | RESOURCE_MONITOR EQ identifier
    | MAX_CONCURRENCY_LEVEL EQ INTEGER_LITERAL
    | STATEMENT_QUEUED_TIMEOUT_IN_SECONDS EQ INTEGER_LITERAL
    | STATEMENT_TIMEOUT_IN_SECONDS EQ INTEGER_LITERAL
    | ENABLE_QUERY_ACCELERATION EQ booleanValue
    | QUERY_ACCELERATION_MAX_SCALE_FACTOR EQ INTEGER_LITERAL
    | GENERATION EQ (STRING_LITERAL | INTEGER_LITERAL)
    | RESOURCE_CONSTRAINT EQ (identifier | STRING_LITERAL)
    | WAIT_FOR_COMPLETION EQ booleanValue
    | COMMENT EQ STRING_LITERAL
    ;

stageProperties
    : URL EQ STRING_LITERAL stageOption*
    | stageOption+                       // internal stage declared by its options alone
    ;

stageOption
    : FILE_FORMAT EQ (STRING_LITERAL | parenOptionList | qualifiedName)
    | ENCRYPTION EQ (booleanValue | parenOptionList)
    | COMMENT EQ STRING_LITERAL
    | identifier EQ (parenOptionList | copyOptionValue)   // CREDENTIALS=(...), TEMPORARY-stage props, ...
    ;

taskAction
    : RESUME
    | SUSPEND
    | tagSet
    | tagUnset
    // WAREHOUSE is read by its own clause, so it is tried first; properties may be separated by commas.
    | SET (warehouseClause | taskOption) (COMMA? (warehouseClause | taskOption))*
    // EXECUTE AS USER is set and unset on its own, never beside another property (live-verified).
    | SET taskExecuteAs
    | UNSET EXECUTE AS USER
    | UNSET taskParamName (COMMA taskParamName)*
    | MODIFY AS taskBody
    | MODIFY WHEN booleanExpr
    | REMOVE WHEN
    | ADD AFTER qualifiedName (COMMA qualifiedName)*
    | REMOVE AFTER qualifiedName (COMMA qualifiedName)*
    ;

// Parameter names that ALTER TASK … UNSET accepts (keyword tokens plus a generic identifier fallback).
taskParamName
    : WAREHOUSE | SCHEDULE | COMMENT | USER_TASK_TIMEOUT_MS | SUSPEND_TASK_AFTER_NUM_FAILURES
    | TASK_AUTO_RETRY_ATTEMPTS | USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE | SERVERLESS_TASK_MAX_STATEMENT_SIZE
    | TARGET_COMPLETION_INTERVAL | USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS | ERROR_INTEGRATION
    | ALLOW_OVERLAPPING_EXECUTION | identifier
    ;

pipeAction
    : REFRESH pipeRefreshOption*
    | tagSet
    | tagUnset
    | SET pipeSetOption+   // PAUSE/RESUME are NOT Snowflake syntax; use SET PIPE_EXECUTION_PAUSED = TRUE/FALSE
    ;

// Option names are identifiers (e.g. PIPE_EXECUTION_PAUSED, PREFIX, MODIFIED_AFTER) so no new keyword
// tokens are needed; the handler validates the name.
pipeSetOption
    : identifier EQ (booleanValue | STRING_LITERAL)
    ;

pipeRefreshOption
    : identifier EQ STRING_LITERAL
    ;

sequenceOptions
    : sequenceOption (COMMA? sequenceOption)*
    ;

sequenceOption
    : START (WITH | EQ)? MINUS? INTEGER_LITERAL
    | INCREMENT (BY | EQ)? MINUS? INTEGER_LITERAL
    | ORDER
    | NOORDER
    | commentClause
    ;

sequenceAction
    : SET INCREMENT (BY | EQ) MINUS? INTEGER_LITERAL
    | RENAME TO qualifiedName
    ;                             // RESTART is not a Snowflake sequence action (live: invalid property)

tagAssign
    : qualifiedName EQ tagValue
    ;

// The value a tag is set to, in SET TAG and in a creation-time TAG list alike. Live reads more than a string there
// and judges what it read while the statement compiles (TagValues): a string, a hex literal, a keyword, a quoted or
// qualified name and a text session variable set the tag; a number, a boolean, a parenthesised list and an
// IDENTIFIER() are invalid values; a NULL, a plain name and a bind variable an unsupported data type (live-verified).
tagValue
    : STRING_LITERAL
    | DOLLAR_QUOTED_STRING
    | HEX_LITERAL
    | MINUS? (INTEGER_LITERAL | FLOAT_LITERAL)
    | TRUE
    | FALSE
    | NULL
    | SESSION_VAR_REF
    | COLON identifier
    | QUESTION
    | LPAREN (tagValue (COMMA tagValue)*)? RPAREN
    | KW_IDENTIFIER_REF LPAREN identifierArgument RPAREN
    | identifier (DOT identifier)*
    ;

tagSet
    : SET TAG tagAssign (COMMA tagAssign)*
    ;

tagUnset
    : UNSET TAG qualifiedName (COMMA qualifiedName)*
    ;

columnTagAction
    : (ALTER | MODIFY) COLUMN identifier (tagSet | tagUnset)
    ;

warehouseAction
    // The two multi-word forms are single LEXER tokens: they add no parser decisions, so large
    // statements elsewhere keep their prediction complexity — an inline optional group after
    // RESUME degrades ALL(*) prediction across a big corpus.
    : RESUME_IF_SUSPENDED
    | RESUME
    | SUSPEND
    | ENABLE
    | DISABLE
    | ABORT_ALL_QUERIES
    | SET warehouseProperty+
    | tagUnset
    | UNSET warehouseUnsetProperty (COMMA warehouseUnsetProperty)*
    | RENAME TO identifier
    | tagSet
    ;

// The names UNSET accepts: the declared warehouse properties, plus a bare identifier so an
// unknown name parses and is refused with live's invalid-property wording. tagUnset is ordered
// first in warehouseAction because TAG itself lexes as an identifier.
warehouseUnsetProperty
    : WAREHOUSE_TYPE | WAREHOUSE_SIZE | AUTO_SUSPEND | AUTO_RESUME | MIN_CLUSTER_COUNT
    | MAX_CLUSTER_COUNT | SCALING_POLICY | INITIALLY_SUSPENDED | RESOURCE_MONITOR
    | MAX_CONCURRENCY_LEVEL | STATEMENT_QUEUED_TIMEOUT_IN_SECONDS | STATEMENT_TIMEOUT_IN_SECONDS
    | ENABLE_QUERY_ACCELERATION | QUERY_ACCELERATION_MAX_SCALE_FACTOR | GENERATION | RESOURCE_CONSTRAINT | COMMENT
    | identifier
    ;

databaseAction
    : RENAME TO identifier
    | SET COMMENT EQ STRING_LITERAL
    | SET optionKey EQ (parenOptionList | copyOptionValue)
    | SET READ_ONLY EQ booleanValue
    | UNSET READ_ONLY
    // UNSET restores a parameter's INHERITANCE: a schema falls back to its database's value and a
    // database to the account default — SHOW answers the container's CURRENT value afterwards
    // (live-verified for DATA_RETENTION_TIME_IN_DAYS).
    | UNSET optionKey
    | tagSet
    | tagUnset
    ;

schemaAction
    // The new name may place the schema in a database, moving it there with its members (live-verified).
    : RENAME TO qualifiedName
    | SET COMMENT EQ STRING_LITERAL
    | SET optionKey EQ (parenOptionList | copyOptionValue)
    | UNSET optionKey
    | tagSet
    | tagUnset
    | (ENABLE | DISABLE) MANAGED ACCESS
    ;

tableAction
    : RENAME TO wholeObjectName
    | SWAP WITH qualifiedName
    | ADD COLUMN? if_not_exists? columnDef (COMMA alterAddColumnItem)*
    | DROP CLUSTERING KEY
    // Automatic reclustering is paused and resumed on a CLUSTERED table; SHOW TABLES'
    // automatic_clustering cell follows it (live-verified: ON -> OFF -> ON).
    | (SUSPEND | RESUME) RECLUSTER
    | RENAME COLUMN identifier TO identifier
    // A constraint may be renamed, and its enforcement properties changed through either spelling
    // (live-verified). VALIDATE / NOVALIDATE are deliberately ABSENT: the documentation lists them,
    // live refuses both as syntax errors, and admitting them would be leniency.
    | RENAME CONSTRAINT identifier TO identifier
    | (ALTER | MODIFY) CONSTRAINT identifier constraintProperty+
    // ALTER TABLE t { ALTER | MODIFY } [(] [COLUMN] c1 action [, [COLUMN] c2 action]* [)] — the
    // column-action list. COLUMN is optional per item, the parens are optional but balanced.
    | (ALTER | MODIFY) LPAREN alterColumnItem (COMMA alterColumnItem)* RPAREN
    | (ALTER | MODIFY) alterColumnItem (COMMA alterColumnItem)*
    | SET COMMENT EQ STRING_LITERAL
    // One SET may carry SEVERAL properties, separated by a space or a comma (live-verified, and the
    // same property may even repeat there — unlike CREATE TABLE, where a repeat is refused). Each
    // pair is its own subrule so key and value stay paired however many there are.
    | SET tableSetProperty (COMMA? tableSetProperty)*
    | CLUSTER BY LPAREN expressionList RPAREN
    | ADD tableConstraint
    | DROP CONSTRAINT identifier
    | DROP PRIMARY KEY
    | DROP UNIQUE LPAREN identifierList RPAREN
    | DROP FOREIGN KEY LPAREN identifierList RPAREN
    | (ALTER | MODIFY) COLUMN identifier SET MASKING POLICY qualifiedName (USING LPAREN identifierList RPAREN)? FORCE?
    | (ALTER | MODIFY) COLUMN identifier UNSET MASKING POLICY
    | ADD DATA METRIC FUNCTION qualifiedName ON LPAREN identifierList? RPAREN
    | DROP DATA METRIC FUNCTION qualifiedName ON LPAREN identifierList? RPAREN
    | MODIFY DATA METRIC FUNCTION qualifiedName ON LPAREN identifierList? RPAREN (SUSPEND | RESUME)
    | ADD SEARCH OPTIMIZATION (ON searchOptimizationTarget (COMMA searchOptimizationTarget)*)?
    | DROP SEARCH OPTIMIZATION (ON searchOptimizationDrop (COMMA searchOptimizationDrop)*)?
    | SET aggregationPolicyClause FORCE?
    | UNSET AGGREGATION POLICY
    | SET joinPolicyClause FORCE?
    | UNSET JOIN POLICY
    | ALTER COLUMN identifier SET PROJECTION POLICY qualifiedName FORCE?
    | ALTER COLUMN identifier UNSET PROJECTION POLICY
    | SET CONTACT contactAssignment (COMMA contactAssignment)*
    | UNSET CONTACT contactPurpose (COMMA contactPurpose)*
    | ADD ROW ACCESS POLICY qualifiedName ON LPAREN identifierList RPAREN
    | DROP ROW ACCESS POLICY qualifiedName
    // Detaches whatever is attached and is a no-op when nothing is (live-verified). Only the plural
    // spelling parses — live refuses `DROP ALL ROW ACCESS POLICY` at the POLICY token.
    | DROP ALL ROW ACCESS POLICIES
    // COLUMN is optional (live-verified: `ALTER TABLE t DROP c`, a comma list and the IF EXISTS form
    // all run). It sits AFTER the specific DROP alternatives so `DROP CLUSTERING KEY` — CLUSTERING
    // being a legal identifier — is never a candidate column name. Each later name carries its own
    // COLUMN and IF EXISTS (alterDropColumnItem), so the IF EXISTS written after DROP covers the FIRST
    // name only (live-verified).
    | DROP COLUMN? if_exists? identifier (COMMA alterDropColumnItem)*
    | tagSet
    | tagUnset
    | columnTagAction
    | tableUnsetProperties
    ;

// ALTER TABLE ... UNSET prop[, prop]* — its own subrule so the masking-policy alternative's UNSET
// keeps a single-token accessor in tableAction.
tableUnsetProperties
    : UNSET optionKey (COMMA optionKey)*
    ;

// One key=value of an ALTER TABLE … SET, kept paired with its value.
tableSetProperty
    : optionKey EQ (parenOptionList | copyOptionValue)
    ;

// An enforcement property of ALTER TABLE … {ALTER | MODIFY} CONSTRAINT. Several may be given at
// once (live-verified: `NOT ENFORCED NORELY` runs).
constraintProperty
    : NOT? ENFORCED
    | relyOption
    ;

cortexSearchOption
    : WAREHOUSE EQ identifier
    | TARGET_LAG EQ STRING_LITERAL
    | EMBEDDING_MODEL EQ STRING_LITERAL
    | COMMENT EQ STRING_LITERAL
    ;

cortexSearchAction
    : SET cortexSearchOption+
    | UNSET COMMENT
    // SUSPEND / RESUME without a target act on both layers.
    | (SUSPEND | RESUME) (INDEXING | SERVING)?
    ;

// Snowpark Container Services objects kept as catalog metadata: image repositories, services and job services,
// and artifact repositories. Every alternative opens on its own statement words (IMAGE REPOSITORY, SERVICE, JOB
// SERVICE, ARTIFACT REPOSITORY, SERVICE CONTAINERS / INSTANCES, ENDPOINTS), none of which another statement takes.
containerServicesStatement
    : CREATE or_replace? IMAGE REPOSITORY if_not_exists? qualifiedName containerProperty* tagList? containerProperty* SEMI?
    | ALTER IMAGE REPOSITORY if_exists? qualifiedName artifactRepositoryAlterAction SEMI?
    | DROP IMAGE REPOSITORY if_exists? qualifiedName SEMI?
    | SHOW IMAGE REPOSITORIES (LIKE STRING_LITERAL)? containerShowScope? SEMI?
    | SHOW IMAGES IN IMAGE REPOSITORY qualifiedName SEMI?
    | CREATE SERVICE if_not_exists? qualifiedName IN COMPUTE POOL identifier serviceSource containerProperty* tagList? containerProperty* SEMI?
    | EXECUTE JOB SERVICE IN COMPUTE POOL identifier serviceSource containerProperty* SEMI?
    | ALTER SERVICE if_exists? qualifiedName serviceAlterAction SEMI?
    | DROP SERVICE if_exists? qualifiedName FORCE? SEMI?
    | SHOW JOB? SERVICES (EXCLUDE JOBS)? (LIKE STRING_LITERAL)? containerShowScope? showTail SEMI?
    | (DESCRIBE | DESC) SERVICE qualifiedName SEMI?
    | SHOW SERVICE (CONTAINERS | INSTANCES) IN SERVICE qualifiedName SEMI?
    | SHOW ENDPOINTS IN SERVICE qualifiedName SEMI?
    | CREATE or_replace? ARTIFACT REPOSITORY if_not_exists? qualifiedName containerProperty* tagList? containerProperty* SEMI?
    | ALTER ARTIFACT REPOSITORY if_exists? qualifiedName artifactRepositoryAlterAction SEMI?
    | DROP ARTIFACT REPOSITORY if_exists? qualifiedName SEMI?
    | SHOW ARTIFACT REPOSITORIES (LIKE STRING_LITERAL)? containerShowScope? (LIMIT INTEGER_LITERAL)? SEMI?
    | (DESCRIBE | DESC) ARTIFACT REPOSITORY qualifiedName SEMI?
    ;

// The IN clause of the container listings: the account, a database, a schema, or (services only) a compute pool.
containerShowScope
    : IN ACCOUNT
    | IN DATABASE identifier?
    | IN SCHEMA qualifiedName?
    | IN COMPUTE POOL identifier
    | IN qualifiedName
    ;

// Where a service's specification comes from: a staged file or inline text, as a specification or a template
// whose USING clause fills it.
serviceSource
    : FROM stageRef? (SPECIFICATION_FILE | SPECIFICATION_TEMPLATE_FILE) EQ STRING_LITERAL serviceUsing?
    | FROM (SPECIFICATION | SPECIFICATION_TEMPLATE) (STRING_LITERAL | DOLLAR_QUOTED_STRING) serviceUsing?
    ;

serviceUsing
    : USING LPAREN identifier ARROW containerValue (COMMA identifier ARROW containerValue)* RPAREN
    ;

serviceAlterAction
    : SUSPEND
    | RESUME
    | serviceSource
    | tagSet
    | tagUnset
    | SET containerProperty (COMMA? containerProperty)*
    | UNSET identifier (COMMA identifier)*
    ;

artifactRepositoryAlterAction
    : tagSet
    | tagUnset
    | SET containerProperty (COMMA? containerProperty)*
    | UNSET identifier (COMMA identifier)*
    ;

// A property of an image repository, a service, a job service or an artifact repository, written NAME = value.
// The name is a catch-all identifier so an unknown one parses and the handler refuses it by name, as an account
// refuses a property the object does not take.
containerProperty
    : identifier EQ containerValue
    ;

containerValue
    : STRING_LITERAL
    | MINUS? INTEGER_LITERAL
    | booleanValue
    | qualifiedName
    | LPAREN containerProperty (COMMA? containerProperty)* RPAREN
    | LPAREN (containerValue (COMMA containerValue)*)? RPAREN
    ;

computePoolOption
    : MIN_NODES EQ INTEGER_LITERAL
    | MAX_NODES EQ INTEGER_LITERAL
    | INSTANCE_FAMILY EQ identifier
    | AUTO_RESUME EQ booleanValue
    | INITIALLY_SUSPENDED EQ booleanValue
    | AUTO_SUSPEND_SECS EQ INTEGER_LITERAL
    | COMMENT EQ STRING_LITERAL
    | PLACEMENT_GROUP EQ STRING_LITERAL
    | BACKUP_INSTANCE_FAMILIES EQ LPAREN stringLiteralList RPAREN
    ;

computePoolAction
    : SUSPEND
    | RESUME
    | STOP ALL (OF TYPE computePoolWorkloadType (COMMA computePoolWorkloadType)*)?
    | tagSet
    | tagUnset
    | SET computePoolSetOption+
    | UNSET computePoolUnsetKey (COMMA computePoolUnsetKey)*
    ;

computePoolWorkloadType
    : identifier
    | ALL
    | USER
    ;

computePoolSetOption
    : MIN_NODES EQ INTEGER_LITERAL
    | MAX_NODES EQ INTEGER_LITERAL
    | AUTO_RESUME EQ booleanValue
    | AUTO_SUSPEND_SECS EQ INTEGER_LITERAL
    | INSTANCE_FAMILY EQ identifier
    | PLACEMENT_GROUP EQ STRING_LITERAL
    | BACKUP_INSTANCE_FAMILIES EQ LPAREN stringLiteralList RPAREN
    | COMMENT EQ STRING_LITERAL
    ;

computePoolUnsetKey
    : AUTO_RESUME
    | AUTO_SUSPEND_SECS
    | PLACEMENT_GROUP
    | BACKUP_INSTANCE_FAMILIES
    | COMMENT
    ;

alterColumnItem
    : COLUMN? identifier alterColumnItemAction
    ;

alterColumnItemAction
    : ((SET DATA)? TYPE)? dataTypeName typeParameters? collateClause?
    // SET is optional before NOT NULL (live-verified: `ALTER COLUMN c NOT NULL` runs, as does the
    // MODIFY spelling and the one that omits COLUMN as well). DROP NOT NULL has no such variant.
    | SET? NOT NULL
    | DROP NOT NULL
    | SET DEFAULT defaultExpression
    | DROP DEFAULT
    | COMMENT STRING_LITERAL
    | UNSET COMMENT
    ;

viewAction
    : RENAME TO qualifiedName
    | SET COMMENT EQ STRING_LITERAL
    | UNSET COMMENT
    | ADD ROW ACCESS POLICY qualifiedName ON LPAREN identifierList RPAREN
    | DROP ROW ACCESS POLICY qualifiedName
    | DROP ALL ROW ACCESS POLICIES
    | tagSet
    | tagUnset
    ;

materializedViewAction
    : SUSPEND
    | RESUME
    | REFRESH
    | RENAME TO identifier
    | SET COMMENT EQ STRING_LITERAL
    | UNSET COMMENT
    ;

dynamicTableOptions
    : dynamicTableOption+
    ;

dynamicTableOption
    : TARGET_LAG EQ (STRING_LITERAL | DOWNSTREAM)
    | WAREHOUSE EQ identifier
    | REFRESH_MODE EQ (AUTO | FULL | INCREMENTAL)
    | INITIALIZE EQ (ON_CREATE | ON_SCHEDULE)
    | DATA_RETENTION_TIME_IN_DAYS EQ INTEGER_LITERAL
    | MAX_FILE_SIZE EQ INTEGER_LITERAL
    | COMMENT EQ STRING_LITERAL
    | CLUSTER BY LPAREN expression (COMMA expression)* RPAREN
    ;

// The options a CREATE DYNAMIC TABLE ... CLONE may give the clone in place of the source's.
dynamicTableCloneOption
    : TARGET_LAG EQ (STRING_LITERAL | DOWNSTREAM)
    | WAREHOUSE EQ identifier
    ;

dynamicTableAction
    : SUSPEND
    | RESUME
    | REFRESH
    | SWAP WITH qualifiedName
    // Automatic reclustering is paused and resumed on a dynamic table that has a clustering key.
    | (SUSPEND | RESUME) RECLUSTER
    | tagSet
    | tagUnset
    // Any property parses, so one the account refuses is refused in its own words before the table is
    // looked up (live-verified); the handler keeps the list of the properties it takes.
    | SET dynamicTableSetting (COMMA? dynamicTableSetting)*
    | UNSET dynamicTableProperty (COMMA dynamicTableProperty)*
    ;

dynamicTableSetting
    : dynamicTableProperty EQ (STRING_LITERAL | MINUS? INTEGER_LITERAL | booleanValue | DOWNSTREAM
        | AUTO | FULL | INCREMENTAL | ON_SCHEDULE | ON_CREATE | identifier)
    ;

dynamicTableProperty
    : TARGET_LAG | WAREHOUSE | COMMENT | REFRESH_MODE | INITIALIZE | DATA_RETENTION_TIME_IN_DAYS | identifier
    ;

streamAction
    : SET COMMENT EQ STRING_LITERAL
    | UNSET COMMENT
    | tagSet
    | tagUnset
    ;

stageAction
    : SET URL EQ STRING_LITERAL
    | SET FILE_FORMAT EQ (STRING_LITERAL | parenOptionList | qualifiedName)
    | SET COMMENT EQ STRING_LITERAL
    | RENAME TO identifier
    | UNSET optionKey
    | SET identifier EQ (STRING_LITERAL | parenOptionList)
    // Registers the stage's files in its directory table; SUBPATH stays a plain word.
    | REFRESH ({ClauseWords.isWord(_input.LT(1), "SUBPATH")}? identifier EQ STRING_LITERAL)?
    ;

tagProperties
    : ALLOWED_VALUES stringLiteralList
    | MASKING EQ booleanValue
    ;

// SET ALLOWED_VALUES stands alone: another property after its list is a syntax error.
tagAction
    : ADD ALLOWED_VALUES stringLiteralList
    | DROP ALLOWED_VALUES stringLiteralList
    | UNSET tagUnsetProperty
    | SET MASKING EQ booleanValue
    | SET ALLOWED_VALUES stringLiteralList
    | SET tagSetProperty+
    | RENAME TO qualifiedName
    ;

// A tag's automatic propagation.
tagPropagation
    : PROPAGATE EQ identifier
    ;

// What a propagated value that conflicts with another becomes.
tagConflict
    : ON_CONFLICT EQ (STRING_LITERAL | ALLOWED_VALUES_SEQUENCE)
    ;

// The properties CREATE TAG and ALTER TAG … SET write after the allowed values, in any order.
tagSetProperty
    : tagPropagation
    | tagConflict
    | commentClause
    ;

tagUnsetProperty
    : ALLOWED_VALUES
    | PROPAGATE
    | ON_CONFLICT
    | COMMENT
    ;

// The body of CREATE ALERT: a CLONE of another alert, or the properties, the condition and the action.
alertDefinition
    : CLONE qualifiedName
    | tagList? alertProperty* IF LPAREN EXISTS LPAREN alertCondition RPAREN RPAREN THEN taskBody
    ;

alertProperty
    : scheduleClause
    | warehouseClause
    | COMMENT EQ STRING_LITERAL
    | CONFIG EQ STRING_LITERAL
    | RUNBOOK EQ STRING_LITERAL
    | SUSPEND_ALERT_AFTER_NUM_FAILURES EQ INTEGER_LITERAL
    ;

// The statements an alert's condition may be: a query, a SHOW listing or a procedure call.
alertCondition
    : selectStatement
    | showStatement
    | callStatement
    ;

alertAction
    : RESUME
    | SUSPEND
    | tagSet
    | tagUnset
    | SET alertProperty+
    | UNSET alertParameter (COMMA alertParameter)*
    | MODIFY CONDITION EXISTS LPAREN alertCondition RPAREN
    | MODIFY ACTION taskBody
    ;

alertParameter
    : WAREHOUSE
    | COMMENT
    | CONFIG
    | RUNBOOK
    | SUSPEND_ALERT_AFTER_NUM_FAILURES
    ;

// What a policy takes after ALTER — a masking, row access, projection, aggregation or join policy alike
// (live-verified for each): a new name, a new body, a TAG list, or a list of properties set or unset. Every
// property name PARSES, so an unknown one meets live's "invalid property" refusal rather than a syntax error; a
// SET list separates its properties with commas or with nothing, an UNSET list only with commas, and a TAG list
// reads every name = 'value' pair after its commas as one more tag. A fault in a TAG list or a property list is
// refused at the list's first word (see AlterStatementAnchor).
policyAction
    // A qualified target is legal and it MOVES the policy: renaming to another schema's name puts the
    // policy in that schema (live-verified, for every policy kind).
    : RENAME TO qualifiedName
    | SET BODY THIN_ARROW (bodyDefinition | booleanExpr)
    | tagSet
    | SET policyProperty (COMMA? policyProperty)*
    | tagUnset
    | UNSET identifier (COMMA identifier)*
    ;

// One property of a policy's SET list: a name and a value, both judged by the handler.
policyProperty
    : identifier EQ policyPropertyValue
    ;

// The value a policy property takes: a literal, a signed number, a name, or one parenthesised list of those — a
// nested parenthesis and an arithmetic expression are syntax errors there (live-verified).
policyPropertyValue
    : policyScalarValue
    | LPAREN (policyScalarValue (COMMA policyScalarValue)*)? RPAREN
    ;

policyScalarValue
    : literal
    | MINUS (INTEGER_LITERAL | FLOAT_LITERAL)
    | identifier (DOT identifier)*
    ;

fileFormatAction
    : RENAME TO qualifiedName
    | SET? copyFormatOption+
    ;

routineAlterAction
    : RENAME TO qualifiedName
    | SET SECURE
    | UNSET SECURE
    | SET TAG qualifiedName EQ STRING_LITERAL (COMMA qualifiedName EQ STRING_LITERAL)*
    | UNSET TAG qualifiedName (COMMA qualifiedName)*
    | SET routineSetProperty+
    | UNSET routineUnsetProperty (COMMA routineUnsetProperty)*
    ;

// A property name is a catch-all `identifier` so an unknown one PARSES and the handler reports it the
// way a real account does — `invalid property 'X' for 'FUNCTION'` is a compilation error there, not a
// syntax error — while SET without a value stays the syntax error live gives it, at the end of the
// statement. SET takes a LIST, and a repeated property is taken with the last one winning.
routineSetProperty
    : COMMENT EQ STRING_LITERAL
    | identifier EQ literal
    ;

routineUnsetProperty
    : COMMENT
    | identifier
    ;

userAction
    : RENAME TO identifier
    | tagSet
    | tagUnset
    | SET userProperty+
    | SET COMMENT EQ STRING_LITERAL
    | UNSET userUnsetProperty (COMMA userUnsetProperty)*
    ;

// The property names UNSET takes. `identifier` is a catch-all so an unknown name PARSES and the
// handler can report it the way a real account does — `invalid property 'X' for 'USER'` is a
// compilation error there, not a syntax error.
userUnsetProperty
    : PASSWORD
    | LOGIN_NAME
    | DISPLAY_NAME
    | FIRST_NAME
    | MIDDLE_NAME
    | LAST_NAME
    | EMAIL
    | DEFAULT_ROLE
    | DEFAULT_WAREHOUSE
    | DEFAULT_NAMESPACE
    | DEFAULT_SECONDARY_ROLES
    | MUST_CHANGE_PASSWORD
    | DISABLED
    | COMMENT
    | TYPE
    | identifier
    ;

roleAction
    : RENAME TO identifier
    | SET COMMENT EQ STRING_LITERAL
    | UNSET COMMENT
    | tagSet
    | tagUnset
    ;

// ALTER DATABASE ROLE's actions.
databaseRoleAction
    : RENAME TO qualifiedName
    | SET COMMENT EQ STRING_LITERAL
    | UNSET COMMENT
    | tagSet
    | tagUnset
    ;

// The properties CREATE ACCOUNT takes, in any order; the handler requires the mandatory ones.
accountProperty
    : ADMIN_NAME EQ (STRING_LITERAL | identifier)
    | ADMIN_PASSWORD EQ STRING_LITERAL
    | ADMIN_RSA_PUBLIC_KEY EQ STRING_LITERAL
    | ADMIN_USER_TYPE EQ (identifier | NULL)
    | FIRST_NAME EQ STRING_LITERAL
    | LAST_NAME EQ STRING_LITERAL
    | EMAIL EQ STRING_LITERAL
    | MUST_CHANGE_PASSWORD EQ booleanValue
    | EDITION EQ identifier
    | REGION_GROUP EQ identifier
    | REGION EQ identifier
    | COMMENT EQ STRING_LITERAL
    | POLARIS EQ booleanValue
    ;

// The properties CREATE MANAGED ACCOUNT takes, separated by commas.
managedAccountProperty
    : ADMIN_NAME EQ (STRING_LITERAL | identifier)
    | ADMIN_PASSWORD EQ STRING_LITERAL
    | TYPE EQ identifier
    | COMMENT EQ STRING_LITERAL
    ;

// Database roles, accounts and managed accounts, and the grant listings that name database roles, roles as
// securables and future grants. Each form opens with keywords of its own (DATABASE ROLE, ACCOUNT, MANAGED
// ACCOUNT, FUTURE GRANTS), so none competes with the statements above for a whole statement.
accessControlStatement
    : CREATE or_replace? DATABASE ROLE if_not_exists? qualifiedName commentClause? SEMI?
    | ALTER DATABASE ROLE if_exists? qualifiedName databaseRoleAction SEMI?
    | DROP DATABASE ROLE if_exists? qualifiedName SEMI?
    | SHOW DATABASE ROLES IN DATABASE identifier (LIMIT INTEGER_LITERAL (FROM STRING_LITERAL)?)? SEMI?
    | SHOW GRANTS TO DATABASE ROLE qualifiedName SEMI?
    | SHOW GRANTS OF DATABASE ROLE qualifiedName SEMI?
    | SHOW GRANTS ON ROLE identifier SEMI?
    | SHOW GRANTS ON DATABASE ROLE qualifiedName SEMI?
    | SHOW FUTURE GRANTS IN SCHEMA qualifiedName SEMI?
    | SHOW FUTURE GRANTS IN DATABASE identifier SEMI?
    | SHOW FUTURE GRANTS TO ROLE identifier SEMI?
    | SHOW FUTURE GRANTS TO DATABASE ROLE qualifiedName SEMI?
    | CREATE ACCOUNT identifier accountProperty+ SEMI?
    | DROP ACCOUNT if_exists? identifier GRACE_PERIOD_IN_DAYS EQ INTEGER_LITERAL SEMI?
    | UNDROP ACCOUNT identifier SEMI?
    | CREATE MANAGED ACCOUNT identifier managedAccountProperty (COMMA managedAccountProperty)* SEMI?
    | DROP MANAGED ACCOUNT identifier SEMI?
    | SHOW MANAGED ACCOUNTS (LIKE STRING_LITERAL)? SEMI?
    ;

sessionAction
    : SET sessionAssignment (COMMA sessionAssignment)*
    | UNSET sessionParameter (COMMA sessionParameter)*
    ;

sessionAssignment
    : sessionParameter EQ literal
    ;

sessionParameter
    : MULTI_STATEMENT_COUNT
    | identifier
    ;

booleanValue
    : TRUE
    | FALSE
    ;

sqlStatement
    : dmlStatement
    | selectStatement
    ;

columnList
    : columnOrConstraint (COMMA columnOrConstraint)*
    ;

columnOrConstraint
    : columnDef
    | tableConstraint
    ;

columnDef
    // columnDefName, not identifier: INNER/JOIN/LEFT/CROSS and CASE are live-legal column names in a
    // column DEFINITION (measured — CREATE TABLE kw (inner INT, join INT, case INT, …) runs).
    : columnDefName dataTypeName typeParameters?  columnConstraint* columnCommentClause?
    ;

typeParameters
    // A NEGATIVE parameter parses and is refused by the width rules, because that is where live refuses
    // it: VARCHAR(-1) is "Invalid character length: -1", not a syntax error. The MINUS is an optional
    // token in a slot the rule already reads, so it adds no parser decision.
    : LPAREN (MINUS? INTEGER_LITERAL (COMMA MINUS? INTEGER_LITERAL)?)? RPAREN   // nvarchar() — empty parens allowed
    ;

tableConstraint
    : constraintName? PRIMARY KEY LPAREN identifierList RPAREN relyOption?
    | constraintName? UNIQUE LPAREN identifierList RPAREN relyOption?
    | constraintName? FOREIGN KEY LPAREN identifierList RPAREN REFERENCES qualifiedName LPAREN identifierList RPAREN referentialActions? relyOption?
    | checkConstraint
    ;

// A CHECK constraint, named or not. The enforcement suffix is optional HERE — a CREATE TABLE takes
// the constraint bare, with ENABLE NOVALIDATE or with NOT ENFORCED (all live-verified) — while the
// ALTER form insists on ENABLE NOVALIDATE, which the handler enforces rather than the grammar so the
// refusal is live's sentence and not a syntax error.
checkConstraint
    : constraintName? CHECK LPAREN booleanExpr RPAREN checkEnforcement?
    ;

checkEnforcement
    : ENABLE NOVALIDATE
    | NOT ENFORCED
    ;

constraintName
    : CONSTRAINT identifier
    ;

// One additional column in a multi-column ALTER TABLE ... ADD. Kept as a SUBRULE so the repeated
// COLUMN / IF NOT EXISTS stay out of tableAction itself: repeating a token inside one alternative
// turns the whole rule's accessor into a List, silently breaking every existing `X() != null` guard.
alterAddColumnItem
    : COLUMN? if_not_exists? columnDef
    ;

// A later name in ALTER TABLE … DROP [COLUMN] a, b: its own optional COLUMN and IF EXISTS
// (live-verified: `DROP COLUMN a, COLUMN IF EXISTS b` runs).
alterDropColumnItem
    : COLUMN? if_exists? identifier
    ;

referentialActions
    : referentialAction+
    ;

// DROP DATABASE/SCHEMA … [CASCADE | RESTRICT] — CASCADE drops the contained objects too, RESTRICT (the
// default) refuses when the schema still has objects in it.
dropBehavior
    : CASCADE
    | RESTRICT
    ;

referentialAction
    : ON DELETE referentialOption
    | ON UPDATE referentialOption
    ;

referentialOption
    : CASCADE
    | SET NULL
    | SET DEFAULT
    | RESTRICT
    | NO ACTION
    ;

// Date/time type keywords usable as a typed-literal prefix, e.g. DATE '2020-01-15' or TIMESTAMP '…'.
structuredFieldList
    : structuredField (COMMA structuredField)*
    ;

structuredField
    : structuredFieldName dataTypeName typeParameters? (NOT? NULL)?
    ;

// Live 2026-08-05 (field/column keyword matrix): INNER, JOIN, CASE, LEFT and CROSS are legal
// unquoted structured-field names — they are legal COLUMN names too, but opening the general
// identifier rule to them would have to disambiguate join parsing, so only the unambiguous field
// position is opened here; the column half is tracked as a round-8 lead. SELECT/FROM/ORDER/GROUP/
// TABLE/NOT/NULL/WHERE/AND were all measured rejected in both positions.
structuredFieldName
    : identifier
    | INNER | JOIN | CASE | LEFT | CROSS
    ;

dateTimeLiteralType
    : DATE | TIME | TIMESTAMP    // only these three; TIMESTAMP_NTZ '...' etc are syntax errors in Snowflake
    ;

dataTypeName
    : INTEGER | INT | BIGINT | SMALLINT | TINYINT | BYTEINT | NUMBER | DECIMAL | NUMERIC | DEC | DECFLOAT | FLOAT | FLOAT4 | FLOAT8 | DOUBLE PRECISION? | REAL
    | VARCHAR | STRING | TEXT | BOOLEAN | DATE | DATETIME | TIME | TIMESTAMP_NTZ | TIMESTAMPNTZ | TIMESTAMP_LTZ | TIMESTAMP_TZ | VARIANT
    | TIMESTAMPLTZ | TIMESTAMPTZ | TIMESTAMP WITH LOCAL TIME ZONE | TIMESTAMP     // TIMESTAMP last: the worded form must win the prediction
    // `X VARYING` is only legal after the three FIXED-length spellings — live 2026-08-04,
    // `VARCHAR VARYING`, `NVARCHAR VARYING`, `NVARCHAR2 VARYING` and `VARCHAR2 VARYING` are all
    // syntax errors.
    | (CHAR | CHARACTER | NCHAR) VARYING? | NVARCHAR | NVARCHAR2 | VARCHAR2
    | ARRAY (LPAREN dataTypeName typeParameters? RPAREN)?      // structured ARRAY(INT)
    // Structured OBJECT(a CHAR NOT NULL, ...). The ZERO-FIELD spelling `OBJECT()` is legal and is its own
    // type — live-verified: `SYSTEM$TYPEOF(CAST(OBJECT_CONSTRUCT() AS OBJECT()))` is `OBJECT()[LOB]`, an
    // empty object casts to it, and a NON-empty one fails the schema check. OBJECT is the only one of the
    // three with that form: `ARRAY()` and `MAP()` are syntax errors live. (`ARRAY()` still PARSES here as
    // bare ARRAY plus an empty `typeParameters` — empty parentheses being a character-type leniency — so
    // DataTypeParser rejects it there rather than in the grammar.)
    | OBJECT (LPAREN structuredFieldList? RPAREN)?
    | BINARY | VARBINARY
    | UUID
    | VECTOR LPAREN (FLOAT | INT) COMMA INTEGER_LITERAL RPAREN
    | GEOGRAPHY | GEOMETRY
    // The FILE column type (a stage-file reference). `FILE` stays a plain identifier too — live-verified
    // 2026-08-03: a column, an alias, a table and a scripting variable may all be named `file` — so this
    // alternative only adds the TYPE position. FILE takes NO type parameters (`FILE(10)` and `FILE()` are
    // both syntax errors live) and is not a legal structured element type (`ARRAY(FILE)`, `OBJECT(x FILE)`
    // and `MAP(VARCHAR, FILE)` all fail "Unsupported data type 'FILE'."); `dataTypeName typeParameters?`
    // and the nested-type positions are shared by every type, so DataTypeParser rejects both there.
    | FILE
    | MAP LPAREN dataTypeName COMMA dataTypeName RPAREN   // MAP(keyType, valueType); backed by OBJECT.
                                                          // The bare `MAP` spelling is a syntax error in
                                                          // Snowflake (live-verified: `NULL::MAP`).
    | INTERVAL intervalTypeFields                         // INTERVAL DAY(3) TO SECOND(3), YEAR TO MONTH …
    ;

// The fields an INTERVAL column or cast target spans. Every unit parses in either place and takes its
// parentheses: which pairs and which precisions are legal is judged on the parsed fields, where live
// refuses them in its own sentence ("Invalid specification for type INTERVAL: INTERVAL MONTH TO DAY").
// A trailing field takes a single precision: `INTERVAL DAY TO SECOND(3,3)` is a syntax error at its comma.
intervalTypeFields
    : intervalTypeUnit intervalTypeLeadingPrecision? (TO intervalTypeUnit intervalTypeTrailingPrecision?)?
    ;

// Only the singular unit keywords name an interval type's fields: WEEK, QUARTER and the plural DAYS are
// syntax errors at that word.
intervalTypeUnit
    : YEAR | MONTH | DAY | HOUR | MINUTE | SECOND
    ;

intervalTypeLeadingPrecision
    : LPAREN INTEGER_LITERAL (COMMA INTEGER_LITERAL)? RPAREN
    ;

intervalTypeTrailingPrecision
    : LPAREN INTEGER_LITERAL RPAREN
    ;

columnConstraint
    : PRIMARY KEY relyOption?
    | NOT? NULL
    | UNIQUE relyOption?
    | tagList
    // A masking policy attaches in the column DEFINITION too, not only through ALTER COLUMN —
    // live-verified on CREATE TABLE and on ALTER TABLE ADD COLUMN, with WITH optional exactly as it
    // is for TAG, and the conditional USING form available here as well.
    | WITH? MASKING POLICY qualifiedName (USING LPAREN identifierList RPAREN)?
    | WITH? PROJECTION POLICY qualifiedName
    | CHECK LPAREN booleanExpr RPAREN checkEnforcement?
    | AUTOINCREMENT identityProperties?
    | IDENTITY identityProperties?
    | DEFAULT defaultExpression
    | REFERENCES qualifiedName (LPAREN identifier RPAREN)? referentialActions? relyOption?
    | FOREIGN KEY REFERENCES qualifiedName (LPAREN identifier RPAREN)? referentialActions? relyOption?
    | collateClause
    ;

// AUTOINCREMENT / IDENTITY seed + step: (start, step), or any mix of START <n> / INCREMENT <n> /
// ORDER / NOORDER word options in any order (each may appear alone); values may be negative.
identityProperties
    : LPAREN signedInteger COMMA signedInteger RPAREN (ORDER | NOORDER)?
    | identityWordOption+
    ;

identityWordOption
    : START signedInteger
    | INCREMENT signedInteger
    | ORDER
    | NOORDER
    ;

signedInteger
    : MINUS? INTEGER_LITERAL
    ;

defaultExpression
    : expression
    ;

relyOption
    : RELY
    | NORELY
    ;

// COPY GRANTS on CREATE [MATERIALIZED] VIEW / TABLE — accepted; grants are not copied (the security
// model keeps per-object grants, and the emulator has no source-object grant transfer semantics).
copyGrants
    : COPY GRANTS
    ;

// [WITH] TAG (k1='v1', k2='v2') at creation time, on tables, schemas and views. The tags are
// recorded so that TAG_REFERENCES can report them; they do not affect query results.
tagList
    : WITH? TAG LPAREN tagAssignment (COMMA tagAssignment)* RPAREN
    ;

tagAssignment
    : qualifiedName EQ tagValue
    ;

// [WITH] ROW ACCESS POLICY p ON (col1, col2) at CREATE time — attached like the ALTER form.
rowAccessPolicyClause
    : WITH? ROW ACCESS POLICY qualifiedName ON LPAREN identifierList RPAREN
    ;

// A parenthesized option group: CREDENTIALS=(AWS_KEY_ID='..' ...), ENCRYPTION=(TYPE='..'),
// STAGE_FILE_FORMAT=(TYPE=CSV ...), FILE_FORMAT=(FORMAT_NAME=db.s.ff). May be empty.
parenOptionList
    : LPAREN parenOption* RPAREN
    ;

parenOption
    : optionKey EQ (copyOptionValue | LPAREN stringLiteralList? RPAREN)
    ;

// Option keys are mostly IDENTIFIER, but several lex as dedicated keyword tokens elsewhere.
optionKey
    : identifier
    | ON_ERROR | SIZE_LIMIT | PURGE | MATCH_BY_COLUMN_NAME | TYPE | FIELD_DELIMITER | RECORD_DELIMITER
    | COMPRESSION | DATE_FORMAT | ESCAPE | SKIP_HEADER | PATTERN | FORCE | HEADER | OVERWRITE | SINGLE
    | MAX_FILE_SIZE | VALIDATION_MODE | ENCRYPTION | FILE_FORMAT | DATA_RETENTION_TIME_IN_DAYS
    | ENABLE
    ;

// EQUALITY(col) / SUBSTRING(col) / FULL_TEXT(col) / GEO(col), or EQUALITY(*) for every column. The
// method is an ordinary identifier — live lets all four words name tables and columns too.
searchOptimizationTarget
    : identifier LPAREN (STAR | qualifiedName) RPAREN
    ;

// A DROP may name the expression or its number (live-verified: DROP … ON 1 removes expression 1).
searchOptimizationDrop
    : searchOptimizationTarget
    | INTEGER_LITERAL
    ;

// An aggregation policy attaches to the TABLE, optionally naming the columns whose DISTINCT values
// count as one entity for the minimum-group-size test (live-verified: without it rows are counted).
aggregationPolicyClause
    : AGGREGATION POLICY qualifiedName (ENTITY KEY LPAREN identifierList RPAREN)?
    ;

// A join policy attaches to the TABLE and takes no arguments of its own.
joinPolicyClause
    : JOIN POLICY qualifiedName
    ;

// CREATE TABLE tail modifiers, in any order: comments, clustering, tags, a row access policy.
tableTailOption
    : commentClause
    | copyGrants
    | clusterByClause
    | tagList
    | rowAccessPolicyClause
    | WITH? aggregationPolicyClause
    | WITH? joinPolicyClause
    | optionKey EQ (parenOptionList | copyOptionValue)   // CHANGE_TRACKING=TRUE etc. — accepted, not modeled
    ;

// CREATE VIEW key=value properties (CHANGE_TRACKING=TRUE, ...) — accepted, not modeled.
viewProperty
    : optionKey EQ copyOptionValue
    ;

dmlStatement
    : insertStatement
    | multiTableInsertStatement
    | updateStatement
    | deleteStatement
    | mergeStatement
    | copyIntoStatement
    ;

insertStatement
    : INSERT OVERWRITE? INTO objectName insertColumnList? VALUES valueTupleList SEMI?
    | INSERT OVERWRITE? INTO objectName insertColumnList? selectStatement SEMI?
    ;

// An INSERT's column list. Each item may carry a qualifier, and the only one the account accepts is
// the TARGET TABLE's own bare name — INSERT INTO t1 (t1.a) VALUES (1) runs, and so does the same list
// against a fully qualified target, while a schema name or any other word is an invalid identifier.
// A THIRD part is a syntax error at the second dot, which falls out of the single optional qualifier.
insertColumnList
    : LPAREN insertColumnItem (COMMA insertColumnItem)* RPAREN
    ;

insertColumnItem
    : namePart (DOT namePart)?
    ;

// Snowflake multi-table INSERT: unconditional (INSERT [OVERWRITE] ALL INTO ...) and
// conditional (INSERT [OVERWRITE] {FIRST | ALL} WHEN cond THEN INTO ... [ELSE INTO ...]).
multiTableInsertStatement
    : INSERT OVERWRITE? ALL multiInsertInto+ selectStatement SEMI?
    | INSERT OVERWRITE? (FIRST | ALL) multiInsertWhen+ multiInsertElse? selectStatement SEMI?
    ;

multiInsertInto
    : INTO qualifiedName columnListOptional? (VALUES LPAREN expressionList RPAREN)?
    ;

multiInsertWhen
    : WHEN booleanExpr THEN multiInsertInto+
    ;

multiInsertElse
    : ELSE multiInsertInto+
    ;

columnListOptional
    // Column-NAME list: the keyword names (incl. CASE) are live-legal here — measured,
    // INSERT INTO kw (inner, join, case, left, cross) VALUES (…) runs on a real account.
    : LPAREN namePart (COMMA namePart)* RPAREN
    ;

identifierList
    : identifier (COMMA identifier)*
    ;

viewColumnList
    : viewColumnDef (COMMA viewColumnDef)*
    ;

viewColumnDef
    : identifier columnCommentClause?
    ;

valueTupleList
    : valueTuple (COMMA valueTuple)*
    ;

valueTuple
    : LPAREN valueList RPAREN
    ;

// A VALUES cell is any expression a select item can be, NOT, AND and OR included:
// VALUES (NOT TRUE) and VALUES (TRUE AND FALSE) insert on the account, and a derived (VALUES (NOT TRUE))
// answers FALSE.
valueList
    : booleanExpr (COMMA booleanExpr)*
    ;

updateStatement
    : UPDATE objectName (AS? identifier)? (FROM tableReference (COMMA tableReference | joinClause)*)? SET assignmentList (FROM tableReference (COMMA tableReference | joinClause)*)? whereClause? SEMI?
    ;   // Snowflake also accepts the FROM clause BEFORE SET (UPDATE t u FROM (...) b SET ... WHERE ...)

deleteStatement
    : DELETE FROM objectName (AS? identifier)? (USING tableReference (COMMA tableReference | joinClause)*)? whereClause? SEMI?
    ;

mergeStatement
    : MERGE INTO qualifiedName (AS? identifier)?
      USING mergeSource (AS? identifier (LPAREN identifierList RPAREN)?)?
      ON booleanExpr
      mergeClause+
      SEMI?
    ;

mergeSource
    : qualifiedName                              # MergeSourceTable
    | LPAREN VALUES valueTupleList RPAREN       # MergeSourceValues
    | LPAREN selectStatement RPAREN             # MergeSourceSubquery
    ;

mergeClause
    : WHEN MATCHED (AND booleanExpr)? THEN UPDATE SET assignmentList
    | WHEN MATCHED (AND booleanExpr)? THEN DELETE
    | WHEN NOT MATCHED (AND booleanExpr)? THEN INSERT mergeInsertColumnList? VALUES valueTuple
    ;

// The MERGE INSERT target column list allows an optional target-table-alias qualifier on each column
// (e.g. INSERT (t.col1, t.col2) ...), mirroring the UPDATE SET assignment target; the qualifier is
// redundant (the target is fixed) and is dropped at execution. A plain `col` is the single-identifier case.
mergeInsertColumnList
    : LPAREN mergeInsertColumn (COMMA mergeInsertColumn)* RPAREN
    ;

mergeInsertColumn
    : (identifier DOT)? identifier
    ;

assignmentList
    : assignment (COMMA assignment)*
    ;

assignment
    // namePart targets: UPDATE kw SET inner = 10 is live-legal (measured).
    : (identifier '.')? namePart EQ expression
    ;

queryStatement
    : selectStatement SEMI?
    ;

// EXPLAIN [USING { TABULAR | JSON | TEXT }] <query|dml> — returns the structural execution plan as a result set.
explainStatement
    : EXPLAIN (USING identifier)? (selectStatement | dmlStatement) SEMI?
    ;

selectStatement
    : withClause? selectOperand (setOperator selectOperand)* orderByClause? (limitClause | fetchClause)?
    ;

selectOperand
    : selectClause
    | LPAREN selectStatement RPAREN
    ;

withClause
    : WITH RECURSIVE? cteDefinition (COMMA cteDefinition)*
    ;

cteDefinition
    // The CTE name takes the full NAME vocabulary, not the column one: live accepts `WITH case AS (…)`
    // and `WITH asof AS (…)` alike (measured for all five name words and all eight join words).
    : nameStartPart columnListOptional? AS LPAREN selectStatement RPAREN   // AS is REQUIRED (live-Snowflake verified)
    ;

// A hierarchical query replaces the grouping tail: Snowflake rejects GROUP BY, HAVING and QUALIFY after
// a CONNECT BY (live-verified — all three are syntax errors), so the two tails are alternatives here.
// The grouping tail is legal without FROM (live-verified: WHERE, GROUP BY, HAVING and QUALIFY all
// parse on a FROM-less select; CONNECT BY parses too but is then refused semantically with
// "missing FROM clause", enforced in the visitor).
// Every query block reads an INTO clause after its select list, as the account's parser does; only a Snowflake
// Scripting block's own SELECT … INTO statement may carry one, and every other is refused while the statement
// compiles, at its query block's SELECT (see IntoClausePlacement).
selectClause
    : SELECT (DISTINCT | ALL)? topClause? selectList intoClause?
      (FROM tableExpression)? whereClause? (connectByClause | groupByClause? havingClause? qualifyClause?)
    ;

// One target: a statement written SELECT … INTO reads both as the scripting statement and as a query, and a query
// reading a longer list would keep the two alike to the end of it, so a fault inside the list would find neither.
// The list of the scripting statement's own INTO clause is read by selectIntoStatement.
intoClause
    : INTO intoTarget
    ;

// Snowflake hierarchical query: START WITH may come before or after CONNECT BY, but CONNECT BY is
// mandatory — a lone `START WITH` is a syntax error (live-verified). Without START WITH every row seeds
// its own tree. The condition is an ordinary predicate in which PRIOR marks the parent-row side.
connectByClause
    : startWithClause connectByPredicate
    | connectByPredicate startWithClause?
    ;

startWithClause
    : START WITH booleanExpr
    ;

connectByPredicate
    : CONNECT BY booleanExpr
    ;

setOperator
    : UNION ALL? (BY identifier)?   // UNION [ALL] BY NAME aligns columns by name; identifier must be NAME
    | INTERSECT ALL?
    | EXCEPT ALL?
    | MINUS_KW ALL?   // MINUS is a Snowflake synonym for EXCEPT
    ;

tableExpression
    : tableReference (COMMA tableReference | joinClause)* COMMA?   // trailing comma allowed (Snowflake), as in selectList
    ;

tableReference
    // PIVOT/UNPIVOT come AFTER the (optionally aliased) source and may carry their own trailing alias, e.g.
    // FROM (subquery) src PIVOT(...) p — so they live here, not glued to tableSource.
    // The bare-alias alternative carries a predicate for the unreserved EXCEPT: `FROM t except` is an
    // alias only when what follows cannot START a set-operation right-hand side — otherwise
    // `FROM t EXCEPT SELECT ...` must stay a set-op (both parses reach EOF, so lookahead alone
    // cannot decide).
    : LATERAL? tableSource (AS aliasName (LPAREN identifierList RPAREN)?
        | {bareAliasAllowed()}? nonJoinKeywordIdentifier (LPAREN identifierList RPAREN)?)?
      (pivotClause pivotAlias? | unpivotClause pivotAlias?)?
      sampleClause?
    ;

// A pivot alias may carry an ANSI derived-column alias list that renames the pivoted result columns,
// e.g. PIVOT(SUM(amount) FOR quarter IN ('Q1','Q2')) AS p (empid, q1, q2).
pivotAlias
    : AS? identifier (LPAREN identifierList RPAREN)?
    ;

sampleClause
    : (SAMPLE | TABLESAMPLE) sampleMethod? LPAREN (INTEGER_LITERAL | FLOAT_LITERAL | SESSION_VAR_REF) ROWS? RPAREN sampleSeed?
    ;

// BLOCK (and any future method word) arrives via the identifier fallback — the clause is anchored
// between SAMPLE/TABLESAMPLE and the parenthesized size, so a loose word cannot leak elsewhere.
sampleMethod
    : BERNOULLI | ROW | SYSTEM | identifier
    ;

sampleSeed
    : (REPEATABLE | SEED) LPAREN INTEGER_LITERAL RPAREN
    ;

tableSource
    : TABLE LPAREN expression RPAREN {refuseUnreadableTableOperand($ctx);}   // Table function call or literal
    | DIRECTORY LPAREN stageRef RPAREN  // Directory table: file-level metadata of a stage
    | stageRef stageQueryParams?        // Query staged files: $1..$n fields + metadata$ columns
    // A quoted location: FROM '@st/path' reads the stage, and any other string is refused while
    // compiling ("invalid URL prefix found in: 'x'"). Two earlier attempts at this alternative made
    // ALL(*) re-derive FROM (VALUES (...), (...)) tuple lists as a parenthesized join; the cure was
    // to let the VALUES alternative be REACHED FIRST, below, rather than to leave the string out.
    | STRING_LITERAL stageQueryParams?
    | FLATTEN LPAREN flattenArgList RPAREN  // LATERAL FLATTEN(expr [, name => val ...])
    | KW_IDENTIFIER_REF LPAREN identifierArgument RPAREN  // IDENTIFIER('<name>') — dynamic table name
    | openedIdentifierReference
    | POSITIONAL_PARAMETER              // $n — a prior flow-chain stage's result (n statements back)
    | tableQualifiedName timeTravelClause?
    | LPAREN selectStatement RPAREN
    // BEFORE the parenthesized join: a tuple list is a VALUES list and not a comma-separated join of
    // its tuples, and ALL(*) will read it the other way given the chance.
    | LPAREN VALUES valueTupleList (AS? identifier columnListOptional?)? RPAREN  // (VALUES (...) [AS] v (cols)) as subquery — Snowflake allows the alias inside the parens
    // A parenthesized FROM group: FROM (a JOIN b ON c ...) — pure grouping, keeps inner aliases in scope. It holds
    // one source and the joins that follow it, none at all included — FROM (t a) and FROM ((a JOIN b ON c)) are
    // groups too — and never a comma: FROM (t a, t b) is a syntax error on the account.
    | LPAREN tableReference joinClause* RPAREN
    | VALUES valueTupleList                // Inline values
    | tableFunctionExpr    // bare table function: LATERAL SPLIT_TO_TABLE(x, ',') — no TABLE() wrapper
    ;

// A bare table-function call used directly as a FROM source. Kept as its own rule (not inline
// `expression`) so it cannot shadow a plain table name — it requires the parenthesised call.
tableFunctionExpr
    : functionName LPAREN RPAREN
    | functionName LPAREN expression (COMMA expression)* RPAREN
    | functionName LPAREN expression (COMMA expression)* (COMMA namedArgument)+ RPAREN
    | functionName LPAREN namedArgument (COMMA namedArgument)* RPAREN
    ;

// SELECT ... FROM @stage (FILE_FORMAT => 'name', PATTERN => 'regex'): named read parameters.
stageQueryParams
    : LPAREN stageQueryParam (COMMA stageQueryParam)* RPAREN
    ;

stageQueryParam
    : (FILE_FORMAT | PATTERN | identifier) ARROW (STRING_LITERAL | qualifiedName)
    ;

// FLATTEN argument list: positional INPUT followed by optional named params, or all named
flattenArgList
    : expression (COMMA namedArgument)*   // positional INPUT [, name => val ...]
    | namedArgumentList                   // all named
    ;

// USING (c1, t2.c2, ...) — Snowflake tolerates qualified names here; only the column part joins.
usingColumnList
    : qualifiedName (COMMA qualifiedName)*
    ;

timeTravelClause
    : AT_KEYWORD LPAREN timeTravelPoint RPAREN
    | BEFORE LPAREN timeTravelPoint RPAREN
    | changesClause
    ;

timeTravelPoint
    : TIMESTAMP ARROW expression
    | OFFSET ARROW expression
    | STATEMENT ARROW expression
    | STREAM ARROW expression
    ;

changesClause
    : CHANGES LPAREN INFORMATION ARROW (DEFAULT | identifier) RPAREN
      (AT_KEYWORD LPAREN timeTravelPoint RPAREN | BEFORE LPAREN timeTravelPoint RPAREN)?
      (END LPAREN timeTravelPoint RPAREN)?
    ;

joinClause
    // DIRECTED is an accepted no-op join modifier (e.g. INNER DIRECTED JOIN): a semantic annotation with no
    // effect on the row-level result, so it parses like a plain join of that type.
    // ASOF is its own modifier group: Snowflake accepts only the bare `ASOF JOIN` (live-verified — LEFT /
    // RIGHT / INNER ASOF and NATURAL ASOF are syntax errors), and MATCH_CONDITION must come BEFORE the
    // ON / USING clause (`ON … MATCH_CONDITION (…)` is a syntax error there).
    // CROSS has its own alternative BECAUSE it takes no ON and no USING: the account refuses
    // `CROSS JOIN r ON …` as a syntax error at the ON (live-verified), and leaving CROSS inside
    // joinType would let the general alternative swallow the tail.
    : NATURAL? CROSS DIRECTED? JOIN LATERAL? tableReference
    | (NATURAL? joinType? DIRECTED? | ASOF) JOIN LATERAL? tableReference asofMatchCondition?
      (ON booleanExpr | USING LPAREN usingColumnList RPAREN)?
    ;

// MATCH_CONDITION ( <left expr> {>= | > | <= | <} <right expr> ) — the closest-match predicate of an
// ASOF JOIN. The optional alias in front exists because Snowflake accepts LIMIT as the right-hand
// table's alias here (live-verified: `ASOF JOIN r LIMIT MATCH_CONDITION (q.t > LIMIT.t)` runs); LIMIT
// is deliberately not a general bare alias in this grammar (it would shadow the LIMIT clause), so it is
// admitted only in this position, where the following MATCH_CONDITION anchors it. OFFSET needs no
// special case — it already reaches this position through the ordinary alias rule.
asofMatchCondition
    : (AS? LIMIT)? MATCH_CONDITION LPAREN booleanExpr RPAREN
    ;

joinType
    : INNER
    | LEFT OUTER?
    | RIGHT OUTER?
    | FULL OUTER?
    ;

pivotClause
    : PIVOT LPAREN aggregateFunction FOR identifier IN LPAREN pivotInList RPAREN
      (DEFAULT ON NULL LPAREN expression RPAREN)? RPAREN
    ;

// The pivot column set: an explicit value list, ANY (dynamic: the distinct FOR-column values,
// optionally ordered), or a subquery producing the values.
pivotInList
    : pivotValueList
    | ANY (ORDER BY expression (COMMA expression)*)?
    | selectStatement
    ;

unpivotClause
    : UNPIVOT unpivotNulls? LPAREN identifier FOR identifier IN LPAREN unpivotColumnList RPAREN RPAREN
    ;

unpivotNulls
    : INCLUDE NULLS
    | EXCLUDE NULLS
    ;

aggregateFunction
    : functionName LPAREN DISTINCT? expression RPAREN
    ;

pivotValueList
    : pivotValue (COMMA pivotValue)*
    ;

pivotValue
    : literal (AS? identifier)?
    ;

unpivotColumnList
    : identifier (COMMA identifier)*
    ;

selectList
    : selectItem (COMMA selectItem)* COMMA?
    ;

selectItem
    : STAR starModifier*                                  # StarItem
    | starQualifiedName DOT STAR starModifier*            # QualifiedStarItem
    // Snowflake's BRACED star is an OBJECT constructor over the row, not a star: {*} is
    // OBJECT_CONSTRUCT(*) and projects ONE column whose keys are the star's effective column names
    // ({* EXCLUDE (c)}, {t.*} pick which columns take part). It therefore needs its own alternative —
    // treating the braces as optional decoration on the star above expanded it to N columns instead.
    // The bare (AS-less) alias carries a predicate for the unreserved EXCEPT: `SELECT 1 except` is
    // an alias only when what follows cannot START a set-operation right-hand side — otherwise
    // `SELECT 1 EXCEPT SELECT 2` must stay a set-op (both parses reach EOF, so lookahead alone
    // cannot decide).
    | LBRACE (starQualifiedName DOT)? STAR starModifier* RBRACE
        (AS aliasName | {bareAliasAllowed()}? identifier)?  # ObjectStarItem
    | booleanExpr (AS aliasName | {bareAliasAllowed()}? identifier)?  # ExprItem
    ;

// A name an EXCLUDE list may write. DEFAULT reaches it where it reaches no other name position: the
// account PARSES `SELECT * EXCLUDE (default)` and then refuses it as a column that does not exist,
// rather than as a syntax error (live-verified).
excludedColumn
    : identifier
    | DEFAULT
    ;

// Snowflake column-list modifiers on a SELECT * : EXCLUDE, RENAME, REPLACE, ILIKE.
starModifier
    : ILIKE STRING_LITERAL
    | EXCLUDE (excludedColumn | LPAREN excludedColumn (COMMA excludedColumn)* RPAREN)
    | RENAME (starRenameItem | LPAREN starRenameItem (COMMA starRenameItem)* RPAREN)
    | REPLACE LPAREN starReplaceItem (COMMA starReplaceItem)* RPAREN
    ;

starRenameItem
    : identifier AS? identifier
    ;

starReplaceItem
    : expression AS identifier
    ;

whereClause
    : WHERE booleanExpr
    ;

groupByClause
    : GROUP BY ALL
    | GROUP BY groupByElement (COMMA groupByElement)*
    ;

// The super-group forms come FIRST, and the order is load-bearing: rollup and cube are legal column
// names live, so they are ordinary identifiers here too, and `ROLLUP(g)` would otherwise read as a
// function call by the expression alternative alone. Matching the keyword form first leaves a bare
// `GROUP BY rollup` — the column — to fall through to expression, which is what live does with both.
groupByElement
    : ROLLUP LPAREN groupByColumnList RPAREN             // ROLLUP(a, b, c)
    | CUBE LPAREN groupByColumnList RPAREN               // CUBE(a, b, c)
    | GROUPING SETS LPAREN groupingSetList RPAREN        // GROUPING SETS((a,b),(a),(b),())
    | expression                                         // plain column/expression
    ;

groupByColumnList
    : expression (COMMA expression)*
    ;

groupingSetList
    : groupingSet (COMMA groupingSet)*
    ;

groupingSet
    : LPAREN (expression (COMMA expression)*)? RPAREN   // (a, b) or ()
    | expression                                        // shorthand single column
    ;

havingClause
    : HAVING booleanExpr
    ;

qualifyClause
    : QUALIFY booleanExpr
    ;

overClause
    : OVER LPAREN partitionByClause? orderByClause? windowFrame? RPAREN
    ;

// Ordered-set aggregate qualifier: LISTAGG(...) / PERCENTILE_CONT(p) WITHIN GROUP (ORDER BY ...).
withinGroupClause
    : WITHIN GROUP LPAREN orderByClause RPAREN
    ;

// Null treatment for window value functions: FIRST_VALUE(x) { IGNORE | RESPECT } NULLS OVER (...).
nullHandling
    : (IGNORE | RESPECT) NULLS
    ;

windowFrame
    : (ROWS | RANGE) (BETWEEN frameBound AND frameBound | frameBound)
    ;

frameBound
    : UNBOUNDED PRECEDING
    | UNBOUNDED FOLLOWING
    | CURRENT ROW
    | expression PRECEDING
    | expression FOLLOWING
    ;

partitionByClause
    : PARTITION BY LPAREN expressionList RPAREN
    | PARTITION BY expressionList
    ;

orderByClause
    : ORDER BY orderItem (COMMA orderItem)*
    ;

orderItem
    : expression (ASC | DESC)? (NULLS (FIRST | LAST))?
    ;

topClause
    : TOP INTEGER_LITERAL
    ;

// LIMIT takes a literal or a BIND, never a general expression — live-verified: `LIMIT :batch_size`
// runs (the Scripting doc's batch-processing loop is written that way) while `LIMIT 1+1` is a syntax
// error. OFFSET binds the same way. A STRING literal parses but only the EMPTY string survives the
// executor's check (live: LIMIT '' means no limit, OFFSET '' means zero, any other string is a
// syntax error at the literal).
limitClause
    : LIMIT (INTEGER_LITERAL | NULL | STRING_LITERAL | COLON identifier)
      (OFFSET (INTEGER_LITERAL | NULL | STRING_LITERAL | COLON identifier))?
    ;

// NULL is a legal count and a legal offset in both spellings, and means "no limit" / "no offset"
// (live-verified: LIMIT 1 OFFSET NULL answers one row, FETCH FIRST NULL ROWS ONLY answers all of them).
// A bare trailing `OFFSET NULL` with no FETCH after it stays a syntax error, as it does on the account.
//
// Every part of the ANSI spelling is optional but FETCH and the count: FETCH 2, FETCH FIRST 2,
// FETCH NEXT 2 ROWS ONLY, and OFFSET 1 [ROWS] FETCH … all run (live-verified); a bare OFFSET
// without a FETCH stays a syntax error, exactly as on the account.
fetchClause
    : (OFFSET (INTEGER_LITERAL | NULL | COLON identifier) (ROW | ROWS)?)?
      FETCH (FIRST | NEXT)? (INTEGER_LITERAL | NULL | COLON identifier) (ROW | ROWS)? ONLY?
    ;

// WORK is the SQL-standard spelling of TRANSACTION and live accepts it on all three statements
// (live-verified: BEGIN WORK / COMMIT WORK / ROLLBACK WORK).
//
// Only the OPENING statement takes a NAME — `COMMIT NAME x` is a syntax error live.
//
// A bare BEGIN starts a transaction only where its statement ends right there, at a semicolon or the
// end of the input; followed by anything else it opens a scripting block, as live reads it. The
// alternatives say so token by token, so a block with a fault inside stays a block and is refused as
// one, instead of being read again as a transaction followed by loose statements.
transactionStatement
    : BEGIN (WORK | TRANSACTION) transactionName? SEMI?
    | BEGIN transactionName SEMI?
    | BEGIN (SEMI | EOF)
    | START TRANSACTION transactionName? SEMI?
    | COMMIT WORK? SEMI?
    | ROLLBACK WORK? SEMI?
    ;

// The name is an IDENTIFIER, never a string literal (live refuses `BEGIN NAME 'a string'`), and it
// replaces the UUID an unnamed transaction carries in SHOW TRANSACTIONS' name cell — folded upper
// unquoted, case-preserved quoted. A DOTTED name is accepted and only its FIRST part is kept
// (live-verified: `BEGIN NAME a.b` shows 'A'), which is why this takes a qualifiedName.
transactionName
    : NAME qualifiedName
    ;

listStatement
    : (LIST | LS) stageRef (PATTERN EQ STRING_LITERAL)? SEMI?
    ;

// Stage file operations: PUT (local → stage), GET (stage → local), REMOVE/RM (delete staged files).
putStatement
    : PUT (STRING_LITERAL | FILE_URL) stageRef stageFileOption* SEMI?
    ;

getStatement
    : GET stageRef (STRING_LITERAL | FILE_URL) stageFileOption*
    ;

removeStatement
    : (REMOVE | RM) stageRef (PATTERN EQ STRING_LITERAL)? SEMI?
    ;

stageFileOption
    : optionKey EQ copyOptionValue
    ;

showStatement
    : SHOW TERSE? DATABASES HISTORY? (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? SCHEMAS HISTORY? (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | DATABASE (objectName | openedIdentifierReference)? | (SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? TABLES HISTORY? (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? ICEBERG TABLES (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? EVENT TABLES (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? EXTERNAL TABLES (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    // The integration and external volume listings take a LIKE and nothing else.
    | SHOW integrationKind? INTEGRATIONS (LIKE STRING_LITERAL)? SEMI?
    | SHOW EXTERNAL VOLUMES (LIKE STRING_LITERAL)? SEMI?
    | SHOW TERSE? VIEWS (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? MATERIALIZED VIEWS (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? DYNAMIC TABLES (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? HYBRID TABLES (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? COLUMNS (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (TABLE | VIEW | DATABASE | SCHEMA) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?   // FROM is not Snowflake syntax (live-verified)
    | SHOW TERSE? STREAMS (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? TASKS (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? ALERTS (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? PIPES (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? SEQUENCES (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? WAREHOUSES (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? CORTEX SEARCH SERVICES (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    // The versions of a notebook or a Streamlit app, listed in one form only, which a LIMIT and its count may close;
    // the tail is read leniently so that any other modifier is refused where the account's parser stops
    // (ShowTailSyntax). Any other SHOW VERSIONS reads as the other listings do and is refused as an unsupported
    // feature, as the account refuses it.
    | SHOW VERSIONS IN (NOTEBOOK | STREAMLIT) qualifiedName showTail SEMI?
    | SHOW TERSE? VERSIONS (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW NOTEBOOKS (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW MODELS (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? STREAMLITS (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW COMPUTE POOLS (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    // The instance-family catalog. Its order is the account's own, so the listing is not re-sorted.
    | SHOW COMPUTE POOL INSTANCE FAMILIES showTail SEMI?
    | SHOW TERSE? STAGES (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? FILE FORMATS (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? TAGS (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    // The routine listings take the same three modifiers, and TERSE must precede USER / BUILTIN.
    // Live-verified on a real account (2026-08-03):
    //   * USER and BUILTIN are mutually exclusive for BOTH families — `SHOW BUILTIN USER FUNCTIONS`,
    //     `SHOW USER BUILTIN FUNCTIONS`, `SHOW BUILTIN USER PROCEDURES`, `SHOW USER BUILTIN PROCEDURES`
    //     and `SHOW TERSE USER BUILTIN PROCEDURES` are all syntax errors, while `SHOW BUILTIN FUNCTIONS`
    //     returns the 1134-row built-in catalog and `SHOW BUILTIN PROCEDURES` the 32-row one.
    //   * TERSE only binds in front: `SHOW TERSE USER FUNCTIONS` / `SHOW TERSE BUILTIN PROCEDURES` run,
    //     while `SHOW USER TERSE FUNCTIONS` and `SHOW BUILTIN TERSE PROCEDURES` are syntax errors.
    // TERSE is accepted here but changes NOTHING — unlike SHOW TERSE TABLES it does not trim the column
    // set (see ShowModifierProfile). Neither do STARTS WITH and LIMIT, which parse and are then ignored.
    | SHOW TERSE? (USER | BUILTIN)? PROCEDURES (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | (APPLICATION PACKAGE? | CLASS) (objectName | openedIdentifierReference) | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? (USER | BUILTIN)? FUNCTIONS (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | (CLASS | APPLICATION) (objectName | openedIdentifierReference) | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? USERS (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? ROLES (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW GRANTS ON showGrantsTarget SEMI?
    | SHOW GRANTS TO (USER | ROLE) identifier SEMI?  // SHOW GRANTS TO USER/ROLE name
    | SHOW GRANTS OF ROLE identifier SEMI?           // who holds this role
    | SHOW GRANTS SEMI?                              // everything granted to the current user
    | SHOW TERSE? MASKING POLICIES (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? CONTACTS (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? PROJECTION POLICIES (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? AGGREGATION POLICIES (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? JOIN POLICIES (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW TERSE? ROW ACCESS POLICIES (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW PARAMETERS (LIKE STRING_LITERAL)? (IN (SESSION | ACCOUNT | (DATABASE | TABLE | WAREHOUSE | USER | ROLE | TASK) identifier | SCHEMA qualifiedName))? SEMI?
    | SHOW TERSE? OBJECTS (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA | TABLE) (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    | SHOW ORGANIZATION ACCOUNTS showTail SEMI?
    | SHOW ACCOUNTS HISTORY? (LIKE STRING_LITERAL)? showTail SEMI?
    | SHOW LOCKS (IN ACCOUNT (objectName | openedIdentifierReference)?)? showTail SEMI?
    | SHOW TRANSACTIONS (IN ACCOUNT (objectName | openedIdentifierReference)?)? showTail SEMI?   // no LIKE (live-verified)
    | SHOW VARIABLES (LIKE STRING_LITERAL)? showTail SEMI?
    | SHOW TERSE? (PRIMARY | UNIQUE | IMPORTED) KEYS (IN (ACCOUNT (objectName | openedIdentifierReference)? | DATABASE identifier? | SCHEMA (objectName | openedIdentifierReference)? | TABLE (objectName | openedIdentifierReference)? | objectName showInstanceName?))? showTail SEMI?
    ;

// The listing of a class's instances, SHOW <class>. No class is modelled, so the class is refused as missing.
showClassStatement
    : SHOW TERSE? className (LIKE STRING_LITERAL)? (IN (ACCOUNT (objectName | openedIdentifierReference)? | (DATABASE | SCHEMA) (objectName | openedIdentifierReference)? | objectName))? showTail SEMI?
    ;

// The class SHOW <class> and DROP <class> <instance> name: a plain word, a quoted name, a qualified name or an
// IDENTIFIER() reference, which names a class too: SHOW IDENTIFIER('x') and DROP IDENTIFIER('x') y both miss the
// class X. A keyword alone is no class name (live-verified), so none of the kinds Frostlake lists and drops reads
// as one, and a statement written with one keeps the fault the parser finds. A word live's own lexer keeps as a
// keyword is refused where it stands (see refuseKeywordClassName).
className
    : IDENTIFIER {refuseKeywordClassName($ctx);}
    // ALERT and ALERTS are keywords of their own statements; written where a class name stands they are refused
    // at the word, as live refuses them.
    | (ALERT | ALERTS) {refuseKeywordClassName($ctx);}
    // The plural listing words are keywords, refused where they stand after DROP; SHOW NOTEBOOKS and
    // SHOW STREAMLITS are the listings, which the statement rule takes first.
    | MODELS {refuseKeywordClassName($ctx);}
    | NOTEBOOKS {refuseKeywordClassName($ctx);}
    | STREAMLITS {refuseKeywordClassName($ctx);}
    | SECRETS {refuseKeywordClassName($ctx);}
    // The Git and version words are keywords of the account's too, refused where they stand.
    | (PULL | PUSH | VERSIONS) {refuseKeywordClassName($ctx);}
    // The user properties are keywords here and plain names to the account, which reads them as a class name.
    | (DAYS_TO_EXPIRY | MINS_TO_UNLOCK | MINS_TO_BYPASS_MFA | RSA_PUBLIC_KEY | RSA_PUBLIC_KEY_2)
      {refuseKeywordClassName($ctx);}
    // EXTERNAL is a keyword of the external volume and integration statements; after DROP it still opens the
    // EXTERNAL TABLE kind.
    | EXTERNAL {refuseKeywordClassName($ctx);}
    | QUOTED_IDENTIFIER
    | nameStartPart (DOT DOT namePart | DOT namePart) (DOT namePart)*
    | KW_IDENTIFIER_REF LPAREN identifierArgument RPAREN
    ;

// The instance of a class scope, SHOW <objects> IN <class> <instance>: live reads a name written after a bare
// scope name as the name of an instance of the class that name stands for, or of the object a scope kind names —
// a word, a quoted or qualified name, or an IDENTIFIER() reference. LIMIT is such a word as well, where no count
// follows it. APPLICATION, COMPUTE, FAILOVER and REPLICATION written straight after the IN read their kind's second
// word first, PACKAGE, POOL or GROUP (ShowScopeWords). No class and none of those objects is modelled, so a
// statement with an instance is refused when it runs, each listing with live's own sentence (ShowScopeRefusal,
// ShowScopeKindRefusal, live-verified). A word live's lexer keeps as a keyword, written alone as the scope, is no
// class and reads no name, unless it is a scope kind: the word after it is refused where it stands
// (ShowScopeWords.takesInstance).
showInstanceName
    : {ShowScopeWords.opensTwoWordKind(_input.LT(-2), _input.LT(-1), _input.LT(1))}?
      showKindWord=(PACKAGE | POOL | GROUP) showScopeName?
    | {ShowScopeWords.takesInstance(_input)}? showScopeName
    | {_input.LA(2) != INTEGER_LITERAL && ShowScopeWords.takesInstance(_input)}? LIMIT
    ;

showScopeName
    : identifier (DOT identifier)*
    | KW_IDENTIFIER_REF LPAREN identifierArgument RPAREN
    ;

// Trailing SHOW modifiers shared by the object listings (all optional; the rule may match empty):
// STARTS WITH 'prefix' (case-sensitive name-prefix filter), LIMIT n [FROM 'name'] (pagination), and
// WITH PRIVILEGES p1, p2. The privilege LIST is mandatory — bare `SHOW TABLES WITH PRIVILEGES` is a
// syntax error live, while `SHOW WAREHOUSES WITH PRIVILEGES USAGE, MODIFY` runs. Which object types
// honour the filter is a SEMANTIC matter (a real account answers `SHOW TABLES … WITH PRIVILEGES` with
// "Unsupported feature", i.e. it parses); TASKS and ROLES read no WITH PRIVILEGES at all.
//
// Parsing STARTS WITH / LIMIT is likewise NOT the same as acting on them: a real account accepts both
// on nearly every listing and then ignores them on several. `SHOW STAGES STARTS WITH 'ZZZ'` returns
// every stage, `SHOW SEQUENCES LIMIT 1` every sequence. ShowModifierProfile carries the measured
// honour/ignore split per listing. This rule parses LENIENTLY — the count, the prefix and the privilege
// list may be missing, and the listings whose grammar reads none of the three (KEYS, LOCKS, TRANSACTIONS,
// VARIABLES) carry it too — so ShowTailSyntax can refuse each shape where live's parser stops: at the
// word after one left unfinished (`SHOW TABLES LIMIT` → '<EOF>'), at a modifier the listing does not
// read (`SHOW PRIMARY KEYS LIMIT 2` → 'LIMIT'), at the word after a WITH that opens no WITH PRIVILEGES.
// Ordering is by name, byte-wise, so uppercase sorts before lowercase; `FROM 'x'` keeps the rows
// sorting strictly after x and is a syntax error without a preceding LIMIT; `LIMIT 0` is rejected
// everywhere with "page size "0" must be greater than 0 in limit clause".
showTail
    : (STARTS (WITH STRING_LITERAL?)?)? (ROOT ONLY)? (LIMIT INTEGER_LITERAL? (FROM STRING_LITERAL)?)? (WITH (PRIVILEGES (showPrivilege (COMMA showPrivilege)*)?)?)?
    ;

// A privilege word in SHOW ... WITH PRIVILEGES — most are keyword tokens elsewhere in the grammar.
// A privilege named in WITH PRIVILEGES is one the account KNOWS, spelled the same way a GRANT spells
// it: a lead word from the closed set and whatever words its name carries after it. So CREATE TABLE
// reads as one privilege, while BOGUS and a QUOTED "USAGE" are syntax errors at the word — this used
// to take any identifier, which let a listing filter on a privilege that does not exist.
showPrivilege
    : privilegeLead privilegeWord*
    | ALL
    ;

describeStatement
    // The relation kinds take an IDENTIFIER() name as well as a written one (live-verified).
    : (DESCRIBE | DESC) TABLE (objectName | openedValueReference) objectSignature? describeProperty* SEMI?
    | (DESCRIBE | DESC) VIEW (objectName | openedValueReference) objectSignature? describeProperty* SEMI?
    | (DESCRIBE | DESC) MATERIALIZED VIEW (objectName | openedValueReference) objectSignature? describeProperty* SEMI?
    | (DESCRIBE | DESC) DYNAMIC TABLE (objectName | openedValueReference) objectSignature? describeProperty* SEMI?
    // A schema lists its tables and views and a database its schemas; both take a TYPE and ignore it.
    | (DESCRIBE | DESC) SCHEMA (objectName | openedValueReference) objectSignature? describeProperty* SEMI?
    | (DESCRIBE | DESC) DATABASE (objectName | openedValueReference) objectSignature? describeProperty* SEMI?
    | (DESCRIBE | DESC) STREAM identifier describeProperty* SEMI?
    | (DESCRIBE | DESC) TASK identifier describeProperty* SEMI?
    | (DESCRIBE | DESC) ALERT qualifiedName SEMI?
    | (DESCRIBE | DESC) PIPE identifier describeProperty* SEMI?
    | (DESCRIBE | DESC) SEQUENCE identifier describeProperty* SEMI?
    | (DESCRIBE | DESC) WAREHOUSE identifier describeProperty* SEMI?
    | (DESCRIBE | DESC) COMPUTE POOL identifier describeProperty* SEMI?
    | (DESCRIBE | DESC) EVENT TABLE (objectName | openedValueReference) describeProperty* SEMI?
    | (DESCRIBE | DESC) ICEBERG TABLE (objectName | openedValueReference) describeProperty* SEMI?
    | (DESCRIBE | DESC) integrationKind? INTEGRATION identifier SEMI?
    | (DESCRIBE | DESC) EXTERNAL VOLUME identifier SEMI?
    | (DESCRIBE | DESC) CORTEX SEARCH SERVICE qualifiedName describeProperty* SEMI?
    | (DESCRIBE | DESC) STAGE qualifiedName describeProperty* SEMI?
    | (DESCRIBE | DESC) TAG identifier describeProperty* SEMI?
    | (DESCRIBE | DESC) FUNCTION qualifiedName (LPAREN dataTypeList? RPAREN)? describeProperty* SEMI?
    | (DESCRIBE | DESC) PROCEDURE qualifiedName (LPAREN dataTypeList? RPAREN)? describeProperty* SEMI?
    | (DESCRIBE | DESC) USER identifier describeProperty* SEMI?
    | (DESCRIBE | DESC) MASKING POLICY qualifiedName describeProperty* SEMI?
    | (DESCRIBE | DESC) ROW ACCESS POLICY qualifiedName describeProperty* SEMI?
    | (DESCRIBE | DESC) PROJECTION POLICY qualifiedName describeProperty* SEMI?
    | (DESCRIBE | DESC) AGGREGATION POLICY qualifiedName describeProperty* SEMI?
    | (DESCRIBE | DESC) JOIN POLICY qualifiedName describeProperty* SEMI?
    | (DESCRIBE | DESC) SEARCH OPTIMIZATION ON qualifiedName describeProperty* SEMI?
    | (DESCRIBE | DESC) FILE FORMAT qualifiedName describeProperty* SEMI?
    | (DESCRIBE | DESC) RESULT (STRING_LITERAL | identifier LPAREN RPAREN) SEMI?
    // An account parses and is never found: Frostlake models no account objects.
    | (DESCRIBE | DESC) ACCOUNT objectName describeProperty* SEMI?
    // No bare `DESCRIBE <name>` alternative: Snowflake requires the object type (live-verified,
    // `DESCRIBE t` is a syntax error there, at the end of input). A plain word standing where the type
    // stands reads as a type the account does not describe: `DESC t1 x` is refused as the unsupported
    // feature 'DESCRIBE T1', and anything else after DESC as the DESC itself (see SyntaxErrorListener). A
    // TYPE property after it parses only so that it is refused where live refuses it, at the TYPE.
    | (DESCRIBE | DESC) NOTEBOOK qualifiedName SEMI?
    | (DESCRIBE | DESC) STREAMLIT qualifiedName SEMI?
    | (DESCRIBE | DESC) IDENTIFIER qualifiedName describeTypeProperty? SEMI?
    ;

// DESCRIBE's TYPE property: STAGE or COLUMNS, written bare, quoted or as a string; the last one written wins.
describeTypeProperty
    : TYPE EQ (STAGE | COLUMNS | identifier | STRING_LITERAL | INTEGER_LITERAL | FLOAT_LITERAL)
    ;

// A property written after a DESCRIBE's object: its TYPE, or any other name with a value, which every kind
// parses and refuses as an invalid parameter (live-verified). The value is a single term, a negative number
// or a parenthesized term included: an operator or a call after it is a syntax error. A name without its
// value parses too, so that the token after it is refused where live refuses it (DescribeProperties).
describeProperty
    : describeTypeProperty
    | describeParameter
    ;

describeParameter
    : (identifier | LIMIT) (EQ describeParameterValue)?
    ;

describeParameterValue
    : MINUS? (INTEGER_LITERAL | FLOAT_LITERAL)
    | STRING_LITERAL
    | TRUE
    | FALSE
    | NULL
    | SESSION_VAR_REF
    | qualifiedName
    | LPAREN describeParameterValue RPAREN
    ;

// Procedural Language Statements
proceduralStatement
    // There is no DECLARE-as-a-statement alternative: in Snowflake a DECLARE only ever opens a block's
    // declaration section, so it must be followed by BEGIN (live-verified — `BEGIN … DECLARE c CURSOR
    // FOR …; OPEN c; … END` is a syntax error, while the nested `DECLARE b INT; BEGIN … END;` form
    // parses because that IS a block). beginEndBlock therefore carries every legal DECLARE.
    : beginEndBlock
    | letStatement
    | assignmentStatement
    | setStatement
    | ifStatement
    | caseStatement
    | loopStatement
    | whileStatement
    | repeatStatement
    | forStatement
    | returnStatement
    | breakStatement
    | continueStatement
    | raiseStatement
    | callStatement
    | asyncStatement
    | awaitStatement
    | executeImmediateStatement
    | openStatement
    | fetchStatement
    | closeStatement
    | selectIntoStatement
    | nullStatement
    ;

declarationItem
    : identifier (EXCEPTION (LPAREN MINUS? INTEGER_LITERAL COMMA STRING_LITERAL RPAREN)? SEMI?
                 | CURSOR FOR cursorSource SEMI?
                 | RESULTSET ((DEFAULT | COLON_EQ) LPAREN resultSetSource RPAREN)? SEMI?
                 | dataTypeName typeParameters? ((DEFAULT | COLON_EQ) booleanExpr)? SEMI?)
    ;

// A DECLARE-section item with the type omitted — Snowflake infers it from the initializer
// (e.g. `cid1 := UUID_STRING();`, `skey1 := 0;`). Kept OUT of `declarationItem` because `x := expr` is
// syntactically an assignment; it is only valid in the pre-BEGIN `declareSection`, which `BEGIN` ends.
untypedDeclarationItem
    : identifier (DEFAULT | COLON_EQ) booleanExpr SEMI?
    ;

// A cursor's source is either a SELECT query or the name of a RESULTSET variable
// (Snowflake: `c CURSOR FOR res;` where `res RESULTSET DEFAULT (…)`).
cursorSource
    : selectStatement
    | identifier
    ;

cursorDeclaration
    : identifier CURSOR FOR selectStatement SEMI?
    ;

variableDeclaration
    : identifier dataTypeName typeParameters? ((DEFAULT | COLON_EQ) booleanExpr)? SEMI?
    ;

letStatement
    : LET identifier CURSOR FOR cursorSource SEMI?
    | LET identifier RESULTSET ((COLON_EQ | DEFAULT) LPAREN resultSetSource RPAREN)? SEMI?
    | LET identifier dataTypeName typeParameters? (COLON_EQ | DEFAULT) booleanExpr SEMI?
    | LET identifier (COLON_EQ | DEFAULT) booleanExpr SEMI?
    ;

assignmentStatement
    : identifier COLON_EQ booleanExpr SEMI?
    | identifier COLON_EQ LPAREN callStatement RPAREN SEMI?
    | identifier COLON_EQ LPAREN executeImmediateStatement RPAREN SEMI?   // rs := (EXECUTE IMMEDIATE :stmt) — documented Snowflake RESULTSET form
    | identifier COLON_EQ LPAREN resultSetBlock RPAREN SEMI?              // rs := (BEGIN … END) — an anonymous block's answer
    | identifier COLON_EQ LPAREN resultSetStatement RPAREN SEMI?          // rs := (SHOW …), (INSERT …), … — the RESULTSET form of any other statement
    ;

// What a RESULTSET is filled from, where it is declared or assigned: a statement the account runs as SQL, whose
// answer the RESULTSET then holds — a query's rows, a DML statement's counts, a DDL statement's status, a SHOW or
// DESCRIBE listing, a CALL's result, an anonymous block's answer — and never a scripting statement or a bare VALUES
// list (live-verified).
resultSetSource
    : selectStatement
    | executeImmediateStatement
    | callStatement
    | resultSetBlock
    | resultSetStatement
    | BEGIN                 // (BEGIN) alone starts a transaction, as BEGIN; does (live-verified)
    ;

// An anonymous block a RESULTSET is filled from. It compiles and runs when the RESULTSET is filled, as an EXECUTE
// IMMEDIATE's text does, and takes no semicolon of its own before the closing parenthesis (live-verified: a DECLARE
// section and an EXCEPTION section are read, and (BEGIN RETURN 1; END;) is a syntax error at the ';').
resultSetBlock
    : declareSection? BEGIN statementList exceptionSection? END
    ;

// The statements a RESULTSET takes besides a query, a CALL and an EXECUTE IMMEDIATE, in the order the statement
// rule tries them.
resultSetStatement
    : ddlStatement
    | dmlStatement
    | explainStatement
    | transactionStatement
    | listStatement
    | getStatement
    | putStatement
    | removeStatement
    | sessionSetStatement
    | sessionUnsetStatement
    | securityObjectListing
    | showStatement
    | showClassStatement
    | describeStatement
    | securityStatement
    | taskStatement
    | accessControlStatement
    | containerServicesStatement
    ;

setStatement
    : SET identifier (EQ | COLON_EQ) expression SEMI?
    ;

// Top-level session SET/UNSET (outside procedural blocks)
// SET var = expr
// SET (var1, var2, ...) = (expr1, expr2, ...)
sessionSetStatement
    : SET identifier EQ booleanExpr SEMI?
    | SET LPAREN identifierList RPAREN EQ LPAREN booleanExprList RPAREN SEMI?
    ;

// UNSET var  |  UNSET (var1, var2, ...)
sessionUnsetStatement
    : UNSET identifier SEMI?
    | UNSET LPAREN identifierList RPAREN SEMI?
    ;

openStatement
    : OPEN identifier (USING LPAREN expressionList RPAREN)? SEMI?
    ;

fetchStatement
    : FETCH identifier INTO identifierList SEMI?
    ;

closeStatement
    : CLOSE identifier SEMI?
    ;

// The IF / ELSEIF condition is PARENTHESIZED in Snowflake (live-verified: `IF 1 = 1 THEN` is a
// syntax error, `IF (1 = 1) THEN` runs). CASE is deliberately not tightened the same way — there
// both `CASE (n)` and `CASE n` are accepted live.
ifStatement
    : IF LPAREN booleanExpr RPAREN THEN statementList
      (ELSEIF LPAREN booleanExpr RPAREN THEN statementList)*
      (ELSE statementList)?
      END IF SEMI?
    ;

caseStatement
    : CASE booleanExpr?
      (WHEN booleanExpr THEN statementList)+
      (ELSE statementList)?
      END CASE? SEMI?
    ;

// A loop label is declared by the TRAILING label — `END LOOP my_loop` / `END FOR sum_loop` /
// `END WHILE w` — and referenced by `BREAK <label>` / `CONTINUE <label>` (live-verified: a BREAK naming
// no trailing label fails SEMANTICALLY with "Label 'MY_LOOP' not found", which is what proves the
// syntax is real). The LEADING `my_loop:` declaration is NOT Snowflake — it is a syntax error at the
// ':' — so there is deliberately no `loopLabel` rule.
// A loop LABEL is written on the END, and may be a join keyword: the Scripting doc's nested example
// closes with `END LOOP INNER;` / `END LOOP OUTER;`. INNER is admitted HERE rather than in the general
// `identifier` rule, because live accepts `CREATE TABLE inner` yet refuses a bare `FROM inner` — see
// KeywordColumnNamesTest.
loopStatement
    : LOOP statementList END LOOP loopLabel? SEMI?
    ;

// END and BEGIN are no labels (live-verified: `END LOOP END;` is refused at that END, and a loop run into a BEGIN
// block without its semicolon at the BEGIN).
loopLabel
    : {_input.LT(1).getType() != END && _input.LT(1).getType() != BEGIN}? identifier
    | INNER
    ;

// WHILE opens its body with DO, closed by END WHILE, or with LOOP, closed by END LOOP; neither closes the other.
whileStatement
    : WHILE LPAREN booleanExpr RPAREN (DO statementList END WHILE | LOOP statementList END LOOP) loopLabel? SEMI?
    ;

forStatement
    : FOR identifier IN REVERSE? expression TO expression DO statementList END FOR loopLabel? SEMI?   // integer range
    | FOR identifier IN expression DO statementList END FOR loopLabel? SEMI?                          // cursor / list
    ;

// UNTIL takes a PARENTHESIZED condition, like IF and WHILE (live-verified: `UNTIL i >= 3` is a
// syntax error, `UNTIL (i >= 3)` runs).
repeatStatement
    : REPEAT statementList UNTIL LPAREN booleanExpr RPAREN END REPEAT loopLabel? SEMI?
    ;

// RETURN TABLE takes a RESULTSET VARIABLE, never a direct query — live refuses
// `RETURN TABLE(SELECT …)` as a syntax error at the SELECT (live-verified), so there is
// deliberately no selectStatement alternative here.
returnStatement
    : RETURN TABLE LPAREN expression RPAREN SEMI?
    | RETURN booleanExpr? SEMI?
    ;

breakStatement
    : BREAK loopLabel? SEMI?
    ;

continueStatement
    : CONTINUE loopLabel? SEMI?
    ;

raiseStatement
    : RAISE (identifier STRING_LITERAL?)? SEMI?
    ;

// A CALL names a stored procedure or one of the account's SYSTEM$ functions, which answers one row in a column
// named after it (live-verified: CALL SYSTEM$WAIT(0) answers 'waited 0 seconds').
callStatement
    : CALL qualifiedName LPAREN callArguments? RPAREN SEMI?
    | CALL systemFunctionName LPAREN callArguments? RPAREN SEMI?
    ;

systemFunctionName
    : SYSTEM_FUNC
    | SYSTEM_STREAM_HAS_DATA
    | SYSTEM_USER_TASK_CANCEL
    ;

callArguments
    : callArgument (COMMA callArgument)*
    ;

callArgument
    : namedArgument   // name => value
    | expression      // positional
    ;

// USING binds the text's placeholders from VARIABLES: each argument is a name, never a literal, a session variable
// or an expression — USING (5) is a syntax error at the 5, USING ($v) at the $v, USING (x + 1) at the '+', USING (:x)
// at the ':' (live-verified).
executeImmediateStatement
    : EXECUTE IMMEDIATE expression (USING LPAREN usingArgument (COMMA usingArgument)* RPAREN)? SEMI?
    | EXECUTE IMMEDIATE FROM (stageRef | STRING_LITERAL) SEMI?
    ;

// TRUE and FALSE read as names here, and are refused as names no variable declares.
usingArgument
    : identifier
    | TRUE
    | FALSE
    ;

beginEndBlock
    : declareSection? BEGIN statementList exceptionSection? END SEMI?
    ;

// A declaration is terminated by its semicolon exactly like a statement (live-verified: an item
// without one is a syntax error at the token that follows it, BEGIN included). Stray semicolons
// AFTER a declaration are tolerated, as Snowflake tolerates them — real deployment scripts end
// their DECLARE section with a lone `;` line before BEGIN — but the section may not OPEN with one.
declareSection
    : DECLARE ((declarationItem | untypedDeclarationItem) {_input.LT(-1).getType() == SEMI}? SEMI*)+
    ;

exceptionSection
    : EXCEPTION exceptionHandler+
    ;

exceptionHandler
    : WHEN exceptionCondition CONTINUE? THEN statementList
    ;

exceptionCondition
    : OTHER
    | identifier
    ;

// SELECT expr1, expr2 INTO var1, var2 FROM table [WHERE ...]
// Targets may be plain identifiers or bind variables (:varname)
selectIntoStatement
    // The filtering and grouping clauses stand WITHOUT a FROM, as they do in a plain SELECT: live runs
    // `SELECT 1 INTO :x WHERE 1 = 1` in a block, and GROUP BY, HAVING and QUALIFY the same way. ALL and TOP n stand
    // where they stand in a query (live-verified: SELECT DISTINCT TOP 1 a INTO :x and SELECT ALL a INTO :x assign).
    // A hierarchical query is no statement of this shape: with its INTO clause it reads as a query, which the
    // account runs and then refuses (see IntoClausePlacement).
    : withClause? SELECT (DISTINCT | ALL)? topClause? selectList INTO intoTargetList (FROM tableExpression)? whereClause? groupByClause? havingClause? qualifyClause? orderByClause? (limitClause | fetchClause)? SEMI?
    ;

intoTargetList
    : intoTarget (COMMA intoTarget)*
    ;

intoTarget
    : COLON identifier   // :varname  (bind variable)
    | identifier         // plain variable name
    ;

nullStatement
    : NULL SEMI   // the separator is mandatory — BEGIN NULL END is a Snowflake syntax error (live-verified)
    // …except when nothing follows at all. A bare `NULL` submitted on its own is refused live for
    // being a scripting statement outside a block, at the WORD's own line and column — a refusal it
    // can only reach by parsing first. Requiring the semicolon everywhere left the parser looking for
    // more input and naming <EOF> instead. The lookahead keeps `BEGIN NULL END` refused: there the
    // next token is END, not the end of input.
    | {_input.LA(2) == Token.EOF}? NULL
    ;

// Snowflake Scripting asynchronous execution. The engine is single-threaded, so ASYNC runs its wrapped
// statement synchronously and AWAIT is a no-op (results are identical, only concurrency is lost).
asyncStatement
    : ASYNC LPAREN (dmlStatement | callStatement) RPAREN SEMI?
    ;

awaitStatement
    : AWAIT (ALL | identifier) SEMI?
    ;

statementList
    // Every statement inside a block is TERMINATED by its semicolon: live refuses a block whose
    // statement lacks one wherever it sits — mid-list, the last one before END, a nested block's
    // END, END IF, END LOOP, END CASE, an EXCEPTION handler's body (live-verified). The statement
    // rules each carry an optional trailing SEMI and consume it greedily, so the terminator is
    // enforced here: once a statement is parsed, the token it just consumed must have been that
    // semicolon. Extra semicolons AFTER a statement are tolerated (`RETURN 1;;`, a lone `;` line
    // between two statements or before END), but a list may not START with one — live refuses
    // `THEN ; RETURN 1;` at the ';' itself.
    : (statement {_input.LT(-1).getType() == SEMI}? SEMI*)+
    ;

// Boolean tier wrapping the value/predicate `expression` below. Currently a pass-through; the
// final migration step gives it NOT/AND/OR alternatives and removes those from `expression`
// (so BETWEEN/LIKE operands, which reference `expression`, can no longer absorb a trailing
// AND/OR). Boolean-context grammar rules reference `booleanExpr`; value operands reference
// `expression`.
booleanExpr
    : NOT booleanExpr                # NotExpr
    | booleanExpr AND booleanExpr    # AndExpr
    | booleanExpr OR booleanExpr     # OrExpr
    | expression                     # ValueExpr
    ;

expression
    : literal                                                    # LiteralExpr
    | SESSION_VAR_REF                                            # SessionVarExpr
    // A stage written bare, GET_PRESIGNED_URL(@st, 'f.csv'), which the account reads as the string '@st'.
    // Only a stage function's first argument and a CALL's argument take one: StageArgumentSyntax refuses
    // it anywhere else, once the text has parsed and as its statement compiles.
    | stageRef                                                   # StageReferenceExpr
    | COLON (identifier | INTEGER_LITERAL)                       # BindVarExpr
    | QUESTION                                                   # PositionalBindExpr
    // A column or a function named by an IDENTIFIER() reference that is not whole: never parses, see
    // openedIdentifierReference. A call's own argument list after it is read whole with it.
    | openedIdentifierReference (LPAREN openedIdentifierContent RPAREN)?  # OpenedIdentifierExpr
    | SYSTEM_STREAM_HAS_DATA LPAREN expressionList? RPAREN       # SystemStreamHasDataExpr
    | SYSTEM_USER_TASK_CANCEL LPAREN expression RPAREN           # SystemUserTaskCancelExpr
    | SYSTEM_FUNC LPAREN booleanExprList? RPAREN                 # SystemFuncExpr
    | (CURRENT_TIMESTAMP | LOCALTIMESTAMP)                       # CurrentTimestampExpr
    | CURRENT_DATE                                               # CurrentDateExpr
    | (CURRENT_TIME | LOCALTIME)                                 # CurrentTimeExpr
    | CURRENT_USER                                               # CurrentUserExpr
    | qualifiedName LPAREN PLUS RPAREN                           # OuterJoinColumnExpr
    // Hierarchical-query pseudo-columns. Both take a bare (optionally qualified) column reference —
    // live-verified: `CONNECT_BY_ROOT (sal + 1)` and `CONNECT_BY_ROOT nm || 'x'` are rejected by
    // Snowflake with "Unsupported feature". They precede QualifiedNameExpr so the keyword wins over the
    // same word used as a plain column name (Snowflake resolves it the same way).
    | PRIOR qualifiedName                                        # PriorExpr
    | CONNECT_BY_ROOT qualifiedName                              # ConnectByRootExpr
    // A column reference must not BEGIN with TRY_CAST: in expression position that token always
    // starts the cast construct, so live refuses the bare reference with a syntax error at the
    // NEXT token (SELECT/WHERE/GROUP BY/ORDER BY alike, live-verified) — while a QUALIFIED
    // reference (t.try_cast) and every name position stay legal. With this alternative gated,
    // TryCastExpr is the lone viable parse and its '(' requirement reports at the right token.
    | {expressionLeadingName()}? qualifiedName                   # QualifiedNameExpr
    | jsonObjectLiteral                                          # JsonObjectExpr
    | jsonArrayLiteral                                           # JsonArrayExpr
    | caseExpression                                             # CaseExpr
    // Snowflake interval literals (live-verified): the quoted-string form `INTERVAL '1 day, 2 hours'`
    // (plural units and comma-separated parts INSIDE the string, bare numbers default to seconds), and
    // `INTERVAL '<text>' <qualifier>`, the text read by the qualifier's fields. An unquoted amount
    // (INTERVAL 10 DAY) is a syntax error there. A plural field after the string is a unit as well —
    // `INTERVAL '10' DAYS` is ten days — while WEEK, QUARTER and the sub-second words are not fields
    // at all: after the string they are an alias.
    | INTERVAL STRING_LITERAL intervalLiteralQualifier           # IntervalExpr
    | INTERVAL STRING_LITERAL                                    # IntervalStringExpr
    | dateTimeLiteralType STRING_LITERAL                         # TypedDateTimeLiteralExpr
    // A plain word before a string is a typed literal of a type the account does not know, refused by its
    // text while the statement compiles: `val 'x'`. A type keyword (`VARCHAR 'x'`) or a quoted name
    // (`"val" 'x'`) before a string stays a syntax error at the string, as it is live.
    | IDENTIFIER STRING_LITERAL                                  # UnknownTypedLiteralExpr
    | CAST LPAREN expression AS dataTypeName typeParameters? ((RENAME | ADD) FIELDS)? RPAREN  # CastExpr
    | TRY_CAST LPAREN expression AS dataTypeName typeParameters? ((RENAME | ADD) FIELDS)? RPAREN  # TryCastExpr
    // The specification parses as any expression so that a computed one is refused in live's own
    // words ("Argument number 2 for function 'COLLATE' needs to be a string literal.") rather than as
    // a syntax error; the builder takes a written literal only.
    | COLLATE LPAREN expression COMMA expression RPAREN                          # CollateFuncExpr
    // ANSI EXTRACT(<part> FROM <expr>). The alternative stays viable for ANY function name on
    // purpose — that is what carries the parse as far as the FROM, so the refusal for every other
    // name can be reported THERE, which is where live reports it. The part is an IDENTIFIER only:
    // EXTRACT('YEAR' FROM d) is refused live as well.
    | functionName LPAREN identifier FROM expression RPAREN                    # ExtractFromExpr
    // The ANSI SUBSTRING(<x> FROM <a> FOR <b>) spelling, which NO name accepts — including SUBSTRING.
    // It is here only so the refusal lands where live puts it, on the FROM; without the alternative
    // the parse dies on the FOR instead. Nothing it matches is ever accepted, so this widens no
    // syntax. The FOR tail is optional and repeats: it has to cover the FROM form whose operand
    // is not an identifier — EXTRACT('YEAR' FROM d) and TRIM(' ' FROM v) are refused there too.
    | functionName LPAREN expression FROM expression (FOR expression)* RPAREN  # AnsiSubstringExpr
    // A star ARGUMENT may be qualified (COUNT(t.*)) and takes EXCLUDE and ILIKE only — RENAME and
    // REPLACE are a select item's modifiers, refused here as live refuses them.
    | functionName LPAREN DISTINCT? (starQualifiedName DOT)? STAR starArgumentModifier* RPAREN   # FunctionCallStarExpr
    // A named call takes the quantifier, the null treatment and the WITHIN GROUP of the positional one, so that
    // each is judged for the function it names, as the positional call's are (see NamedCallRewrite).
    | functionName LPAREN (DISTINCT | ALL)? expression (COMMA expression)* (COMMA namedArgument)+
          ({insideNullTreatmentAllowed()}? nullHandling)? RPAREN withinGroupClause? nullHandling? overClause?  # FunctionCallMixedArgsExpr
    | functionName LPAREN (DISTINCT | ALL)? namedArgumentList
          ({insideNullTreatmentAllowed()}? nullHandling)? RPAREN withinGroupClause? nullHandling? overClause?  # FunctionCallNamedArgsExpr
    // ALL is the quantifier every aggregate takes as a no-op, refused for a scalar by its sentence.
    | functionName LPAREN (DISTINCT | ALL)? functionArgList?
          ({insideNullTreatmentAllowed()}? nullHandling)? RPAREN withinGroupClause?
          (FROM (FIRST | LAST) nullHandling? overClause | nullHandling? overClause?)     # FunctionCallExpr
    // An IDENTIFIER followed by a STRING inside a call. Live reads that pair as a TYPED LITERAL — the
    // DATE '2020-01-01' shape with any word in front — so it consumes both and reports whatever comes
    // next. That is why TRIM(BOTH ' ' FROM v) is refused on its FROM there and on the string here, and
    // it is not a TRIM rule at all: UPPER(FOO ' ') behaves identically.
    //
    // ★ IT SITS AFTER THE ORDINARY CALL ON PURPOSE. Written before it, this alternative also matched
    // HASH(DATE '1970-01-02') — a REAL typed literal, whose type name is an identifier like any other —
    // and refused it. Behind the ordinary call, a known type name parses as the argument it is and only
    // an unrecognised word reaches here. Nothing this matches is ever accepted.
    | functionName LPAREN identifier STRING_LITERAL RPAREN                     # TypedLiteralArgExpr
    // The ANSI POSITION(<needle> IN <haystack>) form. It sits AFTER the ordinary call on purpose, so a
    // legitimate membership test inside an argument — UPPER(a IN (1, 2)) — parses as the call it is;
    // this alternative only gets its turn once that parse has failed. It also matches for ANY function
    // name rather than being gated on POSITION: a predicate here eliminates a SYNTACTICALLY VIABLE
    // alternative, which leaves the decision with nothing to pick and anchors the refusal on the
    // function NAME, where live anchors on the operand after IN. The name is checked after the parse
    // instead, which is where live's sentence can be reproduced exactly.
    | functionName LPAREN expression IN expression RPAREN                      # PositionInExpr
    | EXISTS LPAREN selectStatement RPAREN                       # ExistsExpr
    | LPAREN selectStatement RPAREN                              # ScalarSubqueryExpr
    | expression COLON variantPathKey ((DOT | COLON) variantPathKey)*  # ObjectAccessExpr
    | expression LBRACKET expression RBRACKET                    # ArrayAccessExpr
    | expression DOT variantPathKey                              # FieldAccessExpr
    // The (+) outer-join marker after anything but a column reads, and is refused while the query compiles.
    | expression LPAREN PLUS RPAREN                              # OuterJoinOperandExpr
    | expression DOUBLE_COLON dataTypeName typeParameters?       # CastExpr2
    // COLLATE's infix spelling, `<expr> COLLATE '<spec>'`, the same call as COLLATE(expr, 'spec'). It
    // binds as tightly as `::`: `'x' COLLATE 'en-ci' || ''` joins the collated 'x' to '', and
    // `'a' = 'A' COLLATE 'en-ci'` collates the right side alone. The spec is a literal only, so
    // `COLLATE ('en-ci')` stays the syntax error at the '(' it is live — COLLATE then reads as an alias.
    | expression COLLATE (STRING_LITERAL | DOLLAR_QUOTED_STRING) # CollateExpr
    // AFTER the path and cast alternatives, which is what makes them bind TIGHTER — in a left-recursive
    // rule the earlier alternative wins. Written first, `-src:score` parsed as `(-src)` with the path
    // applied to the negated object, so the negate saw the WHOLE OBJECT and refused an expression live
    // answers with -7.5. Live binds `:`, `[]`, `.` and `::` tighter than the sign: `-src:score::INT` is
    // -8, the cast reached before the minus. It stays AHEAD of ||, * and + so `-2 + 3` is still 1.
    | op=(PLUS | MINUS) expression                               # UnaryExpr
    | expression PIPE_PIPE expression                            # ConcatExpr
    | expression op=(STAR | SLASH | PERCENT) expression          # MultiplicativeExpr
    | expression op=(PLUS | MINUS) expression                    # AdditiveExpr
    | expression IS NOT? NULL                                    # IsNullExpr
    | expression IS NOT? DISTINCT FROM expression                # IsDistinctExpr
    // Snowflake has LIKE ANY, LIKE ALL and ILIKE ANY only: NOT LIKE ANY/ALL and ILIKE ALL are
    // compile errors there (live-verified), so the grammar deliberately omits them.
    | expression (LIKE q=(ANY | ALL) | ILIKE q=ANY) LPAREN patterns+=expression (COMMA patterns+=expression)* RPAREN (ESCAPE esc=escapeOperand)? # LikeAnyAllExpr
    | expression NOT? (LIKE | ILIKE) expression (ESCAPE escapeOperand)? # LikeExpr
    | expression NOT? (RLIKE | REGEXP) expression                # RlikeExpr
    | expression NOT? BETWEEN expression AND expression          # BetweenExpr
    | expression NOT? IN LPAREN selectStatement RPAREN           # InSubqueryExpr
    | expression NOT? IN LPAREN expressionList RPAREN            # InListExpr
    | LPAREN expressionList RPAREN NOT? IN LPAREN selectStatement RPAREN  # TupleInSubqueryExpr
    | LPAREN expressionList RPAREN NOT? IN LPAREN tupleRow (COMMA tupleRow)* RPAREN  # TupleInListExpr
    | LPAREN expressionList RPAREN NOT? IN LPAREN expressionList RPAREN   # TupleInFlatListExpr
    // Two ROW constructors compared: element by element, with SQL's three-valued logic, and
    // lexicographically for the ordering operators. It sits above the scalar comparison because both
    // sides are parenthesised lists, which a scalar comparison would read as two parenthesised
    // expressions and then fail on the comma.
    | LPAREN left=expressionList RPAREN op=(EQ | NEQ | LT | LTE | GT | GTE) LPAREN right=expressionList RPAREN  # RowComparisonExpr
    // A row constructor opposite a scalar parses, and is refused by TYPE while the statement compiles. The row
    // holds two elements at least, so a parenthesised scalar stays the ordinary comparison below.
    | LPAREN rowLeft+=expression (COMMA rowLeft+=expression)+ RPAREN op=(EQ | NEQ | LT | LTE | GT | GTE) scalarRight=expression  # RowScalarComparisonExpr
    | expression op=(EQ | NEQ | LT | LTE | GT | GTE) expression  # ComparisonExpr
    | expression op=(EQ | NEQ | LT | LTE | GT | GTE) LPAREN rowRight+=expression (COMMA rowRight+=expression)+ RPAREN  # ScalarRowComparisonExpr
    | expression op=(EQ | NEQ | LT | LTE | GT | GTE) quantifier LPAREN selectStatement RPAREN # QuantifiedComparisonExpr
    | LPAREN booleanExpr RPAREN                                  # ParenExpr
    ;

caseExpression
    : CASE expression whenClause+ (ELSE booleanExpr)? END         # SimpleCaseExpr
    | CASE whenClause+ (ELSE booleanExpr)? END                    # SearchedCaseExpr
    ;

// THEN / ELSE results are booleanExpr (a superset of expression) so a branch may yield a bare boolean —
// CASE WHEN c THEN TRUE ELSE a != b OR c != d END — without extra parentheses.
whenClause
    : WHEN booleanExpr THEN booleanExpr
    ;

quantifier
    : ANY
    | SOME
    | ALL
    ;

intervalUnit
    : YEAR | YEARS
    | MONTH | MONTHS
    | DAY | DAYS
    | HOUR | HOURS
    | MINUTE | MINUTES
    | SECOND | SECONDS
    ;

// The qualifier of a unit-suffixed interval literal: a field with an optional leading precision (a
// SECOND also its fractional one), optionally TO a finer field, a trailing SECOND with its fractional
// precision. A plural field is a unit on its own but never opens a range, and a TO that no field
// follows is not read either: `DAYS TO HOUR`, `DAY TO HOURS` and `DAY TO )` are all refused at the TO
// (live-verified). Which pairs and precisions make a type is judged once the qualifier is read, in
// the account's own words.
intervalLiteralQualifier
    : intervalField intervalLeadingPrecision? ({intervalRangeFollows()}? TO intervalField intervalTrailingPrecision?)?
    | intervalPluralField intervalLeadingPrecision?
    ;

intervalField
    : YEAR | MONTH | DAY | HOUR | MINUTE | SECOND
    ;

intervalPluralField
    : YEARS | MONTHS | DAYS | HOURS | MINUTES | SECONDS
    ;

intervalLeadingPrecision
    : LPAREN INTEGER_LITERAL (COMMA INTEGER_LITERAL)? RPAREN
    ;

intervalTrailingPrecision
    : LPAREN INTEGER_LITERAL RPAREN
    ;

jsonObjectLiteral
    : LBRACE jsonObjectEntry (COMMA jsonObjectEntry)* RBRACE
    | LBRACE RBRACE
    ;

jsonObjectEntry
    : jsonKeyValuePair
    ;

jsonKeyValuePair
    : STRING_LITERAL COLON expression
    ;

jsonArrayLiteral
    : LBRACKET arrayElement (COMMA arrayElement)* RBRACKET
    | LBRACKET RBRACKET
    ;

arrayElement
    : spreadArgument
    | expression
    ;

// The `**` spread splices the elements of a CONSTANT array into the enclosing argument list. It is
// argument SPLATTING, not an array feature — `[…]` is sugar for ARRAY_CONSTRUCT, which is why the same
// rule serves both (live-verified: `ARRAY_APPEND(** [[1,2], 3])` splices TWO arguments, and
// `GREATEST(** [1,5,3])` → 5). The operand must be an array LITERAL or an ARRAY_CONSTRUCT call: a
// runtime expression is rejected even over literals — `[** PARSE_JSON('[1,2]')]` fails live. There is
// deliberately no spread in object literals (`{'a':1, ** {'b':2}}` is a syntax error in Snowflake) and
// no bare `SELECT **`.
spreadArgument
    : DOUBLE_STAR expression
    ;

expressionList
    : expression (COMMA expression)*
    ;

// A SYSTEM$ call's arguments: whatever a select item can be, NOT, AND and OR included, so
// SYSTEM$TYPEOF(NOT TRUE) and SYSTEM$TYPEOF(TRUE AND FALSE) answer BOOLEAN as they do on the account.
booleanExprList
    : booleanExpr (COMMA booleanExpr)*
    ;

// One parenthesized row of a tuple-IN list: (a, b) IN ((1, 2), (3, 4)). The flat spelling
// (a, b) IN (1, 2) parses via TupleInFlatListExpr but is a compile-time TYPE error (ROW vs
// scalars), matching Snowflake.
tupleRow
    : LPAREN expressionList RPAREN
    ;

// Function-call argument list. Uses booleanExpr (which is a superset of expression) so a bare boolean —
// COUNT_IF(a OR b), IFF(x AND y, …) — is accepted as an argument without extra parentheses, while the
// value-context `expressionList` still keeps AND/OR out (so BETWEEN/LIKE operands don't absorb them).
functionArgList
    : functionArg (COMMA functionArg)*
    ;

// A function argument is either a lambda (for higher-order functions like TRANSFORM/FILTER/REDUCE) or a
// normal boolean expression.
functionArg
    : lambdaFunction
    | exprTuple
    | spreadArgument   // ** [a, b] — splices a constant array's elements as arguments
    | booleanExpr
    | (starQualifiedName DOT)? STAR starArgumentModifier*   // star argument beside others, or under OVER
    | {bareSubqueryArgument()}? selectStatement   // ABS(SELECT -1): a subquery as the call's whole argument list
    ;

// The modifiers a star ARGUMENT takes: the column filters only. A select item's star also takes
// RENAME and REPLACE; an argument's does not (live: "unexpected 'RENAME'").
starArgumentModifier
    : ILIKE STRING_LITERAL
    | EXCLUDE (excludedColumn | LPAREN excludedColumn (COMMA excludedColumn)* RPAREN)
    ;

// A parenthesized tuple argument (two or more expressions): SEARCH((play, line), 'dream').
// A single parenthesized expression stays a plain booleanExpr.
exprTuple
    : LPAREN expression COMMA expression (COMMA expression)* RPAREN
    ;

lambdaFunction
    : lambdaParams THIN_ARROW booleanExpr
    ;

lambdaParams
    : lambdaParam
    | LPAREN lambdaParam (COMMA lambdaParam)* RPAREN
    ;

// A lambda parameter is a name with an optional (ignored) data type — e.g. `x`, `a VARIANT`.
lambdaParam
    : identifier (dataTypeName typeParameters?)?
    ;

namedArgumentList
    : namedArgument (COMMA namedArgument)*
    ;

namedArgument
    : identifier ARROW (expression | selectStatement | argumentRow)   // Snowflake allows a bare subquery value: INPUT => SELECT ...
    ;

// Two or more parenthesized values as a named argument's value, which the account reads as one ROW value:
// INFER_SCHEMA's FILES => ('a.csv', 'b.csv') lists its files this way, and every other parameter refuses it.
argumentRow
    : LPAREN expression (COMMA expression)+ RPAREN
    ;

functionName
    : KW_IDENTIFIER_REF LPAREN identifierArgument RPAREN   // IDENTIFIER('fn') / IDENTIFIER($var) as the function name
    // TRY_CAST never heads a function name: TRY_CAST( always begins the cast construct, so the
    // call shapes TRY_CAST(x, 'type') and TRY_CAST(x) are syntax errors at the comma/paren
    // (live-verified), never calls of a registered or user function.
    | {_input.LT(1).getType() != TRY_CAST}? identifier (DOT DOT identifier)? (DOT identifier)*
    | LIKE   // LIKE/ILIKE also have a function-call form: LIKE(subject, pattern), ILIKE(subject, pattern)
    // Reserved words that are nonetheless FUNCTION names — reserved in identifier positions
    // (live-verified), legal as calls: INSERT(base, pos, len, insert), RLIKE(subject, pattern).
    | INSERT
    | RLIKE
    | ILIKE
    ;

qualifiedName
    // The optional trailing TABLE keyword admits an object part literally named "table"
    // (db.table / db.schema."table"-style references) without making TABLE a general identifier.
    // An empty middle part, db..t, names the database's PUBLIC schema, and only the middle part may be
    // left empty (live-verified); ParseTreeText reads it. The parts after it stay inside the name, so
    // db..t.x.y is refused as an over-long name rather than read as a field of db..t.x.
    : nameStartPart (DOT DOT namePart)? (DOT namePart)*
    ;

// INNER, JOIN, LEFT and CROSS are live-legal unquoted NAMES in every name position — columns and
// tables alike (live matrix: CREATE TABLE column defs, bare and qualified references, WHERE,
// GROUP BY, ORDER BY, aggregate arguments, INSERT column lists, UPDATE SET targets, and even
// CREATE TABLE inner). The general identifier rule cannot admit them, because a bare table alias
// would then swallow the join keyword of `FROM a LEFT JOIN b` — so only the NAME positions gain
// them. CASE additionally works only AFTER a dot (`kw.case` reads the column); a bare leading
// CASE is live's syntax error — the CASE expression owns that spot.
nameStartPart
    : identifier
    | INNER | JOIN | LEFT | CROSS
    // CASE, CAST, CONSTRAINT, DEFAULT and WHEN lead a NAME wherever a name is the only thing that can
    // appear: live accepts all five for a table, a view, a schema, a CTE, a RENAME TO target, an
    // INSERT INTO target, a bare FROM reference and a `<name>.*` qualifier (every cell measured).
    // In an EXPRESSION they reach the column resolver instead of the parser, which refuses the
    // reference by name — the same "invalid identifier 'CONSTRAINT'" live gives. Guarding them here
    // with a predicate is NOT the way to sharpen that: a predicate on the expression's use of
    // qualifiedName is hoisted into the whole select-item decision and kills `case.*` with it.
    | CASE | CAST | CONSTRAINT | DEFAULT | WHEN
    ;

// The FROM position keeps the join keywords as KEYWORDS in the LEADING part — live, `FROM inner`
// is "unexpected '<EOF>'" even though CREATE TABLE inner succeeds (both measured) — while a
// qualified trailing part still reads (sch.inner).
// The qualifier of a `<name>.*` is a NAME, not a column reference, so it takes the full name
// vocabulary — live reads `SELECT case.* FROM case`. It cannot share qualifiedName with the
// expression: the guard on that alternative is hoisted into the whole select-item decision and would
// eliminate this one too.
starQualifiedName
    : nameStartPart (DOT DOT namePart)? (DOT namePart)*
    ;

tableQualifiedName
    : {tableNameLeadAllowed()}? nameStartPart (DOT DOT namePart)? (DOT namePart)*
    ;

// An ALIAS after AS takes a WIDER vocabulary than a column reference does. Live accepts every one of
// `SELECT 1 AS case`, `AS cast`, `AS constraint`, `AS default`, `AS when` and the join words `AS cross`,
// `AS inner`, `AS join` — none of which may name a column in an EXPRESSION, because there each of them
// leads something. Measured word by word for the select-item and table-alias positions, which agree
// exactly. It stays a rule of its own rather than widening `identifier`: the expression grammar has to
// go on reading CASE as the start of a CASE expression.
aliasName
    : identifier
    | CASE | CAST | CONSTRAINT | CROSS | DEFAULT | INNER | JOIN | WHEN
    ;

// A name being READ rather than defined: the part after a dot, a column list's entries, an UPDATE
// SET target. Measured word by word against the account, and the vocabulary here is the column
// DEFINITION's plus exactly one word — CONSTRAINT, which live reads as `t.constraint`,
// `INSERT INTO t (constraint)` and `UPDATE t SET constraint = 1` alike, while refusing it as a
// column's own name. Every other word tested behaves identically in both positions.
namePart
    : columnDefName
    // CAST, DEFAULT and WHEN join CASE in columnDefName for the same reason: live DEFINES a column so
    // named but cannot REFERENCE one — `SELECT cast FROM t` is its syntax error, because the
    // expression these words lead owns that position. So they are names only where a name is the only
    // thing possible.
    | CONSTRAINT
    ;

// A column DEFINITION's name. It is namePart MINUS CONSTRAINT: there the word leads a table
// constraint, so live reads `CREATE TABLE t (constraint INT)` as a constraint clause and trips on the
// close paren. Splitting the two rules is what lets the after-dot position gain the word without
// making that statement legal.
columnDefName
    : identifier
    | INNER | JOIN | LEFT | CROSS | CASE
    | CAST | DEFAULT | WHEN
    ;

identifier
    : IDENTIFIER
    | KW_IDENTIFIER  // the literal word "identifier" as a plain name (it lexes as KW_IDENTIFIER now)
    | KW_IDENTIFIER_REF  // IDENTIFIER('a') in an expression names a column through the IDENTIFIER call
    | ACCOUNTS      // Allow ACCOUNTS as identifier
    | ACTION
    | ASOF          // Allow ASOF as identifier (the ASOF JOIN modifier is anchored by the following JOIN);
                    // live-verified: `CREATE TABLE kw (asof INT)` is accepted by Snowflake
    | MATCH_CONDITION   // Allow MATCH_CONDITION as identifier (the ASOF clause is anchored by ASOF JOIN)
    | PRIOR         // Allow PRIOR as identifier (the CONNECT BY operator is anchored by a following name);
                    // live-verified: `CREATE TABLE kw (prior INT)` is accepted, unlike `connect`
    | CONNECT_BY_ROOT   // Allow CONNECT_BY_ROOT as identifier (the pseudo-column needs a following name)
    | AFTER         // Allow AFTER as identifier
    | ALLOW_OVERLAPPING_EXECUTION
    | BEFORE        // Allow BEFORE as identifier
    | CALLER        // Allow CALLER as identifier
    | CLOSE         // Allow CLOSE as identifier (e.g. a qualified fn name like tools.stats.close(); the
                    // CLOSE <cursor> statement is a separate rule, disambiguated by context)
    | COLUMNS       // Allow COLUMNS as identifier (for INFORMATION_SCHEMA views)
    | MODELS        // Allow MODELS as identifier: SHOW MODELS is a listing, and the account still
                    // takes `models` as a column name and as a table name (live-verified)
    | COMMENT       // Allow COMMENT as identifier
    | COPY          // Allow COPY as identifier (table name)
    | CURRENT_DATE  // Allow CURRENT_DATE as identifier (function name)
    | CURRENT_TIME  // Allow CURRENT_TIME as identifier (function name)
    | CURRENT_TIMESTAMP // Allow CURRENT_TIMESTAMP as identifier (function name)
    | LOCALTIMESTAMP
    | LOCALTIME
    | CURRENT_USER      // Allow CURRENT_USER as identifier (function name)
    | DATA          // Allow DATA as identifier
    | DATABASES     // Allow DATABASES as identifier (for INFORMATION_SCHEMA views)
    | CHAR          // Allow CHAR as identifier (function name)
    | DATE          // Allow DATE as identifier
    | DATETIME      // Allow DATETIME as identifier
    | TIME          // Allow TIME as identifier (column names; also the TIME data type)
    | DAY           // Allow DAY as identifier (can be column name)
    | DAYS          // Allow DAYS as identifier (can be column name)
    | DELIMITER     // Allow DELIMITER as identifier (SPLIT_TO_TABLE parameter)
    | DIRECTED      // Allow DIRECTED as identifier (also the no-op DIRECTED join modifier)
    | DO            // Allow DO as identifier (e.g. a table alias `do`); the WHILE/FOR ... DO loop keyword
                    // is positionally anchored in those rules, so this doesn't shadow it
    | DOWNSTREAM    // Allow DOWNSTREAM as identifier
    | DYNAMIC       // Allow DYNAMIC as identifier
    | ECONOMY       // Allow ECONOMY as identifier
    | ENABLE_QUERY_ACCELERATION
    | FILE
    | FIRST         // Allow FIRST as identifier (also ORDER BY ... NULLS FIRST)
    | FLATTEN       // Allow FLATTEN as identifier (table function)
    | BUILTIN       // Allow BUILTIN as identifier (also the SHOW BUILTIN FUNCTIONS modifier)
    | FUNCTIONS     // Allow FUNCTIONS as identifier (INFORMATION_SCHEMA view)
    | STAGES        // Allow STAGES as identifier (INFORMATION_SCHEMA view; SHOW STAGES is anchored by SHOW)
    | PACKAGES      // Allow PACKAGES as identifier (INFORMATION_SCHEMA view; the UDF PACKAGES = (...)
                    // property is anchored by the following EQ)
    | GENERATION    // Allow GENERATION as identifier (also a CREATE WAREHOUSE property)
    | MANAGED       // WITH MANAGED ACCESS on a schema
    | RESOURCE_CONSTRAINT
    | WAIT_FOR_COMPLETION
    | ADAPTIVE
    | TEMPLATE      // Allow TEMPLATE as identifier (CREATE TABLE ... USING TEMPLATE is anchored by USING)
    | COMPUTE       // Allow COMPUTE as identifier (COMPUTE POOL statements are anchored by CREATE/ALTER/DROP/SHOW/DESCRIBE)
    | POOL          // Allow POOL as identifier
    | POOLS         // Allow POOLS as identifier
    | INSTANCE      // Allow INSTANCE as identifier (SHOW COMPUTE POOL INSTANCE FAMILIES is anchored by SHOW)
    | CORTEX        // Allow CORTEX as identifier (CORTEX SEARCH SERVICE statements are anchored by
                    // CREATE/ALTER/DROP/SHOW/DESCRIBE; SNOWFLAKE.CORTEX.<fn>() is a dotted function name)
    | SEARCH        // Allow SEARCH as identifier
    | SERVICE       // Allow SERVICE as identifier
    | SERVICES      // Allow SERVICES as identifier
    | IMAGE         // Allow IMAGE as identifier (the container statements are anchored by their statement words)
    | IMAGES        // Allow IMAGES as identifier
    | REPOSITORY    // Allow REPOSITORY as identifier
    | REPOSITORIES  // Allow REPOSITORIES as identifier
    | ARTIFACT      // Allow ARTIFACT as identifier
    | JOB           // Allow JOB as identifier
    | JOBS          // Allow JOBS as identifier
    | CONTAINERS    // Allow CONTAINERS as identifier
    | INSTANCES     // Allow INSTANCES as identifier
    | ENDPOINTS     // Allow ENDPOINTS as identifier
    | ENDPOINT      // Allow ENDPOINT as identifier (a service function option, anchored by the following EQ)
    | MAX_BATCH_ROWS  // Allow MAX_BATCH_ROWS as identifier (a service function option)
    | SPECIFICATION // Allow SPECIFICATION as identifier (a service source, anchored by FROM)
    | SPECIFICATION_FILE           // Allow SPECIFICATION_FILE as identifier
    | SPECIFICATION_TEMPLATE       // Allow SPECIFICATION_TEMPLATE as identifier
    | SPECIFICATION_TEMPLATE_FILE  // Allow SPECIFICATION_TEMPLATE_FILE as identifier
    | ATTRIBUTES    // Allow ATTRIBUTES as identifier (the CREATE CORTEX SEARCH SERVICE clause is
                    // positionally anchored between the ON column and the options)
    | EMBEDDING_MODEL   // Allow EMBEDDING_MODEL as identifier (property is anchored by the following EQ)
    | FAMILIES      // Allow FAMILIES as identifier
    | MIN_NODES     // Allow MIN_NODES as identifier (compute pool property, anchored by the following EQ)
    | MAX_NODES     // Allow MAX_NODES as identifier (compute pool property)
    | INSTANCE_FAMILY        // Allow INSTANCE_FAMILY as identifier (compute pool property)
    | AUTO_SUSPEND_SECS      // Allow AUTO_SUSPEND_SECS as identifier (compute pool property)
    | PLACEMENT_GROUP        // Allow PLACEMENT_GROUP as identifier (compute pool property)
    | BACKUP_INSTANCE_FAMILIES  // Allow BACKUP_INSTANCE_FAMILIES as identifier (compute pool property)
    | WORK          // Allow WORK as identifier (live-verified: a column AND a table may be named work;
                    // the BEGIN/COMMIT/ROLLBACK WORK keyword is anchored by its statement word)
    | RECLUSTER     // Allow RECLUSTER as identifier (live-verified: a column AND a table may be named
                    // recluster; the clustering action is anchored by SUSPEND / RESUME)
    | NORELY        // Allow NORELY as identifier (live-verified: `CREATE TABLE kw (norely INT)` runs;
                    // the constraint property is anchored by RELY's own clause position)
    | ENFORCED      // Allow ENFORCED as identifier (live-verified: a column AND a table may be named
                    // enforced; the constraint property is anchored by ALTER/MODIFY CONSTRAINT)
    | NAME          // Allow NAME as identifier (live-verified: a column and a table may be named name,
                    // and `BEGIN NAME name` names a transaction NAME — the keyword is anchored by BEGIN
                    // / START TRANSACTION, which is the only place it is one)
    | STOP          // Allow STOP as identifier (ALTER COMPUTE POOL ... STOP ALL is anchored by ALTER)
    | GENERATOR     // Allow GENERATOR as identifier (table function)
    | GET           // Allow GET as identifier (the GET(array/object, key) semi-structured function; the
                    // GET stage command is a separate statement, disambiguated by context)
    | GRANTS        // Allow GRANTS as identifier
    // Words the access-control statements tokenise (grant options, accounts, managed accounts); each is still a name.
    | OPTION | ADMIN_NAME | ADMIN_PASSWORD | ADMIN_RSA_PUBLIC_KEY | ADMIN_USER_TYPE | EDITION
    | REGION | REGION_GROUP | GRACE_PERIOD_IN_DAYS | POLARIS
    | FORMAT        // Allow FORMAT as identifier/function name (FILE FORMAT is anchored by FILE)
    | DECFLOAT      // Allow DECFLOAT as identifier (the type use is anchored in dataTypeName)
    | FILTER        // Allow FILTER as identifier (the aggregate FILTER clause is anchored by LPAREN WHERE)
    | POLICY        // Allow POLICY as identifier (MASKING/ROW ACCESS POLICY uses are anchored by the preceding keyword)
    | PUT           // Allow PUT as identifier (the PUT stage statement is anchored by its file-URL argument)
    | SCHEMA        // Allow SCHEMA as a qualified-name part (db.schema.object); CREATE/DROP/USE/IN SCHEMA are anchored
    | SESSION       // Allow SESSION as identifier (ALTER SESSION / IN SESSION are anchored)
    | NUMBER        // Allow NUMBER as identifier (the NUMBER type is anchored in dataTypeName)
    | RENAME        // Allow RENAME as identifier (star-modifier / ALTER ... RENAME are anchored)
    | REPLACE       // Allow REPLACE as identifier (CREATE OR REPLACE / star-modifier are anchored)
    | GROUPING      // Allow GROUPING as identifier (function name)
    | HOUR          // Allow HOUR as identifier (can be column name)
    | HOURS         // Allow HOURS as identifier (can be column name)
    | HYBRID        // Allow HYBRID as identifier (also a CREATE [HYBRID] TABLE modifier)
    | IGNORE        // Allow IGNORE as identifier (also FIRST_VALUE(...) IGNORE NULLS)
    | RESPECT       // Allow RESPECT as identifier (also FIRST_VALUE(...) RESPECT NULLS)
    | INCREMENTAL   // Allow INCREMENTAL as identifier
    | INFORMATION   // Allow INFORMATION as identifier
    | INITIALIZE    // Allow INITIALIZE as identifier
    | INITIALLY_SUSPENDED
    | INPUT         // Allow INPUT as identifier (FLATTEN parameter)
    | EXCEPT        // EXCEPT is unreserved (usable as alias/table/column), unlike UNION/INTERSECT/MINUS
    | KEY           // Allow KEY as identifier (FLATTEN output column)
    | LAST          // Allow LAST as identifier (also ORDER BY ... NULLS LAST)
    | LEFT          // Allow LEFT as identifier (Snowflake compatible)
    | LOCKS         // Allow LOCKS as identifier
    | MAP           // Allow MAP as identifier (also the MAP data type)
    | MAX_CONCURRENCY_LEVEL
    | MINUTE        // Allow MINUTE as identifier (can be column name)
    | MINUTES       // Allow MINUTES as identifier (can be column name)
    | MODE          // Allow MODE as identifier (FLATTEN parameter)
    | MONTH         // Allow MONTH as identifier (can be column name)
    | MONTHS        // Allow MONTHS as identifier (can be column name)
    | NEXT          // Allow NEXT as identifier (not reserved in Snowflake; only used after FETCH)
    | NEXTVAL       // Allow NEXTVAL as identifier (function name)
    | NULLS         // Allow NULLS as identifier (also ORDER BY ... NULLS FIRST/LAST)
    | OBJECTS       // Allow OBJECTS as identifier
    | OFFSET
    | ORGANIZATION  // Allow ORGANIZATION as identifier
    | OTHER         // Allow OTHER as identifier (column name)
    | OUTER         // Allow OUTER as identifier (FLATTEN parameter)
    | OWNER         // Allow OWNER as identifier
    | STRICT        // Allow STRICT as identifier
    | IMMUTABLE     // Allow IMMUTABLE as identifier
    | MEMOIZABLE    // Allow MEMOIZABLE as identifier
    | VOLATILE      // Allow VOLATILE as identifier
    | SECURE        // Allow SECURE as identifier
    | CALLED        // Allow CALLED as identifier
    | PARAMETERS    // Allow PARAMETERS as identifier
    | DEFAULT_WAREHOUSE
    | DEFAULT_NAMESPACE
    | DEFAULT_SECONDARY_ROLES
    | LOGIN_NAME
    | DISPLAY_NAME
    | FIRST_NAME
    | MIDDLE_NAME
    | LAST_NAME
    | MUST_CHANGE_PASSWORD
    | EMAIL
    | DISABLED
    | DAYS_TO_EXPIRY | MINS_TO_UNLOCK | MINS_TO_BYPASS_MFA | RSA_PUBLIC_KEY | RSA_PUBLIC_KEY_2
    | PARTITION
    | PATH          // Allow PATH as identifier (FLATTEN parameter)
    | PATTERN
    | PIPES         // Allow PIPES as identifier (INFORMATION_SCHEMA view)
    | POSITIONAL_PARAMETER  // Allow $1, $2, etc. as identifier
    | PRIVILEGES    // Allow PRIVILEGES as identifier
    | PROCEDURES    // Allow PROCEDURES as identifier (INFORMATION_SCHEMA view)
    | QUERY_ACCELERATION_MAX_SCALE_FACTOR
    | QUOTED_IDENTIFIER
    | READ_ONLY     // Allow READ_ONLY as identifier
    | RECURSIVE     // Allow RECURSIVE as identifier (FLATTEN parameter)
    | RANGE         // Allow RANGE as identifier (window-frame keyword)
    | UNBOUNDED     // Allow UNBOUNDED as identifier (window-frame keyword)
    | PRECEDING     // Allow PRECEDING as identifier (window-frame keyword)
    | SYSTEM        // Allow SYSTEM as identifier (sampling method)
    | SEED          // Allow SEED as identifier (sampling keyword)
    | BERNOULLI     // Allow BERNOULLI as identifier (sampling method)
    // The four SCRIPTING statement words are ordinary names in SQL, measured over a raw connection:
    // each may be a column's name, a column reference and a bare table alias, and
    // `SELECT break.x FROM st break` resolves. Only OUTSIDE a block, standing alone as a statement,
    // are they refused — see SQLCommandVisitor.visitProceduralStatement, which is where that belongs.
    | BREAK
    | CONTINUE
    | RAISE
    | RETURN
    | NOVALIDATE    // live: a column may be called novalidate (CHECK itself may NOT — it is reserved)
    | BODY          // only a keyword inside ALTER … SET BODY
    | AGGREGATION   // live: CREATE TABLE aggregation (aggregation INT) runs
    | ENTITY        // live: a column may be called entity
    | PROJECTION    // live: CREATE TABLE projection (projection INT) runs
    | METRIC        // live: CREATE TABLE metric (metric INT, data INT) runs
    | OPTIMIZATION  // live: CREATE TABLE search (optimization INT) runs
    | CONTACT       // live: CREATE TABLE contact (contact INT) runs
    | CONTACTS      // live: a column may be called contacts
    | NATURAL       // Allow NATURAL as identifier (join keyword)
    | REPEATABLE    // Allow REPEATABLE as identifier (sampling keyword)
    | REVERSE       // Allow REVERSE as identifier (range-FOR keyword)
    | REPEAT        // Allow REPEAT as identifier (also the REPEAT() string function)
    // NOTE: UNTIL is deliberately NOT an identifier — as one, `statement+` would swallow the
    // `UNTIL <cond>` that terminates a REPEAT body, breaking the REPEAT … UNTIL boundary.
    | REPLACE
    | EXCLUDE       // Allow EXCLUDE as identifier (also the SELECT * EXCLUDE modifier)
    | INCLUDE       // Allow INCLUDE as identifier (also the UNPIVOT INCLUDE NULLS modifier)
    | REAL          // Allow REAL as identifier (also the REAL data-type name, a FLOAT synonym)
    | PRECISION     // Allow PRECISION as identifier (also the DOUBLE PRECISION data-type name)
    | RESULT        // Allow RESULT as identifier (common alias / procedural variable)
    | RIGHT         // Allow RIGHT as identifier (Snowflake compatible)
    | ROLE          // Allow ROLE as identifier
    | ROLES         // Allow ROLES as identifier
    | ROWCOUNT      // Allow ROWCOUNT as identifier (GENERATOR parameter)
    | SCALING_POLICY
    | SCHEMAS       // Allow SCHEMAS as identifier (for INFORMATION_SCHEMA views)
    | SECOND        // Allow SECOND as identifier (can be column name)
    | SECONDS       // Allow SECONDS as identifier (can be column name)
    | SEQUENCES     // Allow SEQUENCES as identifier (INFORMATION_SCHEMA view)
    | SERVERLESS_TASK_MAX_STATEMENT_SIZE
    | SESSIONS      // Allow SESSIONS as identifier
    | SETS          // Allow SETS as identifier
    | SPLIT_TO_TABLE // Allow SPLIT_TO_TABLE as identifier (table function)
    | STAGE
    | STANDARD      // Allow STANDARD as identifier
    | STATEMENT     // Allow STATEMENT as identifier
    | STATEMENT_QUEUED_TIMEOUT_IN_SECONDS
    | STATEMENT_TIMEOUT_IN_SECONDS
    | STREAMS       // Allow STREAMS as identifier (INFORMATION_SCHEMA view)
    | STRING        // Allow STRING as identifier (data type and parameter)
    | SUSPEND_TASK_AFTER_NUM_FAILURES
    | TABLES        // Allow TABLES as identifier (for INFORMATION_SCHEMA views)
    | TAG           // Allow TAG as identifier
    | DIRECTORY     // Allow DIRECTORY as identifier (also the DIRECTORY(@stage) table source)
    | FIELDS        // Allow FIELDS as identifier (also CAST ... RENAME/ADD FIELDS)
    | NVARCHAR | NVARCHAR2 | VARCHAR2 | NCHAR | CHARACTER | VARYING | TIMESTAMPLTZ | TIMESTAMPTZ | LOCAL | GLOBAL | ZONE
    | TERSE         // Allow TERSE as identifier (also the SHOW TERSE modifier)
    | STARTS        // Allow STARTS as identifier (also SHOW ... STARTS WITH)
    | HISTORY       // Allow HISTORY as identifier (also SHOW ... HISTORY)
    | ICEBERG       // Allow ICEBERG as identifier (also SHOW ICEBERG TABLES)
    | API | CATALOG | NOTIFICATION | STORAGE | SECURITY | EXTERNAL | INTEGRATIONS   // integration statements are anchored by their verbs
    | VOLUME | VOLUMES | EVENT | CONVERT   // external volume, event and Iceberg table statements likewise
    | APPLICATION   // Allow APPLICATION as identifier (also SHOW ... IN APPLICATION)
    | CLASS         // Allow CLASS as identifier (also SHOW FUNCTIONS IN CLASS)
    | PACKAGE       // Allow PACKAGE as identifier (also IN APPLICATION PACKAGE)
    | TAGS
    | TARGET_COMPLETION_INTERVAL
    | TASKS         // Allow TASKS as identifier (INFORMATION_SCHEMA view)
    | TASK_AUTO_RETRY_ATTEMPTS
    | TEMP
    | TEMPORARY
    | TEXT          // Allow TEXT as identifier (data type)
    | TIMELIMIT     // Allow TIMELIMIT as identifier (GENERATOR parameter)
    | TIMESTAMP     // Allow TIMESTAMP as identifier
    | TIMESTAMP_NTZ
    | TIMESTAMPNTZ
    | TRANSACTIONS  // Allow TRANSACTIONS as identifier
    | TRY_CAST      // identifier in NAME positions (alias, column/table name, live-verified);
                    // the cast construct is keyword-anchored, and functionName refuses TRY_CAST as a
                    // call head, so the construct wins wherever a call could be confused with it
    | TYPE
    | URL
    | USER          // Allow USER as identifier
    | USERS         // Allow USERS as identifier
    | USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE
    | USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS
    | USER_TASK_TIMEOUT_MS
    | VARIABLES     // Allow VARIABLES as identifier
    | VIEWS         // Allow VIEWS as identifier (for INFORMATION_SCHEMA views)
    | IMPORTS        // Allow IMPORTS as identifier
    | WAREHOUSE_TYPE // Allow WAREHOUSE_TYPE as identifier
    | ALERT | ALERTS | ALLOWED_VALUES_SEQUENCE | CONDITION | CONFIG | ON_CONFLICT | PROPAGATE | RUNBOOK
    | SUSPEND_ALERT_AFTER_NUM_FAILURES
    | RETRY
    | ROOT
    | YEAR          // Allow YEAR as identifier (can be column name)
    | YEARS         // Allow YEARS as identifier (can be column name)
    | SHOW_INITIAL_ROWS
    | APPEND_ONLY
    | VECTOR
    | RESOURCE
    | UUID
    | REFERENCES
    | SQL
    | TRUNCATE      // Allow TRUNCATE as identifier (TRUNCATE() numeric function, alias of TRUNC)
    | CLUSTER
    | CLUSTERING
    | IDENTITY
    | CHANGES
    | NUMERIC
    | DEC           // Allow DEC as identifier (also the DEC(p,s) NUMBER alias)
    | STREAM
    | NETWORK
    | NETWORK_POLICY
    | RULE
    | RULES
    | SECRET
    | SECRETS
    | UNPIVOT
    | PIVOT
    | DATABASE      // e.g. a VARIANT path key `stats:database`
    // Names that metadata output uses as COLUMN names, and that a real account accepts
    // unquoted in that position. INCREMENT and ROWS are deliberately absent: a real
    // account reserves those, so INFORMATION_SCHEMA.SEQUENCES."increment" and
    // SHOW TABLES' "rows" have to be quoted there too.
    | ALLOWED_VALUES
    | AUTOINCREMENT
    | AUTO_RESUME
    | AUTO_SUSPEND
    | COMPRESSION
    | DATE_FORMAT
    | DEFAULT_ROLE
    | ERROR_INTEGRATION
    | ESCAPE
    | FIELD_DELIMITER
    | HANDLER
    | INTEGRATION
    | INTERVAL
    | LANGUAGE
    | MAX_CLUSTER_COUNT
    | MIN_CLUSTER_COUNT
    | RECORD_DELIMITER
    | RELY
    | RESOURCE_MONITOR
    | RUNTIME_VERSION
    | SCHEDULE
    | SKIP_HEADER
    | WAREHOUSE
    // Snowflake reserves only 64 words, and every OTHER word it lexes may name a column: the
    // list below is the measured remainder — each one accepted live both as a column DEFINITION
    // and as a REFERENCE (SELECT/WHERE/GROUP BY/ORDER BY). They are keywords here only because
    // Frostlake tokenises them for some statement it supports, which is not a reason to refuse
    // them as names. Measure before adding: the five words live DEFINES but cannot REFERENCE
    // (CASE, CAST, DEFAULT, TRY_CAST, WHEN) belong in namePart instead, and the join words
    // (INNER/JOIN/LEFT/CROSS/FULL) cannot live here at all — see nameStartPart.
    | ACCESS | ACCOUNT | ADD | APPLY | ARRAY | ASC
    // AT_KEYWORD, not AT: the token named AT is the '@' of a stage reference, and the WORD at is a
    // separate token. Naming the wrong one here quietly makes '@' an identifier.
    | ASYNC | AT_KEYWORD | AUTO | AUTO_INGEST | AWAIT | AWS_SNS_TOPIC
    | BEGIN | BIGINT | BINARY | BOOLEAN | BREAK | BYTEINT
    | CALL | CASCADE | CLONE | COLLATE | COMMIT | CONTINUE
    | CUBE | CURRVAL | CURSOR | DATA_RETENTION_TIME_IN_DAYS | DECIMAL | DECLARE
    | DESC | DESCRIBE | DISABLE | DOUBLE | ELSEIF | ENABLE
    | ENCRYPTION | END | EXCEPTION | EXECUTE | EXECUTION | EXPLAIN
    | FALSE | FETCH | FILES | FILE_FORMAT | FLOAT | FORCE
    | FOREIGN | FORMATS | FULL | FUNCTION | FUTURE | GEOGRAPHY
    | GEOMETRY | HEADER | IF | IMMEDIATE | IMPORT | IMPORTED
    | INT | INTEGER | JAVA | JAVASCRIPT | KEYS | LATERAL
    | LET | LIMIT | LIST | LOOP | MANAGE | MASKING
    | MASKING_POLICY | MATCHED | MATCH_BY_COLUMN_NAME | MATERIALIZED | MAX_FILE_SIZE | MERGE
    | MODIFY | MONITOR | MULTI_STATEMENT_COUNT | NO | NOORDER | OBJECT
    | ONLY | ON_CREATE | ON_ERROR | ON_SCHEDULE | OPEN | OPERATE
    | OVER | OVERWRITE | OWNERSHIP | PASSWORD | PAUSE | PIPE
    | POLICIES | PRIMARY | PROCEDURE | PURGE | PYTHON | RAISE
    | READ | REBUILD | REFRESH | REFRESH_MODE | REMOVE | RESTART
    | APPLYBUDGET | EVOLVE | ERROR
    | RESTRICT | RESULTSET | RESUME | RETURN | RETURNS | RM | LS
    | ROLLBACK | ROLLUP | ROW_ACCESS_POLICY | SCALA | SECONDARY | SEQUENCE
    | SESSION_POLICY | SHARE | SHOW | SINGLE | SIZE_LIMIT | SMALLINT
    | SUSPEND | SWAP | TARGET_LAG | TASK | TIMESTAMP_LTZ | TIMESTAMP_TZ
    | TINYINT | TOP | TRANSACTION | TRANSIENT | TRUE | UNDROP
    | UNSET | UNTIL | USAGE | USE | USE_ANY_ROLE | USING
    | VALIDATION_MODE | VARBINARY | VARCHAR | VARIANT | VIEW | WAREHOUSES
    | WAREHOUSE_SIZE | WHILE | WITHIN | WRITE
    // Words of the notebook, Streamlit and Cortex Search statements, still plain names elsewhere.
    | ABORT | COMPUTE_POOL | EXTERNAL_ACCESS_INTEGRATIONS | IDLE_AUTO_SHUTDOWN_TIME_SECONDS | INDEXING
    | LIVE | MAIN_FILE | NOTEBOOK | NOTEBOOKS | PULL | PUSH | QUERY_WAREHOUSE | ROOT_LOCATION | RUNTIME_NAME
    | SERVING | STREAMLIT | STREAMLITS | TITLE | VERSION | VERSIONS
    ;

// A VARIANT path key (the field name after `:` or `.`) is just a JSON key, so — unlike a bare identifier —
// it may be ANY reserved keyword: j:create, j:order, j:from are all valid in Snowflake. This rule is used
// ONLY in path access (ObjectAccessExpr / FieldAccessExpr), so these keywords stay out of the general
// identifier rule and never affect statement parsing.
variantPathKey
    : identifier
    | ACCOUNT   // a semi-structured path key may be any word, including otherwise-reserved keywords (e.g. src:account.status)
    | CREATE | DROP | ALTER | SELECT | DELETE | WHERE | GROUP | ORDER | HAVING | FROM
    | JOIN | INNER | OUTER | CROSS | FULL | ON | NATURAL
    | AND | OR | NOT | IN | IS | LIKE | BETWEEN | EXISTS
    | UNION | INTERSECT | ALL | DISTINCT | AS | BY
    | CASE | WHEN | THEN | ELSE | END | NULL | ASC | DESC
    | INTO | INSERT | CURRENT | FOLLOWING | RLIKE | UNIQUE | UPDATE
    | LIMIT | WITH | QUALIFY | PIVOT | UNPIVOT | MERGE | SET | VALUES | SHOW
    | GRANT | REVOKE | DESCRIBE | USE | EXPLAIN | TRUNCATE | CALL | EXECUTE | RETURN
    ;

nonJoinKeywordIdentifier
    // A bare table alias is the WIDEST name position measured: everything a column may be called, less
    // the fifteen words that lead something in a FROM clause (see bareTableAliasAllowed), plus ten that
    // are NOT legal column names yet are legal aliases — live accepts `FROM t current_date` while
    // refusing a column called current_date. The explicit list below it is older and narrower; the
    // words it names that identifier already covers are harmless duplicates.
    : {bareTableAliasAllowed()}? identifier
    | CASE | CAST | CONSTRAINT | CURRENT_DATE | CURRENT_TIME | CURRENT_TIMESTAMP | CURRENT_USER
    | LOCALTIME | LOCALTIMESTAMP
    | DEFAULT | TRY_CAST | WHEN
    | IDENTIFIER
    | KW_IDENTIFIER
    | EXCEPT        // unreserved: `FROM t except` is a bare alias; `FROM t EXCEPT SELECT` still a set-op
    | APPEND_ONLY
    | DECFLOAT
    | FORMAT
    | FILTER
    | DIRECTORY     // vendor idiom: LEFT JOIN (...) directory USING (...)
    | FIELDS
    | PUT           // a bare alias named put (the PUT statement is anchored at statement start)
    | SESSION
    | NUMBER
    | RENAME
    | REPLACE
    | NVARCHAR | NVARCHAR2 | VARCHAR2 | NCHAR | CHARACTER | VARYING | TIMESTAMPLTZ | TIMESTAMPTZ | LOCAL | GLOBAL | ZONE
    | TERSE
    | STARTS
    | HISTORY
    | ICEBERG
    | APPLICATION
    | CLASS
    | PACKAGE
    // Live-verified per word: `FROM h prior` and `FROM h connect_by_root` are accepted as bare table
    // aliases by Snowflake, while `FROM h asof` and `FROM h match_condition` are syntax errors there —
    // so ASOF and MATCH_CONDITION are deliberately NOT listed (ASOF would also make `FROM x asof JOIN y`
    // ambiguous with the ASOF join it introduces).
    | PRIOR
    | CONNECT_BY_ROOT
    | QUOTED_IDENTIFIER
    | POSITIONAL_PARAMETER
    | DATE
    | DATA
    | TIMESTAMP
    | COMMENT
    | USERS
    | ROLES
    | GRANTS
    | PRIVILEGES
    | USER
    | ROLE
    | COPY
    | DATABASES
    | TABLES
    | COLUMNS
    | VIEWS
    | SCHEMAS
    | GENERATOR
    | ROWCOUNT
    | TIMELIMIT
    | SPLIT_TO_TABLE
    | STRING
    | TEXT
    | DELIMITER
    | DO            // Allow DO as an un-AS'd table alias (e.g. `FROM detection_output do`)
    | FLATTEN
    | INPUT
    | PATH
    | RECURSIVE
    | MODE
    | KEY
    | TAG
    | NEXTVAL
    | CURRENT_TIMESTAMP
    | CURRENT_DATE
    | CURRENT_TIME
    | LOCALTIMESTAMP
    | LOCALTIME
    | OTHER
    | TYPE
    | ACTION
    | PATTERN
    | URL
    | FILE
    | TIMESTAMP_NTZ
    | OFFSET
    | PARTITION
    | TAGS
    | TEMP
    | TEMPORARY
    | REPLACE
    | EXCLUDE
    | INCLUDE
    | YEAR
    | YEARS
    | MONTH
    | MONTHS
    | DAY
    | DAYS
    | HOUR
    | HOURS
    | MINUTE
    | MINUTES
    | SECOND
    | SECONDS
    | RESOURCE
    | SHOW_INITIAL_ROWS
    | CLUSTER
    | CLUSTERING
    | IDENTITY
    | CHANGES
    | NUMERIC
    | DEC           // Allow DEC as identifier (also the DEC(p,s) NUMBER alias)
    | STREAM
    | NETWORK
    | UNPIVOT
    | PIVOT
    // Note: Intentionally exclude LEFT, RIGHT, INNER, FULL, CROSS, OUTER
    // These cannot be used as table aliases without AS keyword
    ;

// Common DDL clauses
if_exists
    : IF EXISTS
    ;

if_not_exists
    : IF NOT EXISTS
    ;

or_replace
    : OR REPLACE
    ;

// CREATE OR ALTER: the object is created when it is not there and ALTERED into the written shape when it
// is, which a TABLE does column by column and a VIEW does by taking the new body.
or_alter
    : OR ALTER
    ;

literal
    : INTEGER_LITERAL
    | FLOAT_LITERAL
    | STRING_LITERAL
    | DOLLAR_QUOTED_STRING
    | HEX_LITERAL                 // x'A1B2' hex binary
    | TRUE
    | FALSE
    | NULL
    ;

// Lexer Rules
// Keywords
SELECT: S E L E C T;
DIRECTED: D I R E C T E D;
DISTINCT: D I S T I N C T;
FROM: F R O M;
WHERE: W H E R E;
AND: A N D;
OR: O R;
NOT: N O T;
IN: I N;
TO: T O;
IS: I S;
NULL: N U L L;
TRUE: T R U E;
FALSE: F A L S E;
AS: A S;
GROUP: G R O U P;
BY: B Y;
BETWEEN: B E T W E E N;
LIKE: L I K E;
ILIKE: I L I K E;
RLIKE: R L I K E;
REGEXP: R E G E X P;
ESCAPE: E S C A P E;
HAVING: H A V I N G;
QUALIFY: Q U A L I F Y;
OVER: O V E R;
PARTITION: P A R T I T I O N;
ORDER: O R D E R;
NOORDER: N O O R D E R;
ASC: A S C;
DESC: D E S C;
LIMIT: L I M I T;
TOP: T O P;
OFFSET: O F F S E T;
FIRST: F I R S T;
LAST: L A S T;
NULLS: N U L L S;
IGNORE: I G N O R E;
RESPECT: R E S P E C T;
NEXT: N E X T;
ROWS: R O W S;
RANGE: R A N G E;
UNBOUNDED: U N B O U N D E D;
PRECEDING: P R E C E D I N G;
FOLLOWING: F O L L O W I N G;
CURRENT: C U R R E N T;
ONLY: O N L Y;
CREATE: C R E A T E;
CLONE: C L O N E;
DROP: D R O P;
UNDROP: U N D R O P;
EXPLAIN: E X P L A I N;
RESULT: R E S U L T;
ALTER: A L T E R;
RENAME: R E N A M E;
ADD: A D D;
COLUMN: C O L U M N;
TABLE: T A B L E;
VIEW: V I E W;
MATERIALIZED: M A T E R I A L I Z E D;
DYNAMIC: D Y N A M I C;
TARGET_LAG: T A R G E T '_' L A G;
CORTEX: C O R T E X;
SEARCH: S E A R C H;
SERVICE: S E R V I C E;
SERVICES: S E R V I C E S;
ATTRIBUTES: A T T R I B U T E S;
EMBEDDING_MODEL: E M B E D D I N G '_' M O D E L;
DOWNSTREAM: D O W N S T R E A M;
INCREMENTAL: I N C R E M E N T A L;
INITIALIZE: I N I T I A L I Z E;
ORGANIZATION: O R G A N I Z A T I O N;
ACCOUNTS: A C C O U N T S;
LOCKS: L O C K S;
TRANSACTIONS: T R A N S A C T I O N S;
VARIABLES: V A R I A B L E S;
ON_CREATE: O N '_' C R E A T E;
ON_SCHEDULE: O N '_' S C H E D U L E;
REFRESH_MODE: R E F R E S H '_' M O D E;
DATA_RETENTION_TIME_IN_DAYS: D A T A '_' R E T E N T I O N '_' T I M E '_' I N '_' D A Y S;
READ_ONLY: R E A D '_' O N L Y;
AUTO: A U T O;
DATABASE: D A T A B A S E;
SCHEMA: S C H E M A;
INSERT: I N S E R T;
INTO: I N T O;
VALUES: V A L U E S;
COPY: C O P Y;
UPDATE: U P D A T E;
DELETE: D E L E T E;
SET: S E T;
UNSET: U N S E T;
PRIMARY: P R I M A R Y;
KEYS: K E Y S;
KEY: K E Y;
UNIQUE: U N I Q U E;
DEFAULT: D E F A U L T;
AUTOINCREMENT: A U T O I N C R E M E N T;
IDENTITY: I D E N T I T Y;
// The IDENTIFIER() name-resolution keyword — a distinct token so IDENTIFIER('x') is unambiguous against a
// table name followed by a bare (list); declared before the general IDENTIFIER rule so it wins the literal
// word. Kept usable as a plain identifier via the identifier allowlist below.
KW_IDENTIFIER: I D E N T I F I E R;
UNION: U N I O N;
INTERSECT: I N T E R S E C T;
EXCEPT: E X C E P T;
EXCLUDE: E X C L U D E;
INCLUDE: I N C L U D E;
MINUS_KW: M I N U S;   // the word MINUS (set operator); the '-' symbol is the separate MINUS token
USE: U S E;
SHOW: S H O W;
DESCRIBE: D E S C R I B E;
DATABASES: D A T A B A S E S;
SCHEMAS: S C H E M A S;
TABLES: T A B L E S;
VIEWS: V I E W S;
COLUMNS: C O L U M N S;
FUTURE: F U T U R E;
STREAMS: S T R E A M S;
TASKS: T A S K S;
PIPES: P I P E S;
SEQUENCES: S E Q U E N C E S;
WAREHOUSES: W A R E H O U S E S;
COMPUTE: C O M P U T E;
POOL: P O O L;
POOLS: P O O L S;
INSTANCE: I N S T A N C E;
FAMILIES: F A M I L I E S;
MIN_NODES: M I N '_' N O D E S;
MAX_NODES: M A X '_' N O D E S;
INSTANCE_FAMILY: I N S T A N C E '_' F A M I L Y;
AUTO_SUSPEND_SECS: A U T O '_' S U S P E N D '_' S E C S;
PLACEMENT_GROUP: P L A C E M E N T '_' G R O U P;
BACKUP_INSTANCE_FAMILIES: B A C K U P '_' I N S T A N C E '_' F A M I L I E S;
STOP: S T O P;
STAGES: S T A G E S;
ROLLUP: R O L L U P;
CUBE: C U B E;
GROUPING: G R O U P I N G;
SETS: S E T S;
PARAMETERS: P A R A M E T E R S;
SESSIONS: S E S S I O N S;
OBJECTS: O B J E C T S;
PROCEDURES: P R O C E D U R E S;
FUNCTIONS: F U N C T I O N S;
BUILTIN: B U I L T I N;

// Stream, Task, Warehouse, Stage
STREAM: S T R E A M;
TASK: T A S K;
WAREHOUSE: W A R E H O U S E;
STAGE: S T A G E;
MERGE: M E R G E;
USING: U S I N G;
ON: O N;
OF: O F;
WHEN: W H E N;
MATCHED: M A T C H E D;
PIVOT: P I V O T;
UNPIVOT: U N P I V O T;

// Join keywords
JOIN: J O I N;
INNER: I N N E R;
LEFT: L E F T;
RIGHT: R I G H T;
FULL: F U L L;
OUTER: O U T E R;
CROSS: C R O S S;
LATERAL: L A T E R A L;
// Snowflake ASOF JOIN (closest-match time-series join) and its mandatory MATCH_CONDITION clause.
// Live-verified: only the bare `ASOF JOIN` spelling exists — LEFT/RIGHT/INNER ASOF are syntax errors.
ASOF: A S O F;
MATCH_CONDITION: M A T C H UNDERSCORE C O N D I T I O N;
// Hierarchical-query keywords: `[START WITH <pred>] CONNECT BY [PRIOR] c = [PRIOR] c` plus the
// CONNECT_BY_ROOT pseudo-column. CONNECT is reserved in Snowflake (live-verified: a column named
// `connect` is a syntax error) — ASOF / MATCH_CONDITION / PRIOR / CONNECT_BY_ROOT are not, so they
// stay usable as identifiers via the `identifier` rule.
CONNECT: C O N N E C T;
PRIOR: P R I O R;
CONNECT_BY_ROOT: C O N N E C T UNDERSCORE B Y UNDERSCORE R O O T;
RESUME: R E S U M E;
RETRY: R E T R Y;
ROOT: R O O T;
SUSPEND: S U S P E N D;
RECLUSTER: R E C L U S T E R;
CHECK: C H E C K;
CONTACT: C O N T A C T;
METRIC: M E T R I C;
OPTIMIZATION: O P T I M I Z A T I O N;
PROJECTION: P R O J E C T I O N;
AGGREGATION: A G G R E G A T I O N;
ENTITY: E N T I T Y;
BODY: B O D Y;
NOVALIDATE: N O V A L I D A T E;
CONTACTS: C O N T A C T S;
PAUSE: P A U S E;
REFRESH: R E F R E S H;
REBUILD: R E B U I L D;
APPLYBUDGET: A P P L Y B U D G E T;
EVOLVE: E V O L V E;
ERROR: E R R O R;
DISABLE: D I S A B L E;
ENABLE: E N A B L E;
SCHEDULE: S C H E D U L E;
AFTER: A F T E R;
ALLOW_OVERLAPPING_EXECUTION: A L L O W '_' O V E R L A P P I N G '_' E X E C U T I O N;
USER_TASK_TIMEOUT_MS: U S E R '_' T A S K '_' T I M E O U T '_' M S;
SUSPEND_TASK_AFTER_NUM_FAILURES: S U S P E N D '_' T A S K '_' A F T E R '_' N U M '_' F A I L U R E S;
TASK_AUTO_RETRY_ATTEMPTS: T A S K '_' A U T O '_' R E T R Y '_' A T T E M P T S;
USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE: U S E R '_' T A S K '_' M A N A G E D '_' I N I T I A L '_' W A R E H O U S E '_' S I Z E;
SERVERLESS_TASK_MAX_STATEMENT_SIZE: S E R V E R L E S S '_' T A S K '_' M A X '_' S T A T E M E N T '_' S I Z E;
TARGET_COMPLETION_INTERVAL: T A R G E T '_' C O M P L E T I O N '_' I N T E R V A L;
USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS: U S E R '_' T A S K '_' M I N I M U M '_' T R I G G E R '_' I N T E R V A L '_' I N '_' S E C O N D S;
START: S T A R T;
INCREMENT: I N C R E M E N T;
RESTART: R E S T A R T;
NEXTVAL: N E X T V A L;
CURRVAL: C U R R V A L;
CURRENT_TIMESTAMP: C U R R E N T UNDERSCORE T I M E S T A M P;
LOCALTIMESTAMP: L O C A L T I M E S T A M P;
LOCALTIME: L O C A L T I M E;
CURRENT_DATE: C U R R E N T UNDERSCORE D A T E;
CURRENT_TIME: C U R R E N T UNDERSCORE T I M E;
CURRENT_USER: C U R R E N T UNDERSCORE U S E R;
APPEND_ONLY: A P P E N D UNDERSCORE O N L Y;
SHOW_INITIAL_ROWS: S H O W UNDERSCORE I N I T I A L UNDERSCORE R O W S;
AUTO_INGEST: A U T O UNDERSCORE I N G E S T;
AWS_SNS_TOPIC: A W S UNDERSCORE S N S UNDERSCORE T O P I C;
ERROR_INTEGRATION: E R R O R UNDERSCORE I N T E G R A T I O N;
ON_ERROR: O N UNDERSCORE E R R O R;
FIELD_DELIMITER: F I E L D UNDERSCORE D E L I M I T E R;
SKIP_HEADER: S K I P UNDERSCORE H E A D E R;
DATE_FORMAT: D A T E UNDERSCORE F O R M A T;
WAREHOUSE_SIZE: W A R E H O U S E UNDERSCORE S I Z E;
WAREHOUSE_TYPE: W A R E H O U S E UNDERSCORE T Y P E;
IMAGE: I M A G E;
IMAGES: I M A G E S;
REPOSITORY: R E P O S I T O R Y;
REPOSITORIES: R E P O S I T O R I E S;
ARTIFACT: A R T I F A C T;
JOB: J O B;
JOBS: J O B S;
CONTAINERS: C O N T A I N E R S;
INSTANCES: I N S T A N C E S;
ENDPOINTS: E N D P O I N T S;
ENDPOINT: E N D P O I N T;
MAX_BATCH_ROWS: M A X UNDERSCORE B A T C H UNDERSCORE R O W S;
SPECIFICATION: S P E C I F I C A T I O N;
SPECIFICATION_FILE: S P E C I F I C A T I O N UNDERSCORE F I L E;
SPECIFICATION_TEMPLATE: S P E C I F I C A T I O N UNDERSCORE T E M P L A T E;
SPECIFICATION_TEMPLATE_FILE: S P E C I F I C A T I O N UNDERSCORE T E M P L A T E UNDERSCORE F I L E;
AUTO_SUSPEND: A U T O UNDERSCORE S U S P E N D;
AUTO_RESUME: A U T O UNDERSCORE R E S U M E;
MIN_CLUSTER_COUNT: M I N UNDERSCORE C L U S T E R UNDERSCORE C O U N T;
MAX_CLUSTER_COUNT: M A X UNDERSCORE C L U S T E R UNDERSCORE C O U N T;
SCALING_POLICY: S C A L I N G UNDERSCORE P O L I C Y;
INITIALLY_SUSPENDED: I N I T I A L L Y UNDERSCORE S U S P E N D E D;
// Multi-word statements as single tokens (whitespace inside): zero parser-decision impact, and
// the lexer's longest-match fallback still yields plain RESUME when the tail is absent.
RESUME_IF_SUSPENDED: R E S U M E TOKEN_WS I F TOKEN_WS S U S P E N D E D;
ABORT_ALL_QUERIES: A B O R T TOKEN_WS A L L TOKEN_WS Q U E R I E S;
REVOKE_CURRENT_GRANTS: R E V O K E TOKEN_WS C U R R E N T TOKEN_WS G R A N T S;
fragment TOKEN_WS: [ \t\r\n]+;
MAX_CONCURRENCY_LEVEL: M A X UNDERSCORE C O N C U R R E N C Y UNDERSCORE L E V E L;
STATEMENT_QUEUED_TIMEOUT_IN_SECONDS: S T A T E M E N T UNDERSCORE Q U E U E D UNDERSCORE T I M E O U T UNDERSCORE I N UNDERSCORE S E C O N D S;
STATEMENT_TIMEOUT_IN_SECONDS: S T A T E M E N T UNDERSCORE T I M E O U T UNDERSCORE I N UNDERSCORE S E C O N D S;
ENABLE_QUERY_ACCELERATION: E N A B L E UNDERSCORE Q U E R Y UNDERSCORE A C C E L E R A T I O N;
QUERY_ACCELERATION_MAX_SCALE_FACTOR: Q U E R Y UNDERSCORE A C C E L E R A T I O N UNDERSCORE M A X UNDERSCORE S C A L E UNDERSCORE F A C T O R;
GENERATION: G E N E R A T I O N;
MANAGED: M A N A G E D;
RESOURCE_CONSTRAINT: R E S O U R C E UNDERSCORE C O N S T R A I N T;
WAIT_FOR_COMPLETION: W A I T UNDERSCORE F O R UNDERSCORE C O M P L E T I O N;
ADAPTIVE: A D A P T I V E;
ABORT: A B O R T;
COMPUTE_POOL: C O M P U T E UNDERSCORE P O O L;
EXTERNAL_ACCESS_INTEGRATIONS: E X T E R N A L UNDERSCORE A C C E S S UNDERSCORE I N T E G R A T I O N S;
IDLE_AUTO_SHUTDOWN_TIME_SECONDS: I D L E UNDERSCORE A U T O UNDERSCORE S H U T D O W N UNDERSCORE T I M E UNDERSCORE S E C O N D S;
INDEXING: I N D E X I N G;
LIVE: L I V E;
MAIN_FILE: M A I N UNDERSCORE F I L E;
PULL: P U L L;
PUSH: P U S H;
MODELS: M O D E L S;
NOTEBOOK: N O T E B O O K;
NOTEBOOKS: N O T E B O O K S;
QUERY_WAREHOUSE: Q U E R Y UNDERSCORE W A R E H O U S E;
ROOT_LOCATION: R O O T UNDERSCORE L O C A T I O N;
RUNTIME_NAME: R U N T I M E UNDERSCORE N A M E;
SERVING: S E R V I N G;
STREAMLIT: S T R E A M L I T;
STREAMLITS: S T R E A M L I T S;
TITLE: T I T L E;
VERSION: V E R S I O N;
VERSIONS: V E R S I O N S;
TEMPLATE: T E M P L A T E;
STANDARD: S T A N D A R D;
ECONOMY: E C O N O M Y;
MULTI_STATEMENT_COUNT: M U L T I UNDERSCORE S T A T E M E N T UNDERSCORE C O U N T;
CLUSTERING: C L U S T E R I N G;
CLUSTER: C L U S T E R;
COLLATE: C O L L A T E;
TRANSIENT: T R A N S I E N T;
HYBRID: H Y B R I D;
TEMPORARY: T E M P O R A R Y;
TEMP: T E M P;
REPLACE: R E P L A C E;
WITH: W I T H;
WITHIN: W I T H I N;
SWAP: S W A P;
THEN: T H E N;
URL: U R L;
FILE_FORMAT: F I L E UNDERSCORE F O R M A T;
ENCRYPTION: E N C R Y P T I O N;
VALIDATION_MODE: V A L I D A T I O N UNDERSCORE M O D E;
SIZE_LIMIT: S I Z E UNDERSCORE L I M I T;
PURGE: P U R G E;
FORCE: F O R C E;
ALLOWED_VALUES: A L L O W E D UNDERSCORE V A L U E S;
MATCH_BY_COLUMN_NAME: M A T C H UNDERSCORE B Y UNDERSCORE C O L U M N UNDERSCORE N A M E;
HEADER: H E A D E R;
OVERWRITE: O V E R W R I T E;
SINGLE: S I N G L E;
MAX_FILE_SIZE: M A X UNDERSCORE F I L E UNDERSCORE S I Z E;
COMPRESSION: C O M P R E S S I O N;
RECORD_DELIMITER: R E C O R D UNDERSCORE D E L I M I T E R;
TYPE: T Y P E;
FILES: F I L E S;
COMMENT: C O M M E N T;
LANGUAGE: L A N G U A G E;
JAVASCRIPT: J A V A S C R I P T;
JAVA: J A V A;
SCALA: S C A L A;
PYTHON: P Y T H O N;
RUNTIME_VERSION: R U N T I M E UNDERSCORE V E R S I O N;
HANDLER: H A N D L E R;
PACKAGES: P A C K A G E S;
IMPORTS: I M P O R T S;
SQL: S Q L;
LIST: L I S T;
PUT: P U T;
GET: G E T;
REMOVE: R E M O V E;
LS: L S;
RM: R M;
AT: '@';
AT_KEYWORD: A T;
BEFORE: B E F O R E;
STATEMENT: S T A T E M E N T;
CHANGES: C H A N G E S;
INFORMATION: I N F O R M A T I O N;
PATTERN: P A T T E R N;

// Security
USER: U S E R;
ROLE: R O L E;
USERS: U S E R S;
ROLES: R O L E S;
SECONDARY: S E C O N D A R Y;
FILTER: F I L T E R;
DECFLOAT: D E C F L O A T;
GRANT: G R A N T;
REVOKE: R E V O K E;
GRANTS: G R A N T S;
OPTION: O P T I O N;
ADMIN_NAME: A D M I N UNDERSCORE N A M E;
ADMIN_PASSWORD: A D M I N UNDERSCORE P A S S W O R D;
ADMIN_RSA_PUBLIC_KEY: A D M I N UNDERSCORE R S A UNDERSCORE P U B L I C UNDERSCORE K E Y;
ADMIN_USER_TYPE: A D M I N UNDERSCORE U S E R UNDERSCORE T Y P E;
EDITION: E D I T I O N;
REGION: R E G I O N;
REGION_GROUP: R E G I O N UNDERSCORE G R O U P;
GRACE_PERIOD_IN_DAYS: G R A C E UNDERSCORE P E R I O D UNDERSCORE I N UNDERSCORE D A Y S;
POLARIS: P O L A R I S;
PRIVILEGES: P R I V I L E G E S;
ALL: A L L;
ANY: A N Y;
SOME: S O M E;
USAGE: U S A G E;
OWNERSHIP: O W N E R S H I P;
PASSWORD: P A S S W O R D;
DEFAULT_ROLE: D E F A U L T UNDERSCORE R O L E;
DEFAULT_WAREHOUSE: D E F A U L T UNDERSCORE W A R E H O U S E;
DEFAULT_NAMESPACE: D E F A U L T UNDERSCORE N A M E S P A C E;
DEFAULT_SECONDARY_ROLES: D E F A U L T UNDERSCORE S E C O N D A R Y UNDERSCORE R O L E S;
LOGIN_NAME: L O G I N UNDERSCORE N A M E;
DISPLAY_NAME: D I S P L A Y UNDERSCORE N A M E;
FIRST_NAME: F I R S T UNDERSCORE N A M E;
MIDDLE_NAME: M I D D L E UNDERSCORE N A M E;
LAST_NAME: L A S T UNDERSCORE N A M E;
MUST_CHANGE_PASSWORD: M U S T UNDERSCORE C H A N G E UNDERSCORE P A S S W O R D;
EMAIL: E M A I L;
DISABLED: D I S A B L E D;
DAYS_TO_EXPIRY: D A Y S UNDERSCORE T O UNDERSCORE E X P I R Y;
MINS_TO_UNLOCK: M I N S UNDERSCORE T O UNDERSCORE U N L O C K;
MINS_TO_BYPASS_MFA: M I N S UNDERSCORE T O UNDERSCORE B Y P A S S UNDERSCORE M F A;
RSA_PUBLIC_KEY: R S A UNDERSCORE P U B L I C UNDERSCORE K E Y;
RSA_PUBLIC_KEY_2: R S A UNDERSCORE P U B L I C UNDERSCORE K E Y UNDERSCORE '2';
TRUNCATE: T R U N C A T E;
MODIFY: M O D I F Y;
OPERATE: O P E R A T E;
MONITOR: M O N I T O R;
READ: R E A D;
WRITE: W R I T E;
REFERENCES: R E F E R E N C E S;
CONSTRAINT: C O N S T R A I N T;
FOREIGN: F O R E I G N;
CASCADE: C A S C A D E;
RESTRICT: R E S T R I C T;
ACTION: A C T I O N;
NO: N O;
RELY: R E L Y;
NORELY: N O R E L Y;
ENFORCED: E N F O R C E D;
APPLY: A P P L Y;
IMPORTED: I M P O R T E D;
TERSE: T E R S E;
STARTS: S T A R T S;
HISTORY: H I S T O R Y;
ICEBERG: I C E B E R G;
API: A P I;
CATALOG: C A T A L O G;
NOTIFICATION: N O T I F I C A T I O N;
STORAGE: S T O R A G E;
SECURITY: S E C U R I T Y;
EXTERNAL: E X T E R N A L;
INTEGRATIONS: I N T E G R A T I O N S;
VOLUME: V O L U M E;
VOLUMES: V O L U M E S;
EVENT: E V E N T;
CONVERT: C O N V E R T;
APPLICATION: A P P L I C A T I O N;
CLASS: C L A S S;
PACKAGE: P A C K A G E;
NVARCHAR2: N V A R C H A R '2';
VARCHAR2: V A R C H A R '2';
NVARCHAR: N V A R C H A R;
NCHAR: N C H A R;
CHARACTER: C H A R A C T E R;
VARYING: V A R Y I N G;
TIMESTAMPLTZ: T I M E S T A M P L T Z;
TIMESTAMPTZ: T I M E S T A M P T Z;
GLOBAL: G L O B A L;
LOCAL: L O C A L;
ZONE: Z O N E;
DIRECTORY: D I R E C T O R Y;
FIELDS: F I E L D S;
SEQUENCE: S E Q U E N C E;
PIPE: P I P E;
PROCEDURE: P R O C E D U R E;
FUNCTION: F U N C T I O N;
GENERATOR: G E N E R A T O R;
ROWCOUNT: R O W C O U N T;
TIMELIMIT: T I M E L I M I T;
SPLIT_TO_TABLE: S P L I T '_' T O '_' T A B L E;
DELIMITER: D E L I M I T E R;
FLATTEN: F L A T T E N;
INPUT: I N P U T;
PATH: P A T H;
RECURSIVE: R E C U R S I V E;
MODE: M O D E;
ACCOUNT: A C C O U N T;
INTEGRATION: I N T E G R A T I O N;
NETWORK: N E T W O R K;
NETWORK_POLICY: N E T W O R K UNDERSCORE P O L I C Y;
RULE: R U L E;
RULES: R U L E S;
SECRET: S E C R E T;
SECRETS: S E C R E T S;
POLICY: P O L I C Y;
POLICIES: P O L I C I E S;
MASKING: M A S K I N G;
ROW: R O W;
NATURAL: N A T U R A L;
SAMPLE: S A M P L E;
TABLESAMPLE: T A B L E S A M P L E;
BERNOULLI: B E R N O U L L I;
SYSTEM: S Y S T E M;
SEED: S E E D;
REPEATABLE: R E P E A T A B L E;
REVERSE: R E V E R S E;
REPEAT: R E P E A T;
UNTIL: U N T I L;
ACCESS: A C C E S S;
SESSION: S E S S I O N;
TAG: T A G;
TAGS: T A G S;
ALERT: A L E R T;
ALERTS: A L E R T S;
ALLOWED_VALUES_SEQUENCE: A L L O W E D '_' V A L U E S '_' S E Q U E N C E;
CONDITION: C O N D I T I O N;
CONFIG: C O N F I G;
ON_CONFLICT: O N '_' C O N F L I C T;
PROPAGATE: P R O P A G A T E;
RUNBOOK: R U N B O O K;
SUSPEND_ALERT_AFTER_NUM_FAILURES: S U S P E N D '_' A L E R T '_' A F T E R '_' N U M '_' F A I L U R E S;
IMPORT: I M P O R T;
SHARE: S H A R E;
MANAGE: M A N A G E;
EXECUTION: E X E C U T I O N;
FILE: F I L E;
FORMAT: F O R M A T;
FORMATS: F O R M A T S;
RESOURCE: R E S O U R C E;
USE_ANY_ROLE: U S E UNDERSCORE A N Y UNDERSCORE R O L E;
RESOURCE_MONITOR : R E S O U R C E '_' M O N I T O R;
MASKING_POLICY : M A S K I N G '_' P O L I C Y;
ROW_ACCESS_POLICY : R O W '_' A C C E S S '_' P O L I C Y;
SESSION_POLICY : S E S S I O N '_' P O L I C Y;

// Cursor and ResultSet
CURSOR: C U R S O R;
RESULTSET: R E S U L T S E T;
OPEN: O P E N;
FETCH: F E T C H;
CLOSE: C L O S E;

// Data Types
INTEGER: I N T E G E R;
INT: I N T;
BIGINT: B I G I N T;
SMALLINT: S M A L L I N T;
TINYINT: T I N Y I N T;
BYTEINT: B Y T E I N T;
NUMBER: N U M B E R;
DECIMAL: D E C I M A L;
NUMERIC: N U M E R I C;
DEC: D E C;
FLOAT: F L O A T;
FLOAT4: F L O A T '4';
FLOAT8: F L O A T '8';
DOUBLE: D O U B L E;
REAL: R E A L;
PRECISION: P R E C I S I O N;
UUID: U U I D;
VECTOR: V E C T O R;
GEOGRAPHY: G E O G R A P H Y;
GEOMETRY: G E O M E T R Y;
VARCHAR: V A R C H A R;
STRING: S T R I N G;
TEXT: T E X T;
CHAR: C H A R;
BOOLEAN: B O O L E A N;
DATE: D A T E;
DATA: D A T A;
DATETIME: D A T E T I M E;
TIME: T I M E;
TIMESTAMP: T I M E S T A M P;
TIMESTAMP_NTZ: T I M E S T A M P UNDERSCORE N T Z;
TIMESTAMPNTZ: T I M E S T A M P N T Z;
TIMESTAMP_LTZ: T I M E S T A M P UNDERSCORE L T Z;
TIMESTAMP_TZ: T I M E S T A M P UNDERSCORE T Z;
VARIANT: V A R I A N T;
ARRAY: A R R A Y;
OBJECT: O B J E C T;
MAP: M A P;
BINARY: B I N A R Y;
VARBINARY: V A R B I N A R Y;

// Interval
INTERVAL: I N T E R V A L;
YEAR: Y E A R;
YEARS: Y E A R S;
MONTH: M O N T H;
MONTHS: M O N T H S;
DAY: D A Y;
DAYS: D A Y S;
HOUR: H O U R;
HOURS: H O U R S;
MINUTE: M I N U T E;
MINUTES: M I N U T E S;
SECOND: S E C O N D;
SECONDS: S E C O N D S;

// Transaction
BEGIN: B E G I N;
TRANSACTION: T R A N S A C T I O N;
WORK: W O R K;
NAME: N A M E;
COMMIT: C O M M I T;
ROLLBACK: R O L L B A C K;

// Procedural Language Keywords
DECLARE: D E C L A R E;
LET: L E T;
IF: I F;
EXISTS: E X I S T S;
ELSEIF: E L S E I F;
ELSE: E L S E;
END: E N D;
LOOP: L O O P;
WHILE: W H I L E;
FOR: F O R;
DO: D O;
RETURN: R E T U R N;
RETURNS: R E T U R N S;
BREAK: B R E A K;
CONTINUE: C O N T I N U E;
RAISE: R A I S E;
CALL: C A L L;
ASYNC: A S Y N C;
AWAIT: A W A I T;
EXECUTE: E X E C U T E;
OWNER: O W N E R;
CALLER: C A L L E R;
STRICT: S T R I C T;
IMMUTABLE: I M M U T A B L E;
MEMOIZABLE: M E M O I Z A B L E;
VOLATILE: V O L A T I L E;
SECURE: S E C U R E;
CALLED: C A L L E D;
IMMEDIATE: I M M E D I A T E;
CASE: C A S E;
CAST: C A S T;
TRY_CAST: T R Y '_' C A S T;
EXCEPTION: E X C E P T I O N;
OTHER: O T H E R;

// Identifiers
QUOTED_IDENTIFIER: '"' (~["\r\n] | '""')* '"';
POSITIONAL_PARAMETER: '$' [0-9]+;
SESSION_VAR_REF: '$' [a-zA-Z_][a-zA-Z0-9_]*;
SYSTEM_STREAM_HAS_DATA: S Y S T E M '$' S T R E A M '_' H A S '_' D A T A;
SYSTEM_USER_TASK_CANCEL: S Y S T E M '$' U S E R '_' T A S K '_' C A N C E L '_' O N G O I N G '_' E X E C U T I O N S;
// General SYSTEM$ function token — matches any remaining SYSTEM$NAME pattern
SYSTEM_FUNC: S Y S T E M '$' [A-Za-z_][A-Za-z0-9_]*;

// Unquoted identifiers may contain '$' (but not as the first character), per Snowflake — e.g.
// METADATA$ACTION, COL$1. Declared AFTER the SYSTEM$… / $-prefixed tokens so those specific tokens win the
// equal-length tie-break (ANTLR picks the earliest rule when match lengths are equal).
IDENTIFIER: [a-zA-Z_][a-zA-Z0-9_$]*;

// Literals
INTEGER_LITERAL: [0-9]+;
FLOAT_LITERAL: [0-9]+ DOT [0-9]* ([eE] [+-]? [0-9]+)? | DOT [0-9]+ ([eE] [+-]? [0-9]+)? | [0-9]+ [eE] [+-]? [0-9]+;   // Either side of the point may be empty — 1. is 1 and .5 is 0.5, typed as the full spelling is (1. NUMBER(1,0), .5 NUMBER(2,1)), with or without an exponent (1.e2 is 100, .5e1 is 5) — so 1. AS v is the number under an alias, never a field access on it, and 1.v the same written tighter; a second point after a complete number starts a second number, which no rule takes (1..2, .5.5). The last alternative is the DOT-less exponent (1e0, 1E+3, 1e-3), a number wherever a number is allowed. Without it the lexer split 1e0 into 1 and the identifier e0, which silently read as an ALIAS in a select list and was a syntax error everywhere else. An exponent is folded in before the type is taken, so these are FIXED-point: 1e0 is NUMBER(1,0) and 1e20 NUMBER(21,0).
MALFORMED_EXPONENT: [0-9]+ (DOT [0-9]*)? [eE] [+-]? | DOT [0-9]+ [eE] [+-]?;   // A digit run that OPENS an exponent and then stops — 1e, 12E, 1.5e, 1.e, .5e, 1e+ — which Snowflake reads as a malformed NUMBER rather than as a number beside an identifier: SELECT 1e FROM t is refused where the spaced SELECT 1 e FROM t names the column E. No parser rule uses this token, so every occurrence is a syntax error; it exists only to stop the lexer splitting the text into an INTEGER_LITERAL and an alias. Longest-match keeps the valid forms intact: FLOAT_LITERAL takes 1e5 and 1.5e5 whole, and in 1e1e it takes 1e1 and leaves e as the alias, exactly as live reads them.
STRING_LITERAL: '\'' ('\\' . | '\'\'' | ~['\\])* '\'';    // Single-quoted. Backslash is EXCLUDED from ~[..] so it always begins a '\\' . escape (incl. \'); otherwise maximal-munch lets \'' lex as \ + '' and mis-aligns the string boundaries. Decode via SqlStringLiterals.
DOLLAR_QUOTED_STRING: '$$' .*? '$$';

// Operators and Punctuation
SEMI: ';';
LPAREN: '(';
RPAREN: ')';
COMMA: ',';
DOT: '.';
LBRACKET: '[';
RBRACKET: ']';
LBRACE: '{';
RBRACE: '}';
QUESTION: '?';
COLON: ':';
COLON_EQ: ':=';
EQ: '=';
ARROW: '=>';
FLOW_ARROW: '->>';
THIN_ARROW: '->';
NEQ: '<>' | '!=';
LT: '<';
LTE: '<=';
GT: '>';
GTE: '>=';
PLUS: '+';

// Comments and whitespace before MINUS so '--' is matched as LINE_COMMENT not MINUS MINUS
// x'A1B2' — a hex binary literal (the engine's BINARY values are uppercase hex strings).
// Anything between the quotes, so a malformed body reaches the builder whole and is refused in live's
// own sentence ("Invalid binary literal X'0G'; ...") rather than lexing as a name and a string.
HEX_LITERAL: [xX] '\'' ~['\r\n]* '\'';

// An unquoted file/cloud URL in PUT/GET (Snowflake allows the unquoted form): scheme '://' then
// everything up to whitespace or a statement delimiter.
FILE_URL: (F I L E | S '3' | A Z U R E | G C S | H T T P S | H T T P) ':' '/' '/' ~[ \t\r\n',;)]*;

LINE_COMMENT: ('--' | '//') ~[\r\n]* -> skip;
BLOCK_COMMENT: '/*' .*? '*/' -> skip;
WS: [ \t\r\n]+ -> skip;

MINUS: '-';
STAR: '*';
DOUBLE_STAR: '**';
SLASH: '/';
PERCENT: '%';
TILDE: '~';
PIPE_PIPE: '||';
DOUBLE_COLON: '::';

// Case-insensitive fragments
fragment A: [aA];
fragment B: [bB];
fragment C: [cC];
fragment D: [dD];
fragment E: [eE];
fragment F: [fF];
fragment G: [gG];
fragment H: [hH];
fragment I: [iI];
fragment J: [jJ];
fragment K: [kK];
fragment L: [lL];
fragment M: [mM];
fragment N: [nN];
fragment O: [oO];
fragment P: [pP];
fragment Q: [qQ];
fragment R: [rR];
fragment S: [sS];
fragment T: [tT];
fragment U: [uU];
fragment V: [vV];
fragment W: [wW];
fragment X: [xX];
fragment Y: [yY];
fragment Z: [zZ];
fragment UNDERSCORE: '_';
