package com.life1000.goal;

import com.life1000.common.ApiException;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@Profile("mysql")
public class LocalFiles {
    private final Path root;
    private final Set<String> activeUploads = ConcurrentHashMap.newKeySet();
    public LocalFiles(@Value("${life1000.upload-directory:./uploads}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }
    public Path resolve(String relative) throws IOException {
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root) || path.equals(root)) throw ApiException.badRequest("文件路径不合法");
        for (Path p = path; p != null; p = p.getParent()) {
            if (Files.isSymbolicLink(p)) throw ApiException.badRequest("上传路径不能包含符号链接");
        }
        return path;
    }
    public String save(int slot, MultipartFile file) throws IOException {
        String relative = "goals/%03d/%s".formatted(slot, UUID.randomUUID());
        Path path = resolve(relative);
        activeUploads.add(relative);
        try {
            // Journal before writing, so a crash before the DB insert is recoverable.
            marker(relative, "upload-");
            Files.createDirectories(path.getParent());
            try (var input = file.getInputStream()) { Files.copy(input, path); }
            return relative;
        } catch (IOException | RuntimeException exception) {
            activeUploads.remove(relative);
            try { Files.deleteIfExists(path); } catch (IOException failure) { exception.addSuppressed(failure); }
            throw exception;
        }
    }
    public void settled(String path) { activeUploads.remove(path); }
    public boolean uploading(String path) { return activeUploads.contains(path); }
    public Path queue(String relative) throws IOException { return marker(relative, "delete-"); }
    private Path marker(String relative, String prefix) throws IOException {
        resolve(relative);
        Path marker = resolve(".cleanup/" + prefix + UUID.randomUUID());
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, relative, StandardOpenOption.CREATE_NEW);
        return marker;
    }
    public List<Path> pending() throws IOException {
        Path directory = resolve(".cleanup");
        if (!Files.exists(directory)) return List.of();
        try (var stream = Files.list(directory)) { return stream.filter(Files::isRegularFile).toList(); }
    }
    public void remove(String relative) throws IOException { Files.deleteIfExists(resolve(relative)); }
}
