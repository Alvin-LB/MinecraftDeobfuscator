package com.bringholm.minecraftdeobfuscator.jario;

import com.bringholm.minecraftdeobfuscator.Mappings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class JarWriter implements AutoCloseable {
    private JarOutputStream outputStream;
    private Mappings mappings;

    public JarWriter(File file, Mappings mappings) throws IOException {
        this.outputStream = new JarOutputStream(new FileOutputStream(file));
        this.mappings = mappings;
    }

    public void write(ElementData data) throws IOException {
        if (data instanceof ResourceData) {
            ResourceData resourceData = (ResourceData) data;
            outputStream.putNextEntry(resourceData.getEntry());
            outputStream.write(resourceData.getData());
        } else {
            ClassData classData = (ClassData) data;
            String name = mappings.getClassName(classData.getInternalName());
            JarEntry jarEntry = new JarEntry(name + ".class");
            jarEntry.setSize(classData.getBytecode().length);
            outputStream.putNextEntry(jarEntry);
            outputStream.write(classData.getBytecode());
        }
        outputStream.closeEntry();
    }

    public void close() throws IOException {
        outputStream.close();
    }
}
