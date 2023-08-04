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

    long mainKey;

}
