package org.openrewrite.java.spring.ai.mcp.visitor.maven;

import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.search.FindDependency;
import org.openrewrite.xml.tree.Xml;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

public class MavenMcpVisitor<P> extends MavenIsoVisitor<P> {
    private final AtomicBoolean aiMcpEnabled;

    public MavenMcpVisitor(AtomicBoolean aiMcpEnabled) {
        this.aiMcpEnabled = aiMcpEnabled;
    }

    @Override
    public Xml.@NotNull Document visitDocument(Xml.@NotNull Document document, @NotNull P p) {
        // Find the dependency only once, avoid repeating the search
        if (aiMcpEnabled.get()) {
            return document; // Skip if already enabled
        }

        // Use FindDependency once and directly check if the dependency exists
        boolean hasDependency = FindDependency.find(document, "org.springframework.ai", "spring-ai-starter-mcp-server-webmvc")
                .stream().findFirst().isPresent();

        if (hasDependency) {
            aiMcpEnabled.set(true);
            getCursor().putMessage("BUILD_SYSTEM", "MAVEN");
        }

        return document;
    }
}
