/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.lintrules.issues;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.checks.StringFormatDetector;
import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import lombok.ast.AstVisitor;
import lombok.ast.BinaryExpression;
import lombok.ast.BinaryOperator;
import lombok.ast.BooleanLiteral;
import lombok.ast.CharLiteral;
import lombok.ast.Expression;
import lombok.ast.ExpressionStatement;
import lombok.ast.FloatingPointLiteral;
import lombok.ast.If;
import lombok.ast.InlineIfExpression;
import lombok.ast.IntegralLiteral;
import lombok.ast.MethodInvocation;
import lombok.ast.Node;
import lombok.ast.NullLiteral;
import lombok.ast.StrictListAccessor;
import lombok.ast.StringLiteral;
import lombok.ast.VariableReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;

import static com.android.SdkConstants.GET_STRING_METHOD;
import static com.android.tools.lint.client.api.JavaParser.TYPE_BOOLEAN;
import static com.android.tools.lint.client.api.JavaParser.TYPE_BYTE;
import static com.android.tools.lint.client.api.JavaParser.TYPE_CHAR;
import static com.android.tools.lint.client.api.JavaParser.TYPE_DOUBLE;
import static com.android.tools.lint.client.api.JavaParser.TYPE_FLOAT;
import static com.android.tools.lint.client.api.JavaParser.TYPE_INT;
import static com.android.tools.lint.client.api.JavaParser.TYPE_LONG;
import static com.android.tools.lint.client.api.JavaParser.TYPE_NULL;
import static com.android.tools.lint.client.api.JavaParser.TYPE_OBJECT;
import static com.android.tools.lint.client.api.JavaParser.TYPE_SHORT;
import static com.android.tools.lint.client.api.JavaParser.TYPE_STRING;

public class WrongTimberUsageDetector extends Detector implements Detector.JavaScanner {

    public static final Issue ISSUE_LOG = Issue.create("com.waz.LogNotTimber",
                                                       "Logging call to Log instead of Timber",
                                                       "Since Timber is included in the project, it is likely that calls to Log should "
                                                       + "instead be going to Timber.",
                                                       Category.MESSAGES,
                                                       5,
                                                       Severity.WARNING,
                                                       new Implementation(WrongTimberUsageDetector.class,
                                                                          Scope.JAVA_FILE_SCOPE));
    public static final Issue ISSUE_FORMAT = Issue.create("com.waz.StringFormatInTimber",
                                                          "Logging call with Timber contains String.format",
                                                          "Since Timber handles String.format automatically, you may not use String.format()",
                                                          Category.MESSAGES,
                                                          5,
                                                          Severity.WARNING,
                                                          new Implementation(WrongTimberUsageDetector.class,
                                                                             Scope.JAVA_FILE_SCOPE));

    public static final Issue ISSUE_D_USAGE = Issue.create("com.waz.TimberDDetector",
                                                           "Using `timber.log.Timber#d()` not allowed",
                                                           "We decided to not use `timber.log.Timber#d()` in master. If you really want to keep this log, " +
                                                           "replace it with another suitable `timber.log.Timber` method.",
                                                           Category.MESSAGES,
                                                           5,
                                                           Severity.WARNING,
                                                           new Implementation(WrongTimberUsageDetector.class,
                                                                              Scope.JAVA_FILE_SCOPE));
    public static final Issue ISSUE_THROWABLE = Issue.create("com.waz.ThrowableNotAtBeginning",
                                                             "Exception in Timber#e() not at the beginning",
                                                             "In Timber you have to pass a Throwable at the beginning of the call.",
                                                             Category.MESSAGES,
                                                             5,
                                                             Severity.WARNING,
                                                             new Implementation(WrongTimberUsageDetector.class,
                                                                                Scope.JAVA_FILE_SCOPE));
    public static final Issue ISSUE_BINARY = Issue.create("com.waz.BinaryOperationInTimber",
                                                          "Use String#format()",
                                                          "Since Timber handles String#format() automatically, use this instead of +",
                                                          Category.MESSAGES,
                                                          5,
                                                          Severity.WARNING,
                                                          new Implementation(WrongTimberUsageDetector.class,
                                                                             Scope.JAVA_FILE_SCOPE));
    public static final Issue ISSUE_ARG_COUNT = Issue.create("com.waz.TimberArgCount",
                                                             "Formatting argument types incomplete or inconsistent",
                                                             "When a formatted string takes arguments, you need to pass " +
                                                             "at least that amount of arguments to the formatting call.",
                                                             Category.MESSAGES,
                                                             9,
                                                             Severity.ERROR,
                                                             new Implementation(WrongTimberUsageDetector.class,
                                                                                Scope.JAVA_FILE_SCOPE));
    public static final Issue ISSUE_ARG_TYPES = Issue.create("com.waz.TimberArgTypes",
                                                             "Formatting string doesn't match passed arguments",
                                                             "The argument types that you specified in your formatting " +
                                                             "string does not match the types of the arguments that you " +
                                                             "passed to your formatting call.",
                                                             Category.MESSAGES,
                                                             9,
                                                             Severity.ERROR,
                                                             new Implementation(WrongTimberUsageDetector.class,
                                                                                Scope.JAVA_FILE_SCOPE));

