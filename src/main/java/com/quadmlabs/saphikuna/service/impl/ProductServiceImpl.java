package com.quadmlabs.saphikuna.service.impl;

import com.quadmlabs.saphikuna.dto.ProductRequest;
import com.quadmlabs.saphikuna.dto.ProductVariantRequest;
import com.quadmlabs.saphikuna.entity.Color;
import com.quadmlabs.saphikuna.entity.Size;
import com.quadmlabs.saphikuna.entity.Product;
import com.quadmlabs.saphikuna.entity.ProductCollection;
import com.quadmlabs.saphikuna.entity.ProductCollectionId;
import com.quadmlabs.saphikuna.entity.Category;
import com.quadmlabs.saphikuna.entity.Collection;
import com.quadmlabs.saphikuna.entity.ProductVariant;
import com.quadmlabs.saphikuna.repository.ProductRepository;
import com.quadmlabs.saphikuna.repository.ProductVariantRepository;
import com.quadmlabs.saphikuna.repository.CategoryRepository;
import com.quadmlabs.saphikuna.repository.CollectionRepository;
import com.quadmlabs.saphikuna.repository.ColorRepository;
import com.quadmlabs.saphikuna.repository.ProductCollectionRepository;
import com.quadmlabs.saphikuna.repository.SizeRepository;
import com.quadmlabs.saphikuna.service.ProductService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public ProductServiceImpl(
            ProductRepository productRepository,
            ColorRepository colorRepository,
            SizeRepository sizeRepository,
            CategoryRepository categoryRepository,
            CollectionRepository collectionRepository,
            ProductCollectionRepository productCollectionRepository,
            ProductVariantRepository productVariantRepository) {
        this.productRepository = productRepository;
        this.colorRepository = colorRepository;
        this.sizeRepository = sizeRepository;
        this.categoryRepository = categoryRepository;
        this.collectionRepository = collectionRepository;
        this.productCollectionRepository = productCollectionRepository;
        this.productVariantRepository = productVariantRepository;
    }

    @Override
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    @Override
    @Transactional
    public Product saveProduct(ProductRequest request) {

        OffsetDateTime now = OffsetDateTime.now();

        /*
         * =====================================================
         * PRODUCT
         * =====================================================
         */

        Product product = new Product();

        product.setProductId(UUID.randomUUID());
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

        /*
         * =====================================================
         * SAVE PRODUCT
         * =====================================================
         */

        Product savedProduct = productRepository.save(product);

        /*
         * =====================================================
         * COLLECTIONS
         * =====================================================
         */

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

        /*
         * =====================================================
         * VARIANTS
         * =====================================================
         */

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

                /*
                 * Guardamos directamente la variante.
                 * No dependemos de cascade.
                 */
                productVariantRepository.save(variant);
            }
        }

        return savedProduct;
    }
}