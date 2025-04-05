package org.openrewrite.java.spring.ai.mcp.recipe.config;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.maven.MavenExecutionContextView;
import org.openrewrite.maven.MavenSettings;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.spring.ai.mcp.recipe.mcp.AddToolAnnotationToMappingMethodTest.pom;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class AddSpringAIMcpPropertiesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        InMemoryExecutionContext ctx = new InMemoryExecutionContext();
        MavenExecutionContextView context = MavenExecutionContextView.view(ctx).setMavenSettings(MavenSettings.readMavenSettingsFromDisk(ctx));
        spec.recipe(new AddSpringAIMcpProperties(null))
                .executionContext(context);
    }

    @Test
    public void addToPropertiesSuccess() {
        rewriteRun(
                pomXml(pom),
                properties("""
                        server.port=8080
                        """, """
                        server.port=8080
                        spring.ai.mcp.server.name=webmvc-mcp-server
                        spring.ai.mcp.server.sse-message-endpoint=/mcp/messages
                        spring.ai.mcp.server.type=SYNC
                        spring.ai.mcp.server.version=1.0.0
                        """, spec -> spec.path("src/main/resources/application.properties"))
        );
    }

    @Test
    public void addToYamlSuccess() {
        rewriteRun(
                pomXml(pom),
                yaml("""
                        server:
                          port: 8080
                        """, """
                        server:
                          port: 8080
                        spring:
                          ai:
                            mcp:
                              server:
                                name: webmvc-mcp-server
                                version: 1.0.0
                                type: SYNC
                                sse-message-endpoint: /mcp/messages
                        """, spec -> spec.path("src/main/resources/application.yml"))
        );
    }
}