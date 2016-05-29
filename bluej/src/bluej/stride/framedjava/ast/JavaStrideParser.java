package bluej.stride.framedjava.ast;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import bluej.parser.JavaParser;
import bluej.parser.lexer.JavaLexer;
import bluej.parser.lexer.JavaTokenTypes;
import bluej.parser.lexer.LocatableToken;
import bluej.stride.framedjava.elements.CallElement;
import bluej.stride.framedjava.elements.CodeElement;
import bluej.stride.framedjava.elements.ConstructorElement;
import bluej.stride.framedjava.elements.IfElement;
import bluej.stride.framedjava.elements.NormalMethodElement;
import bluej.stride.framedjava.elements.ReturnElement;
import bluej.stride.framedjava.elements.WhileElement;
import bluej.utility.JavaUtils;
import bluej.utility.Utility;

/**
 * Created by neil on 29/05/2016.
 */
class JavaStrideParser extends JavaParser
{
    private final String source;
    private final Stack<ExpressionHandler> expressionHandlers = new Stack<>();
    private final Stack<StatementHandler> statementHandlers = new Stack<>();
    private final Stack<ArgumentListHandler> argumentHandlers = new Stack<>();
    private List<CodeElement> result = null;
    private final Stack<MethodDetails> methods = new Stack<>();
    private final Stack<String> types = new Stack<>();
    private final List<String> comments = new Stack<>();

    private final List<String> warnings = new ArrayList<>();
    private int startThrows;

    JavaStrideParser(String java)
    {
        super(new StringReader(java), true);
        this.source = java;
        statementHandlers.push(r -> this.result = r);
    }
    
    private static class MethodDetails
    {
        public final String name;
        public final List<LocatableToken> modifiers = new ArrayList<>();
        public final List<ParamFragment> parameters = new ArrayList<>();
        public final List<String> throwsTypes = new ArrayList<>();
        public final String comment; // may be null
        public String constructorCall; // may be null
        public List<String> constructorArgs; // may be null

        public MethodDetails(String name, List<LocatableToken> modifiers, String comment)
        {
            this.name = name;
            this.modifiers.addAll(modifiers);
            this.comment = comment;
        }
    }

    private static interface StatementHandler
    {
        public void foundStatement(List<CodeElement> statements);

        default public void endBlock()
        {
        }

        ;
    }

    private static interface ExpressionHandler
    {
        public void expressionBegun(LocatableToken start);

        public void expressionEnd(LocatableToken end);
    }

    private static interface ArgumentListHandler
    {
        public void argumentListBegun();

        public void gotArgument();

        public void argumentListEnd();
    }

    private class BlockCollector implements StatementHandler
    {
        private final List<CodeElement> content = new ArrayList<>();

        @Override
        public void foundStatement(List<CodeElement> statements)
        {
            content.addAll(statements);
            // We keep ourselves on the stack until someone removes us:
            withStatement(this);
        }

        public List<CodeElement> getContent()
        {
            return content;
        }
    }

    private class IfBuilder implements StatementHandler
    {
        // Size is always >= 1, and either equal to blocks.size, or one less than blocks.size (if last one is else)
        private final ArrayList<FilledExpressionSlotFragment> conditions = new ArrayList<>();
        private final ArrayList<List<CodeElement>> blocks = new ArrayList<>();

        public IfBuilder(String condition)
        {
            this.conditions.add(toFilled(condition));
        }

        public void addCondBlock()
        {
            blocks.add(new ArrayList<>());
            withStatement(this);
        }

        public void addElseIf()
        {
            withExpression(e -> conditions.add(toFilled(e)));
        }

        public void endIf()
        {
            JavaStrideParser.this.foundStatement(new IfElement(null,
                conditions.get(0), blocks.get(0),
                conditions.subList(1, conditions.size()), blocks.subList(1, conditions.size()),
                blocks.size() > conditions.size() ? blocks.get(blocks.size() - 1) : null,
                true
            ));
        }

        @Override
        public void foundStatement(List<CodeElement> statements)
        {
            blocks.get(blocks.size() - 1).addAll(statements);
        }
    }

