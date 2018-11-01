package com.cts.corda.ing.rest;

import com.cts.corda.ing.state.ProductTradeState;

import java.util.List;

public class ProductBalanceResponse {

    String productName;
    String quantity;
    List<ProductTradeState> productTradeStates;

    public ProductBalanceResponse(List<ProductTradeState> productTradeStates) {
        this.productTradeStates = productTradeStates;
    }

    public List<ProductTradeState> getProductTradeStates() {
        return productTradeStates;
    }

    public void setProductTradeStates(List<ProductTradeState> productTradeStates) {
        this.productTradeStates = productTradeStates;
    }
}
