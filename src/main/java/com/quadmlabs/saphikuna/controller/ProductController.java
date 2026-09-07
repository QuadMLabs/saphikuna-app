package com.quadmlabs.saphikuna.controller;

import com.quadmlabs.saphikuna.dto.ProductRequest;
import com.quadmlabs.saphikuna.entity.Category;
import com.quadmlabs.saphikuna.entity.Collection;
import com.quadmlabs.saphikuna.entity.Color;
import com.quadmlabs.saphikuna.entity.Product;
import com.quadmlabs.saphikuna.entity.ProductVariant;
import com.quadmlabs.saphikuna.entity.Size;
import com.quadmlabs.saphikuna.service.CategoryService;
import com.quadmlabs.saphikuna.service.CollectionService;
import com.quadmlabs.saphikuna.service.ColorService;
import com.quadmlabs.saphikuna.service.ProductService;
import com.quadmlabs.saphikuna.service.SizeService;
import com.quadmlabs.saphikuna.service.StorageService;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
public class ProductController {

        private final ProductService productService;
        private final CategoryService categoryService;
        private final CollectionService collectionService;
        private final SizeService sizeService;
        private final ColorService colorService;
        private final StorageService storageService;

        public ProductController(
                        ProductService productService,
                        CategoryService categoryService,
                        CollectionService collectionService,
                        SizeService sizeService,
                        ColorService colorService,
                        StorageService storageService) {

                this.productService = productService;
                this.categoryService = categoryService;
                this.collectionService = collectionService;
                this.sizeService = sizeService;
                this.colorService = colorService;
                this.storageService = storageService;
        }

        @GetMapping("/products/list")
        public String products(Model model) {

                List<Product> products = productService.findAll();
                products.forEach(product -> {
                        if (product.getPrimaryImagePath() != null && !product.getPrimaryImagePath().isBlank()) {
                                product.setPrimaryImagePath(
                                                storageService.getPublicUrl(
                                                                product.getPrimaryImagePath()));
                        }
                });
                List<Category> categories = categoryService.findAll();
                List<Collection> collections = collectionService.findAll();
                List<Size> sizes = sizeService.findAll();
                List<String> statuses = products.stream()
                                .map(Product::getStatus)
                                .filter(status -> status != null &&
                                                !status.isBlank())
                                .distinct()
                                .sorted()
                                .toList();

                model.addAttribute("products", products);
                model.addAttribute("categories", categories);
                model.addAttribute("collections", collections);
                model.addAttribute("sizes", sizes);
                model.addAttribute("statuses", statuses);

                model.addAttribute("activeMenu", "products");
                model.addAttribute("activeSubmenu", "products-list");

                return "admin/products/list";
        }

        @GetMapping("/products/form")
        public String productsNew(Model model) {

                List<Color> colors = colorService.findAll();
                List<Size> sizes = sizeService.findAll();
                List<Category> categories = categoryService.findAll();
                List<Collection> collections = collectionService.findAll();

                model.addAttribute("colors", colors);
                model.addAttribute("sizes", sizes);
                model.addAttribute("categories", categories);
                model.addAttribute("collections", collections);

                model.addAttribute("activeMenu", "products");
                model.addAttribute("activeSubmenu", "products-new");

                return "admin/products/form";
        }

        @PostMapping(value = "/products", consumes = "multipart/form-data")
        @ResponseBody
        public ResponseEntity<Void> saveProduct(
                        @RequestPart("product") ProductRequest request,
                        @RequestPart(value = "images", required = false) List<MultipartFile> images,
                        @RequestParam(value = "primaryImageIndex", defaultValue = "0") int primaryImageIndex) {

                productService.saveProduct(
                                request,
                                images,
                                primaryImageIndex);

                return ResponseEntity.ok().build();
        }

        @GetMapping("/products/form/{id}/edit")
        public String productsEdit(
                        @PathVariable UUID id,
                        Model model) {

                Product product = productService.findById(id);
                product.getProductImages().forEach(image -> image.setPublicUrl(
                                storageService.getPublicUrl(image.getStoragePath())));

                List<ProductVariant> variants = productService.findVariantsByProduct(product);

                model.addAttribute("product", product);
                model.addAttribute("variants", variants);

                model.addAttribute("colors", colorService.findAll());
                model.addAttribute("sizes", sizeService.findAll());
                model.addAttribute("categories", categoryService.findAll());
                model.addAttribute("collections", collectionService.findAll());

                model.addAttribute("activeMenu", "products");
                model.addAttribute("activeSubmenu", "products-new");

                return "admin/products/form";
        }

}