package com.liskovsoft.sharedutils.helpers;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small JVM subset of SmartTube's shared Helpers. */
public final class Helpers {
    public static final String NON_BREAKING_SPACE = "\u00A0";

    private static final String ARRAY_DELIM = "%AR%";
    private static final String DATA_DELIM = "%OB%";
    private static final String PAIR_DELIM = "%PR%";

    private Helpers() {
    }

    public interface Filter<T> {
        boolean test(T item);
    }

    public interface Parser<T> {
        T parse(String value);
    }

    public interface Transform<T> {
        String transform(T value);
    }

    public static boolean equals(Object first, Object second) {
        return Objects.equals(first, second);
    }

    public static boolean equalsAny(Object orig, Object... arr) {
        if (arr == null) return false;
        for (Object item : arr) if (Objects.equals(orig, item)) return true;
        return false;
    }

    public static boolean check(int origin, int... values) {
        if (values == null) return false;
        for (int value : values) if ((origin & value) != value) return false;
        return true;
    }

    public static <T> boolean containsIf(Collection<T> collection, Filter<T> filter) {
        if (collection == null || filter == null) return false;
        for (T item : collection) if (filter.test(item)) return true;
        return false;
    }

    public static boolean containsIgnoreCase(Collection<String> collection, String value) {
        if (collection == null || value == null) return false;
        for (String item : collection) if (value.equalsIgnoreCase(item)) return true;
        return false;
    }

    public static <T> T findFirst(Collection<T> collection, Filter<T> filter) {
        if (collection == null || filter == null) return null;
        for (T item : collection) if (filter.test(item)) return item;
        return null;
    }

    public static <T> T findLast(Collection<T> collection, Filter<T> filter) {
        if (collection == null || filter == null) return null;
        T result = null;
        for (T item : collection) if (filter.test(item)) result = item;
        return result;
    }

    public static <T> List<T> removeIf(Collection<T> collection, Filter<T> filter) {
        List<T> result = new ArrayList<>();
        if (collection != null) for (T item : collection) if (filter == null || !filter.test(item)) result.add(item);
        return result;
    }

    public static <T> T firstNonNull(T... objects) {
        if (objects == null) return null;
        for (T object : objects) if (object != null) return object;
        return null;
    }

    public static <T> boolean allNulls(T... items) {
        if (items == null) return true;
        for (T item : items) if (item != null) return false;
        return true;
    }

    public static <T> boolean anyNull(T... items) {
        if (items == null) return true;
        for (T item : items) if (item == null) return true;
        return false;
    }

