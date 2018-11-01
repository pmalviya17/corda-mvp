package com.cts.corda.ing.model;

import net.corda.core.serialization.CordaSerializable;

@CordaSerializable
public class ProductTradeRequest {

    private String toPartyName;
    private String productName;
    private Long quantity;
    private Long amount;
    private TradeType tradeType;

    public ProductTradeRequest(String toPartyName, String productName, Long quantity, Long amount, TradeType tradeType) {
        this.toPartyName = toPartyName;
        this.productName = productName;
        this.quantity = quantity;
        this.amount = amount;
        this.tradeType = tradeType;
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

    public Long getQuantity() {
        return quantity;
    }

    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public TradeType getTradeType() {
        return tradeType;
    }

    public void setTradeType(TradeType tradeType) {
        this.tradeType = tradeType;
    }
}
