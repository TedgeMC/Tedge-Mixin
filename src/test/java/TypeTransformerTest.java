import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import pl.olafcio.tedge_mixin.jvm.TypeTransformer;

@Test
void main() {
    Assertions.assertEquals("Ljava/lang/Integer;", new TypeTransformer("Ljava/lang/String;") {
        @Override
        protected String transformLiteral(String literal) {
            return literal.equals("java/lang/String") ? "java/lang/Integer" : "INCORRECT!![%s]".formatted(literal);
        }
    }.get());

    Assertions.assertEquals("(Ljava/lang/Integer;D)V", new TypeTransformer("(Ljava/lang/String;D)V") {
        @Override
        protected String transformLiteral(String literal) {
            return literal.equals("java/lang/String") ? "java/lang/Integer" : "INCORRECT!![%s]".formatted(literal);
        }
    }.get());

    Assertions.assertEquals("(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/Minecraft;)V", new TypeTransformer("(Lcom/example/testmod/mixin/MinecraftMixin;Lnet/minecraft/client/Minecraft;)V") {
        @Override
        protected String transformLiteral(String literal) {
            return literal.equals("com/example/testmod/mixin/MinecraftMixin") ? "net/minecraft/client/Minecraft" : literal;
        }
    }.get());
}
