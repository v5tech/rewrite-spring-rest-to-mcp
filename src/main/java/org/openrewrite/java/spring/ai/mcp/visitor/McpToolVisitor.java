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
        boolean toolFound = classDecl.getBody().getStatements().stream()
                .filter(s -> s instanceof J.MethodDeclaration)
                .map(s -> (J.MethodDeclaration) s)
                .anyMatch(m -> !FindAnnotations.find(m, "@org.springframework.ai.tool.annotation.Tool").isEmpty());
        if (toolFound && classDecl.getType() != null) {
            toolSet.add(classDecl.getType().getFullyQualifiedName());
        }
        return super.visitClassDeclaration(classDecl, ctx);
    }
}
