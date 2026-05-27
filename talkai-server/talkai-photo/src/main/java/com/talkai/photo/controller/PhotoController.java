package com.talkai.photo.controller;

import com.talkai.common.result.R;
import com.talkai.photo.dto.PhotoListResponse;
import com.talkai.photo.dto.RepairResponse;
import com.talkai.photo.service.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/photo")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;

    @PostMapping("/upload")
    public Mono<R<RepairResponse>> upload(
            @RequestHeader("X-User-Id") Long userId,
            @RequestPart("file") FilePart file) {
        return photoService.upload(userId, file).map(R::ok);
    }

    @GetMapping("/list")
    public R<List<PhotoListResponse>> list(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(photoService.list(userId, page, size));
    }

    @GetMapping("/{id}")
    public R<RepairResponse> detail(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        return R.ok(photoService.detail(userId, id));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        photoService.delete(userId, id);
        return R.ok();
    }
}
