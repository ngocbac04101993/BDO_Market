package com.bdo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BiddingList {
    List<BuyItem> buyList;

    List<SellItem> sellList;

    int resultCode;

    String resultMsg;
}
