package com.bdo.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AutoItem {
    String name;

    long mainKey;

    long count;

    String minMaxPrice;

    boolean extremeMode;

}
