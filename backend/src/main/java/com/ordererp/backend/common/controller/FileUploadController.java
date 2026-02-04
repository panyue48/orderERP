package com.ordererp.backend.common.controller;

import com.ordererp.backend.common.dto.UploadResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/files")
public class FileUploadController {
    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.max-size-bytes:5242880}")
    private long maxSizeBytes;

    @PostMapping("/upload")
    public UploadResponse upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file too large");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "only image/* is allowed");
        }

        String ext = extension(file.getOriginalFilename());
        if (ext == null || ext.isBlank() || !isAllowedExt(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported image type");
        }
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String name = UUID.randomUUID().toString().replace("-", "");
        String filename = date + "_" + name + "." + ext;

        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            file.transferTo(target);
            return new UploadResponse("/uploads/" + filename, filename, file.getSize());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "upload failed");
        }
    }

    private static String extension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return null;
        }
        String clean = Paths.get(originalFilename).getFileName().toString();
        int idx = clean.lastIndexOf('.');
        if (idx < 0 || idx == clean.length() - 1) {
            return null;
        }
        String ext = clean.substring(idx + 1).trim();
        if (ext.isEmpty()) return null;
        // 出于文件系统安全考虑，只保留安全字符（避免路径穿越/奇怪扩展名等问题）。
        return ext.replaceAll("[^A-Za-z0-9]", "");
    }

    private static boolean isAllowedExt(String ext) {
        String e = ext.toLowerCase(Locale.ROOT);
        return e.equals("png") || e.equals("jpg") || e.equals("jpeg") || e.equals("gif") || e.equals("webp");
    }
}
