package com.quadmlabs.saphikuna.tools;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Un archivo del disco presentado como MultipartFile, para reutilizar el flujo normal de fotos. */
class FileMultipartFile implements MultipartFile {

    private final Path path;
    private final byte[] bytes;

    FileMultipartFile(Path path) throws IOException {
        this.path = path;
        this.bytes = Files.readAllBytes(path);
    }

    @Override
    public String getName() {
        return "newImages";
    }

    @Override
    public String getOriginalFilename() {
        return path.getFileName().toString();
    }

    @Override
    public String getContentType() {
        try {
            String type = Files.probeContentType(path);
            return type != null ? type : "image/jpeg";
        } catch (IOException e) {
            return "image/jpeg";
        }
    }

    @Override
    public boolean isEmpty() {
        return bytes.length == 0;
    }

    @Override
    public long getSize() {
        return bytes.length;
    }

    @Override
    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public void transferTo(File dest) throws IOException {
        Files.write(dest.toPath(), bytes);
    }
}
