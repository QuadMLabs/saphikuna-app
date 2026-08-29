package com.quadmlabs.saphikuna.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.quadmlabs.saphikuna.entity.Product;
import com.quadmlabs.saphikuna.entity.Category;
import com.quadmlabs.saphikuna.entity.Collection;
import com.quadmlabs.saphikuna.entity.Size;
import com.quadmlabs.saphikuna.service.ProductService;
import com.quadmlabs.saphikuna.service.SizeService;
import com.quadmlabs.saphikuna.service.CategoryService;
import com.quadmlabs.saphikuna.service.CollectionService;

import org.springframework.ui.Model;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class ProductController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final CollectionService collectionService;
    private final SizeService sizeService;

    public ProductController(ProductService productService, CategoryService categoryService,
            CollectionService collectionService, SizeService sizeService) {
        this.productService = productService;
        this.categoryService = categoryService;
        this.collectionService = collectionService;
        this.sizeService = sizeService;
    }

    @GetMapping("/products")
    public String products(Model model) {

        List<Product> products = productService.findAll();
        List<Category> categories = categoryService.findAll();
        List<Collection> collections = collectionService.findAll();
        List<Size> sizes = sizeService.findAll();

        List<String> statuses = products.stream()
                .map(Product::getStatus)
                .filter(status -> status != null && !status.isBlank())
                .distinct()
                .sorted()
                .toList();

        model.addAttribute("products", products);
        model.addAttribute("categories", categories);
        model.addAttribute("collections", collections);
        model.addAttribute("sizes", sizes);
        model.addAttribute("statuses", statuses);

        return "admin/products";
    }

}