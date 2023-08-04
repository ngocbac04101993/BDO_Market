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
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.bdo.service.ConfigService.*;

public class Application {


    public static MyBiddingList biddingList;

    public static String report = "";

    public static ObjectMapper objectMapper = new ObjectMapper();

    public static boolean schedule;

    public static void main(String[] args) throws IOException {
        readConfig();
        schedule = props.getProperty("schedule").equals("true");
        if (schedule) {
            Timer t = new Timer();
            ScheduleTask task = new ScheduleTask(t);
            t.scheduleAtFixedRate(task, 0, 20000);
        } else {
            execute(args[0]);
        }
    }

    public static boolean execute(String mode) throws IOException {
        if (readData()) {
            System.out.println("Load Data successfully !");
        } else {
            System.out.println("Can not load data !");
            return false;
        }
        if (getBiddingList()) {
            if (mode.equals("1")) sellReport();
            if (mode.equals("2")) buyReport();
            BufferedWriter writer = new BufferedWriter(new FileWriter(Constant.REPORT));
            writer.write(report);
            writer.close();
            if (!schedule) Desktop.getDesktop().open(new File(Constant.REPORT));
            return true;
        } else {
            Desktop.getDesktop().open(new File(Constant.CURL));
            return false;
        }
    }


    public static boolean getBiddingList() {
        try {
            String mainURL = props.getProperty("urlInfo") + props.getProperty("biddingList");
            URL url = new URL(mainURL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Length", "0");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Cookie", cookie);
            OutputStream os = conn.getOutputStream();
            OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
            osw.write("{}");
            osw.flush();
            osw.close();
            os.close();
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder jsonData = new StringBuilder();
            String output;
            while ((output = br.readLine()) != null) jsonData.append(output);
            conn.disconnect();
            biddingList = objectMapper.readValue(jsonData.toString(), MyBiddingList.class);
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
//                if (item.getMainKey() != 4063) continue;
                if (Constant.NO.equals(item.getMinMaxPrice())) continue;
                SellItem sell = biddingList.getSellList().stream().filter(sellItem -> (item.getMainKey() == sellItem.getMainKey())).findAny().orElse(null);
                boolean tooLow = false;
                long minToSell = 0, curMaxPrice = 0, curMinPrice = 0;
                ArrayList<SellBuyInfo> listPrices = null;
                if (item.isExtremeMode()) {
                    listPrices = getPrices(item.getMainKey());
                    curMaxPrice = getMaxPrice(listPrices);
                    curMinPrice = getMinPrice(listPrices);
                    if (Constant.YES.equals(item.getMinMaxPrice())) minToSell = curMinPrice;
                    else minToSell = Long.parseLong(item.getMinMaxPrice());
                    tooLow = curMaxPrice < minToSell;
                }
                if (!tooLow) {
                    if (sell == null || sell.getLeftCount() == 0L) {
                        report += String.join(" ", "\n", "0/" + item.getCount(), item.getName(), "!");
                    } else {
                        if (sell.getLeftCount() < (item.getCount() / 2)) {
                            report += String.join(" ", "\n", sell.getLeftCount() + "/" + item.getCount(), "! Refill " + (item.getCount() - sell.getLeftCount()), item.getName());
                        }
                    }
                    if (item.isExtremeMode()) {
                        boolean hasListing = false;
                        if (minToSell < curMinPrice) minToSell = curMinPrice;
                        for (SellBuyInfo price : listPrices) {
                            if (sell != null && price.getPricePerOne() == sell.getPricePerOne()) {
                                price.setSellCount(price.getSellCount() - sell.getLeftCount());
                            }
                            if (price.getPricePerOne() < minToSell && price.getSellCount() > item.getCount() / 3) {
                                hasListing = true;
                                break;
                            } else {
                                if (price.getPricePerOne() >= minToSell && price.getSellCount() > item.getCount() / 3) {
                                    hasListing = true;
                                    break;
                                } else {
                                    minToSell = price.getPricePerOne();
                                }
                            }
                        }
                        long desiredPrice = curMaxPrice;
                        if (hasListing) {
                            desiredPrice = minToSell;
                        }
                        if (sell == null || sell.getPricePerOne() != desiredPrice)
                            report += String.join(" ", "\n Minlist", item.getName(), "to", String.valueOf(desiredPrice));
                    }
                } else {
                    if (sell != null && sell.getPricePerOne() <= minToSell)
                        report += String.join(" ", "\n Too low. Remove", String.valueOf(sell.getLeftCount()), item.getName(), "!", curMaxPrice + "/" + minToSell);
                    if (sell == null)
                        report += String.join(" ", "\n Too low.", item.getName(), curMaxPrice + "/" + minToSell);
                }
                Thread.sleep(200L);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void buyReport() {
        report += "\n\n\n\n--Buy Report--";
        try {
            for (AutoItem item : buyList) {
                if (Constant.NO.equalsIgnoreCase(item.getMinMaxPrice())) continue;
                BuyItem buy = biddingList.getBuyList().stream().filter(buyItem -> (item.getMainKey() == buyItem.getMainKey())).findAny().orElse(null);
                boolean tooHigh;
                long maxToBuy, curMinPrice, curMaxPrice;
                if (buy == null || buy.getLeftCount() < item.getCount() || buy.getBoughtCount() > 0L || item.isExtremeMode()) {
                    ItemSellBuyInfo itemSellBuyInfo = getItemInfo(item.getMainKey());
                    ArrayList<SellBuyInfo> listPrices = getPrices(itemSellBuyInfo);
                    curMinPrice = getMinPrice(listPrices);
                    curMaxPrice = getMaxPrice(listPrices);
                    maxToBuy = Constant.YES.equalsIgnoreCase(item.getMinMaxPrice()) ? curMaxPrice : Long.parseLong(item.getMinMaxPrice());
                    tooHigh = curMinPrice > maxToBuy;
                    if (!tooHigh) {
                        long desiredPrice = curMinPrice;
                        Collections.reverse(listPrices);
                        boolean hasOrder = false;
                        for (SellBuyInfo price : listPrices) {
                            if (buy != null && price.getPricePerOne() == buy.getPricePerOne()) {
                                price.setBuyCount(price.getBuyCount() - buy.getLeftCount());
                            }
                            if (price.getPricePerOne() <= maxToBuy && price.getBuyCount() > 0) {
                                hasOrder = true;
                                break;
                            } else {
                                maxToBuy = price.getPricePerOne();
                            }
                        }
                        if (hasOrder) {
                            desiredPrice = maxToBuy;
                        }
                        if (buy == null) {
                            boolean result;
                            do {
                                result = preOrder(item.getMainKey(), desiredPrice, item.getCount(), 0);
                                if (result) {
                                    report = report + String.join(" ", "\n Ordered", String.valueOf(item.getCount()), item.getName(), "!");
                                } else {
                                    report = report + String.join(" ", "\n Ordered", item.getName(), "fail !");
                                }
                                Thread.sleep(200L);
                            } while (result);
                        } else {
                            if (buy.getLeftCount() < item.getCount() || buy.getBoughtCount() > 0L || buy.getPricePerOne() != desiredPrice) {
                                if (preOrder(item.getMainKey(), desiredPrice, item.getCount(), buy.getBuyNo()))
                                    report = report + String.join(" ", "\n Restock", item.getName());
                                boolean result;
                                do {
                                    result = preOrder(item.getMainKey(), desiredPrice, item.getCount(), 0);
                                    if (result) {
                                        report = report + String.join(" ", "\n Ordered", String.valueOf(item.getCount()), item.getName(), "!");
                                    } else {
                                        report = report + String.join(" ", "\n Ordered", item.getName(), "fail !");
                                    }
                                    Thread.sleep(200L);
                                } while (result);
                            }
                        }
                    } else {
                        if (buy != null && buy.getPricePerOne() >= curMinPrice)
                            report += String.join(" ", "\n Too high. Remove order", item.getName(), "!");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean preOrder(long buyMainKey, long buyPrice, long buyCount, long buyNo) {
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
            if (buyNo > 0) params.put("retryBiddingNo", buyNo);
            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String, Object> param : params.entrySet()) {
                if (postData.length() != 0) postData.append('&');
                postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                postData.append('=');
                postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
            }
            byte[] postDataBytes = postData.toString().getBytes(StandardCharsets.UTF_8);
            conn.getOutputStream().write(postDataBytes);
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder jsonData = new StringBuilder();
            String output;
            while ((output = br.readLine()) != null) jsonData.append(output);
            conn.disconnect();
            ObjectMapper objectMapper = new ObjectMapper();
            Response res = objectMapper.readValue(jsonData.toString(), Response.class);
            return res.getResultCode() == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static ArrayList<SellBuyInfo> getPrices(long mainKey) {
        return Objects.requireNonNull(getItemInfo(mainKey)).getMarketConditionList();
    }

    public static ArrayList<SellBuyInfo> getPrices(ItemSellBuyInfo itemSellBuyInfo) {
        return itemSellBuyInfo.getMarketConditionList();
    }

    public static ItemSellBuyInfo getItemInfo(long mainKey) {
        try {
            String mainURL = props.getProperty("urlInfo") + props.getProperty("getItemInfo");
            URL url = new URL(mainURL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Cookie", cookie);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mainKey", mainKey);
            params.put("keyType", 0);
            params.put("subKey", 0);
            params.put("isUp", Boolean.TRUE);
            params.put("__RequestVerificationToken", token);
            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String, Object> param : params.entrySet()) {
                if (postData.length() != 0) postData.append('&');
                postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                postData.append('=');
                postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
            }
            byte[] postDataBytes = postData.toString().getBytes(StandardCharsets.UTF_8);
            conn.getOutputStream().write(postDataBytes);
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder jsonData = new StringBuilder();
            String output;
            while ((output = br.readLine()) != null) jsonData.append(output);
            conn.disconnect();
            ObjectMapper objectMapper = new ObjectMapper();
            ItemSellBuyInfo res = objectMapper.readValue(jsonData.toString(), ItemSellBuyInfo.class);
            if (res.getResultCode() != 0) return null;
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static long getMinPrice(ArrayList<SellBuyInfo> listPrices) {
        if (listPrices.isEmpty()) return 0L;
        return listPrices.get(0).getPricePerOne();
    }

    public static long getMaxPrice(ArrayList<SellBuyInfo> listPrices) {
        if (listPrices.isEmpty()) return 0L;
        return listPrices.get(listPrices.size() - 1).getPricePerOne();
    }

    public static boolean receiveItem(long mainKey, String buyNo) {
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
            params.put("keyType", 0);
            params.put("subKey", 0);
            params.put("buyNo", buyNo);
            params.put("__RequestVerificationToken", token);
            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String, Object> param : params.entrySet()) {
                if (postData.length() != 0) postData.append('&');
                postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                postData.append('=');
                postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
            }
            byte[] postDataBytes = postData.toString().getBytes(StandardCharsets.UTF_8);
            conn.getOutputStream().write(postDataBytes);
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder jsonData = new StringBuilder();
            String output;
            while ((output = br.readLine()) != null) jsonData.append(output);
            conn.disconnect();
            ObjectMapper objectMapper = new ObjectMapper();
            Response res = objectMapper.readValue(jsonData.toString(), Response.class);
            return res.getResultCode() == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean withdraw(long mainKey, String buyNo, long count) {
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
                if (postData.length() != 0) postData.append('&');
                postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                postData.append('=');
                postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
            }
            byte[] postDataBytes = postData.toString().getBytes(StandardCharsets.UTF_8);
            conn.getOutputStream().write(postDataBytes);
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder jsonData = new StringBuilder();
            String output;
            while ((output = br.readLine()) != null) jsonData.append(output);
            conn.disconnect();
            ObjectMapper objectMapper = new ObjectMapper();
            Response res = objectMapper.readValue(jsonData.toString(), Response.class);
            if (res.getResultCode() != 0) return false;
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}