    public WrongTimberUsageDetector() {
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("format", "v", "d", "i", "w", "e", "wtf");
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, AstVisitor visitor, @NonNull MethodInvocation node) {
        String methodName = node.astName().getDescription();
        if ("format".equals(methodName)) {
            if (!(node.astOperand() instanceof VariableReference)) {
                return;
            }
            VariableReference ref = (VariableReference) node.astOperand();
            if (!"String".equals(ref.astIdentifier().astValue())) {
                return;
            }
            // Found a String.format call
            // Look outside to see if we inside of a Timber call
            Node current = node.getParent();
            while (current != null && !(current instanceof ExpressionStatement)) {
                current = current.getParent();
            }
            if (current == null) {
                return;
            }
            ExpressionStatement statement = (ExpressionStatement) current;
            if (!statement.toString().startsWith("Timber.")) {
                return;
            }
            context.report(ISSUE_FORMAT,
                           node,
                           context.getLocation(node),
                           "Using 'String.format' instead of 'Timber'");
        } else {
            if (node.astOperand() instanceof VariableReference) {
                VariableReference ref = (VariableReference) node.astOperand();
                if ("Log".equals(ref.astIdentifier().astValue())) {
                    context.report(ISSUE_LOG,
                                   node,
                                   context.getLocation(node),
                                   "Using 'Log' instead of 'Timber'");
                    return;
                }
                String callName = node.astName().astValue();
                if ("d".equals(callName)) {
                    String message = String.format("Use another method from timber.log.Timber or remove %1$s()", callName);
                    context.report(ISSUE_D_USAGE, node, context.getLocation(node), message);
                }
                checkThrowablePosition(context, node);
                checkArguments(context, node);
            }
        }
    }

