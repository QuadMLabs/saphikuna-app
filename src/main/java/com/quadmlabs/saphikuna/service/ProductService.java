package com.quadmlabs.saphikuna.service;

import com.quadmlabs.saphikuna.dto.ProductRequest;
import com.quadmlabs.saphikuna.entity.Product;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductService {

    List<Product> findAll();

    Product saveProduct(
            ProductRequest request,
            List<MultipartFile> images,
            int primaryImageIndex);
}