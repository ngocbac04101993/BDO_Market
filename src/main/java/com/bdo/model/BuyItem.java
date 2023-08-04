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

    long mainKey;

}
