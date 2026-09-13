package org.spongepowered.asm.util;

/**
 * Borrowed from <a href="https://github.com/SpongePowered/Mixin/blob/master/src/main/java/org/spongepowered/asm/util/Constants.java">https://github.com/SpongePowered/Mixin/blob/master/src/main/java/org/spongepowered/asm/util/Constants.java</a>
 */
public final class Constants {
    private Constants() {}

    public static final String STRING = "java/lang/String";
    public static final String OBJECT = "java/lang/Object";
    public static final String CLASS = "java/lang/Class";

    public static final String STRING_DESC = "L" + Constants.STRING + ";";
    public static final String OBJECT_DESC = "L" + Constants.OBJECT + ";";
    public static final String CLASS_DESC = "L" + Constants.CLASS + ";";
}
