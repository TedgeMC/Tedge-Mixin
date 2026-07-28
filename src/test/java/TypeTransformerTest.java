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
}
