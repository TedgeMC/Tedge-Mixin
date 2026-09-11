package pl.olafcio.tedge_mixin.extension;

import org.objectweb.asm.tree.ClassNode;
import pl.olafcio.tedge_mixin.config.MixinConfig;

public interface Extension {
    default void onPreLoad(MixinConfig config) {}
    default void onPostLoad(MixinConfig config) {}

    default void onMixinPreInit(String className, ClassNode node, MixinConfig config) {}
    default void onMixinPostInit(String className, ClassNode node, MixinConfig config) {}

    default void onSubclassPreTransform(String className, ClassNode node, MixinConfig config, String runtimeClassName) {}
    default void onSubclassPostTransform(String className, ClassNode node, MixinConfig config, String runtimeClassName) {}

    default boolean shouldApply(MixinConfig config, ClassNode mixinNode, ClassNode runtimeNode, String mixinClassName, String runtimeClassName) {
        return true;
    }

    default void onBeforeTargetApply(MixinConfig config, ClassNode mixinNode, ClassNode runtimeNode, String mixinClassName, String runtimeClassName) {}
    default void onAfterTargetApply(MixinConfig config, ClassNode mixinNode, ClassNode runtimeNode, String mixinClassName, String runtimeClassName) {}
}
