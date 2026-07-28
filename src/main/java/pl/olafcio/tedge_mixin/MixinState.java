package pl.olafcio.tedge_mixin;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import pl.olafcio.tedge_mixin.annotation_state.Mixin;
import pl.olafcio.tedge_mixin.config.MixinConfig;
import pl.olafcio.tedge_mixin.jvm.TypeTransformer;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.ArrayList;

import static org.objectweb.asm.Opcodes.ASM9;

public class MixinState {
    protected final String className;
    protected final ClassNode node;
    protected final MixinConfig config;

    public MixinState(String className, ClassNode node, MixinConfig config) {
        this.className = className;
        this.node = node;
        this.config = config;
    }

    protected Mixin mixin;

    public void init() {
        IO.println("[TedgeMixin] Registering '%s'".formatted(node.name));
        IO.println("[TedgeMixin] [Debug] Methods: " + node.methods.stream().map(m -> "[!] " + m.name + " " + m.desc).toList());
        IO.println("[TedgeMixin] [Debug] Fields: " + node.fields.stream().map(f -> "[!] " + f.desc + " " + f.name).toList());
        IO.println("[TedgeMixin] [Debug] VA: " + node.visibleAnnotations);
        IO.println("[TedgeMixin] [Debug] IVA: " + node.invisibleAnnotations.stream().map(a -> a.desc).toList());
        IO.println("[TedgeMixin] [Debug] Attributes: " + node.attrs);
        IO.println("[TedgeMixin] [Debug] Interfaces: " + node.interfaces);

        for (var a : node.invisibleAnnotations)
            if (a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))
                mixin = ann_Mixin(a);
            else
                throw new RuntimeException("Unexpected '@%s'".formatted(a.desc.substring(1, a.desc.length() - 1).replace("/", ".")));
    }

    private static Mixin ann_Mixin(AnnotationNode a) {
        var targets = new ArrayList<String>();

        a.accept(new AnnotationVisitor(ASM9) {
            @Override
            public AnnotationVisitor visitArray(String name) {
                var parent = super.visitArray(name);
                if (name.equals("value")) {
                    return new AnnotationVisitor(ASM9, parent) {
                        @Override
                        public void visit(String name, Object value) {
                            super.visit(name, value);
                            if (value instanceof Type type) {
                                if (type.getSort() == Type.OBJECT) {
                                    targets.add(type.getInternalName());
                                }
                            }
                        }
                    };
                }

                throw new RuntimeException("Unimplemented support for @Mixin(%s = %s)".formatted(name, parent));
            }

            @Override
            public void visit(String name, Object value) {
                throw new RuntimeException("Unimplemented support for @Mixin(%s = %s)".formatted(name, value));
            }
        });

        return new Mixin(targets, 0, false);
    }

    private int registered = 0;
    public void register(Instrumentation inst) {
        var transformer = new ClassFileTransformer[1];

        IO.println("[TedgeMixin] Registering transformer for " + mixin.targets());

        final int require = mixin.targets().size();

        transformer[0] = new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if (mixin.targets().contains(className)) {
                    IO.println("[TedgeMixin] Injecting into " + className);

                    var reader = new ClassReader(classfileBuffer);
                    var node = new ClassNode();

                    reader.accept(node, 0);

                    MixinState.this.transform(className, node);

                    var writer = new ClassWriter(0);
                    node.accept(writer);
                    classfileBuffer = writer.toByteArray();

                    if (++registered == require) {
                        inst.removeTransformer(transformer[0]);
                        IO.println("[TedgeMixin] Unregistering transformer, all mixins applied");
                    }
                }

                return classfileBuffer;
            }
        };

        inst.addTransformer(transformer[0]);
    }

    protected void transform(String className, ClassNode classNode) {
        String prefix = config.tedge().prefix();

        for (var method : node.methods) {
            if (method.name.contains("<"))
                continue;

            method.name = prefix + method.name;

            for (var ins : method.instructions) {
                if (ins instanceof FieldInsnNode fin) {
                    if (fin.owner.equals(this.className)) {
                        fin.name = prefix + fin.name;
                        fin.owner = className;
                        fin.desc = transformType(className, fin.desc);
                    }
                } else if (ins instanceof MethodInsnNode min) {
                    if (min.owner.equals(this.className)) {
                        if (min.name.contains("<"))
                            throw new MixinTransformationError("Illegal method invocation in mixin: '%s'".formatted(min.name));

                        min.name = prefix + min.name;
                        min.owner = className;
                        min.desc = transformType(className, min.desc);
                    }
                }
            }

            classNode.methods.add(method);
        }

        for (var field : node.fields) {
            if (field.name.contains("<"))
                continue;

            field.name = prefix + field.name;
            field.desc = transformType(className, field.desc);

            classNode.fields.add(field);
        }
    }

    protected String transformType(String runtimeClassName, String jvmType) {
        return new TypeTransformer(jvmType) {
            @Override
            protected String transformLiteral(String literal) {
                return literal.equals(className) ? runtimeClassName : literal;
            }
        }.get();
    }
}