    @Override
    protected void beginWhileLoop(LocatableToken token)
    {
        super.beginWhileLoop(token);
        withExpression(exp -> {
            withStatement(body -> {
                foundStatement(new WhileElement(null, toFilled(exp), body, true));
            });
        });
    }

    @Override
    protected void beginIfStmt(LocatableToken token)
    {
        super.beginIfStmt(token);
        withExpression(exp -> {
            withStatement(new IfBuilder(exp));
        });
    }

    @Override
    protected void beginIfCondBlock(LocatableToken token)
    {
        super.beginIfCondBlock(token);
        getIfBuilder(false).addCondBlock();
    }

    @Override
    protected void gotElseIf(LocatableToken token)
    {
        super.gotElseIf(token);
        getIfBuilder(false).addElseIf();
    }

    @Override
    protected void endIfStmt(LocatableToken token, boolean included)
    {
        super.endIfStmt(token, included);
        getIfBuilder(true).endIf();
    }

    // If true, pop it from stack.  If false, peek and leave it on stack
    private IfBuilder getIfBuilder(boolean pop)
    {
        if (statementHandlers.peek() instanceof IfBuilder)
            return (IfBuilder)(pop ? statementHandlers.pop() : statementHandlers.peek());
        else
            return null;
    }

    private FilledExpressionSlotFragment toFilled(String exp)
    {
        return new FilledExpressionSlotFragment(exp, exp);
    }

    private OptionalExpressionSlotFragment toOptional(String exp)
    {
        return new OptionalExpressionSlotFragment(exp, exp);
    }

    @Override
    protected void gotReturnStatement(boolean hasValue)
    {
        super.gotReturnStatement(hasValue);
        if (hasValue)
            withExpression(exp -> foundStatement(new ReturnElement(null, toOptional(exp), true)));
        else
            foundStatement(new ReturnElement(null, toOptional(""), true));
    }

    @Override
    protected void gotEmptyStatement()
    {
        super.gotEmptyStatement();
        foundStatements(Collections.emptyList());
    }

    @Override
    protected void gotStatementExpression()
    {
        super.gotStatementExpression();
        withExpression(e -> foundStatement(new CallElement(null, new CallExpressionSlotFragment(e, e), true)));
    }

    @Override
    protected void beginExpression(LocatableToken token)
    {
        super.beginExpression(token);
        if (!expressionHandlers.isEmpty())
            expressionHandlers.peek().expressionBegun(token);
    }

    @Override
    protected void endExpression(LocatableToken token, boolean emptyExpression)
    {
        super.endExpression(token, emptyExpression);
        expressionHandlers.pop().expressionEnd(token);
    }

    @Override
    protected void beginStmtblockBody(LocatableToken token)
    {
        super.beginStmtblockBody(token);
        withStatement(new StatementHandler()
        {
            final ArrayList<CodeElement> block = new ArrayList<>();

            @Override
            public void endBlock()
            {
                // We were just collecting the block -- pass to parent handler:
                foundStatements(block);
            }

            @Override
            public void foundStatement(List<CodeElement> statements)
            {
                block.addAll(statements);
                //By default we are popped; re-add:
                withStatement(this);
            }
        });
    }

    @Override
    protected void endStmtblockBody(LocatableToken token, boolean included)
    {
        super.endStmtblockBody(token, included);
        statementHandlers.pop().endBlock();
    }

    @Override
    protected void beginThrows(LocatableToken token)
    {
        super.beginThrows(token);
        startThrows = types.size();
    }

    @Override
    protected void endThrows()
    {
        super.endThrows();
        while (types.size() > startThrows)
        {
            methods.peek().throwsTypes.add(types.pop());
        }
        Collections.reverse(methods.peek().throwsTypes);
    }

    @Override
    protected void gotConstructorDecl(LocatableToken token, LocatableToken hiddenToken, List<LocatableToken> modifiers)
    {
        super.gotConstructorDecl(token, hiddenToken, modifiers);
        methods.push(new MethodDetails(null, modifiers, getJavadoc()));
        withStatement(new BlockCollector());
    }

