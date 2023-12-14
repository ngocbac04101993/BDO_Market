package com.bdo;

import com.bdo.constant.Constant;
import com.bdo.model.*;
import com.bdo.schedule.ScheduleTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.math.NumberUtils;

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
            ScheduleTask task = new ScheduleTask(t, args[0]);
            t.scheduleAtFixedRate(task, 0, 50000);
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
            report = "";
            if (mode.equals("1")) sellReport();
            if (mode.equals("2")) buyReport();
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
//                if (item.getMainKey() != 702) continue;
                if (Constant.NO.equals(item.getMinMaxPrice())) continue;
                SellItem sell = biddingList.getSellList().stream().filter(sellItem -> (item.getMainKey() == sellItem.getMainKey())).findAny().orElse(null);
                long minToSell, curMaxPrice, curMinPrice;
                ArrayList<SellBuyInfo> listPrices;
                StringBuilder text = new StringBuilder();
                if (sell == null || sell.getLeftCount() == 0L) {
                    text.append(String.join(" ", " 0/" + item.getCount(), item.getName(), "!"));
                } else {
                    if (sell.getLeftCount() < (item.getCount() / 2)) {
                        text.append(String.join(" ", " " + sell.getLeftCount() + "/" + item.getCount(), "! Refill " + (item.getCount() - sell.getLeftCount()), item.getName(), "!"));
                    }
                }
                if (item.isExtremeMode()) {
                    listPrices = getPrices(item.getMainKey());
                    curMaxPrice = getMaxPrice(listPrices);
                    curMinPrice = getMinPrice(listPrices);
                    minToSell = Constant.YES.equals(item.getMinMaxPrice()) ? curMinPrice : Long.parseLong(item.getMinMaxPrice());
                    if (curMaxPrice < minToSell) {
                        if (sell != null) {
                            if (sell.getPricePerOne() < minToSell) {
                                text.setLength(0);
                                text.append(String.join(" ", " Too low. Remove", String.valueOf(sell.getLeftCount()), item.getName(), "!", curMaxPrice + "/" + minToSell));
                            } else text.setLength(0);
                        } else {
                            text.setLength(0);
//                            text.append(String.join(" ", " Too low.", item.getName(), curMaxPrice + "/" + minToSell));
                        }
                        if (!text.toString().isEmpty())
                            report += "\n" + text;
                        continue;
                    }
                    boolean hasListing = false;
                    if (minToSell < curMinPrice) minToSell = curMinPrice;
                    long previousPrice = minToSell;
                    for (SellBuyInfo price : listPrices) {
                        if (sell != null && price.getPricePerOne() == sell.getPricePerOne()) {
                            price.setSellCount(price.getSellCount() - sell.getLeftCount());
                        }
                        if (price.getPricePerOne() < minToSell && price.getSellCount() > item.getCount() / 10) {
                            hasListing = true;
                            break;
                        } else {
                            if (price.getPricePerOne() >= minToSell && price.getSellCount() > item.getCount() / 10) {
                                hasListing = true;
                                break;
                            }
                            if (price.getPricePerOne() >= minToSell) previousPrice = price.getPricePerOne();
                        }
                    }
                    minToSell = previousPrice;
                    long desiredPrice = curMaxPrice;
                    if (hasListing) {
                        desiredPrice = minToSell;
                    }
                    if (sell == null || sell.getPricePerOne() != desiredPrice)
                        text.append(String.join(" ", " Minlist", item.getName(), "to", String.valueOf(desiredPrice)));
                }
                if (!text.toString().isEmpty())
                    report += "\n" + text;
            }
            BufferedWriter writer = new BufferedWriter(new FileWriter(Constant.SELL_REPORT));
            writer.write(report);
            writer.close();
            if (!schedule) Desktop.getDesktop().open(new File(Constant.SELL_REPORT));
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
                long maxToBuy = 0, curMinPrice, curMaxPrice;
                if (NumberUtils.isCreatable(item.getMinMaxPrice())) maxToBuy = Long.parseLong(item.getMinMaxPrice());
                if (buy == null || buy.getLeftCount() < item.getCount() || buy.getBoughtCount() > 0L || item.isExtremeMode() || buy.getPricePerOne() < maxToBuy) {
                    ItemSellBuyInfo itemSellBuyInfo = getItemInfo(item.getMainKey());
                    item.setCount(0 != itemSellBuyInfo.getAddBuyRefCountForWorldMarket() && itemSellBuyInfo.getAddBuyRefCountForWorldMarket() <= itemSellBuyInfo.getBiddingSellCount() ? itemSellBuyInfo.getAddBuyCountForWorldMarket() : itemSellBuyInfo.getMaxRegisterForWorldMarket());
                    item.setMinCount(itemSellBuyInfo.getMaxRegisterForWorldMarket());
                    ArrayList<SellBuyInfo> listPrices = getPrices(itemSellBuyInfo);
                    curMinPrice = getMinPrice(listPrices);
                    curMaxPrice = getMaxPrice(listPrices);
                    if (Constant.YES.equalsIgnoreCase(item.getMinMaxPrice())) maxToBuy = curMaxPrice;
                    tooHigh = curMinPrice > maxToBuy;
                    if (!tooHigh) {
                        Collections.reverse(listPrices);
                        boolean hasOrder = false;
                        if (maxToBuy > curMaxPrice) maxToBuy = curMaxPrice;
                        long maxToBuyExtreme = maxToBuy;
                        long desiredPrice = maxToBuy;
                        if (item.isExtremeMode()) {
                            for (SellBuyInfo price : listPrices) {
                                if (price.getPricePerOne() > maxToBuy) continue;
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
                            desiredPrice = curMinPrice;
                        }
                        if (hasOrder) {
                            desiredPrice = maxToBuy;
                        }
                        if (buy == null) {
                            boolean result;
                            do {
                                result = preOrder(item.getMainKey(), desiredPrice, item.getCount(), 0, item.getMinCount());
                                if (result) {
                                    report = report + String.join(" ", "\n Ordered", String.valueOf(item.getCount()), item.getName(), String.valueOf(desiredPrice), "!");
                                } else {
                                    report = report + String.join(" ", "\n Ordered", item.getName(), "fail !");
                                }
                                Thread.sleep(200L);
                            } while (result);
                        } else {
                            if (buy.getLeftCount() < item.getCount() || buy.getBoughtCount() > 0L || buy.getPricePerOne() != desiredPrice) {
                                if (preOrder(item.getMainKey(), desiredPrice, item.getCount(), buy.getBuyNo(), item.getMinCount()))
                                    report = report + String.join(" ", "\n Restock", item.getName(), String.valueOf(desiredPrice));
                                boolean result;
                                do {
                                    result = preOrder(item.getMainKey(), desiredPrice, item.getCount(), 0, item.getMinCount());
                                    if (result) {
                                        report = report + String.join(" ", "\n Ordered", String.valueOf(item.getCount()), item.getName(), String.valueOf(desiredPrice), "!");
                                    } else {
                                        report = report + String.join(" ", "\n Ordered", item.getName(), "fail !");
                                    }
                                    Thread.sleep(200L);
                                } while (result);
                            }
                        }
                        if (item.isExtremeMode()) {
                            boolean result;
                            do {
                                result = preOrder(item.getMainKey(), maxToBuyExtreme, item.getCount(), 0, item.getMinCount());
                                if (result) {
                                    report = report + String.join(" ", "\n Ordered", String.valueOf(item.getCount()), item.getName(), String.valueOf(maxToBuyExtreme), "!");
                                }
                                Thread.sleep(200L);
                            } while (result);
                        }
                    } else {
                        if (buy != null && buy.getPricePerOne() >= curMinPrice)
                            report += String.join(" ", "\n Too high. Remove order", item.getName(), "!");
                    }
                }
            }
            BufferedWriter writer = new BufferedWriter(new FileWriter(Constant.BUY_REPORT));
            writer.write(report);
            writer.close();
            if (!schedule) Desktop.getDesktop().open(new File(Constant.BUY_REPORT));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean preOrder(long buyMainKey, long buyPrice, long buyCount, long buyNo, long minCount) {
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
            int resultCode = res.getResultCode();
            if (resultCode == 20) return preOrder(buyMainKey, buyPrice, minCount, buyNo, 0);
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
            long startTime = System.nanoTime();
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
            long endTime = System.nanoTime();
            System.out.println(mainKey + " - " + (endTime - startTime) / 1000000);
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

