package me.cortex.cullmister.utils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static me.cortex.cullmister.commandListStuff.CommandListTokenWriter.*;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.NVCommandList.*;

public class ShaderPreprocessor {
    static List<Path> inbuilt = new LinkedList<>();
    static Map<String, String> inbuiltDefines = new HashMap<>();
    static {
        try {
            inbuilt.add(Path.of(ShaderPreprocessor.class.getResource("/assets/cullmister/include/").toURI()));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        define("NOPCommandHeader", ""+NVHeader(GL_NOP_COMMAND_NV));
        define("TerminateSequenceCommandHeader", ""+NVHeader(GL_TERMINATE_SEQUENCE_COMMAND_NV));
        define("AttributeAddressCommandHeader", ""+NVHeader(GL_ATTRIBUTE_ADDRESS_COMMAND_NV));
        define("DrawElementsInstancedCommandHeader", ""+NVHeader(GL_DRAW_ELEMENTS_INSTANCED_COMMAND_NV));
        define("sizeofAttributeAddressCommand", ""+NVSize(GL_ATTRIBUTE_ADDRESS_COMMAND_NV));
        define("sizeofDrawElementsInstancedCommand", ""+NVSize(GL_DRAW_ELEMENTS_INSTANCED_COMMAND_NV));
        define("__GL_TRIANGLES", ""+GL_TRIANGLES);
    }


    public static void define(String key, String val) {
        inbuiltDefines.put(key, val);
    }

    public static String load(Path file, Path... includePaths) {
        List<Path> includes = new LinkedList<>(List.of(includePaths));
        includes.addAll(inbuilt);
        List<String> lines = resolver(file, includes);
        for (Map.Entry<String, String> def : inbuiltDefines.entrySet()) {
            lines.add(1, "#define " + def.getKey() + " " + def.getValue());
        }
        return String.join("\n", lines);
    }

    private static Path resolveImports(String name, Path requester, List<Path> includes) {
        if (requester.resolve(name).toFile().isFile())
            return requester.resolve(name);
        String base = name.contains("/")?name.substring(name.lastIndexOf("/")):name;
        for (Path i : includes) {
            if (i.toFile().isFile()) {
                if (i.toString().endsWith(base))
                    return i;
            } else
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
