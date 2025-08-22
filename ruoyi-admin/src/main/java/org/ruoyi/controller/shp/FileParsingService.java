package org.ruoyi.controller.shp;

import org.locationtech.jts.geom.Geometry;
import net.lingala.zip4j.ZipFile;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.locationtech.jts.index.strtree.STRtree;
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

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

@Service
public class FileParsingService {

    private static final Logger log = LoggerFactory.getLogger(FileParsingService.class);
    private final StorageService storageService;
    private final SseService sseService;
    private final Map<String, File> stagedFiles = new ConcurrentHashMap<>();
    private static final String DEFAULT_CHARSET = "GBK";

    @Autowired
    public FileParsingService(StorageService storageService, SseService sseService) {
        this.storageService = storageService;
        this.sseService = sseService;
    }

    public void stageFile(String taskId, File tempFile) {
        stagedFiles.put(taskId, tempFile);
    }

    public void startParsingForStagedFile(String taskId) {
        File fileToParse = stagedFiles.remove(taskId);
        if (fileToParse != null) {
            this.parseShapefile(taskId, fileToParse);
        } else {
            Map<String, Object> sseData = Map.of("status", "FAILED", "error", "Task file not found or has expired.");
            sseService.send(taskId, sseData);
            sseService.complete(taskId);
        }
    }


    @Async
    public void parseShapefile(String taskId, File zipInputFile) {
        long startNs = System.nanoTime();
        log.info("开始解析SHP文件并分析空间关系，任务ID: {}", taskId);
        storageService.updateTaskStatus(taskId, StorageService.TaskStatus.PROCESSING);

        Path tempUnzipDir = null;
        ShapefileDataStore dataStore = null;

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

            // 4. 使用工厂创建 DataStore
            ShapefileDataStoreFactory factory = new ShapefileDataStoreFactory();
            Map<String, Object> params = new HashMap<>();
            params.put(ShapefileDataStoreFactory.URLP.key, shpFile.toURI().toURL());
            params.put(ShapefileDataStoreFactory.CREATE_SPATIAL_INDEX.key, Boolean.FALSE);
            dataStore = (ShapefileDataStore) factory.createDataStore(params);
            if (dataStore == null) {
                throw new IOException("无法使用 ShapefileDataStoreFactory 创建 DataStore。");
            }

            // 5. 设置字符集
            String charsetName = detectCharset(shpFile.toPath());
            log.info("任务 {} 检测到字符集: {}", taskId, charsetName);
            if (StringUtils.hasText(charsetName)) {
                try {
                    dataStore.setCharset(Charset.forName(charsetName));
                } catch (Exception e) {
                    log.warn("无法设置指定的字符集 '{}'，将回退到系统默认编码。错误: {}", charsetName, e.getMessage());
                }
            }

            // 6. 读取所有要素到内存
            String typeName = dataStore.getTypeNames()[0];
            SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
            List<SimpleFeature> featuresList;
            try (SimpleFeatureIterator iterator = featureSource.getFeatures().features()) {
                featuresList = new ArrayList<>();
                while (iterator.hasNext()) {
                    featuresList.add(iterator.next());
                }
            }

            // 7. 构建空间索引 (与之前相同)
            List<FeatureData> featureDataList = new ArrayList<>();
            STRtree spatialIndex = new STRtree();
            log.info("开始为任务 {} 构建空间索引...", taskId);
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

                Geometry geom = (Geometry) feature.getDefaultGeometry();
                if (geom != null && !geom.isEmpty()) {
                    spatialIndex.insert(geom.getEnvelopeInternal(), feature);
                }
            }
            log.info("任务 {} 空间索引构建完成。", taskId);