    public static <T> T[] appendArray(T[] first, T[] second) {
        if (first == null) return second;
        if (second == null) return first;
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public static <T> T getNextValue(T[] values, T current) {
        if (values == null || values.length == 0) return null;
        int index = Arrays.asList(values).indexOf(current);
        return values[(index + 1) % values.length];
    }

    public static Random getRandom() {
        return new Random();
    }

    public static int getRandomNumber(int min, int max) {
        if (max <= min) return min;
        return min + new Random().nextInt(max - min + 1);
    }

    public static String getUserDeviceName(Context context) {
        return "Nuvio One Desktop";
    }

    public static boolean isJUnitTest() {
        return false;
    }

    public static int hashCode(Object... items) {
        if (items == null || items.length == 0) return -1;
        int hash = 0;
        for (Object item : items) if (item != null) hash = 31 * hash + item.hashCode();
        return hash;
    }

    public static int hashCodeAny(Object... items) {
        if (items == null || items.length == 0) return -1;
        int hash = -1;
        for (Object item : items) {
            if (item != null && item.hashCode() != -1) {
                hash = 31 * hash + item.hashCode();
                break;
            }
        }
        return hash;
    }

    public static boolean hasWords(String s) {
        return s != null && s.matches(".*\\p{L}+.*");
    }

    public static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isDash(String id) {
        return id != null && id.startsWith("-");
    }

    public static boolean isNullOrEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    public static String join(CharSequence delim, CharSequence... elements) {
        if (elements == null) return null;
        StringBuilder builder = new StringBuilder();
        for (CharSequence element : elements) {
            if (element == null) continue;
            if (builder.length() > 0) builder.append(delim);
            builder.append(element);
        }
        return builder.toString();
    }

    public static <T> String join(CharSequence delim, List<T> elements, Transform<T> transform) {
        if (elements == null) return null;
        StringBuilder builder = new StringBuilder();
        for (T element : elements) {
            if (element == null) continue;
            if (builder.length() > 0) builder.append(delim);
            builder.append(transform == null ? element.toString() : transform.transform(element));
        }
        return builder.toString();
    }

    public static String merge(String delim, Object... params) {
        if (params == null) return null;
        if (params.length == 0) return " ";
        StringBuilder builder = new StringBuilder();
        for (Object param : params) {
            if (builder.length() > 0) builder.append(delim);
            if (param instanceof Collection) {
                param = mergeList((Collection<?>) param);
            }
            builder.append(param == null ? "" : param);
        }
        return builder.toString();
    }

    public static <T> String mergeList(Collection<T> list) {
        return list == null ? null : merge(ARRAY_DELIM, list.toArray());
    }

    public static <T> String mergeList(String delim, Collection<T> list) {
        return list == null ? null : merge(delim, list.toArray());
    }

    public static String mergeArray(Object... items) {
        return merge(ARRAY_DELIM, items);
    }

    public static String mergeData(Object... items) {
        return merge(DATA_DELIM, items);
    }

    public static String[] split(String data, String delim) {
        if (data == null) return null;
        if (data.trim().isEmpty()) return new String[]{};
        return data.split(Pattern.quote(delim));
    }

    public static String[] splitArray(String data) {
        return split(data, ARRAY_DELIM);
    }

    public static String[] splitData(String data) {
        return split(data, DATA_DELIM);
    }

    public static boolean startsWith(String word, String prefix) {
        return word != null && prefix != null && word.startsWith(prefix);
    }

    public static boolean startsWithAny(String word, String... prefixes) {
        if (word == null || prefixes == null) return false;
        for (String prefix : prefixes) {
            if (startsWith(word, prefix)) return true;
        }
        return false;
    }

    public static boolean matchAll(String input, Pattern... patterns) {
        if (input == null || patterns == null) return false;
        for (Pattern pattern : patterns) {
            if (!pattern.matcher(input).find()) return false;
        }
        return true;
    }

    public static boolean matchAll(String input, String... regex) {
        if (input == null || regex == null) return false;
        for (String reg : regex) {
            if (!Pattern.compile(reg).matcher(input).find()) return false;
        }
        return true;
    }

    public static boolean isValidUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        return Pattern.compile("^[a-z.]+://.+$").matcher(url).matches();
    }

    public static String combineItems(String divider, Object... items) {
        StringBuilder result = new StringBuilder();
        if (items != null) {
            for (Object item : items) {
                if (item == null) continue;
                String strItem = item.toString();
                if (strItem.isEmpty()) continue;
                if (divider == null || result.length() == 0) {
                    result.append(strItem);
                } else {
                    result.append(divider).append(strItem);
                }
            }
        }
        return result.length() != 0 ? result.toString() : null;
    }

    public static String replace(String content, Pattern oldVal, String newVal) {
        return content == null || oldVal == null ? content : oldVal.matcher(content).replaceAll(newVal);
    }

