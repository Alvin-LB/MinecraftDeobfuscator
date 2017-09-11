package com.bringholm.minecraftdeobfuscator.util;

import com.google.common.collect.Maps;
import org.objectweb.asm.Type;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public class LocalVariableNameHelper {
    private static final Map<String, String> RESERVED_KEYWORDS_WITH_ALTERNATIVES = Maps.newHashMap();

    private static final Map<String, String> EXPLICIT_NAMES = Maps.newHashMap();
    private static final Map<Predicate<String>, Function<String, String>> GENERALIZED_EXPLICIT_NAMES = Maps.newHashMap();

    static {
        EXPLICIT_NAMES.put("net.minecraft.server.CompoundNBTTag", "compound");
        GENERALIZED_EXPLICIT_NAMES.put(s -> s.contains("Abstract"), (string) -> {
            String simpleNameWithoutAbstract;
            if (string.indexOf('.') == -1) {
                simpleNameWithoutAbstract = string.replace("Abstract", "");
            } else {
                simpleNameWithoutAbstract = string.substring(string.lastIndexOf('.') + 1).replace("Abstract", "");
            }
            char firstChar = simpleNameWithoutAbstract.charAt(0);
            return Character.toLowerCase(firstChar) + simpleNameWithoutAbstract.substring(1);
        });
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("abstract", "abstr");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("assert", "ass");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("boolean", "bool");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("break", "br");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("byte", "b");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("case", "c");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("catch", "caught");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("char", "c");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("class", "clazz");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("const", "constant");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("continue", "cont");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("default", "def");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("do", "d");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("double", "d");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("else", "e");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("enum", "enumeration");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("extends", "ext");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("false", "f");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("final", "fin");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("finally", "fin");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("float", "f");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("goto", "go");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("for", "f");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("if", "i");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("implements", "impl");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("import", "imp");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("instanceof", "instance");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("int", "i");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("interface", "inter");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("long", "l");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("native", "nat");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("new", "n");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("null", "nu");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("package", "pkg");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("private", "pr");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("protected", "prot");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("public", "pub");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("return", "ret");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("short", "s");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("static", "stat");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("strictfp", "strictFloat");
        RESERVED_KEYWORDS_WITH_ALTERNATIVES.put("true", "t");
    }

    public static String getVariableName(String oldName, String desc, Map<String, Integer> indices) {
        if (!isSnowman(oldName)) {
            return oldName;
        }
        Type type = Type.getType(desc);
        String name = createName(type);
        int index = 0;
        if (indices.containsKey(name)) {
            index = indices.get(name);
        }
        index++;
        indices.put(name, index);
        if (index != 1) {
            name += index;
        }
        return name;
    }

    private static String createName(Type type) {
        if (type.getSort() == Type.OBJECT) {
            if (EXPLICIT_NAMES.containsKey(type.getClassName())) {
                return EXPLICIT_NAMES.get(type.getClassName());
            }
            String name = type.getClassName();
            boolean modified = false;
            for (Map.Entry<Predicate<String>, Function<String, String>> entry : GENERALIZED_EXPLICIT_NAMES.entrySet()) {
                if (entry.getKey().test(name)) {
                    name = entry.getValue().apply(name);
                    modified = true;
                }
            }
            if (modified) {
                return name;
            }
            String simpleName = type.getClassName().substring(type.getClassName().lastIndexOf('.') + 1);
            char firstChar = simpleName.charAt(0);
            simpleName = Character.toLowerCase(firstChar) + simpleName.substring(1);
            if (RESERVED_KEYWORDS_WITH_ALTERNATIVES.containsKey(simpleName)) {
                return RESERVED_KEYWORDS_WITH_ALTERNATIVES.get(simpleName);
            }
            return simpleName;
        } else if (type.getSort() == Type.ARRAY) {
            if (type.getElementType().getSort() == Type.OBJECT) {
                String name = createName(type.getElementType());
                name += (name.charAt(name.length() - 1) == 's' ? "es" : 's');
                if (RESERVED_KEYWORDS_WITH_ALTERNATIVES.containsKey(name)) {
                    return RESERVED_KEYWORDS_WITH_ALTERNATIVES.get(name);
                }
                return name;
            }
            String name = type.getElementType().getClassName();
            name += (name.charAt(name.length() - 1) == 's' ? "es" : 's');
            if (RESERVED_KEYWORDS_WITH_ALTERNATIVES.containsKey(name)) {
                return RESERVED_KEYWORDS_WITH_ALTERNATIVES.get(name);
            }
            return name;
        } else {
            return createPrimitiveTypeName(type);
        }
    }

    private static String createPrimitiveTypeName(Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
                return "bool";
            case Type.BYTE:
                return "b";
            case Type.CHAR:
                return "c";
            case Type.DOUBLE:
                return "d";
            case Type.FLOAT:
                return "f";
            case Type.INT:
                return "i";
            case Type.LONG:
                return "l";
            case Type.SHORT:
                return "s";
            default:
                throw new IllegalArgumentException("Unknown primitive: " + type);
        }
    }

    private static boolean isSnowman(String string) {
        return string.equals("☃");
    }
}
