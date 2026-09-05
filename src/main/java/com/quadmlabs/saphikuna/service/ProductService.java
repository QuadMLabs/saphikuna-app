package com.quadmlabs.saphikuna.service;

import com.quadmlabs.saphikuna.dto.ProductRequest;
import com.quadmlabs.saphikuna.entity.Product;
import com.quadmlabs.saphikuna.entity.ProductVariant;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface ProductService {

    List<Product> findAll();

    Product saveProduct(
            ProductRequest request,
            List<MultipartFile> images,
            int primaryImageIndex);

    Product findById(UUID productId);

    List<ProductVariant> findVariantsByProduct(Product product);
}