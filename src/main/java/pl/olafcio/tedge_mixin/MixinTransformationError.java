package pl.olafcio.tedge_mixin;

public class MixinTransformationError extends RuntimeException {
    public MixinTransformationError(String message) {
        super(message);
    }
}
