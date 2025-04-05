package org.openrewrite.java.spring.ai.mcp.recipe.mcp;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AddToolCallbackProviderBeanTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipes(new AddToolCallbackProviderBean());
    }

    @Test
    public void addToolCallbackProviderBeanSuccess() {
        rewriteRun(
                java(AddToolAnnotationToMappingMethodTest.expectedHelloTool),
                java(entryClassWithoutTargetBeanMethod, entryClassWithTargetBeanMethod)
        );
    }

    @Test
    public void successUpdateBeanDefinition() {
        rewriteRun(
                java(AddToolAnnotationToMappingMethodTest.expectedHelloTool),
                java(AddToolAnnotationToMappingMethodTest.expectedUserTool),
                java(entryClassWithTargetBeanMethod, entryClassWithBeanMethodUpdated)
        );
    }

    @Test
    public void failDueToBadSituation() {
        Assertions.assertThrows(AssertionError.class, () -> rewriteRun(
                java(AddToolAnnotationToMappingMethodTest.expectedHelloTool),
                java(entryClassWithDuplicatedTargetBeanMethod, entryClassWithDuplicatedTargetBeanMethodWithFailMessage)
        ), "There should be at most one method with return type ToolCallbackProvider");
    }

    @Language("java")
    public static final String entryClassWithoutTargetBeanMethod = """
            package com.atbug.rewrite.test;
            
            import org.springframework.boot.SpringApplication;
            import org.springframework.boot.autoconfigure.SpringBootApplication;
            
            @SpringBootApplication
            public class SpringMainApp {
            
                public static void main(String[] args) {
                    SpringApplication.run(SpringMainApp.class, args);
                }
            }
            """;

    @Language("java")
    public static final String entryClassWithTargetBeanMethod = """
            package com.atbug.rewrite.test;
            
            import com.atbug.rewrite.test.controller.HelloController;
            import org.springframework.ai.tool.ToolCallbackProvider;
            import org.springframework.ai.tool.method.MethodToolCallbackProvider;
            import org.springframework.boot.SpringApplication;
            import org.springframework.boot.autoconfigure.SpringBootApplication;
            import org.springframework.context.annotation.Bean;
            
            @SpringBootApplication
            public class SpringMainApp {
            
                public static void main(String[] args) {
                    SpringApplication.run(SpringMainApp.class, args);
                }
            
                @Bean
                ToolCallbackProvider toolCallbackProvider(HelloController helloController) {
                    return MethodToolCallbackProvider.builder()
                            .toolObjects(helloController)
                            .build();
                }
            }
            """;

    @Language("java")
    public static final String entryClassWithBeanMethodUpdated = """
            package com.atbug.rewrite.test;
            
            import com.atbug.rewrite.test.controller.HelloController;
            import com.atbug.rewrite.test.controller.UserController;
            import org.springframework.ai.tool.ToolCallbackProvider;
            import org.springframework.ai.tool.method.MethodToolCallbackProvider;
            import org.springframework.boot.SpringApplication;
            import org.springframework.boot.autoconfigure.SpringBootApplication;
            import org.springframework.context.annotation.Bean;
            
            @SpringBootApplication
            public class SpringMainApp {
            
                public static void main(String[] args) {
                    SpringApplication.run(SpringMainApp.class, args);
                }
            
                @Bean
                ToolCallbackProvider toolCallbackProvider(HelloController helloController, UserController userController) {
                    return MethodToolCallbackProvider.builder()
                            .toolObjects(helloController, userController)
                            .build();
                }
            }
            """;

    @Language("java")
    public static final String entryClassWithDuplicatedTargetBeanMethod = """
            package com.atbug.rewrite.test;
            
            import org.springframework.ai.tool.ToolCallbackProvider;
            import com.atbug.rewrite.test.controller.HelloController;
            import org.springframework.ai.tool.method.MethodToolCallbackProvider;
            import org.springframework.boot.SpringApplication;
            import org.springframework.boot.autoconfigure.SpringBootApplication;
            import org.springframework.context.annotation.Bean;
            
            @SpringBootApplication
            public class SpringMainApp {
            
                public static void main(String[] args) {
                    SpringApplication.run(SpringMainApp.class, args);
                }
            
                @Bean
                ToolCallbackProvider toolCallbackProvider() {
                    return MethodToolCallbackProvider.builder()
                            .toolObjects(new Object())
                            .build();
                }
            
                @Bean
                ToolCallbackProvider toolCallbackProvider2() {
                    return MethodToolCallbackProvider.builder()
                            .toolObjects(new Object())
                            .build();
                }
            }
            """;

    @Language("java")
    public static final String entryClassWithDuplicatedTargetBeanMethodWithFailMessage = """
            package com.atbug.rewrite.test;
            
            import org.springframework.ai.tool.ToolCallbackProvider;
            import com.atbug.rewrite.test.controller.HelloController;
            import org.springframework.ai.tool.method.MethodToolCallbackProvider;
            import org.springframework.boot.SpringApplication;
            import org.springframework.boot.autoconfigure.SpringBootApplication;
            import org.springframework.context.annotation.Bean;
            
            /*~~(There should be at most one method with return type ToolCallbackProvider)~~>*/@SpringBootApplication
            public class SpringMainApp {
            
                public static void main(String[] args) {
                    SpringApplication.run(SpringMainApp.class, args);
                }
            
                @Bean
                ToolCallbackProvider toolCallbackProvider() {
                    return MethodToolCallbackProvider.builder()
                            .toolObjects(new Object())
                            .build();
                }
            
                @Bean
                ToolCallbackProvider toolCallbackProvider2() {
                    return MethodToolCallbackProvider.builder()
                            .toolObjects(new Object())
                            .build();
                }
            }
            """;
}