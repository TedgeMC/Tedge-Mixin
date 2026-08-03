package pl.olafcio.tedge_mixin.jvm.atpoint;

import org.objectweb.asm.tree.MethodNode;
import pl.olafcio.tedge_mixin.MixinIssue;
import pl.olafcio.tedge_mixin.jvm.instance.ApplyParams;

public interface AtPoint {
    default boolean setProperty(String name, Object value) {
        return false;
    }

    default void check(MethodNode method) {
        if (!method.desc.endsWith(")V"))
            throw new MixinIssue("HEAD injection method must return void");
    }

    void apply(ApplyParams params);
}