    private void checkArguments(JavaContext context, MethodInvocation node) {
        StrictListAccessor<Expression, MethodInvocation> astArguments = node.astArguments();
        Iterator<Expression> iterator = astArguments.iterator();
        if (!iterator.hasNext()) {
            return;
        }
        int startIndexOfArguments = 1;
        Expression formatStringArg = iterator.next();
        if (formatStringArg instanceof VariableReference) {
            if (isSubclassOf(context, (VariableReference) formatStringArg, Exception.class)) {
                formatStringArg = iterator.next();
                startIndexOfArguments++;
            }
        }

        String formatString = findLiteralValue(context, formatStringArg);
        // We passed for example a method call
        if (formatString == null) {
            return;
        }
        int argumentCount = getFormatArgumentCount(formatString);
        int passedArgCount = astArguments.size() - startIndexOfArguments;
        if (argumentCount < passedArgCount) {
            context.report(ISSUE_ARG_COUNT,
                           node,
                           context.getLocation(node),
                           String.format("Wrong argument count, format string `%1$s` requires `%2$d` but format " +
                                         "call supplies `%3$d`",
                                         formatString,
                                         argumentCount,
                                         passedArgCount));
            return;
        }

        if (argumentCount == 0) {
            return;
        }

        List<String> types = getStringArgumentTypes(formatString);
        Expression argument = null;
        boolean valid = true;
        for (int i = 0; i < types.size(); i++) {
            String formatType = types.get(i);
            if (iterator.hasNext()) {
                argument = iterator.next();
            } else {
                context.report(ISSUE_ARG_COUNT,
                               node,
                               context.getLocation(node),
                               String.format("Wrong argument count, format string `%1$s` requires `%2$d` but format " +
                                             "call supplies `%3$d`",
                                             formatString,
                                             argumentCount,
                                             passedArgCount));
            }

            char last = formatType.charAt(formatType.length() - 1);
            if (formatType.length() >= 2 &&
                Character.toLowerCase(formatType.charAt(formatType.length() - 2)) == 't') {
                // Date time conversion.
                // TODO
                continue;
            }
            Class type = getType(context, argument);
            if (type != null) {
                switch (last) {
                    // Booleans. It's okay to pass objects to these;
                    // it will print "true" if non-null, but it's
                    // unusual and probably not intended.
                    case 'b':
                    case 'B':
                        valid = type == Boolean.TYPE;
                        break;
                    // Numeric: integer and floats in various formats
                    case 'x':
                    case 'X':
                    case 'd':
                    case 'o':
                    case 'e':
                    case 'E':
                    case 'f':
                    case 'g':
                    case 'G':
                    case 'a':
                    case 'A':
                        valid = type == Integer.TYPE ||
                                type == Float.TYPE ||
                                type == Double.TYPE ||
                                type == Long.TYPE ||
                                type == Byte.TYPE ||
                                type == Short.TYPE;
                        break;
                    case 'c':
                    case 'C':
                        // Unicode character
                        valid = type == Character.TYPE;
                        break;
                    case 'h':
                    case 'H': // Hex print of hash code of objects
                    case 's':
                    case 'S':
                        // String. Can pass anything, but warn about
                        // numbers since you may have meant more
                        // specific formatting. Use special issue
                        // explanation for this?
                        valid = type != Boolean.TYPE &&
                                !Number.class.isAssignableFrom(type);
                        break;
                }
                if (!valid) {
                    String message = String.format(
                        "Wrong argument type for formatting argument '#%1$d' " +
                        "in `%2$s`: conversion is '`%3$s`', received `%4$s` " +
                        "(argument #%5$d in method call)",
                        i, formatString, formatType, type.getSimpleName(),
                        startIndexOfArguments + i + 1);
                    context.report(ISSUE_ARG_TYPES,
                                   node,
                                   context.getLocation(argument),
                                   message);
                }
            }
        }
    }

    private Class<?> getType(JavaContext context, Expression expression) {
        if (expression == null) {
            return null;
        }

        if (expression instanceof MethodInvocation) {
            MethodInvocation method = (MethodInvocation) expression;
            String methodName = method.astName().astValue();
            if (methodName.equals(GET_STRING_METHOD)) {
                return String.class;
            }
        } else if (expression instanceof StringLiteral) {
            return String.class;
        } else if (expression instanceof IntegralLiteral) {
            return Integer.TYPE;
        } else if (expression instanceof FloatingPointLiteral) {
            return Float.TYPE;
        } else if (expression instanceof CharLiteral) {
            return Character.TYPE;
        } else if (expression instanceof BooleanLiteral) {
            return Boolean.TYPE;
        } else if (expression instanceof NullLiteral) {
            return Object.class;
        }

        if (context != null) {
            JavaParser.TypeDescriptor type = context.getType(expression);
            if (type != null) {
                Class<?> typeClass = getTypeClass(type);
                if (typeClass != null) {
                    return typeClass;
                } else {
                    return Object.class;
                }
            }
        }

        return null;
    }

