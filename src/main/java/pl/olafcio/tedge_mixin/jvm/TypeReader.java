package pl.olafcio.tedge_mixin.jvm;

import pl.olafcio.tedge_mixin.MixinTypeConversionError;
import pl.olafcio.tedge_mixin.jvm.types.*;

import java.util.ArrayList;
import java.util.List;

public class TypeReader {
    private final char[] input;
    private int index;
    private List<Type> output;

    public TypeReader(String jvmType) {
        this.input = jvmType.toCharArray();
        this.index = 0;
        this.output = new ArrayList<>();
    }

    public List<Type> get() {
        apply();
        return output;
    }

    private boolean apply() {
        if (now('L')) {
            // Literal
            var value = new StringBuilder();
            while (!now(';'))
                value.append(consume());

            output.add(new Literal(value.toString()));
        } else if (now('[')) {
            // Array
            var origout = this.output;
            this.output = new ArrayList<>(1);

            mustApply();

            var newout = this.output;

            this.output = origout;
            this.output.add(new Array(newout.get(0)));
        } else if (now('(')) {
            // Signature
            var origout = this.output;
            this.output = new ArrayList<>();

            while (true)
                if (!apply())
                    break;

            if (!now(')'))
                throw new MixinTypeConversionError("Expected ')'  (input: '%s')'".formatted(new String(input)));

            var newout = this.output;

            output = origout;
            output.add(new Callable(newout));

            apply();
        } else if (nowp('I') || nowp('F') || nowp('Z') || nowp('D') || nowp('V') || nowp('C') || nowp('B') || nowp('J') || nowp('S')) {
            // Primitive
            output.add(new Primitive(consume()));
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
