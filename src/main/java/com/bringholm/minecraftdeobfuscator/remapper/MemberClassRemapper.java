package com.bringholm.minecraftdeobfuscator.remapper;

import com.bringholm.minecraftdeobfuscator.jario.ClassData;
import com.bringholm.minecraftdeobfuscator.jario.JarLoader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public class MemberClassRemapper extends ClassRemapper {
    private MemberRemapper memberRemapper;
    private JarLoader loader;

    public MemberClassRemapper(ClassVisitor cv, MemberRemapper remapper, JarLoader loader) {
        super(cv, remapper);
        this.memberRemapper = remapper;
        this.loader = loader;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        ClassData data = loader.getDataFor(name);
        if (data != null) {
            // This is to ensure that all of the bridge methods have been added
            // to the addBridgeModifiers Set before the visitMethod method is called.
            // These results are cached and used later, so it is not too big of a
            // performance overhead to do this.
            for (MethodNode methodNode : (List<MethodNode>) data.getNode().methods) {
                memberRemapper.getBridgeMethodName(name, methodNode.name, methodNode.desc);
            }
        }
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if (memberRemapper.addBridgeModifiers.contains(this.className + "." + name + desc)) {
            access |= MemberRemapper.BRIDGE;
        }
        return super.visitMethod(access, name, desc, signature, exceptions);
    }
}
