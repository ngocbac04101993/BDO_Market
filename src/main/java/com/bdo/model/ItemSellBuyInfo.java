package com.bdo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemSellBuyInfo {

    ArrayList<SellBuyInfo> marketConditionList;

    long addBuyRefCountForWorldMarket;

    long addBuyCountForWorldMarket;

    long maxRegisterForWorldMarket;

    int resultCode;

    int biddingSellCount;
}
