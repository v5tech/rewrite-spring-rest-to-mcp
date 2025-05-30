package org.openrewrite.java.spring.ai.mcp.visitor;

import org.openrewrite.SourceFile;

import java.nio.file.Path;

public class BuildSystemDetector {

    // Maven 项目通过检查文件名是否为 "pom.xml"
    public static boolean isMavenProject(SourceFile sourceFile) {
        Path path = sourceFile.getSourcePath();
        // 更简洁地通过 path 的父目录来确认是否是 pom.xml
        return "pom.xml".equals(path.getFileName().toString());
    }

    // Gradle 项目通过检查文件名是否为 "build.gradle" 或 "build.gradle.kts"
    public static boolean isGradleProject(SourceFile sourceFile) {
        Path path = sourceFile.getSourcePath();
        String fileName = path.getFileName().toString();
        // 使用 startsWith 和 endsWith 比较的方式代替正则匹配
        return fileName.startsWith("build.gradle") && (fileName.equals("build.gradle") || fileName.equals("build.gradle.kts"));
    }
}

