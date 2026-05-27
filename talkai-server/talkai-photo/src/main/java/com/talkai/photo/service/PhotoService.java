package com.talkai.photo.service;

import cn.hutool.core.io.FileUtil;
import com.talkai.common.exception.BusinessException;
import com.talkai.photo.dto.PhotoListResponse;
import com.talkai.photo.dto.RepairResponse;
import com.talkai.photo.entity.Photo;
import com.talkai.photo.mapper.PhotoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoService {

    private final PhotoMapper photoMapper;

    @Value("${talkai.photo.upload-dir:./uploads/photo}")
    private String uploadDir;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "bmp");
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB

    public Mono<RepairResponse> upload(Long userId, FilePart file) {
        String originalFilename = file.filename();
        String extension = getExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            return Mono.error(new BusinessException(400, "不支持的文件格式，仅支持 JPG/PNG/WEBP/BMP"));
        }

        // 生成唯一文件名
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String storedFilename = timestamp + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + extension;

        // 确保目录存在
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        Path destPath = new File(dir, storedFilename).toPath();

        return DataBufferUtils.write(file.content(), destPath)
                .then(Mono.fromCallable(() -> {
                    // 检查文件大小
                    long fileSize = destPath.toFile().length();
                    if (fileSize > MAX_FILE_SIZE) {
                        FileUtil.del(destPath);
                        throw new BusinessException(400, "文件大小超过 20MB 限制");
                    }
                    if (fileSize == 0) {
                        FileUtil.del(destPath);
                        throw new BusinessException(400, "文件为空");
                    }

                    // 插入数据库记录
                    Photo photo = new Photo();
                    photo.setUserId(userId);
                    photo.setOriginalUrl(storedFilename);
                    photo.setStatus("UPLOADED");
                    photoMapper.insert(photo);

                    log.info("Photo uploaded: id={}, userId={}, file={}", photo.getId(), userId, storedFilename);
                    return toResponse(photo);
                }));
    }

    public List<PhotoListResponse> list(Long userId, int page, int size) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Photo> pageParam =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Photo> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Photo>()
                        .eq(Photo::getUserId, userId)
                        .orderByDesc(Photo::getCreateTime);
        photoMapper.selectPage(pageParam, wrapper);

        return pageParam.getRecords().stream().map(p -> PhotoListResponse.builder()
                .id(p.getId())
                .originalUrl(p.getOriginalUrl())
                .restoredUrl(p.getRestoredUrl())
                .status(p.getStatus())
                .damageLevel(p.getDamageLevel())
                .createTime(p.getCreateTime())
                .build()).collect(Collectors.toList());
    }

    public RepairResponse detail(Long userId, Long id) {
        Photo photo = getAndValidate(userId, id);
        return toResponse(photo);
    }

    public void delete(Long userId, Long id) {
        Photo photo = getAndValidate(userId, id);
        // 删除磁盘文件
        FileUtil.del(new File(uploadDir, photo.getOriginalUrl()));
        if (photo.getRestoredUrl() != null) {
            FileUtil.del(new File(uploadDir, photo.getRestoredUrl()));
        }
        if (photo.getComparisonUrl() != null) {
            FileUtil.del(new File(uploadDir, photo.getComparisonUrl()));
        }
        photoMapper.deleteById(id);
        log.info("Photo deleted: id={}, userId={}", id, userId);
    }

    public Photo getAndValidate(Long userId, Long id) {
        Photo photo = photoMapper.selectById(id);
        if (photo == null) {
            throw new BusinessException(404, "照片记录不存在");
        }
        if (!photo.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问该照片记录");
        }
        return photo;
    }

    public String getFullPath(String filename) {
        return new File(uploadDir, filename).getAbsolutePath();
    }

    private RepairResponse toResponse(Photo p) {
        return RepairResponse.builder()
                .id(p.getId())
                .userId(p.getUserId())
                .originalUrl(p.getOriginalUrl())
                .restoredUrl(p.getRestoredUrl())
                .comparisonUrl(p.getComparisonUrl())
                .status(p.getStatus())
                .damageLevel(p.getDamageLevel())
                .fidelity(p.getFidelity())
                .colorize(p.getColorize())
                .copyText(p.getCopyText())
                .errorMessage(p.getErrorMessage())
                .createTime(p.getCreateTime())
                .updateTime(p.getUpdateTime())
                .build();
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
