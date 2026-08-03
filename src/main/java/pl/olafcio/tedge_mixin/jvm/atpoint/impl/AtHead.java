package pl.olafcio.tedge_mixin.jvm.atpoint.impl;

import pl.olafcio.tedge_mixin.jvm.atpoint.AtPoint;
import pl.olafcio.tedge_mixin.jvm.instance.ApplyParams;

public record AtHead() implements AtPoint {
    public static final AtHead INSTANCE = new AtHead();

    @Override
    public void apply(ApplyParams params) {
        var targetMethod = params.targetMethod();
        var mixinMethod = params.mixinMethod();

        targetMethod.maxLocals += 2;
        targetMethod.maxStack += 2;
        targetMethod.instructions.insert(params.callbacks().voidCI(mixinMethod, 1, params.cancellable()));
    }
}
