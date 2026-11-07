package com.quadmlabs.saphikuna.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "productCollections")
public class ProductCollection {

    @EmbeddedId
    private ProductCollectionId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("productId")
    @JoinColumn(name = "productId")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("collectionId")
    @JoinColumn(name = "collectionId")
    private Collection collection;

    public ProductCollectionId getId() {
        return id;
    }

    public void setId(ProductCollectionId id) {
        this.id = id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Collection getCollection() {
        return collection;
    }

    public void setCollection(Collection collection) {
        this.collection = collection;
    }
}