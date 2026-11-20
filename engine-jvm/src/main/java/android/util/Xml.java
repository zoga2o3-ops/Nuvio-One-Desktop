package android.util;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;
import org.kxml2.io.KXmlParser;
import org.kxml2.io.KXmlSerializer;

/** JVM stand-in for android.util.Xml backed by kxml2. */
public final class Xml {
    private Xml() {
    }

    public static XmlPullParser newPullParser() {
        return new KXmlParser();
    }

    public static XmlSerializer newSerializer() {
        return new KXmlSerializer();
    }
}
