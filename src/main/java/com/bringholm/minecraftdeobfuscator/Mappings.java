package com.bringholm.minecraftdeobfuscator;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.objectweb.asm.Type;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class Mappings {
    private Map<String, String> classMappings = new HashMap<>();
    private Map<String, String> memberMappings = new HashMap<>();
    private BiMap<String, String> hashes = HashBiMap.create();

    Mappings(InputStream classMappingsInputStream, InputStream memberMappingsInputStream) {
        try (LineNumberReader reader = new LineNumberReader(new InputStreamReader(classMappingsInputStream))) {
            String line = reader.readLine();
            while (line != null) {
                if (line.startsWith("#") || line.isEmpty()) {
                    line = reader.readLine();
                    continue;
                }
                String[] split = line.split(" ");
                if (MinecraftDeobfuscator.options.has("check-hashes")) {
                    if (split.length == 3) {
                        String newName = split[1];
                        if (!newName.startsWith("net/minecraft/server/")) {
                            newName = "net/minecraft/server/" + newName;
                        }
                        classMappings.put(split[0], newName);
                        hashes.put(split[0], split[2]);
                    } else {
                        System.out.println("Malformed class mapping at ln " + reader.getLineNumber() + "!");
                    }
                } else {
                    if (split.length == 2) {
                        String newName = split[1];
                        if (!newName.startsWith("net/minecraft/server/")) {
                            newName = "net/minecraft/server/" + newName;
                        }
                        classMappings.put(split[0], newName);
                    } else {
                        System.out.println("Malformed class mapping at ln " + reader.getLineNumber() + "!");
                    }
                }
                line = reader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (LineNumberReader reader = new LineNumberReader(new InputStreamReader(memberMappingsInputStream))) {
            String line = reader.readLine();
            while (line != null) {
                if (line.startsWith("#") || line.isEmpty()) {
                    line = reader.readLine();
                    continue;
                }
                String[] split = line.split(" ");
                String className = split[0];
                if (!className.startsWith("net/minecraft/server/")) {
                    className = "net/minecraft/server/" + className;
                }
                if (split.length == 3) {
                    // Field mapping
                    memberMappings.put(className + "." + split[1], split[2]);
                } else if (split.length == 4) {
                    // Method mapping
                    memberMappings.put(className + "." + split[1] + addPackageToMethodDesc(split[2], reader.getLineNumber()), split[3]);
                } else {
                    System.out.println("Malformed member mapping at ln " + reader.getLineNumber() + "!");
                }
                line = reader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getHash(String className) {
        if (!MinecraftDeobfuscator.options.has("check-hashes")) {
            throw new IllegalStateException("Check hashes is not enabled");
        }
        return this.hashes.get(className);
    }

    /*
     * The mappings provided don't include net/minecraft/server before the class name in most cases,
     * so we have to add it.
     */
    private String addPackageToMethodDesc(String desc, int line) {
        StringBuilder builder = new StringBuilder("(");
        Type[] parameters = Type.getArgumentTypes(desc);
        for (Type type : parameters) {
            if (type.getSort() == Type.OBJECT && type.getInternalName().indexOf('/') == -1 &&
                    classMappings.containsValue("net/minecraft/server/" + type.getInternalName())) {
                builder.append(type.getDescriptor().replace(type.getInternalName(), "net/minecraft/server/" +
                        type.getInternalName()));
            } else {
                builder.append(type.getDescriptor());
            }
        }
        Type returnType;
        try {
            returnType = Type.getReturnType(desc);
        } catch (Exception e) {
            System.out.println("Malformed member mapping at ln " + line + "!");
            return null;
        }
        builder.append(')');
        if (returnType.getSort() == Type.OBJECT && returnType.getInternalName().indexOf('/') == -1 &&
                classMappings.containsValue("net/minecraft/server/" + returnType.getInternalName())) {
            builder.append(returnType.getDescriptor().replace(returnType.getInternalName(), "net/minecraft/server/" +
                returnType.getInternalName()));
        } else {
            builder.append(returnType.getDescriptor());
        }
        return builder.toString();
    }

    public Map<String, String> getMemberMappings() {
        return memberMappings;
    }

    public Map<String, String> getClassMappings() {
        return classMappings;
    }

    private static final Pattern PATTERN = Pattern.compile("(\\$[0-9]+)+");

    public String getClassName(String oldClassName) {
        String name = classMappings.get(oldClassName);
        if (name == null && oldClassName.indexOf('$') != -1) {
            int dollarIndex = oldClassName.indexOf('$');
            while (dollarIndex != -1) {
                String beforeDollar = oldClassName.substring(0, dollarIndex);
                String afterDollar = oldClassName.substring(dollarIndex);
                // Check if the after dollar bit only contains numbers and dollar signs, meaning it should be an anonymous class.
                if (PATTERN.matcher(afterDollar).matches()) {
                    String outerMapping = classMappings.get(beforeDollar);
                    if (outerMapping != null) {
                        name = outerMapping + afterDollar;
                    }
                    break;
                }
                dollarIndex = oldClassName.indexOf('$', dollarIndex + 1);
            }

        }
        return name == null ? oldClassName : name;
    }
}
