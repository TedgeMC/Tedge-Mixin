package pl.olafcio.tedge_mixin;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import pl.olafcio.tedge_mixin.annotation_state.Mixin;
import pl.olafcio.tedge_mixin.config.MixinConfig;
import pl.olafcio.tedge_mixin.jvm.Transformer;

import java.io.IOException;
import java.lang.instrument.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.jar.JarFile;

import static org.objectweb.asm.Opcodes.ASM9;

/**
 * This class is used to register mixins.
 */
public class MixinLoader {
    protected final Instrumentation inst;
    protected final ArrayList<String> mixins
              = new ArrayList<>();

    public MixinLoader(Instrumentation inst) {
        this.inst = inst;
    }

    public void finishInjections() {
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                if (className.equals("net/minecraft/client/Minecraft")||className.startsWith("com/example")) {
                    try {
                        Files.write(Path.of("./"+className.replace("/",".")+".class"), classfileBuffer);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    //FIXME Delete this
                }

                var klass = new ClassReader(classfileBuffer);
                var visitor = new ClassVisitor(ASM9) {
                    @Override
                    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                        if (mixins.contains(name)) {
                            new NoClassDefFoundError("Mixins can't be referenced").printStackTrace();
                            System.exit(1);
                        }

                        super.visit(version, access, name, signature, superName, interfaces);
                    }
                };

                klass.accept(visitor, 0);

                return null;
            }
        });
    }

    /**
     * Initializes transformers to perform the provided injections.<br/>
     * After everything has been transformed, the transformer is unregistered.
     */
    public void addInjections(MixinConfig config, JarFile file) {
        var entries = file.entries();

        do {
            var nxt = entries.nextElement();
            var name = nxt.getName();

            if (name.endsWith(".class")) {
                if (name.startsWith(config._package().replace(".", "/"))) {
                    try (var stream = file.getInputStream(nxt)) {
                        String internalName = name.substring(0, name.length() - 6);

                        var klass = new ClassReader(stream.readAllBytes());
                        var node = new ClassNode(ASM9);

                        klass.accept(node, 0);

                        addInjection(node, internalName, config);

                        var writer = new ClassWriter(0);
                        node.accept(writer);
                        inst.redefineClasses(new ClassDefinition(Class.forName(internalName.replace("/", ".")), writer.toByteArray()));
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read mixin '%s'".formatted(name), e);
                    } catch (Exception e) {
                        IO.println("ERROR!"+e);
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                }
            }
        } while (entries.hasMoreElements());
    }

    private record DollarSir(String classname, ClassNode code) {}
    private final HashMap<String, ArrayList<DollarSir>> dollarSirs
            = new HashMap<>();

    private void addInjection(ClassNode node, String className, MixinConfig config) {
        Mixin mixin = null;

        if (node.invisibleAnnotations != null)
            for (var a : node.invisibleAnnotations)
                if (a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))
                    mixin = ann_Mixin(a);

        if (mixin == null) {
            if (className.contains("$")) {
                dollarSirs.computeIfAbsent(node.outerClass, _ -> new ArrayList<>()).add(
                        new DollarSir(className, node)
                );

                node.outerClass = null;

                return;
            }

            throw new RuntimeException("Non-mixin class in mixin package");
        }

        mixins.add(className);

        var state = new MixinState(className, node, config);
        state.init(mixin);
        state.register(inst);

        node.innerClasses.clear();

        var runtimeClassName = mixin.targets().size() == 1
                ? mixin.targets().getFirst()
                : "Ljava/lang/Object;";

        try {
            var entries = dollarSirs.get(className);
            for (var e : entries) {
                if (!runtimeClassName.equals("Ljava/lang/Object;"))
                    e.code.outerClass = runtimeClassName;

                inst.redefineClasses(new ClassDefinition(
                        Class.forName(e.classname().replace("/", ".")),
                        transformDependant(e.classname(), e.code(), config, runtimeClassName)
                ));
            }
        } catch (UnmodifiableClassException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] transformDependant(String className2, ClassNode node, MixinConfig config, String runtimeClassName) {
        var transformer = new Transformer(config, null, null, className2, runtimeClassName);

        var shadowedFields = new ArrayList<String>();
        var shadowedMethods = new ArrayList<String>();

        for (var f : node.fields) {
            f.desc = transformer.transformType(f.desc);

            if (f.invisibleAnnotations != null && f.invisibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;")))
                shadowedFields.add(f.name);
        }

        for (var m : node.methods) {
            m.desc = transformer.transformType(m.desc);

            if (m.invisibleAnnotations != null && m.invisibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;")))
                shadowedMethods.add(m.name + m.desc);
        }

        for (var m : node.methods) {
            transformer.transformInstructions(m, shadowedFields, config.tedge().prefix(), shadowedMethods);
        }

        var writer = new ClassWriter(0);

        node.accept(writer);

        return writer.toByteArray();
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
}
