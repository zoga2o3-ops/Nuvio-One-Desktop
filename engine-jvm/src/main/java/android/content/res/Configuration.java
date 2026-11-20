package android.content.res;

import android.os.LocaleList;

import java.util.Locale;

public class Configuration {
    public Locale locale = Locale.getDefault();

    public LocaleList getLocales() {
        return new LocaleList(Locale.getDefault());
    }
}