            // 8. *** 优化点：使用并行流计算空间关系 ***
            log.info("开始为任务 {} 使用并行流和索引计算空间关系...", taskId);
            // 使用并行流 parallelStream() 来利用多核CPU
            List<SpatialRelationship> relationships = featuresList.parallelStream()
                    .flatMap(feature1 -> {
                        Geometry geom1 = (Geometry) feature1.getDefaultGeometry();
                        if (geom1 == null || geom1.isEmpty()) {
                            return Stream.empty();
                        }

                        // *** 关键优化点：为 feature1 创建 PreparedGeometry ***
                        PreparedGeometry preparedGeom1 = PreparedGeometryFactory.prepare(geom1);

                        @SuppressWarnings("unchecked")
                        List<SimpleFeature> candidates = spatialIndex.query(geom1.getEnvelopeInternal());

                        List<SpatialRelationship> foundRelations = new ArrayList<>();

                        for (SimpleFeature feature2 : candidates) {
                            if (feature1.getID().compareTo(feature2.getID()) >= 0) {
                                continue;
                            }

                            Geometry geom2 = (Geometry) feature2.getDefaultGeometry();
                            if (geom2 == null || geom2.isEmpty()) {
                                continue;
                            }

                            // *** 使用 preparedGeom1 进行判断，速度更快 ***
                            if (preparedGeom1.intersects(geom2)) {
                                // 这里的 touches, crosses 等方法仍然使用原始的 geom1 对象
                                // 因为 PreparedGeometry 主要优化 intersects, contains, covers, within 等谓词
                                if (geom1.touches(geom2)) {
                                    foundRelations.add(new SpatialRelationship(feature1.getID(), feature2.getID(), "TOUCHES"));
                                }
                                if (geom1.crosses(geom2)) {
                                    foundRelations.add(new SpatialRelationship(feature1.getID(), feature2.getID(), "CROSSES"));
                                }
                                // 对于这些判断，也可以用 preparedGeom1
                                if (preparedGeom1.contains(geom2)) {
                                    foundRelations.add(new SpatialRelationship(feature1.getID(), feature2.getID(), "CONTAINS"));
                                }
                                if (preparedGeom1.within(geom2)) { // JTS 内部会自动优化为 geom2.contains(geom1)
                                    foundRelations.add(new SpatialRelationship(feature1.getID(), feature2.getID(), "WITHIN"));
                                }
                                if (geom1.overlaps(geom2)) {
                                    foundRelations.add(new SpatialRelationship(feature1.getID(), feature2.getID(), "OVERLAPS"));
                                }
                            }
                        }
                        return foundRelations.stream();
                    })
                    .collect(Collectors.toList()); // 将所有线程找到的关系汇总到一个列表

            log.info("任务 {} 空间关系计算完成，发现 {} 个关系。", taskId, relationships.size());

            // 9. 封装并返回结果 (与之前相同)
            ShpParseResult finalResult = new ShpParseResult(featureDataList, relationships);
            storageService.storeTaskResult(taskId, finalResult);
            storageService.updateTaskStatus(taskId, StorageService.TaskStatus.COMPLETED);
            log.info("SHP文件解析和空间分析成功，任务ID: {}", taskId);

//            Map<String, Object> sseData = Map.of(
//                    "status", "COMPLETED",
//                    "data", finalResult
//            );
//            sseService.send(taskId, sseData);
//            sseService.complete(taskId);

        } catch (Exception e) {
            log.error("解析SHP文件失败，任务ID: {}", taskId, e);
            String errorMessage = "解析SHP文件失败: " + e.getMessage();
            storageService.storeTaskError(taskId, errorMessage);
            storageService.updateTaskStatus(taskId, StorageService.TaskStatus.FAILED);

//            Map<String, Object> sseData = Map.of(
//                    "status", "FAILED",
//                    "error", errorMessage
//            );
//            sseService.send(taskId, sseData);
//            sseService.complete(taskId);
        } finally {
            long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            log.info("任务 {} 解析+分析耗时 {} ms", taskId, costMs);
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

    private String detectCharset(Path shpFilePath) {
        String cpgFileName = shpFilePath.getFileName().toString().replaceAll("(?i)\\.shp$", ".cpg");
        Path cpgFilePath = shpFilePath.getParent().resolve(cpgFileName);

        if (Files.exists(cpgFilePath) && Files.isReadable(cpgFilePath)) {
            try {
                String encoding = new String(Files.readAllBytes(cpgFilePath), StandardCharsets.UTF_8).trim();
                if (StringUtils.hasText(encoding)) {
                    log.info("从 {} 文件中读取到编码: {}", cpgFilePath.toString(), encoding);
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
