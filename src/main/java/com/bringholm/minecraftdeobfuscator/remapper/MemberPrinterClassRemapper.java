package com.bringholm.minecraftdeobfuscator.remapper;

import com.bringholm.minecraftdeobfuscator.jario.JarLoader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

public class MemberPrinterClassRemapper extends MemberClassRemapper {

    private String className;

    public MemberPrinterClassRemapper(ClassVisitor cv, MemberRemapper remapper, JarLoader loader) {
        super(cv, remapper, loader);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        String mappedName = remapper.mapFieldName(className, name, desc);
        if (!mappedName.equals(name)) {
            if (mappedName.startsWith("$SwitchMap$")) {
                System.out.println("Deobfuscated Enum SwitchMap field " + className + "." + name + " to " + mappedName);
            } else {
                System.out.println("Deobfuscated field " + className + "." + name + " to " + mappedName);
            }
        }
        return super.visitField(access, name, desc, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        String mappedName = remapper.mapMethodName(className, name, desc);
        if (!mappedName.equals(name)) {
            System.out.println("Deobfuscated method " + className + "." + name + desc + " to " + mappedName);
        }
        return super.visitMethod(access, name, desc, signature, exceptions);
    }
}
