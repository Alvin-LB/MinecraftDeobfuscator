package com.bringholm.minecraftdeobfuscator;

import com.bringholm.minecraftdeobfuscator.jario.*;
import com.bringholm.minecraftdeobfuscator.remapper.*;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.commons.codec.digest.DigestUtils;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.jar.JarFile;

public class MinecraftDeobfuscator {
    public static OptionSet options;

    public static void main(String[] args) {
        OptionParser parser = new OptionParser() {
            {
                acceptsAll(Arrays.asList("?", "help"), "Displays help");
                acceptsAll(Arrays.asList("mj", "mjar", "minecraft-jar"), "The minecraft jar to deobfuscate").withRequiredArg().ofType(File.class).required();
                acceptsAll(Arrays.asList("rf", "rfile", "remapped-file"), "The output file for the deobfuscated jar").withRequiredArg().ofType(File.class).required();
                acceptsAll(Arrays.asList("clm", "clmappings", "class-mappings"), "The class mappings").withRequiredArg().ofType(File.class).required();
                acceptsAll(Arrays.asList("mem", "memappings", "member-mappings"), "The member mappings").withRequiredArg().ofType(File.class).required();
                acceptsAll(Arrays.asList("dp", "dprint", "debug-print"), "Prints each remapped value to console");
                acceptsAll(Arrays.asList("ghm", "ghmappings", "generate-hash-mappings"), "Generates class mappings with MD-5 hashes corresponding to each of the classes");
                acceptsAll(Arrays.asList("gfh", "gfromhashes", "generate-mappings-from-hashes"), "Attempts to generate new mappings using hashes to account for refactoring. Needs mappings to contain hashes");
            }
        };
        try {
            options = parser.parse(args);
        } catch (OptionException e) {
            System.err.println("Incorrect arguments!");
            try {
                parser.printHelpOn(System.err);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            return;
        }
        if (options.has("help")) {
            try {
                parser.printHelpOn(System.out);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
        Mappings mappings;
        try (FileInputStream classMappingsInputStream = new FileInputStream((File) options.valueOf("class-mappings"));
             FileInputStream memberMappingsInputStream = new FileInputStream((File) options.valueOf("member-mappings"))) {
            mappings = new Mappings(classMappingsInputStream, memberMappingsInputStream);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        long startTime = System.currentTimeMillis();
        String classRemappedJar = options.valueOf("minecraft-jar").toString();
        classRemappedJar = classRemappedJar.substring(0, classRemappedJar.lastIndexOf('.')) + "_class_remapped.jar";
        try (JarWriter writer = new JarWriter(new File(classRemappedJar), mappings); JarLoader loader = new JarLoader(new JarFile((File) options.valueOf("minecraft-jar")))) {
            System.out.println("Deobfuscating classes...");
            remapClasses(loader, writer, mappings);
        } catch (IOException e) {
            System.err.println("Failed to deobfuscate classes!");
            e.printStackTrace();
            return;
        }
        if (options.has("generate-hash-mappings")) {
            System.out.println("Generating hashes...");
            String hashMappingsFile = options.valueOf("class-mappings").toString();
            String extension = hashMappingsFile.substring(hashMappingsFile.lastIndexOf('.'));
            hashMappingsFile = hashMappingsFile.substring(0, hashMappingsFile.lastIndexOf('.')) + "-hashes" + extension;
            try (JarLoader loader = new JarLoader(new JarFile(classRemappedJar)); PrintWriter writer = new PrintWriter(hashMappingsFile)) {
                writeHashMappings(writer, loader, mappings);
            } catch (IOException e) {
                System.err.println("Failed to generate hash mappings!");
                e.printStackTrace();
                return;
            }
        }
        if (options.has("generate-mappings-from-hashes")) {
            System.out.println("Generating mappings from hashes...");
            String generatedMappingsFile = options.valueOf("class-mappings").toString();
            String extension = generatedMappingsFile.substring(generatedMappingsFile.lastIndexOf('.'));
            generatedMappingsFile = generatedMappingsFile.substring(0, generatedMappingsFile.lastIndexOf('.')) + "-from-hashes" + extension;
            try (JarLoader loader = new JarLoader(new JarFile(classRemappedJar)); PrintWriter writer = new PrintWriter(generatedMappingsFile)) {
                generateNewMappingsFromHashes(loader, writer, mappings);
            } catch (IOException e) {
                System.err.println("Failed to generate mappings from hashes!");
                e.printStackTrace();
            }
            deleteClassRemappedJar(classRemappedJar);
            return;
        }
        try (JarLoader loader = new JarLoader(new JarFile(classRemappedJar)); JarWriter writer = new JarWriter((File) options.valueOf("remapped-file"), mappings)){
            System.out.println("Remapping members...");
            remapMembers(loader, writer, mappings);
        } catch (IOException e) {
            System.err.println("Failed to deobfuscate members!");
            e.printStackTrace();
            return;
        }
        deleteClassRemappedJar(classRemappedJar);
        long elapsedTime = System.currentTimeMillis() - startTime;
        System.out.println("Deobfuscated " + mappings.getClassMappings().size() + " classes and " + mappings.getMemberMappings().size() + " member mappings in " + elapsedTime + "ms!");
    }

    private static void generateNewMappingsFromHashes(JarLoader loader, PrintWriter writer, Mappings mappings) {

    }

    private static void deleteClassRemappedJar(String path) {
        try {
            // This throws exceptions, so it is better than File#delete()
            Files.delete(new File(path).toPath());
        } catch (IOException e) {
            System.err.println("Failed to delete class remapped jar " + path + "!");
            e.printStackTrace();
        }
    }

    private static void writeHashMappings(PrintWriter writer, JarLoader loader, Mappings mappings) {
        for (Map.Entry<String, String> entry : mappings.getClassMappings().entrySet()) {
            ClassData data = loader.getDataFor(entry.getValue());
            if (data != null) {
                writer.println(entry.getKey() + " " + entry.getValue() + " " + DigestUtils.md5Hex(data.getBytecode()));
            }
        }
    }

    private static void remapMembers(JarLoader loader, JarWriter writer, Mappings mappings) throws IOException {
        MemberRemapper remapper = new MemberRemapper(mappings.getMemberMappings(), loader);
        for (ElementData data : loader) {
            if (data instanceof ResourceData) {
                writer.write(data);
            } else {
                ClassData classData = (ClassData) data;
                if (!classData.getInternalName().startsWith("net/minecraft/server/")) {
                    writer.write(classData);
                    continue;
                }
                ClassWriter classWriter = new ClassWriter(0);
                ClassRemapper classRemapper = options.has("debug-print") ? new MemberPrinterClassRemapper(classWriter, remapper, loader) : new MemberClassRemapper(classWriter, remapper, loader);
                classData.getNode().accept(classRemapper);
                classData.setBytecode(classWriter.toByteArray());
                writer.write(classData);
            }
        }
    }

    private static void remapClasses(JarLoader loader, JarWriter writer, Mappings mappings) throws IOException {
        Remapper remapper = new AnonymousClassNameRemapper(mappings.getClassMappings());
        for (ElementData data : loader) {
            if (data instanceof ResourceData) {
                writer.write(data);
            } else {
                ClassData classData = (ClassData) data;
                if (classData.getInternalName().contains("/") && !classData.getInternalName().startsWith("net/minecraft/server/")) {
                    writer.write(classData);
                    continue;
                }
                ClassWriter classWriter = new ClassWriter(0);
                ClassRemapper classRemapper = new InnerNameClassRemapper(classWriter, remapper, mappings);
                classData.getNode().accept(classRemapper);
                classData.setBytecode(classWriter.toByteArray());
                writer.write(classData);
                if (options.has("debug-print") && mappings.getClassMappings().containsKey(classData.getInternalName())) {
                    System.out.println("Remapped " + classData.getInternalName() + " to " + mappings.getClassMappings()
                            .get(classData.getInternalName()));
                }
            }
        }
    }
}
