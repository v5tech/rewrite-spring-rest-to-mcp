package org.openrewrite.java.spring.ai.mcp.visitor;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.search.FindDependency;
import org.openrewrite.xml.tree.Xml;

import java.util.concurrent.atomic.AtomicBoolean;

@Value
@EqualsAndHashCode(callSuper = false)
public class SpringAIMcpVisitor<P> extends MavenIsoVisitor<P> {

    @NonNull AtomicBoolean aiMcpEnabled;

    @Override
    public Xml.@NotNull Document visitDocument(Xml.@NotNull Document document, @NotNull P p) {
        if (!FindDependency.find(document, "org.springframework.ai", "spring-ai-starter-mcp-server-webmvc").isEmpty()) {// supports spring-ai-starter-mcp-server-webmvc only
            aiMcpEnabled.set(true);
        }
        return super.visitDocument(document, p);
    }
}
