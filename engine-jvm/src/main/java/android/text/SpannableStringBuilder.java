package android.text;

/** JVM stand-in for android.text.SpannableStringBuilder (text only). */
public class SpannableStringBuilder implements CharSequence {
    private final StringBuilder mBuilder = new StringBuilder();

    public SpannableStringBuilder() {
    }

    public SpannableStringBuilder(CharSequence value) {
        if (value != null) mBuilder.append(value);
    }

    public SpannableStringBuilder append(CharSequence value) {
        mBuilder.append(value);
        return this;
    }

    public SpannableStringBuilder append(CharSequence value, int start, int end) {
        mBuilder.append(value, start, end);
        return this;
    }

    public SpannableStringBuilder append(char value) {
        mBuilder.append(value);
        return this;
    }

    public SpannableStringBuilder append(String value) {
        mBuilder.append(value);
        return this;
    }

    public SpannableStringBuilder append(Object value) {
        mBuilder.append(value);
        return this;
    }

    public int length() {
        return mBuilder.length();
    }

    @Override
    public char charAt(int index) {
        return mBuilder.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return mBuilder.subSequence(start, end);
    }

    @Override
    public String toString() {
        return mBuilder.toString();
    }
}
