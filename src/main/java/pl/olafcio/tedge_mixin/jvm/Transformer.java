package pl.olafcio.tedge_mixin.jvm;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import pl.olafcio.tedge_mixin.MixinIssue;
import pl.olafcio.tedge_mixin.MixinTransformationError;
import pl.olafcio.tedge_mixin.config.MixinConfig;

import java.util.ArrayList;

import static org.objectweb.asm.Opcodes.*;

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

            if (method.visibleAnnotations != null) {
                for (var a : method.visibleAnnotations) {
                    if (a.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) {
                        var targetedMethods = new ArrayList<MethodNode>();
                        var atpoint = new String[1];

                        a.accept(new AnnotationVisitor(ASM9) {
                            @Override
                            public void visit(String name, Object value) {
                                throw new MixinIssue("Unimplemented property @Inject(%s = %s)".formatted(name, value));
                            }

                            @Override
                            public AnnotationVisitor visitArray(String name) {
                                if (name.equals("at")) {
                                    return new AnnotationVisitor(ASM9) {
                                        @Override
                                        public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                                            return new AnnotationVisitor(ASM9) {
                                                @Override
                                                public void visit(String name, Object value) {
                                                    if (name.equals("value")) {
                                                        if (value.equals("HEAD")) {
                                                            atpoint[0] = (String) value;

                                                            if (!method.desc.endsWith(")V"))
                                                                throw new MixinIssue("HEAD injection method must return void");
                                                        } else
                                                            throw new MixinIssue("Unimplemented property atpoint '%s'  (mixin: %s)".formatted(value, mixinClassName));
                                                    } else {
                                                        throw new MixinIssue("Unimplemented property @At(%s = %s)".formatted(name, value));
                                                    }
                                                }

                                                @Override
                                                public AnnotationVisitor visitArray(String name) {
                                                    throw new MixinIssue("Unimplemented property @At(%s = ...)".formatted(name));
                                                }

                                                @Override
                                                public void visitEnum(String name, String descriptor, String value) {
                                                    throw new MixinIssue("Unimplemented property @At(%s = %s)".formatted(name, value));
                                                }

                                                @Override
                                                public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                                                    throw new MixinIssue("Unimplemented property @At(%s = ...)".formatted(name));
                                                }
                                            };
                                        }
                                    };
                                } else if (name.equals("method")) {
                                    return new AnnotationVisitor(ASM9) {
                                        @Override
                                        public void visit(String name, Object valueraw) {
                                            var value = (String) valueraw;
                                            var methods = runtimeNode.methods;

                                            if (value.contains("(")) {
                                                // Name + Signature
                                                if (targetedMethods.contains(value))
                                                    throw new MixinIssue("Method '%s' specified twice in a single injection  (mixin: %s)".formatted(value, mixinClassName));

                                                int injected = 0;

                                                for (var m : methods) {
                                                    if ((m.name + m.desc).equals(value)) {
                                                        targetedMethods.add(m);
                                                        injected++;
                                                    }
                                                }

                                                if (injected < config.injectors().defaultRequire())
                                                    throw new MixinIssue("Not enough injections for method '%s'; had %d, expected %d  (mixin %s)".formatted(value, injected, config.injectors().defaultRequire(), mixinClassName));
                                            } else {
                                                // Name-only
                                                for (var m : methods) {
                                                    if (m.name.equals(value)) {
                                                        var sign = value + m.desc;
                                                        if (targetedMethods.contains(sign))
                                                            throw new MixinIssue("Method '%s' specified twice in a single injection  (mixin: %s)".formatted(sign, mixinClassName));

                                                        targetedMethods.add(m);
                                                    }
                                                }
                                            }
                                        }
                                    };
                                } else {
                                    throw new MixinIssue("Unimplemented property @Inject(%s = ...)".formatted(name));
                                }
                            }

                            @Override
                            public void visitEnum(String name, String descriptor, String value) {
                                throw new MixinIssue("Unimplemented property @Inject(%s = %s)".formatted(name, value));
                            }

                            @Override
                            public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                                throw new MixinIssue("Unimplemented property @Inject(%s = ...)".formatted(name));
                            }
                        });

                        if (targetedMethods.isEmpty())
                            throw new MixinIssue("No targeted methods  (mixin: %s)".formatted(mixinClassName));

                        targetedMethods.forEach(m -> {
                            m.maxLocals += 2;
                            m.maxStack += 2;
                            m.instructions.insert(
                                    new InsnList() {{
                                        // creating the CallbackInfo object
                                        add(new TypeInsnNode(NEW, "org/spongepowered/asm/mixin/injection/callback/CallbackInfo"));
                                        add(new InsnNode(DUP));
                                        add(new MethodInsnNode(INVOKESPECIAL, "org/spongepowered/asm/mixin/injection/callback/CallbackInfo", "<init>", "()V"));
                                        add(new VarInsnNode(ASTORE, 1));
                                        // calling the mixin method
                                        add(new VarInsnNode(ALOAD, 0));
                                        add(new VarInsnNode(ALOAD, 1));
                                        add(new MethodInsnNode(INVOKEVIRTUAL, runtimeClassName, method.name, method.desc));
                                        // returning if cancelled
                                        add(new VarInsnNode(ALOAD, 1));
                                        add(new MethodInsnNode(INVOKEVIRTUAL, "org/spongepowered/asm/mixin/injection/callback/CallbackInfo", "isCancelled", "()Z", false));

                                        var label = new LabelNode();

                                        add(new JumpInsnNode(IFEQ, label));
                                        add(new InsnNode(RETURN));

                                        add(label);
                                        add(new FrameNode(F_APPEND, 1, new Object[]{ "org/spongepowered/asm/mixin/injection/callback/CallbackInfo" }, 0, new Object[]{}));
                                    }}
                            );
                        });

                        break;
                    }
                }

                runtimeNode.methods.add(method);
            }
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