    private static Class<?> getTypeClass(@Nullable JavaParser.TypeDescriptor type) {
        if (type != null) {
            return getTypeClass(type.getName());
        }
        return null;
    }

    private static Class<?> getTypeClass(@Nullable String typeClassName) {
        if (typeClassName == null) {
            return null;
        } else if (typeClassName.equals(TYPE_STRING) ||
                   "String".equals(typeClassName)) {
            return String.class;
        } else if (typeClassName.equals(TYPE_INT)) {
            return Integer.TYPE;
        } else if (typeClassName.equals(TYPE_BOOLEAN)) {
            return Boolean.TYPE;
        } else if (typeClassName.equals(TYPE_NULL)) {
            return Object.class;
        } else if (typeClassName.equals(TYPE_LONG)) {
            return Long.TYPE;
        } else if (typeClassName.equals(TYPE_FLOAT)) {
            return Float.TYPE;
        } else if (typeClassName.equals(TYPE_DOUBLE)) {
            return Double.TYPE;
        } else if (typeClassName.equals(TYPE_CHAR)) {
            return Character.TYPE;
        } else if ("BigDecimal".equals(typeClassName) ||
                   "java.math.BigDecimal".equals(typeClassName)) {
            return Float.TYPE;
        } else if ("BigInteger".equals(typeClassName) ||
                   "java.math.BigInteger".equals(typeClassName)) {
            return Integer.TYPE;
        } else if (typeClassName.equals(TYPE_OBJECT)) {
            return null;
        } else if (typeClassName.startsWith("java.lang.")) {
            if ("java.lang.Integer".equals(typeClassName) ||
                "java.lang.Short".equals(typeClassName) ||
                "java.lang.Byte".equals(typeClassName) ||
                "java.lang.Long".equals(typeClassName)) {
                return Integer.TYPE;
            } else if ("java.lang.Float".equals(typeClassName) ||
                       "java.lang.Double".equals(typeClassName)) {
                return Float.TYPE;
            } else {
                return null;
            }
        } else if (typeClassName.equals(TYPE_BYTE)) {
            return Byte.TYPE;
        } else if (typeClassName.equals(TYPE_SHORT)) {
            return Short.TYPE;
        } else {
            return null;
        }
    }

    private boolean isSubclassOf(JavaContext context, VariableReference variableReference, Class clazz) {
        JavaParser.ResolvedNode resolved = context.resolve(variableReference);
        if (resolved instanceof JavaParser.ResolvedVariable) {
            JavaParser.ResolvedClass typeClass = ((JavaParser.ResolvedVariable) resolved).getType().getTypeClass();
            return (typeClass != null && typeClass.isSubclassOf(clazz.getName(), false));
        }
        return false;
    }

    private List<String> getStringArgumentTypes(String formatString) {
        List<String> types = new ArrayList<>();
        Matcher matcher = StringFormatDetector.FORMAT.matcher(formatString);
        int index = 0;
        int prevIndex = 0;
        int nextNumber = 1;
        while (true) {
            if (matcher.find(index)) {
                int matchStart = matcher.start();
                // Make sure this is not an escaped '%'
                for (; prevIndex < matchStart; prevIndex++) {
                    char c = formatString.charAt(prevIndex);
                    if (c == '\\') {
                        prevIndex++;
                    }
                }
                if (prevIndex > matchStart) {
                    // We're in an escape, ignore this result
                    index = prevIndex;
                    continue;
                }

                index = matcher.end(); // Ensure loop proceeds
                String str = formatString.substring(matchStart, matcher.end());
                if ("%%".equals(str) || "%n".equals(str)) {
                    // Just an escaped %
                    continue;
                }

                // Shouldn't throw a number format exception since we've already
                // matched the pattern in the regexp
                int number;
                String numberString = matcher.group(1);
                if (numberString != null) {
                    numberString = numberString.substring(0, numberString.length() - 1);
                    number = Integer.parseInt(numberString);
                    nextNumber = number + 1;
                } else {
                    number = nextNumber++;
                }
                String format = matcher.group(6);
                types.add(format);
            } else {
                break;
            }
        }
        return types;
    }

