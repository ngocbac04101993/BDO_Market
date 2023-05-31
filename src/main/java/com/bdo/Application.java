package com.bdo;

import com.bdo.constant.Constant;
import com.bdo.model.*;
import com.bdo.schedule.ScheduleTask;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;

import static com.bdo.service.ConfigService.*;

public class Application {


    public static BiddingList biddingList;

    public static String report;

    public static ObjectMapper objectMapper = new ObjectMapper();

    public static boolean schedule;

    public static void main(String[] args) {
        readConfig();
        if (schedule = props.getProperty("schedule").equals("true")) {
            Timer t = new Timer();
            ScheduleTask task = new ScheduleTask(t);
            t.scheduleAtFixedRate(task, 0, 20000);
        } else {
            execute();
        }
    }

    public static boolean execute() {
        if (readData()) {
            System.out.println("Load Data successfully !");
        } else {
            System.out.println("Can not load data !");
            return false;
        }
        if (getPreOrderList()) {
            sellReport();
            buyReport();
            return true;
        }
        return false;
    }


    public static boolean getPreOrderList() {
        try {
            String mainURL = props.getProperty("urlInfo") + props.getProperty("preOrderList");
            URL url = new URL(mainURL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Length", "0");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Cookie", cookie);
            OutputStream os = conn.getOutputStream();
            OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
            osw.write("{}");
            osw.flush();
            osw.close();
            os.close();
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String jsonData = "";
            String output;
            while ((output = br.readLine()) != null)
                jsonData += output;
            conn.disconnect();
            biddingList = objectMapper.readValue(jsonData, BiddingList.class);
            if (biddingList.getResultCode() != 0) {
                System.out.println("Get Pre Order list error: " + jsonData);
                return false;
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void sellReport() {
        report = "--Sell Report--";
        try {
            for (AutoItem item : sellList) {
                if (!"y".equals(item.getPreOrder()))
                    continue;
                SellItem sell = biddingList.getSellList().stream()
                        .filter(sellItem -> (Long.valueOf(item.getIndex()).longValue() == sellItem.getMainKey())).findAny()
                        .orElse(null);
                if (sell == null || sell.getLeftCount() == 0L) {
                    report += String.join(" ", "\n", "0/" + item.getCount(), item.getName(), "!");
                    continue;
                }
                if (sell.getLeftCount() < (Integer.parseInt(item.getCount()) / 2)) {
                    report += String.join(" ", "\n", String.valueOf(sell.getLeftCount()) + "/" + item.getCount(), item.getName(), "!");
                    report += String.join(" ", " Refill " + (Long.parseLong(item.getCount()) - sell.getLeftCount()),
                            item.getName(), "!");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void buyReport() {
        report += "\n\n\n\n--Buy Report--";
        try {
            for (AutoItem item : buyList) {
                BuyItem buy = biddingList.getBuyList().stream()
                        .filter(buyItem -> (Long.valueOf(item.getIndex()).longValue() == buyItem.getMainKey())).findAny()
                        .orElse(null);
                if (buy == null) {
                    boolean result;
                    if ("n".equalsIgnoreCase(item.getPreOrder())) continue;
                    String price = "y".equals(item.getPreOrder()) ?
                            String.valueOf(getMaxPrice(item.getIndex())) :
                            item.getPreOrder();
                    do {
                        result = preOrder(item.getIndex(), price, item.getCount());
                        if (result) {
                            report = report + String.join(" ", "\nOrdered", item.getCount(), item.getName(), "!");
                        } else {
                            report = report + String.join(" ", "\nOrdered", item.getName(), "fail !");
                        }
                        Thread.sleep(300L);
                    } while (result);
                    continue;
                }
                if (buy.getBoughtCount() > 0L &&
                        receiveItem(item.getIndex(), String.valueOf(buy.getBuyNo())))
                    report = report + String.join(" ", "\nReceived", String.valueOf(buy.getBoughtCount()),
                            item.getName(), ".", buy.getLeftCount() + "/" + item.getCount(),
                            "left !");
                if (buy.getLeftCount() < Long.parseLong(item.getCount()) && buy.getLeftCount() > 0L && withdraw(
                        item.getIndex(), String.valueOf(buy.getBuyNo()), String.valueOf(buy.getLeftCount()))) {
                    report = report + String.join(" ", "\nRestock", item.getName());
                    buy.setLeftCount(0L);
                }
                if (buy.getLeftCount() == 0L) {
                    boolean result;
                    if ("n".equalsIgnoreCase(item.getPreOrder())) continue;
                    String price = "y".equals(item.getPreOrder()) ?
                            String.valueOf(getMaxPrice(item.getIndex())) :
                            item.getPreOrder();
                    do {
                        result = preOrder(item.getIndex(), price, item.getCount());
                        if (result) {
                            report = report + String.join(" ", "\nOrdered", item.getCount(), item.getName(), "!");
                        } else {
                            report = report + String.join(" ", "\nOrdered", item.getName(), "fail !");
                        }
                        Thread.sleep(300L);
                    } while (result);
                }
            }
            BufferedWriter writer = new BufferedWriter(new FileWriter(Constant.REPORT));
            writer.write(report);
            writer.close();
            if(!schedule) Desktop.getDesktop().open(new File(Constant.REPORT));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean preOrder(String buyMainKey, String buyPrice, String buyCount) {
        try {
            String mainURL = props.getProperty("urlAction") + props.getProperty("preOrder");
            URL url = new URL(mainURL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Cookie", cookie);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("buyMainKey", buyMainKey);
            params.put("buyPrice", buyPrice);
            params.put("buyCount", buyCount);
            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String, Object> param : params.entrySet()) {
                if (postData.length() != 0)
                    postData.append('&');
                postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                postData.append('=');
                postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
            }
            byte[] postDataBytes = postData.toString().getBytes("UTF-8");
            conn.getOutputStream().write(postDataBytes);
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String jsonData = "";
            String output;
            while ((output = br.readLine()) != null)
                jsonData += output;
            conn.disconnect();
            ObjectMapper objectMapper = new ObjectMapper();
            Response res = objectMapper.readValue(jsonData, Response.class);
            if (res.getResultCode() != 0)
                return false;
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static long getMaxPrice(String mainKey) {
        try {
            String mainURL = props.getProperty("urlInfo") + props.getProperty("getPrice");
            URL url = new URL(mainURL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Cookie", cookie);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mainKey", mainKey);
            params.put("keyType", Integer.valueOf(0));
            params.put("subKey", Integer.valueOf(0));
            params.put("isUp", Boolean.valueOf(true));
            params.put("__RequestVerificationToken", token);
            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String, Object> param : params.entrySet()) {
                if (postData.length() != 0)
                    postData.append('&');
                postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                postData.append('=');
                postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
            }
            byte[] postDataBytes = postData.toString().getBytes("UTF-8");
            conn.getOutputStream().write(postDataBytes);
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String jsonData = "";
            String output;
            while ((output = br.readLine()) != null)
                jsonData += output;
            conn.disconnect();
            ObjectMapper objectMapper = new ObjectMapper();
            ItemSellBuy res = objectMapper.readValue(jsonData, ItemSellBuy.class);
            if (res.getResultCode() != 0)
                return 0L;
            return res.getMarketConditionList().get(res.getMarketConditionList().size() - 1).getPricePerOne();
        } catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }

    public static boolean receiveItem(String mainKey, String buyNo) {
        try {
            String mainURL = props.getProperty("urlAction") + props.getProperty("receiveItem");
            URL url = new URL(mainURL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Cookie", cookie);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mainKey", mainKey);
            params.put("keyType", Integer.valueOf(0));
            params.put("subKey", Integer.valueOf(0));
            params.put("buyNo", buyNo);
            params.put("__RequestVerificationToken", token);
            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String, Object> param : params.entrySet()) {
                if (postData.length() != 0)
                    postData.append('&');
                postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                postData.append('=');
                postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
            }
            byte[] postDataBytes = postData.toString().getBytes("UTF-8");
            conn.getOutputStream().write(postDataBytes);
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String jsonData = "";
            String output;
            while ((output = br.readLine()) != null)
                jsonData += output;
            conn.disconnect();
            ObjectMapper objectMapper = new ObjectMapper();
            Response res = objectMapper.readValue(jsonData, Response.class);
            if (res.getResultCode() != 0)
                return false;
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean withdraw(String mainKey, String buyNo, String count) {
        try {
            String mainURL = props.getProperty("urlAction") + props.getProperty("withdraw");
            URL url = new URL(mainURL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Cookie", cookie);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mainKey", mainKey);
            params.put("buyNo", buyNo);
            params.put("count", count);
            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String, Object> param : params.entrySet()) {
                if (postData.length() != 0)
                    postData.append('&');
                postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                postData.append('=');
                postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
            }
            byte[] postDataBytes = postData.toString().getBytes("UTF-8");
            conn.getOutputStream().write(postDataBytes);
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String jsonData = "";
            String output;
            while ((output = br.readLine()) != null)
                jsonData += output;
            conn.disconnect();
            ObjectMapper objectMapper = new ObjectMapper();
            Response res = objectMapper.readValue(jsonData, Response.class);
            if (res.getResultCode() != 0)
                return false;
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}

