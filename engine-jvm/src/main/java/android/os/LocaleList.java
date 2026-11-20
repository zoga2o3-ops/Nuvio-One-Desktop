package android.os;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class LocaleList {
    private final List<Locale> locales;

    public LocaleList(Locale... locales) {
        this.locales = Arrays.asList(locales);
    }

    public boolean isEmpty() {
        return locales.isEmpty();
    }

    public Locale get(int index) {
        return locales.get(index);
    }
}
