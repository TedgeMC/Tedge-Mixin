package pl.olafcio.tedge_mixin;

public class MixinTypeConversionError extends RuntimeException {
    public MixinTypeConversionError(String message) {
        super(message);
    }
}
