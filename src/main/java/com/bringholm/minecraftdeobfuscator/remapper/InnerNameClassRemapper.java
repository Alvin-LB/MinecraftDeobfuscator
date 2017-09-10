package com.bringholm.minecraftdeobfuscator.remapper;

import com.bringholm.minecraftdeobfuscator.Mappings;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

public class InnerNameClassRemapper extends ClassRemapper {
    private Mappings mappings;

    public InnerNameClassRemapper(ClassVisitor cv, Remapper remapper, Mappings mappings) {
        super(cv, remapper);
        this.mappings = mappings;
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        String mappedName = mappings.getClassMappings().get(name);
        if (mappedName != null && mappedName.contains("$")) {
            String newInnerName = mappedName.substring(mappedName.lastIndexOf('$') + 1);
            // Don't rename anonymous subclasses
            if (innerName != null) {
                innerName = newInnerName;
            }
        }
        super.visitInnerClass(name, outerName, innerName, access);
    }
}
