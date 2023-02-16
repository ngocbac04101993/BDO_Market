package com.bdo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BuyItem {
    long buyNo;

    long leftCount;

    long boughtCount;

    long pricePerOne;

    long addEnchantPrice;

    long registerMoneyCount;

    long keyType;

    long mainKey;

    long subKey;

    long count;

    String name;

    long grade;

    long mainCategory;

    long subCategory;

    long chooseKey;
}
