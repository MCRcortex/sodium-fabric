package me.cortex.cullmister.utils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShaderPreprocessor {
    public static String load(Path file, Path... includePaths) {
        List<Path> includes = new LinkedList<>(List.of(includePaths));
        try {
            includes.add(Path.of(ClassLoader.getSystemClassLoader().getResource("assets/cullermister/include").toURI()));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return String.join("\n", resolver(file, includes));
    }

    private static Path resolveImports(String name, Path requester, List<Path> includes) {
        if (requester.resolve(name).toFile().isFile())
            return requester.resolve(name);
        String base = name.substring(name.lastIndexOf("/"));
        for (Path i : includes) {
            if (i.toFile().isFile())
                if (i.toString().endsWith(base))
                    return i;
            else
                if (i.resolve(name).toFile().isFile())
                    return i.resolve(name);
        }
        return null;
    }

    private static final Pattern IMPORT_PATTERN = Pattern.compile("#import <(?<name>.*)>");


    private static List<String> resolver(Path f, List<Path> includes) {
        try {
            LinkedList<String> lines = new LinkedList<>();
            for (String line : Files.readAllLines(f)) {
                if (line.startsWith("#import")) {
                    Matcher res = IMPORT_PATTERN.matcher(line);
                    if (!res.matches()) {
                        throw new IllegalStateException("Malformed #include");
                    }
                    String name = res.group("name");
                    Path importFile = resolveImports(name, f, includes);
                    if (importFile == null)
                        throw new IllegalStateException("Could not find import");
                    lines.addAll(resolver(importFile, includes));
                } else {
                    lines.add(line);
                }
            }
            return lines;
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalStateException("Could not open file "+f);
        }
    }
}
