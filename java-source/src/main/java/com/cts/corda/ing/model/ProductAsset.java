package com.cts.corda.ing.model;


import net.corda.core.serialization.CordaSerializable;

import java.util.Objects;

@CordaSerializable
public class ProductAsset {

    private String productName;
    private Long quantity;

    public ProductAsset(String productName, Long quantity) {
        this.productName = productName;
        this.quantity = quantity;
    }

    public Long getQuantity() {
        return quantity;
    }

    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductAsset productAsset = (ProductAsset) o;
        return quantity == productAsset.quantity &&
                Objects.equals(productName, productAsset.productName);
    }

    @Override
    public int hashCode() {

        return Objects.hash(productName, quantity);
    }

    @Override
    public String toString() {
        return "ProductAsset{" +
                "productName='" + productName + '\'' +
                ", quantity=" + quantity +
                '}';
    }
}
