package com.bdo.utils;

public class StringUtils {
    public static boolean isNullOrEmpty(String inStr) {
        if (inStr != null) {
            if ("".equals(inStr.trim()))
                return true;
            return false;
        }
        return true;
    }
}
