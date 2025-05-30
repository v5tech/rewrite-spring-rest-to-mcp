package org.openrewrite.java.spring.ai.mcp.visitor;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.J;

import java.util.Set;

/**
 * This visitor is used to find all classes that have the @Tool annotation.
 * It collects the fully qualified names of these classes into a set.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class McpToolVisitor extends JavaIsoVisitor<ExecutionContext> {

    @NotNull Set<String> toolSet;

    @Override
    public J.@NotNull ClassDeclaration visitClassDeclaration(J.@NotNull ClassDeclaration classDecl, @NotNull ExecutionContext ctx) {
        // Early return if no tool annotations are found, avoiding unnecessary checks
        if (classDecl.getBody() == null || classDecl.getType() == null) {
            return super.visitClassDeclaration(classDecl, ctx);
        }

        // Efficiently check for tool annotations in methods
        boolean toolFound = classDecl.getBody().getStatements().stream()
                .filter(statement -> statement instanceof J.MethodDeclaration) // Only check methods
                .map(statement -> (J.MethodDeclaration) statement)
                .anyMatch(method -> hasToolAnnotation(method));

        // If tool annotation found, add class type to toolSet
        if (toolFound) {
            toolSet.add(classDecl.getType().getFullyQualifiedName());
        }

        return super.visitClassDeclaration(classDecl, ctx);
    }

    private boolean hasToolAnnotation(J.MethodDeclaration method) {
        // Only call FindAnnotations once per method
        return !FindAnnotations.find(method, "@org.springframework.ai.tool.annotation.Tool").isEmpty();
    }
}
