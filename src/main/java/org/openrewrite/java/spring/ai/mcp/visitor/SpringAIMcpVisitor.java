package org.openrewrite.java.spring.ai.mcp.visitor;

import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openrewrite.SourceFile;
import org.openrewrite.java.spring.ai.mcp.visitor.gradle.GradleMcpVisitor;
import org.openrewrite.java.spring.ai.mcp.visitor.maven.MavenMcpVisitor;

import java.util.concurrent.atomic.AtomicBoolean;

public class SpringAIMcpVisitor<P> extends TreeVisitor<Tree, P> {
    private final AtomicBoolean aiMcpEnabled;

    // Caching visitors to avoid unnecessary re-creation
    private final MavenMcpVisitor<P> mavenVisitor;
    private final GradleMcpVisitor<P> gradleVisitor;

    public SpringAIMcpVisitor(AtomicBoolean aiMcpEnabled) {
        this.aiMcpEnabled = aiMcpEnabled;
        // Initialize Maven and Gradle visitors once
        this.mavenVisitor = new MavenMcpVisitor<>(aiMcpEnabled);
        this.gradleVisitor = new GradleMcpVisitor<>(aiMcpEnabled);
    }

    @Override
    public @Nullable Tree visit(@Nullable Tree tree, @NotNull P p) {
        if (tree == null || aiMcpEnabled.get()) {
            return tree; // Early return to avoid unnecessary processing
        }

        if (!(tree instanceof SourceFile sourceFile)) {
            return tree; // Only process SourceFile nodes
        }

        // Check project type only once per visit
        if (BuildSystemDetector.isMavenProject(sourceFile)) {
            return mavenVisitor.visit(tree, p); // Use cached Maven visitor
        } else if (BuildSystemDetector.isGradleProject(sourceFile)) {
            return gradleVisitor.visit(tree, p); // Use cached Gradle visitor
        }

        return tree; // No specific processing for non-Maven/Gradle projects
    }
}
