package org.openrewrite.java.spring.ai.mcp.visitor.gradle;

import org.jetbrains.annotations.NotNull;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class GradleMcpVisitor<P> extends GroovyIsoVisitor<P> {

    private static final Pattern MCP_DEPENDENCY_PATTERN =
            Pattern.compile("org\\.springframework\\.ai:spring-ai-starter-mcp-server-webmvc");

    private final AtomicBoolean aiMcpEnabled;

    public GradleMcpVisitor(AtomicBoolean aiMcpEnabled) {
        this.aiMcpEnabled = aiMcpEnabled;
    }

    // Match DependencyHandlerSpec methods, but also ensure we check for the actual argument type.
    private static final MethodMatcher DEPENDENCY_METHODS = new MethodMatcher(
            "DependencyHandlerSpec *(..)"
    );

    @Override
    public J.@NotNull MethodInvocation visitMethodInvocation(J.@NotNull MethodInvocation method, @NotNull P p) {
        if (DEPENDENCY_METHODS.matches(method)) {
            method.getArguments().forEach(arg -> {
                if (arg instanceof J.Literal) {
                    // Check if the argument is a string literal that matches the dependency pattern
                    String argumentValue = ((J.Literal) arg).getValue().toString();
                    if (MCP_DEPENDENCY_PATTERN.matcher(argumentValue).find()) {
                        aiMcpEnabled.set(true);
                    }
                }
            });
        }
        return super.visitMethodInvocation(method, p);
    }
}
