package pl.olafcio.tedge_mixin.extension.impl;

import org.objectweb.asm.tree.*;
import pl.olafcio.tedge_mixin.config.MixinConfig;
import pl.olafcio.tedge_mixin.extension.Extension;

import static org.objectweb.asm.Opcodes.*;

/**
 * Provides Groovy mixin compatibility.
 */
public final class GroovyExtension implements Extension {
    @Override
    public void onMixinPreInit(String className, ClassNode node, MixinConfig config) {
        if ((node.access & ACC_ABSTRACT) == ACC_ABSTRACT && node.interfaces.contains("groovy/lang/GroovyObject")) {
            node.interfaces.remove("groovy/lang/GroovyObject");

            for (var m : node.methods) {
                if (m.name.equals("getMetaClass") && m.desc.equals("()Lgroovy/lang/MetaClass;")) {
                    if ((m.access & ACC_ABSTRACT) == ACC_ABSTRACT) {
                        m.access -= ACC_ABSTRACT;

                        m.maxStack = 2;
                        m.maxLocals = 1;

                        m.instructions = new InsnList() {{
                            add(new VarInsnNode(ALOAD, 0));
                            add(new FieldInsnNode(GETFIELD, className, config.tedge().prefix() + "metaClass", "Lgroovy/lang/MetaClass"));
                            add(new InsnNode(DUP));

                            var label = new LabelNode();
                            add(new JumpInsnNode(IFNULL, label));
                            add(new InsnNode(ARETURN));
                            add(label);

                            add(new FrameNode(F_SAME1, 0, null, 1, new Object[]{ "Lgroovy/lang/MetaClass;" }));
                            add(new InsnNode(POP));
                            add(new VarInsnNode(ALOAD, 0));
                            add(new InsnNode(DUP));
                            add(new MethodInsnNode(INVOKEVIRTUAL, className, "$getStaticMetaClass", "()Lgroovy/lang/MetaClass"));
                            add(new FieldInsnNode(PUTFIELD, className, config.tedge().prefix() + "metaClass", "Lgroovy/lang/MetaClass;"));
                            add(new VarInsnNode(ALOAD, 0));
                            add(new FieldInsnNode(GETFIELD, className, config.tedge().prefix() + "metaClass", "Lgroovy/lang/MetaClass;"));
                            add(new InsnNode(ARETURN));
                        }};
                    }
                }
            }
        }
    }

    @Override
    public void onSubclassPostTransform(String className, ClassNode node, MixinConfig config, String runtimeClassName) {
        for (var m : node.methods) {
            var copy = new InsnList();

            boolean ok = false;

            for (var ins : m.instructions) {
                if (!ok) {
                    if (ins.getOpcode() == INVOKEDYNAMIC) {
                        var inv = (InvokeDynamicInsnNode) ins;
//                    IO.println("@@InvokeDynamic %s %s".formatted(inv.name, inv.desc));
                        if (inv.name.equals("cast")) {
                            if (inv.desc.equals("(Ljava/lang/Object;)L" + runtimeClassName + ";")) {
//                            m.maxStack += 3;
//                            m.maxLocals += 2;
//
//                            copy.add(new TypeInsnNode(NEW, "org/codehaus/groovy/runtime/wrappers/PojoWrapper"));
//                            copy.add(new InsnNode(DUP));
//                            copy.add(new VarInsnNode(ALOAD, 0));
//                            copy.add(new FieldInsnNode(GETFIELD, runtimeClassName, "this$0", "L" + runtimeClassName + ";"));
//                            copy.add(new MethodInsnNode(INVOKESPECIAL, "org/codehaus/groovy/runtime/wrappers/PojoWrapper", "<init>", "(L" + runtimeClassName + ";)V"));
//                            copy.add(new VarInsnNode(ASTORE, 2));
//                            copy.add(new VarInsnNode(ALOAD, 2));
////                            copy.add(new MethodInsnNode(INVOKEVIRTUAL, "org/codehaus/groovy/runtime/wrappers/PojoWrapper", "unwrap", "()Ljava/lang/Object;"));
////                            copy.add(new TypeInsnNode(CHECKCAST, runtimeClassName));
                                ((VarInsnNode) copy.getLast()).var -= 1;
                                continue;
                            }
                        }
                    } else if (ins.getOpcode() == INVOKEVIRTUAL) {
                        var inv = (MethodInsnNode) ins;
                        if (inv.owner.equals(runtimeClassName) && inv.name.equals("$getStaticMetaClass") && inv.desc.equals("()Lgroovy/lang/MetaClass;")) {
                            m.instructions.remove(copy.getLast());
                            continue;
                        }
                    } else if (ins.getOpcode() == PUTFIELD) {
                        var put = (FieldInsnNode) ins;
                        if (put.owner.equals(runtimeClassName) && put.name.equals("metaClass") && put.desc.equals("Lgroovy/lang/MetaClass;")) {
                            m.instructions.remove(copy.getLast());
                            m.instructions.remove(copy.getLast());
                            continue;
                        }
                    }
                }

                copy.add(ins);
            }

            m.instructions = copy;
        }
    }
}
