package com.bringholm.minecraftdeobfuscator;

import com.bringholm.minecraftdeobfuscator.remapper.AnonymousClassNameRemapper;
import org.junit.Test;
import org.objectweb.asm.commons.Remapper;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class MappingTests {
    @Test
    public void testAnonymousClassNameRemapper() {
        Map<String, String> mappings = new HashMap<>();
        mappings.put("aa", "Test");
        mappings.put("aa$a", "Test$InnerClass");
        Remapper remapper = new AnonymousClassNameRemapper(mappings);
        assertEquals("Test", remapper.map("aa"));
        assertEquals("Test$InnerClass", remapper.map("aa$a"));
        assertEquals(null, remapper.map("aa$b"));
        assertEquals(null, remapper.map("aa$a$b"));
        assertEquals("Test$1", remapper.map("aa$1"));
        assertEquals("Test$InnerClass$1", remapper.map("aa$a$1"));
        assertEquals("Test$1$0", remapper.map("aa$1$0"));
        assertEquals("Test$99", remapper.map("aa$99"));
        assertEquals("Test$InnerClass$0$1", remapper.map("aa$a$0$1"));
        assertEquals("Test$InnerClass$9999", remapper.map("aa$a$9999"));
    }
}
