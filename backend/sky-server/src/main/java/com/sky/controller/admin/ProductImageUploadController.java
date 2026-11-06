package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.EmployeePermissionService;
import com.sky.service.ProductImageStorage;
import com.sky.vo.ProductImageVO;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@Profile("!bootstrap")
public class ProductImageUploadController {

    private final EmployeePermissionService permissionService;
    private final ProductImageStorage imageStorage;

    public ProductImageUploadController(
            EmployeePermissionService permissionService,
            ProductImageStorage imageStorage) {
        this.permissionService = permissionService;
        this.imageStorage = imageStorage;
    }

    @PostMapping(value = "/api/admin/common/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ProductImageVO> upload(@RequestParam("file") MultipartFile file) throws IOException {
        permissionService.requireOperationsAccess();
        return Result.success(imageStorage.store(file));
    }
}
