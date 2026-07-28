package pl.olafcio.tedge_mixin.config;

public record MixinConfig(boolean required, String refmap, String _package, String[] mixins, String[] client, String minVersion, InjectorsConfig injectors) {
}
