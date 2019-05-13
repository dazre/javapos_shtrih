package com.shtrih.util;

public class SysUtils {

    private static String filePath = "";

    public static String getFilesPath() {
        return filePath;
    }

    public static void setFilePath(String value) {
        filePath = value;
    }

    public static void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    public static double round2(double value) {
        return Math.round(value * 100) / 100;
    }
}