    public static String runMultiMatcher(String input, String... patterns) {
        if (input == null || patterns == null) return null;
        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern).matcher(input);
            if (matcher.find()) return matcher.group();
        }
        return null;
    }

    public static String runMultiMatcher(String input, Pattern... patterns) {
        if (input == null || patterns == null) return null;
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(input);
            if (matcher.find()) return matcher.group();
        }
        return null;
    }

    public static String parseStr(String str) {
        if (str == null || "null".equals(str)) return null;
        return str;
    }

    public static String parseStr(String[] arr, int index) {
        return parseStr(arr, index, null);
    }

    public static String parseStr(String[] arr, int index, String defaultValue) {
        if (arr == null || index < 0 || index >= arr.length) return defaultValue;
        String value = parseStr(arr[index]);
        return value == null ? defaultValue : value;
    }

    public static int parseInt(String numString) {
        return parseInt(numString, 0);
    }

    public static int parseInt(String numString, int defaultValue) {
        try {
            return numString == null ? defaultValue : Integer.parseInt(numString.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static int parseInt(String[] arr, int index) {
        return parseInt(arr, index, 0);
    }

    public static int parseInt(String[] arr, int index, int defaultValue) {
        return parseInt(parseStr(arr, index), defaultValue);
    }

    public static long parseLong(String numString) {
        return parseLong(numString, 0L);
    }

    public static long parseLong(String numString, long defaultValue) {
        try {
            return numString == null ? defaultValue : Long.parseLong(numString.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static long parseLong(String[] arr, int index) {
        return parseLong(arr, index, 0L);
    }

    public static long parseLong(String[] arr, int index, long defaultValue) {
        return parseLong(parseStr(arr, index), defaultValue);
    }

    public static float parseFloat(String numString) {
        return parseFloat(numString, 0f);
    }

    public static float parseFloat(String numString, float defaultValue) {
        try {
            return numString == null ? defaultValue : Float.parseFloat(numString.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static float parseFloat(String[] arr, int index) {
        return parseFloat(arr, index, 0f);
    }

    public static float parseFloat(String[] arr, int index, float defaultValue) {
        return parseFloat(parseStr(arr, index), defaultValue);
    }

    public static boolean parseBoolean(String value) {
        return Boolean.parseBoolean(value);
    }

    public static boolean parseBoolean(String[] arr, int index) {
        return parseBoolean(arr, index, false);
    }

    public static boolean parseBoolean(String[] arr, int index, boolean defaultValue) {
        String value = parseStr(arr, index);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    public static <T> T parseItem(String[] arr, int index, Parser<T> parser) {
        return parseItem(arr, index, parser, null);
    }

    public static <T> T parseItem(String[] arr, int index, Parser<T> parser, T defaultValue) {
        String value = parseStr(arr, index);
        if (value == null || parser == null) return defaultValue;
        try {
            T parsed = parser.parse(value);
            return parsed == null ? defaultValue : parsed;
        } catch (RuntimeException e) {
            return defaultValue;
        }
    }

    public static java.io.InputStream toStream(String content) {
        if (content == null) return null;
        return new java.io.ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    public static List<String> parseStrList(String[] arr, int index) {
        return parseList(arr, index, Helpers::parseStr);
    }

    public static <T> List<T> parseList(String[] arr, int index, String delim, Parser<T> itemParser) {
        return parseList(delim, parseStr(arr, index), itemParser);
    }

    public static <T> List<T> parseList(String delim, String data, Parser<T> itemParser) {
        List<T> result = new ArrayList<>();
        if (data != null) {
            String[] listArr = split(data, delim);
            if (listArr != null) {
                for (String item : listArr) {
                    T parsed = itemParser.parse(item);
                    if (parsed != null) result.add(parsed);
                }
            }
        }
        return result;
    }

    public static <T> List<T> parseList(String[] arr, int index, Parser<T> itemParser) {
        return parseList(ARRAY_DELIM, parseStr(arr, index), itemParser);
    }

    public static <T> List<T> createSafeLRUList(final int maxEntries) {
        return new java.util.concurrent.CopyOnWriteArrayList<T>() {
            @Override
            public boolean add(T element) {
                if (contains(element)) remove(element);
                if (size() > maxEntries) remove(0);
                return super.add(element);
            }
        };
    }

    public static String toString(Object obj) {
        return obj == null ? null : obj.toString();
    }

    public static String toString(Throwable ex) {
        return ex == null ? null : ex.getMessage();
    }

    public static String toString(InputStream content) {
        if (content == null) return null;
        try (InputStream input = content; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return null;
        }
    }
}
