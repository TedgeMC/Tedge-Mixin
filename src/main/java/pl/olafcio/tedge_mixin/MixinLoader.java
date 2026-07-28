package pl.olafcio.tedge_mixin;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;
import pl.olafcio.tedge_mixin.config.MixinConfig;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

import static org.objectweb.asm.Opcodes.ASM9;

/**
 * This class is used to register mixins.
 */
@SuppressWarnings("ClassCanBeRecord")
public class MixinLoader {
    protected final Instrumentation inst;

    public MixinLoader(Instrumentation inst) {
        this.inst = inst;
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
                        addInjection(name.substring(0, name.length() - 6).replace(".", "/"), config, stream.readAllBytes());
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
