package com.quadmlabs.saphikuna.service.impl;

import com.quadmlabs.saphikuna.dto.ProductRequest;
import com.quadmlabs.saphikuna.dto.ProductVariantRequest;
import com.quadmlabs.saphikuna.entity.Category;
import com.quadmlabs.saphikuna.entity.Collection;
import com.quadmlabs.saphikuna.entity.Color;
import com.quadmlabs.saphikuna.entity.Product;
import com.quadmlabs.saphikuna.entity.ProductCollection;
import com.quadmlabs.saphikuna.entity.ProductCollectionId;
import com.quadmlabs.saphikuna.entity.ProductImage;
import com.quadmlabs.saphikuna.entity.ProductVariant;
import com.quadmlabs.saphikuna.entity.Size;
import com.quadmlabs.saphikuna.repository.CategoryRepository;
import com.quadmlabs.saphikuna.repository.CollectionRepository;
import com.quadmlabs.saphikuna.repository.ColorRepository;
import com.quadmlabs.saphikuna.repository.ProductCollectionRepository;
import com.quadmlabs.saphikuna.repository.ProductImageRepository;
import com.quadmlabs.saphikuna.repository.ProductRepository;
import com.quadmlabs.saphikuna.repository.ProductVariantRepository;
import com.quadmlabs.saphikuna.repository.SizeRepository;
import com.quadmlabs.saphikuna.service.ProductService;
import com.quadmlabs.saphikuna.service.StorageService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ColorRepository colorRepository;
    private final SizeRepository sizeRepository;
    private final CategoryRepository categoryRepository;
    private final CollectionRepository collectionRepository;
    private final ProductCollectionRepository productCollectionRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final StorageService storageService;

    public ProductServiceImpl(
            ProductRepository productRepository,
            ColorRepository colorRepository,
            SizeRepository sizeRepository,
            CategoryRepository categoryRepository,
            CollectionRepository collectionRepository,
            ProductCollectionRepository productCollectionRepository,
            ProductVariantRepository productVariantRepository,
            ProductImageRepository productImageRepository,
            StorageService storageService) {

        this.productRepository = productRepository;
        this.colorRepository = colorRepository;
        this.sizeRepository = sizeRepository;
        this.categoryRepository = categoryRepository;
        this.collectionRepository = collectionRepository;
        this.productCollectionRepository = productCollectionRepository;
        this.productVariantRepository = productVariantRepository;
        this.productImageRepository = productImageRepository;
        this.storageService = storageService;
    }

    @Override
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    @Override
    @Transactional
    public Product saveProduct(
            ProductRequest request,
            List<MultipartFile> images,
            int primaryImageIndex) {

        OffsetDateTime now = OffsetDateTime.now();

        Product product;

        if (request.getProductId() != null) {

            product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Producto no encontrado"));

        } else {

            product = new Product();

            product.setProductId(UUID.randomUUID());
            product.setCreatedAt(now);
        }

        // product.setProductId(UUID.randomUUID());
        product.setName(request.getName());
        product.setGeneralPrice(request.getGeneralPrice());
        product.setDescription(request.getDescription());
        product.setStatus(request.getStatus());
        product.setCreatedAt(now);
        product.setUpdatedAt(now);

        if (request.getCategoryId() != null) {

            Category category = categoryRepository
                    .findById(request.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Categoría no encontrada"));

            product.setCategory(category);
        }

        Product savedProduct = productRepository.save(product);

        if (request.getCollectionIds() != null) {

            for (UUID collectionId : request.getCollectionIds()) {

                Collection collection = collectionRepository
                        .findById(collectionId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Colección no encontrada"));

                ProductCollectionId id = new ProductCollectionId();

                id.setProductId(savedProduct.getProductId());
                id.setCollectionId(collection.getCollectionId());

                ProductCollection productCollection = new ProductCollection();

                productCollection.setId(id);
                productCollection.setProduct(savedProduct);
                productCollection.setCollection(collection);

                productCollectionRepository.save(productCollection);
            }
        }

        if (request.getVariants() == null ||
                request.getVariants().isEmpty()) {

            throw new IllegalArgumentException(
                    "El producto debe tener al menos una variante");
        }

        for (ProductVariantRequest variantRequest : request.getVariants()) {

            Color color = colorRepository
                    .findById(variantRequest.getColorId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Color no encontrado"));

            for (UUID sizeId : variantRequest.getSizeIds()) {

                Size size = sizeRepository
                        .findById(sizeId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Talla no encontrada"));

                ProductVariant variant = new ProductVariant();

                variant.setProductVariantId(UUID.randomUUID());
                variant.setProduct(savedProduct);
                variant.setColor(color);
                variant.setSize(size);
                variant.setStock(variantRequest.getStock());

                if (variantRequest.isDifferentPrice()) {
                    variant.setPrice(variantRequest.getPrice());
                } else {
                    variant.setPrice(null);
                }

                variant.setAvailability(
                        variantRequest.getStock() > 0
                                ? "AVAILABLE"
                                : "OUT_OF_STOCK");

                variant.setCreatedAt(now);
                variant.setUpdatedAt(now);

                productVariantRepository.save(variant);
            }
        }

        List<UUID> existingImageIds = request.getExistingImageIds() != null
                ? request.getExistingImageIds()
                : List.of();

        List<ProductImage> existingImages = productImageRepository
                .findByProductOrderBySortOrderAsc(product);

        for (ProductImage existingImage : existingImages) {

            if (!existingImageIds.contains(
                    existingImage.getProductImageId())) {

                storageService.delete(
                        existingImage.getStoragePath());

                productImageRepository.delete(existingImage);
            }
        }

        if (images != null && !images.isEmpty()) {

            if (primaryImageIndex < 0 ||
                    primaryImageIndex >= images.size()) {

                primaryImageIndex = 0;
            }

            String primaryImagePath = null;
            short sortOrder = 0;

            for (int i = 0; i < images.size(); i++) {

                MultipartFile image = images.get(i);

                if (image == null || image.isEmpty()) {
                    continue;
                }

                String path = "products/"
                        + savedProduct.getProductId()
                        + "/"
                        + UUID.randomUUID()
                        + ".webp";

                storageService.upload(image, path);

                ProductImage productImage = new ProductImage();

                productImage.setProductImageId(UUID.randomUUID());
                productImage.setProduct(savedProduct);
                productImage.setStoragePath(path);
                productImage.setSortOrder(sortOrder);
                productImage.setCreatedAt(now);

                productImageRepository.save(productImage);

                if (i == primaryImageIndex) {
                    primaryImagePath = path;
                }

                sortOrder++;
            }

            if (primaryImagePath == null && sortOrder > 0) {
                primaryImagePath = getFirstImagePath(savedProduct);
            }

            savedProduct.setPrimaryImagePath(primaryImagePath);

            productRepository.save(savedProduct);
        }

        return savedProduct;
    }

    private String getFirstImagePath(Product product) {

        return productImageRepository
                .findByProductOrderBySortOrderAsc(product)
                .stream()
                .findFirst()
                .map(ProductImage::getStoragePath)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Product findById(UUID productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

        // Forzar la carga de las relaciones LAZY
        product.getProductVariants().size();
        product.getProductCollections().size();
        product.getProductImages().size();

        return product;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariant> findVariantsByProduct(Product product) {
        return product.getProductVariants();
    }
}