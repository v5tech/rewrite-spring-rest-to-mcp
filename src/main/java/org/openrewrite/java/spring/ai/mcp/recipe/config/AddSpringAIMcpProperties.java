package org.openrewrite.java.spring.ai.mcp.recipe.config;

import org.openrewrite.java.spring.ai.mcp.visitor.SpringAIMcpVisitor;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.properties.AddProperty;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.MergeYaml;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddSpringAIMcpProperties extends ScanningRecipe<AtomicBoolean> {
    List<String> SpringDefaultConfigurationPaths = Arrays.asList("**/application.yml", "**/application.properties", "**/application.yaml");

    @Option(displayName = "MCP server name",
            description = "The name of the MCP server.",
            example = "webmvc-mcp-server")
    @Nullable
    String serverName = "webmvc-mcp-server";

    @Option(displayName = "MCP server version",
            description = "The version of the MCP server.",
            example = "1.0.0")
    @Nullable
    String serverVersion = "1.0.0";

    @Option(displayName = "MCP server type",
            description = "The type of the MCP server.",
            example = "SYNC")
    @Nullable
    String serverType = "SYNC";

    @Option(displayName = "MCP server SSE message endpoint",
            description = "The SSE message endpoint of the MCP server.",
            example = "/mcp/messages")
    @Nullable
    String sseMessageEndpoint = "/mcp/messages";

    @Option(displayName = "Optional list of file path matcher",
            description = "Each value in this list represents a glob expression that is used to match which files will " +
                    "be modified. If this value is not present, this recipe will use the defaults. " +
                    "(\"**/application.yml\", \"**/application.yml\", and \"**/application.properties\".",
            required = false,
            example = "[\"**/application.yml\"]")
    @Nullable
    List<String> pathExpressions;

    // TODO: to handle the async type in future
    @Language("yml")
    String yaml = """
            spring:
              ai:
                mcp:
                  server:
                    name: %s
                    version: %s
                    type: %s
                    sse-message-endpoint: %s
            """;
    @Language("properties")
    String properties = """
            spring.ai.mcp.server.name=%s
            spring.ai.mcp.server.version=%s
            spring.ai.mcp.server.type=%s
            spring.ai.mcp.server.sse-message-endpoint=%s
            """;

    @Override
    public @NotNull AtomicBoolean getInitialValue(@NotNull ExecutionContext ctx) {
        return new AtomicBoolean(false);
    }

    @Override
    public @NotNull TreeVisitor<?, ExecutionContext> getScanner(@NotNull AtomicBoolean aiMcpEnabled) {
        return new SpringAIMcpVisitor<>(aiMcpEnabled);
    }

    @Override
    public @NotNull TreeVisitor<?, ExecutionContext> getVisitor(AtomicBoolean aiMcpEnabled) {
        TreeVisitor<Tree, ExecutionContext> visitor = new TreeVisitor<>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree t, @NotNull ExecutionContext ctx, @NotNull Cursor parent) {
                if (t instanceof Yaml.Documents && sourcePathMatch(((SourceFile) t).getSourcePath())) {
                    t = new MergeYaml("$", updateContent(yaml), true, null, null, null, null, null)
                            .getVisitor().visit(t, ctx, parent);
                } else if (t instanceof Properties.File && sourcePathMatch(((SourceFile) t).getSourcePath())) {
                    List<String[]> props = Arrays.stream(updateContent(properties).split("\n"))
                            .map(line -> line.split("=", 2))
                            .filter(parts -> parts.length == 2)
                            .toList();
                    for (String[] prop : props) {
                        String key = prop[0].trim();
                        String value = prop[1].trim();
                        if (key.isEmpty() || value.isEmpty()) {
                            continue;
                        }
                        t = new AddProperty(key, value, null, null)
                                .getVisitor().visit(t, ctx, parent);
                    }
                }
                return t;
            }

        };
        return Preconditions.check(aiMcpEnabled.get(), visitor);
    }

    private String updateContent(String content) {
        return String.format(content, serverName, serverVersion, serverType, sseMessageEndpoint);
    }

    private boolean sourcePathMatch(Path sourcePath) {
        List<String> expressions = pathExpressions;
        if (expressions == null || pathExpressions.isEmpty()) {
            //If not defined, get defaults.
            expressions = SpringDefaultConfigurationPaths;
        }
        if (expressions.isEmpty()) {
            return true;
        }
        for (String filePattern : expressions) {
            if (PathUtils.matchesGlob(sourcePath, filePattern)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public @NlsRewrite.DisplayName @NotNull String getDisplayName() {
        return "Add Spring AI MCP properties";
    }

    @Override
    public @NlsRewrite.Description @NotNull String getDescription() {
        return "Add properties to the Spring AI MCP configuration file.";
    }
}
