package org.spongepowered.asm.mixin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface Mixin {
    Class<?>[] value() default {};
    String[] targets() default { };

    int priority() default 1000;
    boolean remap() default true;

    /**
     * <b>(Tedge Mixin Addition: <u>Redefinals</u>)</b>
     * <br><br/>
     * There's an issue with mixins: Already loaded classes can't be injected into.<br/>
     * By setting this true, your mixin will redefine the provided class instead of deferring its transformation.
     */
    boolean redefine() default false;
}
