package com.xiangqi.server.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 对局存档仓库：每局一个 JSON 文件，写入采用“临时文件 + 原子替换”，避免半截文件。
 * <p>
 * 不引入数据库依赖；文件数量与单文件体积都很小，适合本地/单机部署。
 */
@Service
public class GameArchiveRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameArchiveRepository.class);
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final int MAX_LOAD = 2000;

    private final Path directory;
    private final ObjectMapper mapper;

    public GameArchiveRepository(@Value("${app.archive.dir:./data/games}") String directory,
                                 ObjectMapper mapper) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.mapper = mapper;
        try {
            Files.createDirectories(this.directory);
        } catch (IOException exception) {
            LOGGER.error("无法创建对局存档目录 {}", this.directory, exception);
        }
    }

    public void save(StoredGame record) {
        if (record == null || record.id() == null || !SAFE_ID.matcher(record.id()).matches()) {
            LOGGER.warn("拒绝写入非法对局标识的存档");
            return;
        }
        Path target = directory.resolve(record.id() + ".json");
        Path temporary = directory.resolve(record.id() + ".json.tmp");
        try {
            String json = mapper.writeValueAsString(record);
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            LOGGER.warn("写入对局存档失败: {}", record.id(), exception);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // 清理失败不影响主流程
            }
        }
    }

    /**
     * 读取全部存档，按最近更新时间倒序；损坏或格式不符的文件会被跳过。
     */
    public List<StoredGame> loadAll() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparingLong(GameArchiveRepository::lastModified).reversed())
                    .limit(MAX_LOAD)
                    .forEach(files::add);
        } catch (IOException exception) {
            LOGGER.warn("扫描对局存档目录失败", exception);
            return List.of();
        }

        List<StoredGame> records = new ArrayList<>(files.size());
        for (Path file : files) {
            try {
                StoredGame record = mapper.readValue(Files.readString(file, StandardCharsets.UTF_8),
                        StoredGame.class);
                if (record != null && record.id() != null && record.moves() != null) {
                    records.add(record);
                }
            } catch (Exception exception) {
                LOGGER.warn("跳过无法解析的存档文件 {}", file.getFileName(), exception);
            }
        }
        return records;
    }

    /**
     * 读取单局存档；不存在或格式非法时返回 null。
     */
    public StoredGame load(String id) {
        if (id == null || !SAFE_ID.matcher(id).matches()) {
            return null;
        }
        Path file = directory.resolve(id + ".json");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            StoredGame record = mapper.readValue(Files.readString(file, StandardCharsets.UTF_8),
                    StoredGame.class);
            if (record != null && record.moves() != null) {
                return record;
            }
        } catch (Exception exception) {
            LOGGER.warn("读取对局存档失败 {}", id, exception);
        }
        return null;
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return 0L;
        }
    }
}
