package com.bdo.service;

import com.bdo.constant.Constant;
import com.bdo.model.AutoItem;
import com.bdo.utils.StringUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

public class ConfigService {
    public static Properties props = new Properties();
    public static String cookie;

    public static String token;
    public static List<AutoItem> sellList;

    public static List<AutoItem> buyList;

    public static boolean readConfig() {
        try {
            File file = new File(Constant.API_CONFIG);
            props.load(new FileInputStream(file));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean readData() {
        try {
            String data;
            File file = new File(Constant.CURL);
            data = FileUtils.readFileToString(file, "UTF-8");
            if (StringUtils.isNullOrEmpty(data))
                return false;
            getHeader(data);
            sellList = CSVService.readCSV(Constant.SELL_LIST);
            buyList = CSVService.readCSV(Constant.BUY_LIST);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void getHeader(String header) {
        int cookieIndex = header.indexOf("'cookie");
        int indexEndCookie = header.indexOf("'", cookieIndex + 1);
        cookie = header.substring(cookieIndex + 9, indexEndCookie);
        int tokenIndex = header.indexOf("'__RequestVerificationToken");
        int indexEndToken = header.indexOf("&", tokenIndex);
        token = header.substring(tokenIndex + 28, indexEndToken);
    }
}
