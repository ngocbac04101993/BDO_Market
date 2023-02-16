package com.bdo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemSellBuy {
    int[] priceList;

    List<SellBuyInfo> marketConditionList;

    long basePrice;

    long maxRegisterForWorldMarket;

    int resultCode;
}
