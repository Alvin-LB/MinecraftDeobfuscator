package com.bringholm.minecraftdeobfuscator.jario;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarLoader implements Iterable<ElementData>, AutoCloseable {
    private JarFile jarFile;
    private LoadingCache<String, ClassData> classCache = CacheBuilder.newBuilder().maximumSize(4096L).build(new CacheLoader<String, ClassData>() {
        @Override
        public ClassData load(String key) throws Exception {
            return loadClassFromJar(key);
        }
    });

    public JarLoader(JarFile jarFile) {
        this.jarFile = jarFile;
    }

    public Iterator<ElementData> iterator() {
        return new Iterator<ElementData>() {
            private Enumeration<JarEntry> entries = jarFile.entries();

            @Override
            public boolean hasNext() {
                return entries.hasMoreElements();
            }

            @Override
            public ElementData next() {
                try {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".class")) {
                        return loadClassFromJar(entry);
                    } else {
                        return new ResourceData(IOUtils.toByteArray(jarFile.getInputStream(entry)), entry);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }
        };
    }

    public ClassData loadClassFromJar(String internalName) throws IOException {
        return loadClassFromJar(jarFile.getJarEntry(internalName + ".class"));
    }

    public ClassData loadClassFromJar(JarEntry entry) throws IOException {
        if (entry != null) {
            return getClassData(jarFile.getInputStream(entry), entry.getName().replace(".class", ""));
        }
        return null;
    }

    public boolean hasClass(String className) {
        return jarFile.getJarEntry(className + ".class") != null;
    }

    public ClassData getDataFor(String className) {
        if (!hasClass(className)) {
            return null;
        }
        try {
            return classCache.get(className);
        } catch (ExecutionException e) {
            e.printStackTrace();
            return null;
        }
    }

    private ClassData getClassData(InputStream inputStream, String name) throws IOException {
        byte[] bytes = IOUtils.toByteArray(inputStream);
        return new ClassData(getClassNodeFromBytes(bytes), name, this, bytes);
    }

    private ClassNode getClassNodeFromBytes(byte[] bytes) {
        ClassReader classReader = new ClassReader(bytes);
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, ClassReader.EXPAND_FRAMES);
        return classNode;
    }

    public void close() throws IOException {
        jarFile.close();
    }
}
