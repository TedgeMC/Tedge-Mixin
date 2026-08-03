package pl.olafcio.tedge_mixin.jvm.instance;

import org.objectweb.asm.tree.MethodNode;

public record ApplyParams(
        MethodNode mixinMethod,
        MethodNode targetMethod,
        Callbacks callbacks
) {}
