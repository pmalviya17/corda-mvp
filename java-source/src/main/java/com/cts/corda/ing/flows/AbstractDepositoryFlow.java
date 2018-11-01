package com.cts.corda.ing.flows;

import com.cts.corda.ing.state.ProductTradeState;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.FlowSession;

import java.util.HashSet;

abstract public class AbstractDepositoryFlow extends FlowLogic<String> {

    protected static HashSet<ProductTradeState> productTradeBuyRequests = new HashSet<ProductTradeState>(); //
    protected static HashSet<ProductTradeState> productTradeSellRequests = new HashSet<ProductTradeState>(); //
    private FlowSession flowSession;

    public AbstractDepositoryFlow(FlowSession flowSession) {
        this.flowSession = flowSession;
        System.out.println("Inside depositoryflow called by " + flowSession.getCounterparty());
    }

    public FlowSession getFlowSession() {
        return this.flowSession;
    }

}