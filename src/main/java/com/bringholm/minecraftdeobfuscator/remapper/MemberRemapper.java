package com.bringholm.minecraftdeobfuscator.remapper;

import com.bringholm.minecraftdeobfuscator.jario.ClassData;
import com.bringholm.minecraftdeobfuscator.jario.JarLoader;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;

import javax.annotation.Nonnull;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class MemberRemapper extends SimpleRemapper {

    Set<String> addBridgeModifiers = Sets.newHashSet();
    private static final int SYNTHETIC = 0x00001000;
    // 0x40 is also used for volatile on fields, but we
    // only check methods using it.
    static final int BRIDGE = 0x00000040;

    private LoadingCache<String, Boolean> switchMapCache = CacheBuilder.newBuilder().maximumSize(256L).build(new CacheLoader<String, Boolean>() {
        @Override
        public Boolean load(@Nonnull String key) throws Exception {
            return isEnumSwitchMap(key);
        }
    });
    private Cache<String, Optional<String>> bridgeMethodCache = CacheBuilder.newBuilder().maximumSize(512L).build();
    private JarLoader loader;

    public MemberRemapper(Map mapping, JarLoader loader) {
        super(mapping);
        this.loader = loader;
    }

    @Override
    public String mapMethodName(String owner, String name, String desc) {
        String mappedName = map(owner + "." + name + desc);
        if (mappedName == null) {
            ClassData originalDeclarer = getOriginalMethodDeclarer(owner, name, desc, true);
            if (originalDeclarer != null) {
                mappedName = mapMethodName(originalDeclarer.getInternalName(), name, desc);
            }
        }
        if (mappedName == null && owner.startsWith("net/minecraft/server/")) {
            mappedName = getBridgeMethodName(owner, name, desc);
        }
        return mappedName == null ? name : mappedName;
    }

    @Override
    public String mapFieldName(String owner, String name, String desc) {
        String mappedName = map(owner + "." + name);
        if (mappedName == null) {
            ClassData originalDeclarer = getOriginalFieldDeclarer(owner, name, desc, true);
            if (originalDeclarer != null) {
                mappedName = mapFieldName(originalDeclarer.getInternalName(), name, desc);
            }
        }
        if (mappedName == null && owner.startsWith("net/minecraft/server/")) {
            try {
                if (switchMapCache.get(owner)) {
                    mappedName = getSwitchMapArrayName(owner, name, desc);
                }
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
        return mappedName == null ? name : mappedName;
    }

    @SuppressWarnings("Duplicates")
    private ClassData getOriginalFieldDeclarer(String owner, String name, String desc, boolean skipFirst) {
        ClassData data = loader.getDataFor(owner);
        if (data != null) {
            FieldNode fieldNode = data.getField(name, desc);
            if (fieldNode != null) {
                if (Modifier.isPrivate(fieldNode.access)) {
                    return null;
                }
                if (!skipFirst) {
                    return data;
                }
            }
            for (ClassData superData : data.getSuperClasses()) {
                data = getOriginalFieldDeclarer(superData.getInternalName(), name, desc, false);
                if (data != null) {
                    return data;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("Duplicates")
    private ClassData getOriginalMethodDeclarer(String owner, String name, String desc, boolean skipFirst) {
        ClassData data = loader.getDataFor(owner);
        if (data != null) {
            MethodNode methodNode = data.getMethod(name, desc);
            if (methodNode != null) {
                if (Modifier.isPrivate(methodNode.access)) {
                    return null;
                }
                if (!skipFirst) {
                    return data;
                }
            }
            for (ClassData superData : data.getSuperClasses()) {
                data = getOriginalMethodDeclarer(superData.getInternalName(), name, desc, false);
                if (data != null) {
                    return data;
                }
            }
        }
        return null;
    }

    /**
     * When dealing with generic types as parameters and/or return types, these types just get compiled to their least
     * specific type (if parameter was <E extends CharSequence> it'd be CharSequence, if type was just <E> it'd be Object.
     * To support overriding of these methods with more specific types the compiler creates a synthetic bridge
     * method that actually overrides the super method, and then just 'bridges' that on with a cast to the method
     * containing the actual code. For example, if we had this interface:
     *
     * interface MyInterface<E extends CharSequence> {
     *     void doSomething(E e);
     * }
     *
     * Then an implementation would actually get compiled like this:
     *
     * class MyClass implements MyInterface<String> {
     *     void doSomething(String string) {
     *         // Here's the actual user written code
     *     }
     *
     *     // This is created by the compiler!
     *     synthetic bridge void doSomething(CharSequence object) {
     *         doSomething((String) object));
     *     }
     * }
     *
     * As usual, our problem is that Mojang's Obfuscation tool changes the names. It renames the method
     * that it bridges to, so the decompiler's standard procedure of just hiding the bridge method fails.
     * Mojang's classes also don't seem to have the bridge modifier, just the synthetic one for some reason.
     */
    String getBridgeMethodName(String owner, String name, String desc) {
        Optional<String> optional = bridgeMethodCache.getIfPresent(owner + "." + name + desc);
        if (optional != null) {
            return optional.orElse(null);
        }
        if (!owner.startsWith("net/minecraft/server/")) {
            bridgeMethodCache.put(owner + "." + name + desc, Optional.empty());
            return null;
        }
        ClassData data = loader.getDataFor(owner);
        if (data == null) {
            bridgeMethodCache.put(owner + "." + name + desc, Optional.empty());
            return null;
        }
        MethodNode targetNode = data.getMethod(name, desc);
        if (targetNode == null) {
            bridgeMethodCache.put(owner + "." + name + desc, Optional.empty());
            return null;
        }
        //noinspection unchecked
        for (MethodNode methodNode : (List<MethodNode>) data.getNode().methods) {
            if ((methodNode.access & SYNTHETIC) == SYNTHETIC || (methodNode.access & BRIDGE) == BRIDGE) {
                for (AbstractInsnNode insnNode : methodNode.instructions.toArray()) {
                    if (insnNode instanceof MethodInsnNode) {
                        MethodInsnNode methodInsn = (MethodInsnNode) insnNode;
                        if (methodInsn.owner.equals(owner) && methodInsn.name.equals(targetNode.name) && methodInsn.desc.equals(targetNode.desc)) {
                            if (!methodNode.name.equals(targetNode.name)) {
                                bridgeMethodCache.put(owner + "." + name + desc, Optional.of(methodNode.name));
                                if ((methodNode.access & SYNTHETIC) == SYNTHETIC && (methodNode.access & BRIDGE) == 0 && !addBridgeModifiers.contains(owner + "." + name + desc)) {
                                    addBridgeModifiers.add(owner + "." + methodNode.name + methodNode.desc);
                                }
                                return methodNode.name;
                            }
                        }
                    }
                }
            }
        }
        bridgeMethodCache.put(owner + "." + name + desc, Optional.empty());
        return null;
     }

    /**
     * When you have a Switch on an Enum, the java compiler creates an anonymous inner class with mappings for the enums.
     * The class contains a synthetic static final int[] with the name $SwitchMap$<Enum> (the enum uses $ as package
     * separator). For each Enum there exists a switch on in the class, one of these fields are created. The arrays
     * are then populated in the static initializer like this:
     *
     * synthetic static final int[] $SwitchMap$my$package$ExampleEnum
     *
     * static {
     *     $SwitchMap$my$package$ExampleEnum = new int[my.package.ExampleEnum.values().length];
     *     try {
     *         $SwitchMap$my$package$ExampleEnum[my.package.ExampleEnum.ENUM_VALUE.ordinal()] = 1; // Indexes start at 1, not 0!
     *     } catch (NoSuchFieldError err) {}
     *     try {
     *         $SwitchMap$my$package$ExampleEnum[my.package.ExampleEnum.ENUM_VALUE_2.ordinal()] = 2;
     *     } catch (NoSuchFieldError err) {}
     * }
     *
     * NOTE: Inner classes don't create a separate SwitchMap, they use the one of the outer class.
     *
     * Our problem is that Mojang's Obfuscation tool renames the static array fields, which makes most decompilers
     * trip up, so we need to try to fix it.
     */
    private boolean isEnumSwitchMap(String owner) {
        ClassData data = loader.getDataFor(owner);
        if (data == null) {
            return false;
        }
        // Check if the class is synthetic and has a static initializer method.
        if ((data.getNode().access & SYNTHETIC) == SYNTHETIC && data.getMethod("<clinit>", "()V") != null) {
            //noinspection unchecked
            for (FieldNode fieldNode : (List<FieldNode>) data.getNode().fields) {
                // Check if the class has at least one synthetic static final int[] field whose name doesn't start with
                // '$SwitchMap$'
                if (!fieldNode.name.startsWith("$SwitchMap$") && fieldNode.desc.equals("[I") && isSyntheticFinalAndStatic(fieldNode.access)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets the appropriate name of the SwitchMap field. This assumes that it has already been made sure that this
     * class is a SwitchMap.
     *
     * @see #isEnumSwitchMap(String)
     */
    private String getSwitchMapArrayName(String owner, String name, String desc) {
        ClassData data = loader.getDataFor(owner);
        if (data == null) {
            return null;
        }
        FieldNode fieldNode = data.getField(name, desc);
        if (!fieldNode.desc.equals("[I") || !isSyntheticFinalAndStatic(fieldNode.access)) {
            return null;
        }
        /*
         * Bytecode for the array creation is:
         * INVOKESTATIC Enum.values()
         * ARRAYLENGTH
         * NEWARRAY int
         * PUTSTATIC $SwitchMap$Enum
         */
        MethodNode clinitNode = data.getMethod("<clinit>", "()V");
        for (AbstractInsnNode insnNode : clinitNode.instructions.toArray()) {
            if (insnNode.getOpcode() == Opcodes.PUTSTATIC) {
                FieldInsnNode fieldInsnNode = (FieldInsnNode) insnNode;
                if (fieldInsnNode.owner.equals(owner) && fieldInsnNode.name.equals(name) && fieldInsnNode.desc.equals(desc)) {
                    // This should be a MethodInsnNode with Opcode INVOKESTATIC
                    insnNode = insnNode.getPrevious().getPrevious().getPrevious();
                    if (insnNode.getOpcode() != Opcodes.INVOKESTATIC || !(insnNode instanceof MethodInsnNode)) {
                        throw new IllegalStateException("InsnNode " + insnNode + " (Opcode " + insnNode.getOpcode() + ") was expected to be a MethodInsnNode with Opcode INVOKESTATIC!");
                    }
                    MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
                    if (!methodInsnNode.name.equals("values")) {
                        throw new IllegalStateException("Name of node was expected to be \"values\", but was " + methodInsnNode.name);
                    }
                    return "$SwitchMap$" + methodInsnNode.owner.replace('/', '$');
                }
            }
        }
        return null;
    }

    private boolean isSyntheticFinalAndStatic(int modifier) {
        return Modifier.isFinal(modifier) && Modifier.isStatic(modifier) && (modifier & SYNTHETIC) == SYNTHETIC;
    }
}
