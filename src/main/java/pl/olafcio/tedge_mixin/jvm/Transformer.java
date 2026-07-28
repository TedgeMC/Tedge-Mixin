package pl.olafcio.tedge_mixin.jvm;

import org.objectweb.asm.tree.*;
import pl.olafcio.tedge_mixin.MixinTransformationError;
import pl.olafcio.tedge_mixin.config.MixinConfig;

import java.util.ArrayList;

public class Transformer {
    private final MixinConfig config;

    private final ClassNode mixinNode;
    private final ClassNode runtimeNode;

    private final String mixinClassName;
    private final String runtimeClassName;

    public Transformer(MixinConfig config, ClassNode mixinNode, ClassNode runtimeNode, String mixinClassName, String runtimeClassName) {
        this.config = config;
        this.mixinNode = mixinNode;
        this.runtimeNode = runtimeNode;
        this.mixinClassName = mixinClassName;
        this.runtimeClassName = runtimeClassName;
    }

    public void transform() {
        var prefix = config.tedge().prefix();
        var shadowed = new ArrayList<String>();

        for (FieldNode field : mixinNode.fields) {
            if (field.name.contains("<")) {
                continue;
            }

            if (field.visibleAnnotations != null && field.visibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;"))) {
                shadowed.add(field.name);
                continue;
            }

            field.name = prefix + field.name;
            field.desc = transformType(field.desc);

            runtimeNode.fields.add(field);
        }

        for (var method : mixinNode.methods) {
            if (method.name.contains("<"))
                continue;

            method.name = prefix + method.name;

            for (var ins : method.instructions) {
                if (ins instanceof FieldInsnNode fin) {
                    if (fin.owner.equals(this.mixinClassName)) {
                        if (!shadowed.contains(fin.name))
                            fin.name = prefix + fin.name;

                        fin.owner = runtimeClassName;
                        fin.desc = transformType(fin.desc);
                    }
                } else if (ins instanceof MethodInsnNode min) {
                    if (min.owner.equals(this.mixinClassName)) {
                        if (min.name.contains("<"))
                            throw new MixinTransformationError("Illegal method invocation in mixin: '%s'".formatted(min.name));

                        min.name = prefix + min.name;
                        min.owner = runtimeClassName;
                        min.desc = transformType(min.desc);
                    }
                } else if (ins instanceof InvokeDynamicInsnNode idn) {
//                    IO.println("@@ INVOKEDYNAMIC :: [bsm(" + idn.bsm.getOwner() + ") " + idn.bsm.getName() + "] " + idn.name + " <<" + idn.desc + ">>");
                    idn.desc = transformType(idn.desc);
                }
            }

            runtimeNode.methods.add(method);
        }
    }

    protected String transformType(String jvmType) {
        return new TypeTransformer(jvmType) {
            @Override
            protected String transformLiteral(String literal) {
                return literal.equals(Transformer.this.mixinClassName) ? runtimeClassName : literal;
            }
        }.get();
    }
}
