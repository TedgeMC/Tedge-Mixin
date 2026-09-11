package pl.olafcio.tedge_mixin;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import pl.olafcio.tedge_mixin.annotation_state.Mixin;
import pl.olafcio.tedge_mixin.config.MixinConfig;
import pl.olafcio.tedge_mixin.extension.Extension;
import pl.olafcio.tedge_mixin.extension.impl.GroovyExtension;
import pl.olafcio.tedge_mixin.jvm.Transformer;

import java.io.IOException;
import java.lang.instrument.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.objectweb.asm.Opcodes.*;

/**
 * This class is used to register mixins.
 */
public class MixinLoader {
    protected final Instrumentation inst;
    protected final ArrayList<String> mixins
              = new ArrayList<>();

    private final ArrayList<Extension> extensions
            = new ArrayList<>();

    protected void addExtension(Extension extension) {
        extensions.add(extension);
    }

    public List<Extension> getExtensions() {
        return extensions;
    }

    public MixinLoader(Instrumentation inst) {
        this.inst = inst;
        addStandardExtensions();
    }

    protected void addStandardExtensions() {
        addExtension(new GroovyExtension());
    }

    public void finishInjections() {
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
//                if (className.equals("net/minecraft/client/Minecraft")||className.startsWith("com/example")) {
//                    try {
//                        Files.write(Path.of("./"+className.replace("/",".")+".class"), classfileBuffer);
//                    } catch (IOException e) {
//                        throw new RuntimeException(e);
//                    }
//                    //FIXME Delete this
//                }

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
     * Initializes transformers to perform the provided injections, and a plugin first if present.<br/>
     * After everything has been transformed, the transformer is unregistered.
     */
    public void addInjections(MixinConfig config, JarFile file, ZipOutputStream output, Environment environment) {
        if (config.plugin() != null) {
            var pluginEntry = file.getEntry(config.plugin().replace(".", "/") + ".class");
            if (pluginEntry == null)
                throw new RuntimeException("Mixin plugin '%s' not found".formatted(config.plugin()));

            try (var ucl = new URLClassLoader(new URL[]{ URI.create(file.getName()).toURL() })) {
                var pluginClass = ucl.loadClass(config.plugin());
                var plugin = (IMixinConfigPlugin) pluginClass.getDeclaredConstructor().newInstance();

                addExtension(new Extension() {
                    @Override
                    public void onPreLoad(MixinConfig config) {
                        plugin.onLoad(config._package());
                    }

                    @Override
                    public boolean shouldApply(MixinConfig config, ClassNode mixinNode, ClassNode runtimeNode, String mixinClassName, String runtimeClassName) {
                        return plugin.shouldApplyMixin(runtimeClassName, mixinClassName);
                    }

                    @Override
                    public void onBeforeTargetApply(MixinConfig config, ClassNode mixinNode, ClassNode runtimeNode, String mixinClassName, String runtimeClassName) {
                        plugin.preApply(runtimeClassName, runtimeNode, mixinClassName, null);
                    }

                    @Override
                    public void onAfterTargetApply(MixinConfig config, ClassNode mixinNode, ClassNode runtimeNode, String mixinClassName, String runtimeClassName) {
                        plugin.postApply(runtimeClassName, runtimeNode, mixinClassName, null);
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException("Failed to access JAR of mixin plugin", e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Failed to access class of mixin plugin", e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException("Failed to construct mixin plugin/invocation error", e);
            } catch (InstantiationException e) {
                throw new RuntimeException("Failed to construct mixin plugin/java error", e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to construct mixin plugin/access error", e);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Failed to construct mixin plugin/a no-arg constructor isn't defined", e);
            } catch (ClassCastException e) {
                throw new RuntimeException("Failed to process mixin plugin/IMixinConfigPlugin isn't implemented", e);
            }
        }

        for (var ext : extensions)
            ext.onPreLoad(config);

        var zipEntries = file.entries();

        List<String> mixinList;
        List<String> ignoreList;

        mixinList = new ArrayList<>(Arrays.asList(config.mixins()));

        // epstein client list
        if (environment == Environment.CLIENT) {
            Collections.addAll(mixinList, config.client());
            ignoreList = Arrays.stream(config.server()).toList();
        } else if (environment == Environment.SERVER) {
            Collections.addAll(mixinList, config.server());
            ignoreList = Arrays.stream(config.client()).toList();
        } else
            throw new RuntimeException("Unknown environment (specified '%s')".formatted(environment));

        do {
            var entry = zipEntries.nextElement();
            var name = entry.getName();

            final byte[] content;

            contentSet:
            {
                if (name.endsWith(".class")) {
                    if (name.startsWith(config._package().replace(".", "/"))) {
                        try (var stream = file.getInputStream(entry)) {
                            String internalName = name.substring(0, name.length() - 6);

                            var klass = new ClassReader(stream.readAllBytes());
                            var node = new ClassNode(ASM9);

                            klass.accept(node, 0);

                            String jname1 = internalName.replace("/", ".");
                            String jname2 = Arrays.stream(internalName.split("/")).toList().getLast();

                            var actuallyMixin = (
                                    mixinList.contains(jname1) ||
                                    mixinList.contains(jname2)
                            );

                            if (ignoreList.contains(jname1) || ignoreList.contains(jname2));
                            else if (actuallyMixin)
                                addInjection(node, internalName, config);
                            else if (registerInner(node, entry));
                            else
                                throw new RuntimeException("Non-mixin class in mixin package");

                            break contentSet;
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to read mixin '%s'".formatted(name), e);
                        }
                    }
                }

                try (var stream = file.getInputStream(entry)) {
                    content = stream.readAllBytes();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read resource in a mixin container '%s'".formatted(name), e);
                }

                try {
                    output.putNextEntry(entry);
                    output.write(content);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to write resource in a mixin container '%s'".formatted(name), e);
                }
            }
        } while (zipEntries.hasMoreElements());

        for (var innerClass : innerClasses.entrySet()) {
            var mixinClassName = innerClass.getKey();
            var mixin = mixinNotes.get(mixinClassName);

            if (mixin == null)
                continue;

            var runtimeClassName = mixin.targets().size() == 1
                    ? mixin.targets().getFirst()
                    : "Ljava/lang/Object;";

            var entries = innerClasses.get(mixinClassName);
            for (var entry : entries) {
                if (!runtimeClassName.equals("Ljava/lang/Object;"))
                    entry.code.outerClass = runtimeClassName;

                transformDependant(mixinClassName, entry.code(), config, runtimeClassName);

                var node = entry.code;
                var zipEntry = entry.zipEntry;

                var writer = new ClassWriter(0);
                node.accept(writer);

                byte[] content = writer.toByteArray();

                try {
                    output.putNextEntry(zipEntry);
                    output.write(content);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to write mixin subclass in a mixin container '%s'".formatted(entry.zipEntry.getName()), e);
                }
            }
        }

        for (var ext : extensions)
            ext.onPostLoad(config);
    }

    /** TODO Optimize this crap */
    private record InnerClass(ZipEntry zipEntry, ClassNode code) {}
    // Omg I have to refactor all dis
    private final HashMap<String, ArrayList<InnerClass>> innerClasses
            = new HashMap<>();
    private final HashMap<String, Mixin> mixinNotes
            = new HashMap<>();

    private void addInjection(ClassNode node, String className, MixinConfig config) {
        Mixin mixin = null;

        if (node.invisibleAnnotations != null)
            for (var a : node.invisibleAnnotations)
                if (a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))
                    mixin = ann_Mixin(a);

        if (mixin == null)
            throw new RuntimeException("Mixin class (listed in mixins.json file) not annotated with @Mixin");

        mixins.add(className);

        for (var ext : extensions)
            ext.onMixinPreInit(className, node, config);

        var state = new MixinState(className, node, config, extensions);
        state.init(mixin);

        for (var ext : extensions)
            ext.onMixinPostInit(className, node, config);

        state.register(inst);

        node.innerClasses.clear();

        mixinNotes.put(className, mixin);
    }

    private boolean registerInner(ClassNode node, ZipEntry entryForInnerClass) {
        if (node.outerClass != null) {
            innerClasses.computeIfAbsent(node.outerClass, _ -> new ArrayList<>()).add(
                    new InnerClass(entryForInnerClass, node)
            );

            node.outerClass = null;

            return true;
        }

        return false;
    }

    private void transformDependant(String mixinClassName, ClassNode mixinNode, MixinConfig config, String runtimeClassName) {
        for (var ext : extensions)
            ext.onSubclassPreTransform(mixinClassName, mixinNode, config, runtimeClassName);

        var transformer = new Transformer(config, null, null, mixinClassName, runtimeClassName);

        var shadowedFields = new ArrayList<String>();
        var shadowedMethods = new ArrayList<String>();

        mixinNode.access = publicize(mixinNode.access);

        for (var f : mixinNode.fields) {
            f.access = publicize(f.access);
            f.desc = transformer.transformType(f.desc);

            if (f.invisibleAnnotations != null && f.invisibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;")))
                shadowedFields.add(f.name);
        }

        for (var m : mixinNode.methods) {
            m.access = publicize(m.access);
            m.desc = transformer.transformType(m.desc);

            if (m.invisibleAnnotations != null && m.invisibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;")))
                shadowedMethods.add(m.name + m.desc);
        }

        for (var m : mixinNode.methods) {
            transformer.transformInstructions(m, shadowedFields, config.tedge().prefix(), shadowedMethods);
        }

        for (var ext : extensions)
            ext.onSubclassPostTransform(mixinClassName, mixinNode, config, runtimeClassName);
    }

    private static int publicize(int access) {
        if ((access & ACC_PRIVATE) == ACC_PRIVATE)
            access -= ACC_PRIVATE;
        else if ((access & ACC_PROTECTED) == ACC_PROTECTED)
            access -= ACC_PROTECTED;
        else if ((access & ACC_PUBLIC) == ACC_PUBLIC)
            return access;

        access |= ACC_PUBLIC;

        return access;
    }

    private static Mixin ann_Mixin(AnnotationNode a) {
        var targets = new ArrayList<String>();
        var visitor = new AnnotationVisitor(ASM9) {
            String plugin = null;

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
                if (name.equals("plugin")) {
                    if (!(value instanceof String str))
                        throw new RuntimeException("Invalid @Mixin(plugin = %s)".formatted(value));

                    this.plugin = str;
                } else {
                    throw new RuntimeException("Unimplemented support for @Mixin(%s = %s)".formatted(name, value));
                }
            }
        };

        a.accept(visitor);

        return new Mixin(targets, 0, false, visitor.plugin);
    }
}