    private static String findLiteralValue(@NonNull JavaContext context, @NonNull Node argument) {
        if (argument instanceof StringLiteral) {
            return ((StringLiteral) argument).astValue();
        } else if (argument instanceof BinaryExpression) {
            BinaryExpression expression = (BinaryExpression) argument;
            if (expression.astOperator() == BinaryOperator.PLUS) {
                String left = findLiteralValue(context, expression.astLeft());
                String right = findLiteralValue(context, expression.astRight());
                if (left != null && right != null) {
                    return left + right;
                }
            }
        } else {
            JavaParser.ResolvedNode resolved = context.resolve(argument);
            if (resolved instanceof JavaParser.ResolvedField) {
                JavaParser.ResolvedField field = (JavaParser.ResolvedField) resolved;
                Object value = field.getValue();
                if (value instanceof String) {
                    return (String) value;
                }
            }
        }

        return null;
    }

    private int getFormatArgumentCount(@NonNull String s) {
        Matcher matcher = StringFormatDetector.FORMAT.matcher(s);
        int index = 0;
        int prevIndex = 0;
        int nextNumber = 1;
        int max = 0;
        while (true) {
            if (matcher.find(index)) {
                String value = matcher.group(6);
                if ("%".equals(value) || "n".equals(value)) { //$NON-NLS-1$ //$NON-NLS-2$
                    index = matcher.end();
                    continue;
                }
                int matchStart = matcher.start();
                // Make sure this is not an escaped '%'
                for (; prevIndex < matchStart; prevIndex++) {
                    char c = s.charAt(prevIndex);
                    if (c == '\\') {
                        prevIndex++;
                    }
                }
                if (prevIndex > matchStart) {
                    // We're in an escape, ignore this result
                    index = prevIndex;
                    continue;
                }

                // Shouldn't throw a number format exception since we've already
                // matched the pattern in the regexp
                int number;
                String numberString = matcher.group(1);
                if (numberString != null) {
                    // Strip off trailing $
                    numberString = numberString.substring(0, numberString.length() - 1);
                    number = Integer.parseInt(numberString);
                    nextNumber = number + 1;
                } else {
                    number = nextNumber++;
                }
                if (number > max) {
                    max = number;
                }
                index = matcher.end();
            } else {
                break;
            }
        }

        return max;
    }

    private void checkThrowablePosition(JavaContext context, MethodInvocation node) {
        int index = 0;
        for (Node argument : node.astArguments()) {
            if (checkNode(context, node, argument)) {
                break;
            }
            if (argument instanceof VariableReference) {
                if (isSubclassOf(context, (VariableReference) argument, Exception.class) && index > 0) {
                    context.report(ISSUE_THROWABLE,
                                   node,
                                   context.getLocation(node),
                                   "Please use exception as first argument");
                }
            }
            index++;
        }
    }

    private boolean checkNode(JavaContext context, MethodInvocation node, Node argument) {
        if (argument instanceof BinaryExpression) {
            context.report(ISSUE_BINARY,
                           node,
                           context.getLocation(argument),
                           "Replace usage of + with String.format");
            return true;
        } else if (argument instanceof If ||
                   argument instanceof InlineIfExpression) {
            return checkConditionalUsage(context, node, argument);
        }
        return false;
    }

    private boolean checkConditionalUsage(JavaContext context, MethodInvocation node, Node argument) {
        Node thenStatement;
        Node elseStatement;
        if (argument instanceof If) {
            thenStatement = ((If) argument).astStatement();
            elseStatement = ((If) argument).astElseStatement();
        } else if (argument instanceof InlineIfExpression) {
            thenStatement = ((InlineIfExpression) argument).astIfFalse();
            elseStatement = ((InlineIfExpression) argument).astIfTrue();
        } else {
            return false;
        }
        if (!checkNode(context, node, thenStatement)) {
            return checkNode(context, node, elseStatement);
        }
        return false;
    }
}
