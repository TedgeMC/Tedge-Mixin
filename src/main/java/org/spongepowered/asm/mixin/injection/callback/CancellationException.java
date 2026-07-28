package org.spongepowered.asm.mixin.injection.callback;

import java.io.Serial;

public class CancellationException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public CancellationException() {
    }

    public CancellationException(String message) {
        super(message);
    }

    public CancellationException(Throwable cause) {
        super(cause);
    }

    public CancellationException(String message, Throwable cause) {
        super(message, cause);
    }
}
