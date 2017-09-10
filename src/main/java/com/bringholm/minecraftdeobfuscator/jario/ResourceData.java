package com.bringholm.minecraftdeobfuscator.jario;

import java.util.jar.JarEntry;

public class ResourceData implements ElementData {
    private byte[] data;
    private JarEntry entry;

    public ResourceData(byte[] data, JarEntry entry) {
        this.data = data;
        this.entry = entry;
    }

    public byte[] getData() {
        return data;
    }

    public JarEntry getEntry() {
        return entry;
    }
}
