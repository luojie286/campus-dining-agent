package com.sky;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_DISH_IMAGE_DB_TEST", matches = "true")
class DishImageDatabaseTest {

    private static final String UPLOAD = "/api/admin/common/upload";
    private static final String DISH = "/api/admin/dish";

    @TempDir static Path images;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtil jwt;
    @Autowired ObjectMapper json;

    @DynamicPropertySource
    static void configureStorage(DynamicPropertyRegistry registry) {
        registry.add("sky.dish.image-directory", () -> images.toString());
    }

    @Test
    void uploadsPhotoAndSavesItsUrlThroughCreateAndEdit() throws Exception {
        String token = adminToken();
        byte[] png = png();
        String url = upload(token, png);

        mvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png));

        long categoryId = jdbc.queryForObject(
                "SELECT id FROM category WHERE type=1 ORDER BY id LIMIT 1", Long.class);
        var body = json.createObjectNode()
                .put("id", 0).put("kind", "dish").put("name", "图片验证_" + UUID.randomUUID())
                .put("categoryId", categoryId).put("price", 8)
                .put("description", "").put("status", 0).put("stock", 3)
                .put("art", "chicken").put("image", url);
        body.putArray("composition");
        body.putArray("tags");
        String created = mvc.perform(post(DISH).header("token", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.image").value(url))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).path("data").path("id").asLong();
        assertThat(jdbc.queryForObject("SELECT image FROM dish WHERE id=?", String.class, id / 2))
                .isEqualTo(url);

        byte[] webp = Base64.getDecoder().decode(
                "UklGRh4AAABXRUJQVlA4TBEAAAAvAUAAAAdQvFp0rP+BiOh/AAA=");
        String replacement = upload(token, webp);
        mvc.perform(get(replacement)).andExpect(status().isOk())
                .andExpect(content().contentType("image/webp")).andExpect(content().bytes(webp));
        body.put("id", id).put("image", replacement);
        body.remove("stock");
        mvc.perform(put(DISH).header("token", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.image").value(replacement));
        mvc.perform(get(DISH + "/" + id).header("token", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.image").value(replacement));

        body.put("image", "/api/media/product-images/" + "0".repeat(32) + ".png");
        mvc.perform(put(DISH).header("token", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingInvalidAndOversizeFile() throws Exception {
        String token = adminToken();
        mvc.perform(multipart(UPLOAD).header("token", token)).andExpect(status().isBadRequest());
        mvc.perform(multipart(UPLOAD).file(part("<svg/>".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .header("token", token)).andExpect(status().isBadRequest());
        mvc.perform(multipart(UPLOAD).file(part(new byte[5 * 1024 * 1024 + 1]))
                .header("token", token)).andExpect(status().isBadRequest());
        mvc.perform(multipart(UPLOAD).file(part(png()))).andExpect(status().isUnauthorized());
    }

    private String upload(String token, byte[] image) throws Exception {
        String response = mvc.perform(multipart(UPLOAD).file(part(image)).header("token", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.size").value(image.length))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("url").asText();
    }

    private MockMultipartFile part(byte[] bytes) {
        // 故意提供错误扩展名和 MIME，验证服务按实际图片内容识别。
        return new MockMultipartFile("file", "photo.bin", "application/octet-stream", bytes);
    }

    private String adminToken() {
        Long id = jdbc.queryForObject("SELECT id FROM employee WHERE status=1 ORDER BY id LIMIT 1", Long.class);
        return jwt.createToken(id, "admin");
    }

    private byte[] png() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }
}
