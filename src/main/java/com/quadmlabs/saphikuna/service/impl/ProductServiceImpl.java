package com.quadmlabs.saphikuna.service.impl;

import com.quadmlabs.saphikuna.entity.Product;
import com.quadmlabs.saphikuna.repository.ProductRepository;
import com.quadmlabs.saphikuna.service.ProductService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public List<Product> findAll() {
        return productRepository.findAll();
    }
}