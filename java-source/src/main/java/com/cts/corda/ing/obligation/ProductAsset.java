package com.cts.corda.ing.obligation;

import net.corda.core.serialization.CordaSerializable;

import java.util.Objects;

@CordaSerializable
public class ProductAsset {

    private String productName;
    private int quantity;

    public ProductAsset(String productName, int quantity) {
        this.productName = productName;
        this.quantity = quantity;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
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
