package com.bdo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SellItem {
    long sellNo;

    long leftCount;

    long pricePerOne;

    long soldCount;

    long accumulateMoneyCount;

    @JsonProperty("isSealed")
    boolean isSealed;

    long enchantNeedCount;

    long enchantMaterialPrice;

    @JsonProperty("ringBuff")
    boolean ringBuff;

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