    @Override
    protected void gotMethodDeclaration(LocatableToken nameToken, LocatableToken hiddenToken, List<LocatableToken> modifiers)
    {
        super.gotMethodDeclaration(nameToken, hiddenToken, modifiers);
        methods.push(new MethodDetails(nameToken.getText(), modifiers, getJavadoc()));
        withStatement(new BlockCollector());
    }

    @Override
    protected void endMethodDecl(LocatableToken token, boolean included)
    {
        super.endMethodDecl(token, included);
        List<CodeElement> body = ((BlockCollector)statementHandlers.pop()).getContent();
        MethodDetails details = methods.pop();
        String name = details.name;
        List<ThrowsTypeFragment> throwsTypes = details.throwsTypes.stream().map(t -> new ThrowsTypeFragment(new TypeSlotFragment(t, t))).collect(Collectors.toList());
        List<LocatableToken> modifiers = details.modifiers;
        // If they make the item package-visible, we will turn this into protected:
        AccessPermission permission = AccessPermission.PROTECTED;
        // These are not else-if, so that we remove all recognised modifiers:
        if (modifiers.removeIf(t -> t.getText().equals("private")))
            permission = AccessPermission.PRIVATE;
        if (modifiers.removeIf(t -> t.getText().equals("protected")))
            permission = AccessPermission.PROTECTED;
        if (modifiers.removeIf(t -> t.getText().equals("public")))
            permission = AccessPermission.PUBLIC;
        if (name != null)
        {
            boolean _final = modifiers.removeIf(t -> t.getText().equals("final"));
            boolean _static = modifiers.removeIf(t -> t.getText().equals("static"));
            // Any remaining are unrecognised:
            modifiers.forEach(t -> warnings.add("Unsupported method modifier: " + t.getText()));
            String type = types.pop();
            foundStatement(new NormalMethodElement(null, new AccessPermissionFragment(permission),
                _static, _final, new TypeSlotFragment(type, type), new NameDefSlotFragment(name), details.parameters,
                throwsTypes, body, new JavadocUnit(details.comment), true));
        }
        else
        {
            // Any remaining are unrecognised:
            modifiers.forEach(t -> warnings.add("Unsupported method modifier: " + t.getText()));
            SuperThis delegate = SuperThis.fromString(details.constructorCall);
            String delegateArgs = delegate == null ? null : details.constructorArgs.stream().collect(Collectors.joining(","));
            foundStatement(new ConstructorElement(null, new AccessPermissionFragment(permission),
                details.parameters,
                throwsTypes, delegate == null ? null : new SuperThisFragment(delegate), delegateArgs == null ? null : new SuperThisParamsExpressionFragment(delegateArgs, delegateArgs), body, new JavadocUnit(details.comment), true));
        }
    }

    @Override
    protected void gotTypeSpec(List<LocatableToken> tokens)
    {
        super.gotTypeSpec(tokens);
        types.add(tokens.stream().map(LocatableToken::getText).collect(Collectors.joining()));
    }

    @Override
    protected void gotMethodParameter(LocatableToken token, LocatableToken ellipsisToken)
    {
        super.gotMethodParameter(token, ellipsisToken);
        if (ellipsisToken != null) //TODO or support it?
            warnings.add("Unsupported feature: varargs");
        String type = types.pop();
        methods.peek().parameters.add(new ParamFragment(new TypeSlotFragment(type, type), new NameDefSlotFragment(token.getText())));
    }

    @Override
    protected void gotConstructorCall(LocatableToken token)
    {
        super.gotConstructorCall(token);
        MethodDetails method = methods.peek();
        method.constructorCall = token.getText();
        // This will be parsed as an expression statement, so we need to cancel
        // that and replace with our own handler to discard:
        expressionHandlers.pop();
        withExpression(e -> {
        });
        withArgumentList(args -> {
            method.constructorArgs = args;
        });
    }

