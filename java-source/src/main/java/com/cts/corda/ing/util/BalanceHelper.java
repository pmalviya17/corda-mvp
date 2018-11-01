package com.cts.corda.ing.util;

import com.cts.corda.ing.state.ProductTradeState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.node.ServiceHub;

import java.util.ArrayList;
import java.util.List;

public class BalanceHelper {

    public List<com.cts.corda.ing.state.ProductTradeState> getBalance(ServiceHub serviceHub, String type) {
        System.out.print("checking balance for " + type);
        List<StateAndRef<ProductTradeState>> productTradeStatesQueryResp = serviceHub.getVaultService().queryBy(ProductTradeState.class).getStates();
        List<ProductTradeState> productTradeStates = new ArrayList<>();
        for (StateAndRef<ProductTradeState> stateAndRef : productTradeStatesQueryResp
                ) {
            ProductTradeState productTradeState = stateAndRef.getState().getData();
            if (productTradeState.getTradeType().equals(type)) {
                productTradeStates.add(stateAndRef.getState().getData());
            }
        }
        System.out.print("balance for " + type + " is " + productTradeStates.size());
        return productTradeStates;
    }
}

