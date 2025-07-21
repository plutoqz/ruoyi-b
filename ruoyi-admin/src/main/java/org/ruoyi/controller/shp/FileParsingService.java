package org.ruoyi.controller.shp;

import org.locationtech.jts.geom.Geometry;
import net.lingala.zip4j.ZipFile;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.ruoyi.controller.shp.dto.FeatureData;
import org.ruoyi.controller.shp.dto.ShpParseResult;
import org.ruoyi.controller.shp.dto.SpatialRelationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FileParsingService {

    private static final Logger log = LoggerFactory.getLogger(FileParsingService.class);
    private final StorageService storageService;

    // 定义一个默认编码，当.cpg文件不存在时使用
    private static final String DEFAULT_CHARSET = "GBK";

    @Autowired
    public FileParsingService(StorageService storageService) {
        this.storageService = storageService;
    }

    @Async
    public void parseShapefile(String taskId, File zipInputFile) {
        log.info("开始解析SHP文件并分析空间关系，任务ID: {}", taskId);
        storageService.updateTaskStatus(taskId, StorageService.TaskStatus.PROCESSING);

        Path tempUnzipDir = null;
        DataStore dataStore = null;

        try {
            // 1. & 2. 解压文件
            tempUnzipDir = Files.createTempDirectory("shp-unzip-" + taskId + "-");
            try (ZipFile zipFile = new ZipFile(zipInputFile)) {
                zipFile.extractAll(tempUnzipDir.toString());
            }

            // 3. 查找 .shp 文件
            File shpFile = findShpFile(tempUnzipDir);
            if (shpFile == null) {
                throw new IOException("在解压后的目录中未找到 .shp 文件。");
            }

            // 4. ***核心修改***: 自动检测或使用默认编码
            String charset = detectCharset(shpFile.toPath());
            log.info("任务 {} 检测到字符集: {}", taskId, charset);

            Map<String, Object> params = new HashMap<>();
            params.put("url", shpFile.toURI().toURL());
            params.put("charset", charset);

            dataStore = DataStoreFinder.getDataStore(params);
            if (dataStore == null) {
                throw new IOException("无法使用指定的参数获取 DataStore。请检查shp文件是否有效以及GeoTools依赖是否完整。");
            }

            // 5. 读取几何和属性数据
            String typeName = dataStore.getTypeNames()[0];
            SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
            List<SimpleFeature> featuresList = new ArrayList<>();
            try (SimpleFeatureIterator iterator = featureSource.getFeatures().features()) {
                while (iterator.hasNext()) {
                    featuresList.add(iterator.next());
                }
            }

            // 6. 处理要素和分析空间关系 (逻辑不变)
            List<FeatureData> featureDataList = new ArrayList<>();
            List<SpatialRelationship> relationships = new ArrayList<>();

            for (SimpleFeature feature : featuresList) {
                Map<String, Object> attributes = new LinkedHashMap<>();
                for (Property property : feature.getProperties()) {
                    String propertyName = property.getName().getLocalPart();
                    if (propertyName.equalsIgnoreCase("the_geom") || propertyName.equalsIgnoreCase("geometry")) {
                        continue;
                    }
                    attributes.put(propertyName, property.getValue());
                }
                featureDataList.add(new FeatureData(feature.getID(), attributes));
            }

            log.info("开始为任务 {} 计算所有空间关系...", taskId);
            for (int i = 0; i < featuresList.size(); i++) {
                for (int j = i + 1; j < featuresList.size(); j++) {
                    SimpleFeature feature1 = featuresList.get(i);
                    SimpleFeature feature2 = featuresList.get(j);

                    Geometry geom1 = (Geometry) feature1.getDefaultGeometry();
                    Geometry geom2 = (Geometry) feature2.getDefaultGeometry();

                    if (geom1 != null && geom2 != null && !geom1.isEmpty() && !geom2.isEmpty()) {
                        // 注意: 我们检查多种关系。一个要素对可以有多个关系。
                        // 例如，如果A包含B，它们也相交。

                        if (geom1.intersects(geom2)) {
                            // Intersects 是最基本的关系，但我们可能需要更具体的。
                            // 为了避免图谱过于拥挤，可以选择只添加更具体的关系。
                            // 这里我们先添加所有关系，后续可以根据需求筛选。

                            if (geom1.touches(geom2)) {
                                relationships.add(new SpatialRelationship(feature1.getID(), feature2.getID(), "TOUCHES"));
                            }
                            if (geom1.crosses(geom2)) {
                                relationships.add(new SpatialRelationship(feature1.getID(), feature2.getID(), "CROSSES"));
                            }
                            if (geom1.contains(geom2)) {
                                relationships.add(new SpatialRelationship(feature1.getID(), feature2.getID(), "CONTAINS"));
                            }
                            if (geom1.within(geom2)) {
                                relationships.add(new SpatialRelationship(feature1.getID(), feature2.getID(), "WITHIN"));
                            }
                            if (geom1.overlaps(geom2)) {
                                relationships.add(new SpatialRelationship(feature1.getID(), feature2.getID(), "OVERLAPS"));
                            }
                            // 如果只想在没有其他更具体关系时才标记为INTERSECTS，可以把其他if包在一个else里
                            // else {
                            //     relationships.add(new SpatialRelationship(feature1.getID(), feature2.getID(), "INTERSECTS"));
                            // }
                        }
                    }
                }
            }
            log.info("任务 {} 空间关系计算完成，发现 {} 个关系。", taskId, relationships.size());

            // 7. 封装最终结果
            ShpParseResult finalResult = new ShpParseResult(featureDataList, relationships);
            storageService.storeTaskResult(taskId, finalResult);
            storageService.updateTaskStatus(taskId, StorageService.TaskStatus.COMPLETED);
            log.info("SHP文件解析和空间分析成功，任务ID: {}", taskId);

        } catch (Exception e) {
            log.error("解析SHP文件失败，任务ID: {}", taskId, e);
            storageService.storeTaskError(taskId, "解析SHP文件失败: " + e.getMessage());
            storageService.updateTaskStatus(taskId, StorageService.TaskStatus.FAILED);
        } finally {
            // 清理逻辑
            if (dataStore != null) {
                dataStore.dispose();
            }
            if (zipInputFile.exists() && !zipInputFile.delete()) {
                log.warn("无法删除临时ZIP文件: {}", zipInputFile.getAbsolutePath());
            }
            if (tempUnzipDir != null) {
                try {
                    FileSystemUtils.deleteRecursively(tempUnzipDir);
                } catch (IOException e) {
                    log.warn("清理临时解压目录失败: {}", tempUnzipDir, e);
                }
            }
        }
    }

    /**
     * NEW: 自动检测Shapefile的字符集编码
     * 它会查找与.shp文件同名的.cpg文件并读取其内容。
     * @param shpFilePath .shp文件的路径
     * @return 检测到的编码字符串，如果找不到.cpg文件则返回默认编码 (GBK)
     */
    private String detectCharset(Path shpFilePath) {
        // 将 .shp 扩展名替换为 .cpg
        String cpgFileName = shpFilePath.getFileName().toString().replaceAll("(?i)\\.shp$", ".cpg");
        Path cpgFilePath = shpFilePath.getParent().resolve(cpgFileName);

        if (Files.exists(cpgFilePath) && Files.isReadable(cpgFilePath)) {
            try {
                // 读取.cpg文件的第一行内容
                String encoding = new String(Files.readAllBytes(cpgFilePath), StandardCharsets.UTF_8).trim();
                if (StringUtils.hasText(encoding)) {
                    log.info("从 {} 文件中读取到编码: {}", cpgFilePath.toString(), encoding);
                    // 简单的规范化，例如将 "UTF-8" 变为 "UTF8" 以兼容某些库
                    return encoding.replace("-", "");
                }
            } catch (IOException e) {
                log.warn("读取 .cpg 文件失败: {}, 将使用默认编码。", cpgFilePath.toString(), e);
            }
        } else {
            log.info("未找到 .cpg 文件，将使用默认编码: {}", DEFAULT_CHARSET);
        }
        return DEFAULT_CHARSET;
    }

    private File findShpFile(Path directory) throws IOException {
        return Files.walk(directory, 1)
                .filter(path -> path.toString().toLowerCase().endsWith(".shp"))
                .map(Path::toFile)
                .findFirst()
                .orElse(null);
    }
}