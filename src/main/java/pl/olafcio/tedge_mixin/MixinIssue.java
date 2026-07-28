package pl.olafcio.tedge_mixin;

public class MixinIssue extends RuntimeException {
    public MixinIssue(String message) {
        super(message);
    }
}
