// --- File: src/main/java/org/ruoyi/controller/shp/FileParsingService.java ---
package org.ruoyi.controller.shp;

import net.lingala.zip4j.ZipFile; // 导入 zip4j
import org.geotools.data.DataStore;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FileParsingService {

    private static final Logger log = LoggerFactory.getLogger(FileParsingService.class);
    private final StorageService storageService;

    @Autowired
    public FileParsingService(StorageService storageService) {
        this.storageService = storageService;
    }

    @Async
    public void parseShapefile(String taskId, File zipInputFile) {
        log.info("开始解析SHP文件，任务ID: {}", taskId);
        storageService.updateTaskStatus(taskId, StorageService.TaskStatus.PROCESSING);

        Path tempUnzipDir = null;
        DataStore dataStore = null;

        try {
            // 1. 创建一个临时目录用于解压
            tempUnzipDir = Files.createTempDirectory("shp-unzip-" + taskId + "-");
            log.info("为任务 {} 创建临时解压目录: {}", taskId, tempUnzipDir);

            // 2. 使用 zip4j 解压，并指定 GBK 编码
            try (ZipFile zipFile = new ZipFile(zipInputFile)) {
                // 关键：告诉 zip4j 文件名是 GBK 编码的
                // zipFile.setCharset(Charset.forName("GBK")); // 旧版 API
                zipFile.extractAll(tempUnzipDir.toString()); // 新版API自动检测或可配置
                log.info("成功解压文件到: {}", tempUnzipDir);
            }

            // 3. 在解压目录中查找 .shp 文件
            File shpFile = findShpFile(tempUnzipDir);
            if (shpFile == null) {
                throw new IOException("在解压后的目录中未找到 .shp 文件。");
            }
            log.info("在解压目录中找到 SHP 文件: {}", shpFile.getAbsolutePath());

            // 4. 使用 ShapefileDataStore 直接打开 .shp 文件
            dataStore = new ShapefileDataStore(shpFile.toURI().toURL());
            // 关键：设置 DBF 文件的字符集编码，这与文件名编码是两回事！
            ((ShapefileDataStore) dataStore).setCharset(Charset.forName("GBK"));


            // 5. 读取数据（后续逻辑与之前类似）
            String typeName = dataStore.getTypeNames()[0];
            SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
            List<Map<String, Object>> attributeList = new ArrayList<>();

            try (SimpleFeatureIterator iterator = featureSource.getFeatures().features()) {
                while (iterator.hasNext()) {
                    SimpleFeature feature = iterator.next();
                    Map<String, Object> attributes = new LinkedHashMap<>();
                    for (Property property : feature.getProperties()) {
                        String propertyName = property.getName().getLocalPart();
                        if (propertyName.equalsIgnoreCase("the_geom") || propertyName.equalsIgnoreCase("geometry")) {
                            continue;
                        }
                        attributes.put(propertyName, property.getValue());
                    }
                    attributeList.add(attributes);
                }
            }

            storageService.storeTaskResult(taskId, attributeList);
            storageService.updateTaskStatus(taskId, StorageService.TaskStatus.COMPLETED);
            log.info("SHP文件解析成功，任务ID: {}", taskId);

        } catch (Exception e) {
            log.error("解析SHP文件失败，任务ID: {}", taskId, e);
            storageService.storeTaskError(taskId, "解析SHP文件失败: " + e.getMessage());
            storageService.updateTaskStatus(taskId, StorageService.TaskStatus.FAILED);
        } finally {
            if (dataStore != null) {
                dataStore.dispose();
            }
            // 清理上传的 zip 文件
            if (zipInputFile.exists() && !zipInputFile.delete()) {
                log.warn("无法删除临时ZIP文件: {}", zipInputFile.getAbsolutePath());
            }
            // 清理解压后的临时目录及其内容
            if (tempUnzipDir != null) {
                try {
                    FileSystemUtils.deleteRecursively(tempUnzipDir);
                    log.info("成功清理临时解压目录: {}", tempUnzipDir);
                } catch (IOException e) {
                    log.warn("清理临时解压目录失败: {}", tempUnzipDir, e);
                }
            }
        }
    }

    /**
     * 在指定目录中查找第一个.shp文件。
     * @param directory 要搜索的目录
     * @return 找到的 .shp 文件，如果找不到则返回 null
     * @throws IOException
     */
    private File findShpFile(Path directory) throws IOException {
        return Files.walk(directory, 1) // 只搜索当前层级
                .filter(path -> path.toString().toLowerCase().endsWith(".shp"))
                .map(Path::toFile)
                .findFirst()
                .orElse(null);
    }
}