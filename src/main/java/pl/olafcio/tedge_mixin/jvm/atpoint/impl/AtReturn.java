package pl.olafcio.tedge_mixin.jvm.atpoint.impl;

import org.objectweb.asm.tree.InsnList;
import pl.olafcio.tedge_mixin.jvm.atpoint.AtPoint;
import pl.olafcio.tedge_mixin.jvm.instance.ApplyParams;

import static org.objectweb.asm.Opcodes.RETURN;

public record AtReturn() implements AtPoint {
    public static final AtReturn INSTANCE = new AtReturn();

    @Override
    public void apply(ApplyParams params) {
        var targetMethod = params.targetMethod();
        var mixinMethod = params.mixinMethod();

        var copy = new InsnList();

        for (var ins : targetMethod.instructions) {
            if (ins.getOpcode() == RETURN) {
                targetMethod.maxLocals += 2;
                targetMethod.maxStack += 2;

                copy.add(params.callbacks().voidCI(mixinMethod, targetMethod.maxLocals - 2));
            }

            copy.add(ins);
        }

        targetMethod.instructions = copy;
    }
}
