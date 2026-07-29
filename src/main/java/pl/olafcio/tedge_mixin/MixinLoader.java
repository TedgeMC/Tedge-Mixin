package pl.olafcio.tedge_mixin;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;
import pl.olafcio.tedge_mixin.config.MixinConfig;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.ArrayList;
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

        attachGlobal(inst);
    }

    private void attachGlobal(Instrumentation inst) {
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
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

                        addInjection(internalName, config, stream.readAllBytes());

                        mixins.add(internalName);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read mixin '%s'".formatted(name), e);
                    }
                }
            }
        } while (entries.hasMoreElements());
    }

    private void addInjection(String className, MixinConfig config, byte[] data) {
        var klass = new ClassReader(data);
        var node = new ClassNode(ASM9);

        klass.accept(node, 0);

        var state = new MixinState(className, node, config);
        state.init();
        state.register(inst);
    }
}
