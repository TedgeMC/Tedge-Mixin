package pl.olafcio.tedge_mixin.jvm;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.tree.*;
import pl.olafcio.tedge_mixin.MixinIssue;
import pl.olafcio.tedge_mixin.MixinTransformationError;
import pl.olafcio.tedge_mixin.config.MixinConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

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
        runtimeNode.innerClasses.addAll(mixinNode.innerClasses);

        var prefix = config.tedge().prefix();
        var shadowedFields = new ArrayList<String>();

        for (FieldNode field : mixinNode.fields) {
            if (field.name.contains("<")) {
                continue;
            }

            if (field.visibleAnnotations != null && field.visibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;"))) {
                shadowedFields.add(field.name);

                var realField = runtimeNode.fields.stream().filter(f -> f.name.equals(field.name) &&
                                                                                  f.desc.equals(field.desc))
                                                           .findAny()
                                                           .orElseThrow(() -> new MixinIssue("@Shadow field not present  (mixin: %s)".formatted(mixinClassName)));

                boolean finalAnnot = field.visibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Final;"));
                boolean finalKeywd = (field.access & ACC_FINAL) == ACC_FINAL;

                boolean targetFinal;
                boolean mutable;

                if (field.visibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mutable;"))) {
                    if (!finalAnnot)
                        throw new MixinIssue("@Mutable shadow field without @Final");

                    targetFinal = false;
                    mutable = true;

                    if ((realField.access & ACC_FINAL) == ACC_FINAL)
                        realField.access -= ACC_FINAL;
                } else {
                    targetFinal = (realField.access & ACC_FINAL) == ACC_FINAL;
                    mutable = false;
                }

                if (finalKeywd && mutable)
                    throw new MixinIssue("@Mutable shadow field marked with 'final' keyword");

                if (finalKeywd != targetFinal)
                    IO.println("[TedgeMixin] Mismatched final state on a @Shadow field; target %s final, mixin field %s".formatted(
                            targetFinal ? "is" : "isn't",
                            finalKeywd ? "is" : "isn't"
                    ));

                if ((finalKeywd && !finalAnnot) || (!finalKeywd && finalAnnot && !mutable))
                    throw new MixinIssue("[TedgeMixin] Mismatched final state on a @Shadow field in the mixin itself; %s final,%s annotated with @Final".formatted(
                            finalKeywd ? "is" : "isn't",
                            finalAnnot ? "" : " not"
                    ));

                continue;
            }

            field.name = prefix + field.name;
            field.desc = transformType(field.desc);

            runtimeNode.fields.add(field);
        }

        var shadowedMethods = new ArrayList<String>();

        for (var iname : mixinNode.interfaces) {
            runtimeNode.interfaces.add(iname);

            try {
                var klass = Class.forName(iname);
                while (klass != null && klass != Object.class) {
                    var methods = klass.getDeclaredMethods();
                    for (var m : methods)
                        shadowedMethods.add(m.getName() + "(" + Arrays.stream(m.getParameterTypes()).map(Class::descriptorString).collect(Collectors.joining()) + ")"
                                                         + m.getReturnType().descriptorString());

                    klass = klass.getSuperclass();
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Unable to iterate through implemented interface's methods", e);
            }
        }

        for (var method : mixinNode.methods) {
            if (method.name.contains("<"))
                continue;

            if (method.visibleAnnotations != null && method.visibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;"))) {
                if (
                        (method.access & ACC_STATIC) != ACC_STATIC &&
                        (method.access & ACC_ABSTRACT) != ACC_ABSTRACT
                )
                    throw new MixinIssue("Non-static shadow methods must be abstract");

                shadowedMethods.add(method.name + method.desc);

                var realMethod = runtimeNode.methods.stream().filter(m -> m.name.equals(method.name) &&
                                                                                      m.desc.equals(method.desc))
                                                             .findAny()
                                                             .orElseThrow(() -> new MixinIssue("@Shadow method not present  (mixin: %s)".formatted(mixinClassName)));

                boolean mixinFin  = (method.visibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Final;")));
                boolean targetFin = (realMethod.access & ACC_FINAL) == ACC_FINAL;

                if (mixinFin != targetFin)
                    IO.println("[TedgeMixin] Mismatched final state on a @Shadow method; target %s @Final, mixin method %s".formatted(
                            targetFin ? "is" : "isn't",
                            mixinFin ? "is" : "isn't"
                    ));
            } else {
                if ((method.access & ACC_ABSTRACT) == ACC_ABSTRACT)
                    throw new MixinIssue("Non-shadow abstract methods are not allowed");
            }
        }

        for (var method : mixinNode.methods) {
            if (method.name.contains("<")) {
                continue;
            }

            if (shadowedMethods.contains(method.name + method.desc)) {
                continue;
            }

            method.name = prefix + method.name;

            transformInstructions(method, shadowedFields, prefix, shadowedMethods);

            if (method.visibleAnnotations != null) {
                for (var a : method.visibleAnnotations) {
                    if (a.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) {
                        var targetedMethods = new ArrayList<MethodNode>();
                        var atpoint = new ArrayList<String>();

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
                                                            atpoint.add((String) value);

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

                        for (String at : atpoint) {
                            targetedMethods.forEach(m -> {
                                m.maxLocals += 2;
                                m.maxStack += 2;

                                if (at.equals("HEAD"))
                                    m.instructions.insert(voidCI(method));
                            });
                        }

                        break;
                    }
                }

                runtimeNode.methods.add(method);
            }
        }
    }

    public void transformInstructions(MethodNode method, ArrayList<String> shadowedFields, String prefix, ArrayList<String> shadowedMethods) {
        for (var ins : method.instructions) {
            if (ins instanceof FieldInsnNode fin) {
                if (fin.owner.equals(this.mixinClassName)) {
                    if (!shadowedFields.contains(fin.name))
                        fin.name = prefix + fin.name;

                    fin.owner = runtimeClassName;
                }

                fin.desc = transformType(fin.desc);
            } else if (ins instanceof MethodInsnNode min) {
                if (min.owner.equals(this.mixinClassName)) {
                    if (min.name.contains("<"))
                        throw new MixinTransformationError("Illegal method invocation in mixin: '%s'".formatted(min.name));

                    if (!shadowedMethods.contains(min.name + min.desc))
                        min.name = prefix + min.name;

                    min.owner = runtimeClassName;
                }

                min.desc = transformType(min.desc);
            } else if (ins instanceof InvokeDynamicInsnNode idn) {
//                    IO.println("@@ INVOKEDYNAMIC :: [bsm(" + idn.bsm.getOwner() + ") " + idn.bsm.getName() + "] " + idn.name + " <<" + idn.desc + ">>");
                idn.desc = transformType(idn.desc);
            }
        }
    }

    protected InsnList voidCI(MethodNode method) {
        return new InsnList() {{
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
            add(new FrameNode(F_APPEND, 1, new Object[]{"org/spongepowered/asm/mixin/injection/callback/CallbackInfo"}, 0, new Object[]{}));
        }};
    }

    public String transformType(String jvmType) {
        return new TypeTransformer(jvmType) {
            @Override
            protected String transformLiteral(String literal) {
                return literal.equals(Transformer.this.mixinClassName) ? runtimeClassName : literal;
            }
        }.get();
    }
}
