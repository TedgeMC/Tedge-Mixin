package pl.olafcio.tedge_mixin.jvm.types;

import java.util.List;

public record Callable(List<Type> arguments) implements Type {
}
