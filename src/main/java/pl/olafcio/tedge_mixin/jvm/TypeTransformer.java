package pl.olafcio.tedge_mixin.jvm;

import pl.olafcio.tedge_mixin.MixinTypeConversionError;

public abstract class TypeTransformer {
    private final char[] input;
    private int index;
    private String output;

    public TypeTransformer(String jvmType) {
        this.input = jvmType.toCharArray();
        this.index = 0;
        this.output = "";
    }

    protected abstract String transformLiteral(String literal);

    public String get() {
        apply();
        return output;
    }

    private boolean apply() {
        if (now('L')) {
            // Literal
            var value = new StringBuilder();
            while (!now(';'))
                value.append(consume());

            output += 'L';
            output += transformLiteral(value.toString());
            output += ';';
        } else if (now('[')) {
            // Array
            output += '[';
            mustApply();
        } else if (now('(')) {
            // Signature
            output += '(';

            while (true)
                if (!apply())
                    break;

            if (!now(')'))
                throw new MixinTypeConversionError("Expected ')'  (input: '%s')'".formatted(new String(input)));

            output += ')';

            apply();
        } else if (nowp('I') || nowp('F') || nowp('Z') || nowp('D') || nowp('V') || nowp('C') || nowp('B') || nowp('J') || nowp('S')) {
            // Primitive
            output += consume();
        } else {
            return false;
        }

        return true;
    }

    private void mustApply() {
        if (!apply())
            throw new MixinTypeConversionError("Invalid type '%s'".formatted(new String(input)));
    }

    private boolean now(char ch) {
        if (input[index] == ch) {
            index++;
            return true;
        }

        return false;
    }

    private boolean nowp(char ch) {
        return (input[index] == ch);
    }

    private char consume() {
        return input[index++];
    }
}
