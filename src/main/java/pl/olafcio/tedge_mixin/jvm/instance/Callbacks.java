package pl.olafcio.tedge_mixin.jvm.instance;

import org.objectweb.asm.tree.*;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.F_APPEND;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.RETURN;

public record Callbacks(String runtimeClassName) {
    public InsnList voidCI(MethodNode method, final int varIndex) {
        return new InsnList() {{
            // creating the CallbackInfo object
            add(new TypeInsnNode(NEW, "org/spongepowered/asm/mixin/injection/callback/CallbackInfo"));
            add(new InsnNode(DUP));
            add(new MethodInsnNode(INVOKESPECIAL, "org/spongepowered/asm/mixin/injection/callback/CallbackInfo", "<init>", "()V"));
            add(new VarInsnNode(ASTORE, varIndex));
            // calling the mixin method
            add(new VarInsnNode(ALOAD, 0));
            add(new VarInsnNode(ALOAD, varIndex));
            add(new MethodInsnNode(INVOKEVIRTUAL, runtimeClassName, method.name, method.desc));
            // returning if cancelled
            add(new VarInsnNode(ALOAD, varIndex));
            add(new MethodInsnNode(INVOKEVIRTUAL, "org/spongepowered/asm/mixin/injection/callback/CallbackInfo", "isCancelled", "()Z", false));

            var label = new LabelNode();

            add(new JumpInsnNode(IFEQ, label));
            add(new InsnNode(RETURN));

            add(label);
            add(new FrameNode(F_APPEND, varIndex, new Object[]{"org/spongepowered/asm/mixin/injection/callback/CallbackInfo"}, 0, new Object[]{}));
        }};
    }
}
