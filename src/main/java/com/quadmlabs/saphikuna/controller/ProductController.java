package com.quadmlabs.saphikuna.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.quadmlabs.saphikuna.entity.Product;
import com.quadmlabs.saphikuna.service.ProductService;

import org.springframework.ui.Model;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/products")
    public String products(Model model) {

        List<Product> products = productService.findAll();

        System.out.println("Productos encontrados: " + products.size());

        products.forEach(product -> System.out.println(
                product.getProductId() + " - " + product.getName()));

        return "admin/products";
    }
}