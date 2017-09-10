package com.bringholm.minecraftdeobfuscator.remapper;

import org.objectweb.asm.commons.SimpleRemapper;

import java.util.Map;
import java.util.regex.Pattern;

public class AnonymousClassNameRemapper extends SimpleRemapper {

    private static final Pattern PATTERN = Pattern.compile("(\\$[0-9]+)+");

    public AnonymousClassNameRemapper(Map mapping) {
        super(mapping);
    }

    @Override
    public String map(String key) {
        String mappedName = super.map(key);
        if (mappedName == null && key.indexOf('$') != -1) {
            int dollarIndex = key.indexOf('$');
            while (dollarIndex != -1) {
                String beforeDollar = key.substring(0, dollarIndex);
                String afterDollar = key.substring(dollarIndex);
                // Check if the after dollar bit only contains numbers and dollar signs, meaning it should be an anonymous class.
                if (PATTERN.matcher(afterDollar).matches()) {
                    String outerMapping = super.map(beforeDollar);
                    if (outerMapping != null) {
                        mappedName = outerMapping + afterDollar;
                    }
                    break;
                }
                dollarIndex = key.indexOf('$', dollarIndex + 1);
            }

        }
        return mappedName;
    }
}
