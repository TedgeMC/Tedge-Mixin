package pl.olafcio.tedge_mixin.jvm.instance;

import org.objectweb.asm.tree.*;

public abstract class Callbacks {
    protected final String runtimeClassName;

    protected Callbacks(String runtimeClassName) {
        this.runtimeClassName = runtimeClassName;
    }

    public String runtimeClassName() {
        return runtimeClassName;
    }

    public abstract InsnList voidCI(MethodNode method, final int varIndex, final boolean cancellable, MethodNode target);
}