    @Override
    public void gotComment(LocatableToken token)
    {
        super.gotComment(token);
        String comment = token.getText();
        if (comment.startsWith("//"))
            comment = comment.substring(2).trim();
        else
            comment = JavaUtils.javadocToString(comment);
        comment = Arrays.stream(Utility.split(comment, System.getProperty("line.separator")))
            .map(String::trim)
            .reduce((a, b) -> {
                a = a.isEmpty() ? "\n" : a;
                if (a.endsWith("\n"))
                    return a + (b.isEmpty() ? "\n" : b);
                else if (b.isEmpty())
                    return a + "\n";
                else
                    return a + " " + b;
            }).orElse("");
        comments.add(comment);
    }

    @Override
    protected void beginArgumentList(LocatableToken token)
    {
        super.beginArgumentList(token);
        if (!argumentHandlers.isEmpty())
            argumentHandlers.peek().argumentListBegun();
    }

    @Override
    protected void endArgumentList(LocatableToken token)
    {
        super.endArgumentList(token);
        if (!argumentHandlers.isEmpty())
            argumentHandlers.peek().argumentListEnd();
    }

    @Override
    protected void endArgument()
    {
        super.endArgument();
        if (!argumentHandlers.isEmpty())
            argumentHandlers.peek().gotArgument();
    }

    private String getJavadoc()
    {
        if (!comments.isEmpty())
            return comments.remove(comments.size() - 1);
        else
            return null;
    }

    private String getText(LocatableToken start, LocatableToken end)
    {
        return source.substring(start.getPosition(), end.getPosition());
    }

    private void withExpression(Consumer<String> handler)
    {
        expressionHandlers.push(new ExpressionHandler()
        {
            LocatableToken start;
            // Amount of expressions begun but not ending:
            int outstanding = 0;

            @Override
            public void expressionBegun(LocatableToken start)
            {
                // Only record first begin:
                if (outstanding == 0)
                    this.start = start;
                outstanding += 1;
            }

            @Override
            public void expressionEnd(LocatableToken end)
            {
                outstanding -= 1;
                if (outstanding == 0)
                    handler.accept(replaceInstanceof(getText(start, end)));
                else
                    // We get popped by default; add ourselves back in:
                    expressionHandlers.push(this);
            }
        });
    }

    private void withStatement(StatementHandler handler)
    {
        statementHandlers.push(handler);
    }

    private void withArgumentList(Consumer<List<String>> argHandler)
    {
        argumentHandlers.push(new ArgumentListHandler()
        {
            final List<String> args = new ArrayList<String>();
            int outstanding = 0;

            @Override
            public void argumentListBegun()
            {
                outstanding += 1;
                if (outstanding == 1)
                {
                    withExpression(args::add);
                }
            }

            @Override
            public void gotArgument()
            {
                if (outstanding == 1)
                {
                    withExpression(args::add);
                }
            }

            @Override
            public void argumentListEnd()
            {
                if (outstanding == 1)
                {
                    expressionHandlers.pop();
                    argHandler.accept(args);
                    argumentHandlers.pop();
                }
                outstanding -= 1;
            }
        });
    }

    private void foundStatement(CodeElement statement)
    {
        foundStatements(Collections.singletonList(statement));
    }

    private void foundStatements(List<CodeElement> statements)
    {
        statementHandlers.pop().foundStatement(statements);
    }

    public List<CodeElement> getCodeElements()
    {
        return result;
    }

    // package-visible for testing
    static String replaceInstanceof(String src)
    {
        // It is a bit inefficient to re-lex the string, but
        // it's easiest this way and conversion is not particularly time sensitive:
        JavaLexer lexer = new JavaLexer(new StringReader(src));
        StringBuilder r = new StringBuilder();
        while (true)
        {
            LocatableToken token = lexer.nextToken();
            if (token.getType() == JavaTokenTypes.EOF)
                return r.toString();
            if (r.length() != 0)
                r.append(" ");
            if (token.getType() == JavaTokenTypes.LITERAL_instanceof)
                r.append("<:");
            else
                r.append(token.getText());
        }
    }

}