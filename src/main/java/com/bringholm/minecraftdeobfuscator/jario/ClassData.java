package com.bringholm.minecraftdeobfuscator.jario;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class ClassData implements ElementData {

    private Map<String, MethodNode> methods = new HashMap<>();
    private Map<String, FieldNode> fields = new HashMap<>();
    private ClassNode node;
    private String name;
    private byte[] bytecode;
    private Set<ClassData> superClasses;

    ClassData(ClassNode node, String name, JarLoader loader, byte[] bytecode) {
        this.node = node;
        this.name = name;
        if (bytecode != null) {
            this.bytecode = bytecode;
        }
        this.superClasses = new HashSet<>();
        //noinspection unchecked
        for (String interfaceName : (List<String>) node.interfaces) {
            ClassData data = loader.getDataFor(interfaceName);
            if (data != null) {
                superClasses.add(data);
            }
        }
        if (node.superName != null) {
            ClassData data = loader.getDataFor(node.superName);
            if (data != null) {
                superClasses.add(data);
            }
        }
    }

    public String getInternalName() {
        return name;
    }

    public boolean isAnonymousInnerClass() {
        return node.outerClass != null && node.name.contains("$") && node.name.substring(node.name.lastIndexOf('$') + 1).matches("[0-9]+");
    }

    public ClassNode getNode() {
        return node;
    }


    public byte[] getBytecode() {
        return bytecode;
    }

    public void setBytecode(byte[] bytecode) {
        this.bytecode = bytecode;
    }


    public MethodNode getMethod(String name, String desc) {
        if (methods.containsKey(name + desc)) {
            return methods.get(name + desc);
        }
        //noinspection unchecked
        for (MethodNode methodNode : (List<MethodNode>) node.methods) {
            if (methodNode.name.equals(name) && methodNode.desc.equals(desc)) {
                methods.put(name + desc, methodNode);
                return methodNode;
            }
        }
        return null;
    }

    public FieldNode getField(String name, String desc) {
        if (fields.containsKey(name + desc)) {
            return fields.get(name + desc);
        }
        //noinspection unchecked
        for (FieldNode fieldNode : (List<FieldNode>) node.fields) {
            if (fieldNode.name.equals(name) && fieldNode.desc.equals(desc)) {
                fields.put(name + desc, fieldNode);
                return fieldNode;
            }
        }
        return null;
    }

    public Set<ClassData> getSuperClasses() {
        return superClasses;
    }

    @Override
    public String toString() {
        return "ClassData{name=" + name + "}";
    }
}
