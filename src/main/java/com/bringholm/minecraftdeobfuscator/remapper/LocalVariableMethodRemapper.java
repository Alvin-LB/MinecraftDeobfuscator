package com.bringholm.minecraftdeobfuscator.remapper;

import com.bringholm.minecraftdeobfuscator.MinecraftDeobfuscator;
import com.bringholm.minecraftdeobfuscator.util.LocalVariableNameHelper;
import com.google.common.collect.Maps;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.Remapper;

import java.util.Map;

public class LocalVariableMethodRemapper extends MethodRemapper {
    private Map<String, Integer> indices = Maps.newHashMap();
    public LocalVariableMethodRemapper(MethodVisitor mv, Remapper remapper) {
        super(mv, remapper);
    }

    @Override
    public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
        String newName = LocalVariableNameHelper.getVariableName(name, desc, this.indices);
        if (MinecraftDeobfuscator.options.has("debug-print") && !newName.equals(name)) {
            System.out.println("Deobfuscated local variable " + newName + "!");
        }
        super.visitLocalVariable(newName, desc, signature, start, end, index);
    }
}
