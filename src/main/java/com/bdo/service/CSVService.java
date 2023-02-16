package com.bdo.service;

import com.bdo.model.AutoItem;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CSVService {
    public static List<AutoItem> readCSV(String fileName) throws IOException {
        List<AutoItem> lsItem = new ArrayList<>();
        BufferedReader br = new BufferedReader(new FileReader(fileName));
        String line;
        while ((line = br.readLine()) != null) {
            String[] data = line.split(",");
            AutoItem item = new AutoItem(data[0], data[1], data[2], data[3]);
            lsItem.add(item);
        }
        br.close();
        return lsItem;
    }
}
