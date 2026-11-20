package com.liskovsoft.sharedutils.helpers;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class FileHelpers {
    private FileHelpers() {
    }

    public static File getFilesDir(Context context) {
        return context.getFilesDir();
    }

    public static File getBackupDir(Context context) {
        return new File(context.getFilesDir(), "backup");
    }

    public static File getExternalFilesDir(Context context) {
        return context.getFilesDir();
    }

    public static String getFileContents(File file) {
        if (file == null || !file.isFile()) return null;
        try (InputStream input = new FileInputStream(file)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            copy(input, output);
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            return null;
        }
    }

    public static void stringToFile(String data, File file) {
        if (file == null) return;
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (OutputStream output = new FileOutputStream(file)) {
            if (data != null) output.write(data.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    public static InputStream toStream(String data) {
        return new ByteArrayInputStream(data == null ? new byte[0] : data.getBytes(StandardCharsets.UTF_8));
    }

    public static void streamToFile(InputStream input, File file) {
        if (input == null || file == null) return;
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (InputStream source = input; OutputStream output = new FileOutputStream(file)) {
            copy(source, output);
        } catch (IOException ignored) {
        }
    }

    public static void deleteByPrefix(File dir, String prefix) {
        if (dir == null || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.getName().startsWith(prefix)) {
                file.delete();
            }
        }
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }
}
