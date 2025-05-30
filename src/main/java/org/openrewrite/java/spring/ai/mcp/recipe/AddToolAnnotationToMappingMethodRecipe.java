package org.openrewrite.java.spring.ai.mcp.recipe;

import org.jetbrains.annotations.NotNull;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.spring.ai.mcp.visitor.SpringAIMcpVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Javadoc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class AddToolAnnotationToMappingMethodRecipe extends ScanningRecipe<AtomicBoolean> {
    private static final String MCP_TOOL_PACKAGE = "org.springframework.ai.tool.annotation";
    private static final String MCP_TOOL_SIMPLE_NAME = "Tool";
    private static final String MCP_TOOL_PARAM_SIMPLE_NAME = "ToolParam";
    private static final String MCP_TOOL_FULLY_QUALIFIED_NAME = MCP_TOOL_PACKAGE + "." + MCP_TOOL_SIMPLE_NAME;
    private static final String MCP_TOOL_PARAM_FULLY_QUALIFIED_NAME = MCP_TOOL_PACKAGE + "." + MCP_TOOL_PARAM_SIMPLE_NAME;
    private boolean causesAnotherCycle;
    /**
     * JavaTemplate for @Tool(description="") annotation
     */
    private static final JavaTemplate TOOL_ANNO_TEMPLATE = JavaTemplate.builder("@" + MCP_TOOL_SIMPLE_NAME + "(description = \"#{}\")")
            .imports(MCP_TOOL_FULLY_QUALIFIED_NAME)
            .javaParser(JavaParser.fromJavaVersion().dependsOn("package " + MCP_TOOL_PACKAGE +
                    "; public @interface " + MCP_TOOL_SIMPLE_NAME + " {}"))
            .build();
    /**
     * JavaTemplate for @ToolParam(description="") annotation
     */
    private static final JavaTemplate TOOL_PARAM_ANNO_TEMPLATE = JavaTemplate.builder("@" + MCP_TOOL_PARAM_SIMPLE_NAME + "(description = \"#{}\")")
            .imports(MCP_TOOL_PARAM_FULLY_QUALIFIED_NAME)
            .javaParser(JavaParser.fromJavaVersion().dependsOn("package " + MCP_TOOL_PACKAGE +
                    "; public @interface " + MCP_TOOL_PARAM_SIMPLE_NAME + " {}"))
            .build();

    @Override
    public @NotNull AtomicBoolean getInitialValue(@NotNull ExecutionContext ctx) {
        return new AtomicBoolean(false);
    }

    @Override
    public @NotNull TreeVisitor<?, ExecutionContext> getScanner(@NotNull AtomicBoolean aiMcpEnabled) {
        return Preconditions.check(!aiMcpEnabled.get(), new SpringAIMcpVisitor<>(aiMcpEnabled));
    }

    @Override
    public @NotNull TreeVisitor<?, ExecutionContext> getVisitor(@NotNull AtomicBoolean aiMcpEnabled) {
        JavaIsoVisitor<ExecutionContext> visitor = new JavaIsoVisitor<>() {

            @Override
            public J.@NotNull MethodDeclaration visitMethodDeclaration(J.@NotNull MethodDeclaration method, @NotNull ExecutionContext ctx) {
                if (FindAnnotations.find(method, "@" + MCP_TOOL_FULLY_QUALIFIED_NAME).isEmpty()
                        && (!FindAnnotations.find(method, "@org.springframework.web.bind.annotation.GetMapping").isEmpty()
                        || !FindAnnotations.find(method, "@org.springframework.web.bind.annotation.PostMapping").isEmpty()
                        || !FindAnnotations.find(method, "@org.springframework.web.bind.annotation.RequestMapping").isEmpty()
                        || !FindAnnotations.find(method, "@org.springframework.web.bind.annotation.PatchMapping").isEmpty()
                        || !FindAnnotations.find(method, "@org.springframework.web.bind.annotation.DeleteMapping").isEmpty()
                        || !FindAnnotations.find(method, "@org.springframework.web.bind.annotation.PutMapping").isEmpty())) { // has any web mapping annotation, but no mcp tool annotation
                    AtomicReference<String> toolDesc = new AtomicReference<>();
                    Map<String, String> toolParamMap = new HashMap<>();
                    Optional<Javadoc.DocComment> docComment = method.getComments().stream()
                            .map(comment -> ((Javadoc.DocComment) comment))
                            .findFirst();
                    if (docComment.isPresent()) {
                        Javadoc.DocComment comment = docComment.get();
                        String methodDesc = getDescription(comment.getBody());
                        toolDesc.set(methodDesc);

                        comment.getBody().stream()
                                .filter(l -> l instanceof Javadoc.Parameter)
                                .map(l -> (Javadoc.Parameter) l)
                                .filter(p -> p.getNameReference() != null && p.getNameReference().getTree() != null)
                                .forEach(p -> {
                                    String pName = p.getNameReference().getTree().toString();
                                    String pDesc = getDescription(p.getDescription());
                                    toolParamMap.put(pName, pDesc);
                                });
                    } else {
                        toolDesc.set(method.getSimpleName());
                    }
                    //Add Tool annotation to method
                    method = TOOL_ANNO_TEMPLATE.apply(getCursor(), method.getCoordinates().addAnnotation((an1, an2) -> 0), toolDesc.get()); // insert to the last position
                    maybeAddImport(MCP_TOOL_FULLY_QUALIFIED_NAME);
                    updateCursor(method);
                    //Add ToolParam annotation to method parameters
                    List<J.VariableDeclarations> params = method.getParameters().stream()
                            .filter(statement -> statement instanceof J.VariableDeclarations)
                            //TODO: maybe filter with variable annotations like PathVariable, RequestParam, RequestBody or RequestHeader
                            // The parameters with these annotations are resolved from request. And in mcp, they will be taken as tool parameters.
                            // The required option should be extract from these annotations and set to the tool parameters.
                            .map(statement -> (J.VariableDeclarations) statement)
                            .toList();
                    for (J.VariableDeclarations varDecl : params) {
                        String paramName = varDecl.getVariables().get(0).getSimpleName();
                        String paraDesc = toolParamMap.get(paramName);
                        paraDesc = paraDesc != null ? paraDesc : paramName;
                        method = TOOL_PARAM_ANNO_TEMPLATE
                                .apply(getCursor(), varDecl.getCoordinates().addAnnotation((an1, an2) -> 0), paraDesc);
                    }
                    maybeAddImport(MCP_TOOL_PARAM_FULLY_QUALIFIED_NAME);
                    causesAnotherCycle = true;
                }
                return method;
            }
        };

        //make sure the target class is a Spring Bean
        TreeVisitor<?, ExecutionContext> beanChecker = Preconditions.or(
                new UsesType<>("org.springframework.stereotype.Controller", false),
                new UsesType<>("org.springframework.stereotype.Component", false),
                new UsesType<>("org.springframework.stereotype.Service", false),
                new UsesType<>("org.springframework.stereotype.Repository", false),
                new UsesType<>("org.springframework.web.bind.annotation.RestController", false)
        );
        return Preconditions.check(
                aiMcpEnabled.get(), Preconditions.check(beanChecker, visitor));
    }

    @Override
    public boolean causesAnotherCycle() {
        return causesAnotherCycle || super.causesAnotherCycle();
    }

    /**
     * Get the description of the javaDocs.
     *
     * @param javaDocs the javaDocs
     * @return the description
     */
    private static @NotNull String getDescription(List<Javadoc> javaDocs) {
        return javaDocs.stream()
                .filter(l -> l instanceof Javadoc.Text)
                .map(l -> ((Javadoc.Text) l).getText().trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
    }

    @Override
    public @NlsRewrite.DisplayName @NotNull String getDisplayName() {
        return "Add MCP Tool annotation to mapping method";
    }

    @Override
    public @NlsRewrite.Description @NotNull String getDescription() {
        return "Add MCP annotations (Tool/ToolParam) to the method with Spring Web mapping annotations.";
    }
}