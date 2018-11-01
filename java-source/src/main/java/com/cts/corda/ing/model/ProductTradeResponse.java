package com.cts.corda.ing.model;

import net.corda.core.serialization.CordaSerializable;

@CordaSerializable
public class ProductTradeResponse {

    private String toPartyName;
    private String productName;
    private int quantity;
    private int amount;

    public ProductTradeResponse(String toPartyName, String productName, int quantity, int amount) {
        this.toPartyName = toPartyName;
        this.productName = productName;
        this.quantity = quantity;
        this.amount = amount;
    }

    public String getToPartyName() {
        return toPartyName;
    }

    public void setToPartyName(String toPartyName) {
        this.toPartyName = toPartyName;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

}
