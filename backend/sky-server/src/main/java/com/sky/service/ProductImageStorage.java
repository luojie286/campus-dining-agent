package com.sky.service;

import com.sky.exception.BadRequestException;
import com.sky.exception.NotFoundException;
import com.sky.vo.ProductImageVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Profile("!bootstrap")
public class ProductImageStorage {

    public static final String PUBLIC_PATH = "/api/media/product-images/";
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final long MAX_PIXELS = 32_000_000L;
    private static final Pattern FILENAME = Pattern.compile("[0-9a-f]{32}\\.(jpg|png|webp)");

    private final Path directory;

    public ProductImageStorage(@Value("${sky.dish.image-directory}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public ProductImageVO store(MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new BadRequestException("请选择不超过5 MiB的图片");
        }

        byte[] bytes = file.getBytes();
        String extension = validateAndIdentify(bytes);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Files.createDirectories(directory);

        // 校验通过后才写入，完整文件就绪后再让读取接口看到它。
        Path temporary = Files.createTempFile(directory, "upload-", ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, directory.resolve(filename));
        } finally {
            Files.deleteIfExists(temporary);
        }

        return new ProductImageVO(PUBLIC_PATH + filename, filename, bytes.length);
    }

    public boolean contains(String filename) {
        return FILENAME.matcher(filename).matches()
                && Files.isRegularFile(directory.resolve(filename), LinkOption.NOFOLLOW_LINKS);
    }

    public FileSystemResource read(String filename) {
        if (!contains(filename)) {
            throw new NotFoundException("商品图片不存在");
        }
        return new FileSystemResource(directory.resolve(filename));
    }

    private String validateAndIdentify(byte[] bytes) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BadRequestException("仅支持JPEG、PNG和WebP图片");
            }

            ImageReader reader = readers.next();
            try {
                String extension = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "jpeg", "jpg" -> "jpg";
                    case "png" -> "png";
                    case "webp" -> "webp";
                    default -> throw new BadRequestException("仅支持JPEG、PNG和WebP图片");
                };
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 8192 || height > 8192
                        || (long) width * height > MAX_PIXELS) {
                    throw new BadRequestException("图片像素尺寸过大");
                }

                // 实际解码可以拦住仅改了扩展名或内容损坏的文件。
                reader.read(0).flush();
                return extension;
            } finally {
                reader.dispose();
            }
        } catch (IOException | IndexOutOfBoundsException | IllegalArgumentException exception) {
            throw new BadRequestException("图片无法解码，请重新选择");
        }
    }
}
