package com.bdo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SellBuyInfo {
    long sellCount;

    long buyCount;

    long pricePerOne;
}
