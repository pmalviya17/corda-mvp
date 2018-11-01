package com.cts.corda.ing.flows;

import com.cts.corda.ing.state.ProductTradeState;
import net.corda.core.flows.FlowLogic;
import net.corda.core.transactions.SignedTransaction;

import java.util.HashSet;

public abstract class AbstractApFlow extends FlowLogic<SignedTransaction> {
    public static HashSet<ProductTradeState> productTradeBuyRequests = new HashSet<ProductTradeState>(); //
    public static HashSet<ProductTradeState> productTradeSellRequests = new HashSet<ProductTradeState>(); //

}
