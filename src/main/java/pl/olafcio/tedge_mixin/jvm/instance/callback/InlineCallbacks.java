package pl.olafcio.tedge_mixin.jvm.instance.callback;

import org.objectweb.asm.tree.*;
import pl.olafcio.tedge_mixin.MixinIssue;
import pl.olafcio.tedge_mixin.jvm.TypeReader;
import pl.olafcio.tedge_mixin.jvm.instance.Callbacks;
import pl.olafcio.tedge_mixin.jvm.types.Callable;
import pl.olafcio.tedge_mixin.jvm.types.Literal;

import static org.objectweb.asm.Opcodes.*;

public final class InlineCallbacks extends Callbacks {
    public InlineCallbacks(String runtimeClassName) {
        super(runtimeClassName);
    }

    @Override
    public InsnList voidCI(MethodNode method, final int varIndex, final boolean cancellable, MethodNode targetMethod) {
        targetMethod.maxLocals += method.maxLocals - 1;
        targetMethod.maxStack += method.maxStack;

        return new InsnList() {{
            var returning = new LabelNode();
            int load = -1;

            for (var ins : method.instructions) {
                if (ins.getOpcode() == RETURN) {
                    add(new FrameNode(F_SAME, 1, null, 0, null));
                    add(new JumpInsnNode(GOTO, returning));
                    add(new FrameNode(F_CHOP, 0, new Object[]{}, 0, null));

                    continue;
                } else if (ins.getType() == AbstractInsnNode.VAR_INSN) {
                    var cast = (VarInsnNode) ins;
                    if (ins.getOpcode() == ALOAD) {
                        load = cast.var;
                    }

                    if (cast.var >= new TypeReader(method.desc).get().size())
                        cast.var += method.maxLocals - 1;
                } else if (ins.getOpcode() == INVOKEVIRTUAL) {
                    if (
                            load < method.parameters.size() &&

                            new TypeReader(method.desc).get().getFirst() instanceof Callable(var args)         &&
                                                         args.get (load) instanceof Literal (var internalName) &&

                            (internalName.equals("org/spongepowered/asm/mixin/injection/callback/CallbackInfo") ||
                             internalName.equals("org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable"))
                    ) {
                        remove(getLast());

                        if (method.name.equals("cancel"))
                            add(new InsnNode(RETURN));
                        else if (method.name.equals("setReturnValue"))
                            add(new InsnNode(ARETURN));
                        else if (method.name.equals("setReturnValueB"))
                            add(new InsnNode(IRETURN));
                        else if (method.name.equals("setReturnValueC"))
                            add(new InsnNode(IRETURN));
                        else if (method.name.equals("setReturnValueD"))
                            add(new InsnNode(DRETURN));
                        else if (method.name.equals("setReturnValueF"))
                            add(new InsnNode(FRETURN));
                        else if (method.name.equals("setReturnValueI"))
                            add(new InsnNode(IRETURN));
                        else if (method.name.equals("setReturnValueJ"))
                            add(new InsnNode(LRETURN));
                        else if (method.name.equals("setReturnValueS"))
                            add(new InsnNode(IRETURN));
                        else if (method.name.equals("setReturnValueZ"))
                            add(new InsnNode(IRETURN));
                        else if (method.name.equals("isCancelled"))
                            add(new LdcInsnNode(0));  // 0=false
                        else if (method.name.equals("isCancellable"))
                            add(new LdcInsnNode(cancellable ? 1 : 0));
                        else
                            throw new MixinIssue(("@InjectInline cannot have 'CallbackInfo#%s' calls\n" +
                                                  "(only .cancel(), .isCancelled() and .isCancellable() is permitted)").formatted(method.name));
                    }
                }

                add(ins);
            }

            add(returning);
        }};
    }
}
