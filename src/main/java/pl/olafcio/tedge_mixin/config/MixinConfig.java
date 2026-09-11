package pl.olafcio.tedge_mixin.config;

public record MixinConfig(boolean required, String refmap, String _package, String[] mixins, String[] client, String[] server, String minVersion, InjectorsConfig injectors, TedgeConfig tedge, String plugin) {
    public MixinConfig(boolean required, String refmap, String _package, String[] mixins, String[] client, String[] server, String minVersion, InjectorsConfig injectors, TedgeConfig tedge) {
        this(required, refmap, _package, mixins, client, server, minVersion, injectors, tedge, null);
    }
}
