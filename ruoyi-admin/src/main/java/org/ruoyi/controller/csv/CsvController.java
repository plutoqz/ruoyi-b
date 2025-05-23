package org.ruoyi.controller.csv;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@RestController
public class CsvController {

    @Value("E:/vscode/my-ruoyi-admin-webgis/ruoyi-admin/apps/web-antd/public")
    private String serverBasePath;

    @GetMapping("/read-csv-folder")
    public List<Object> readCsvFolder() {
        File nodeDir = new File(serverBasePath, "node");
        File edgeDir = new File(serverBasePath, "edge");
        List<Object> allRecords = new ArrayList<>();

        // 读取 node 下的所有 CSV
        if (nodeDir.exists() && nodeDir.isDirectory()) {
            File[] nodeFiles = nodeDir.listFiles((d, n) -> n.endsWith(".csv"));
            if (nodeFiles != null) {
                for (File f : nodeFiles) {
                    List<List<String>> recs = CsvParserUtil.parseCsvFile(f.getAbsolutePath());
                    allRecords.addAll(formatNodeRecords(recs));
//                    if (recs != null && !recs.isEmpty()) {
//                        // 打印前几行解析出的原始 CSV 数据
//                        for (int i = 0; i < Math.min(3, recs.size()); i++) {
//                            System.out.println("Raw CSV row (node) [{}]: {}"+ i+ allRecords.get(i));
//                            System.out.println("Raw CSV row (node) [" + i + "]: " + allRecords.get(i));
//                        }
//                    }
                }
            }
        }

        // 读取 edge 下的所有 CSV
        if (edgeDir.exists() && edgeDir.isDirectory()) {
            File[] edgeFiles = edgeDir.listFiles((d, n) -> n.endsWith(".csv"));
            if (edgeFiles != null) {
                for (File f : edgeFiles) {
                    List<List<String>> recs = CsvParserUtil.parseCsvFile(f.getAbsolutePath());
                    allRecords.addAll(formatEdgeRecords(recs));
                }
            }
        }

        return allRecords;
    }


    private List<Object> formatNodeRecords(List<List<String>> records) {
        List<Object> formattedRecords = new ArrayList<>();
        for (List<String> record : records) {
            if (record == null || record.size() < 2 ||
                    record.get(0) == null || record.get(0).trim().isEmpty()) {
                // Skip this row as it's considered invalid or empty
                continue;
            }
            formattedRecords.add(new NodeDto(
                    record.get(0), // ID
                    record.get(1), // entity_type
                    record.get(2)  // name
            ));
        }

        return formattedRecords;
    }

    private List<Object> formatEdgeRecords(List<List<String>> records) {
        List<Object> formattedRecords = new ArrayList<>();
        for (List<String> record : records) {
            if (record == null || record.size() < 2 ||
                    record.get(0) == null || record.get(0).trim().isEmpty()) {
                // Skip this row as it's considered invalid or empty
                continue;
            }
            formattedRecords.add(new EdgeDto(
                    record.get(0), // ID
                    record.get(1), // relation_type
                    record.get(2), // begin_id
                    record.get(3)  // end_id
            ));
        }
        return formattedRecords;
    }
}