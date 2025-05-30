package org.openrewrite.java.spring.ai.mcp.recipe;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.spring.ai.mcp.visitor.McpToolVisitor;
import org.openrewrite.java.tree.J;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class AddToolCallbackProviderRecipe extends ScanningRecipe<Set<String>> {
    private static final String SPRING_BOOT_APPLICATION_FQN = "org.springframework.boot.autoconfigure.SpringBootApplication";
    private static final String SPRING_BEAN_FQN = "org.springframework.context.annotation.Bean";
    private static final String METHOD_TOOL_CB_PROVIDER_FQN = "org.springframework.ai.tool.method.MethodToolCallbackProvider";
    public static final String TOOL_CB_PROVIDER_PACKAGE = "org.springframework.ai.tool";
    public static final String TOOL_CB_PROVIDER_SIMPLE_NAME = "ToolCallbackProvider";
    public static final String TOOL_CB_PROVIDER_FQN = TOOL_CB_PROVIDER_PACKAGE + "." + TOOL_CB_PROVIDER_SIMPLE_NAME;
    public static final String PROVIDER_METHOD_TEMPLATE = """
            @Bean
            ToolCallbackProvider %s(#{}) {
                return MethodToolCallbackProvider.builder()
                        .toolObjects(#{})
                        .build();
            }
            """;
    private static final String BEAN_METHOD_NAME = "toolCallbackProvider";

    @Override
    public @NotNull Set<String> getInitialValue(@NotNull ExecutionContext ctx) {
        return new HashSet<>();
    }

    @Override
    public @NotNull TreeVisitor<?, ExecutionContext> getScanner(@NotNull Set<String> toolSet) {
        return new McpToolVisitor(toolSet);
    }

    @Override
    public @NotNull TreeVisitor<?, ExecutionContext> getVisitor(@NotNull Set<String> toolObjectSet) {
        JavaIsoVisitor<ExecutionContext> visitor = new JavaIsoVisitor<>() {
            @Override
            public J.@NotNull ClassDeclaration visitClassDeclaration(J.@NotNull ClassDeclaration classDecl, @NotNull ExecutionContext ctx) {
                classDecl = super.visitClassDeclaration(classDecl, ctx);
                if (toolObjectSet.isEmpty()) {
                    return classDecl; // No tool objects found, return early
                }
                Set<J.Annotation> annotations = FindAnnotations.find(classDecl, SPRING_BOOT_APPLICATION_FQN);
                if (annotations.isEmpty()) {
                    return classDecl; // No @SpringBootApplication annotation found, return early
                }
                List<J.MethodDeclaration> providerMethodList = classDecl.getBody().getStatements().stream()
                        .filter(s -> s instanceof J.MethodDeclaration)
                        .map(s -> (J.MethodDeclaration) s)
                        .filter(m -> m.getReturnTypeExpression() != null && TOOL_CB_PROVIDER_SIMPLE_NAME.equals(m.getReturnTypeExpression().toString()))
                        .toList();

                assert providerMethodList.size() <= 1 : String.format("There should be at most one method with return type %s", TOOL_CB_PROVIDER_SIMPLE_NAME);

                if (providerMethodList.size() == 1) {
                    J.MethodDeclaration m = providerMethodList.get(0);
                    List<J.VariableDeclarations> params = m.getParameters().stream()
                            .filter(s -> s instanceof J.VariableDeclarations)
                            .map(s -> (J.VariableDeclarations) s)
                            .toList();

                    if (params.size() == toolObjectSet.size()
                            && params.stream().filter(varDecl -> toolObjectSet.contains(varDecl.getTypeAsFullyQualified().toString())).count() == params.size()) {
                        return classDecl;
                    }
                    // Update the method to use the new tool object list
                    J.Block block = buildJavaTemplate(toolObjectSet, m.getName().toString())
                            .apply(new Cursor(getCursor(), classDecl.getBody()), m.getCoordinates().replace(), buildArguments(toolObjectSet), buildVariables(toolObjectSet));
                    classDecl = classDecl.withBody(block);
                } else {
                    // Create a new method with the tool object list
                    classDecl = buildJavaTemplate(toolObjectSet, BEAN_METHOD_NAME)
                            .apply(getCursor(), classDecl.getBody().getCoordinates().lastStatement(), buildArguments(toolObjectSet), buildVariables(toolObjectSet));
                }
                Arrays.stream(buildImports(toolObjectSet)).forEach(this::maybeAddImport);
                return classDecl;
            }
        };
        return Preconditions.check(!toolObjectSet.isEmpty(), visitor);
    }

    private @NotNull String buildVariables(Set<String> toolObjectList) {
        return toolObjectList.stream()
                .map(clazzName -> clazzName.substring(clazzName.lastIndexOf('.') + 1))
                .map(clazzName -> Character.toLowerCase(clazzName.charAt(0)) + clazzName.substring(1))
                .collect(Collectors.joining(", "));
    }

    private @NotNull String buildArguments(Set<String> toolObjectList) {
        return toolObjectList.stream()
                .map(clazzName -> {
                    String clazzSimpleName = clazzName.substring(clazzName.lastIndexOf('.') + 1);
                    String clazzVarName = Character.toLowerCase(clazzSimpleName.charAt(0)) + clazzSimpleName.substring(1);
                    return String.format("%s %s", clazzSimpleName, clazzVarName);
                })
                .collect(Collectors.joining(", "));
    }

    private JavaTemplate buildJavaTemplate(Set<String> toolObjectList, @NonNull String methodName) {
        @Language("java") String[] dependsOn = buildDependsOn(toolObjectList);
        String[] imports = buildImports(toolObjectList);
        return JavaTemplate.builder(String.format(PROVIDER_METHOD_TEMPLATE, methodName))
                .imports(imports)
                .contextSensitive()
                .javaParser(JavaParser.fromJavaVersion().dependsOn(dependsOn).logCompilationWarningsAndErrors(true))
                .build();
    }

    private @NotNull String[] buildImports(Set<String> toolObjectList) {
        HashSet<String> importsToAdd = moreToImportAndDependOn(toolObjectList);
        return importsToAdd.toArray(new String[0]);
    }

    private @NotNull HashSet<String> moreToImportAndDependOn(Set<String> toolObjectList) {
        HashSet<String> importsToAdd = new HashSet<>(toolObjectList);
        importsToAdd.add(SPRING_BEAN_FQN);
        importsToAdd.add(METHOD_TOOL_CB_PROVIDER_FQN);
        importsToAdd.add(TOOL_CB_PROVIDER_FQN);
        return importsToAdd;
    }

    private @NotNull String[] buildDependsOn(Set<String> toolObjectList) {
        List<String> dependsOnList = new ArrayList<>(moreToImportAndDependOn(toolObjectList).stream()
                .filter(dep -> !METHOD_TOOL_CB_PROVIDER_FQN.equals(dep)) // ignore the MethodToolCallbackProvider
                .map(clazzName -> {
                    String clazzSimpleName = clazzName.substring(clazzName.lastIndexOf('.') + 1);
                    String packageName = clazzName.substring(0, clazzName.lastIndexOf('.'));
                    return String.format("""
                            package %s;
                            public class %s {}
                            """, packageName, clazzSimpleName);
                })
                .toList());

        // special case for MethodToolCallbackProvider
        @Language("java")
        String methodCallbackProvider = String.format(""" 
                package %s;
                
                import java.util.List;
                public class %s {
                    public static Builder builder() {
                        return new Builder();
                    }
                    public static class Builder {
                        private List<Object> toolObjects;
                        private Builder() {}
                        public Builder toolObjects(Object... toolObjects) {
                            return this;
                        }
                        public MethodToolCallbackProvider build() {
                            return null;
                        }
                    }
                }
                """, METHOD_TOOL_CB_PROVIDER_FQN.substring(0, METHOD_TOOL_CB_PROVIDER_FQN.lastIndexOf('.')), METHOD_TOOL_CB_PROVIDER_FQN.substring(METHOD_TOOL_CB_PROVIDER_FQN.lastIndexOf('.') + 1));
        dependsOnList = new ArrayList<>(dependsOnList);
        dependsOnList.add(methodCallbackProvider);
        return dependsOnList.toArray(new String[0]);
    }

    @Override
    public @NlsRewrite.DisplayName @NotNull String getDisplayName() {
        return "Add tool callback provider bean";
    }

    @Override
    public @NlsRewrite.Description @NotNull String getDescription() {
        return "Add tool callback provider bean to the class annotated with @SpringBootApplication.";
    }
}
