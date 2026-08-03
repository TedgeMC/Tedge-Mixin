package pl.olafcio.tedge_mixin.jvm.atpoint.impl;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import pl.olafcio.tedge_mixin.jvm.atpoint.AtPoint;
import pl.olafcio.tedge_mixin.jvm.instance.ApplyParams;

import static org.objectweb.asm.Opcodes.RETURN;

public record AtTail() implements AtPoint {
    public static final AtTail INSTANCE = new AtTail();

    @Override
    public void apply(ApplyParams params) {
        var targetMethod = params.targetMethod();
        var mixinMethod = params.mixinMethod();

        AbstractInsnNode lastReturn = null;

        for (var ins : targetMethod.instructions)
            if (ins.getOpcode() == RETURN)
                lastReturn = ins;

        assert lastReturn != null;

        targetMethod.maxLocals += 2;
        targetMethod.maxStack += 2;

        var copy = new InsnList();

        for (var ins : targetMethod.instructions) {
            if (ins == lastReturn)
                copy.add(params.callbacks().voidCI(mixinMethod, targetMethod.maxLocals - 2, params.cancellable()));

            copy.add(ins);
        }

        targetMethod.instructions = copy;
    }
}
