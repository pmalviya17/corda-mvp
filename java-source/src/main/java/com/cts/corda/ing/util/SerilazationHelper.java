package com.cts.corda.ing.util;

import com.cts.corda.ing.model.ProductTradeRequest;
import com.cts.corda.ing.model.ProductTradeResponse;
import com.cts.corda.ing.state.ProductTradeState;
import net.corda.core.flows.FlowException;
import net.corda.core.utilities.UntrustworthyData;

public class SerilazationHelper {

    public static ProductTradeResponse getProductTradeResponse(UntrustworthyData<ProductTradeResponse> output) throws FlowException {
        return output.unwrap(new UntrustworthyData.Validator<ProductTradeResponse, ProductTradeResponse>() {
            @Override
            public ProductTradeResponse validate(ProductTradeResponse data) throws FlowException {
                return data;
            }
        });
    }

    public static ProductTradeRequest getProductTradeRequest(UntrustworthyData<ProductTradeRequest> inputFromAP) throws FlowException {
        return inputFromAP.unwrap(new UntrustworthyData.Validator<ProductTradeRequest, ProductTradeRequest>() {
            @Override
            public ProductTradeRequest validate(ProductTradeRequest data) throws FlowException {
                System.out.println("**In validate method for custodian flow received data " + data);
                return data;
            }
        });
    }


    public static ProductTradeState getProductTradeState(UntrustworthyData<ProductTradeState> output) throws FlowException {
        return output.unwrap(new UntrustworthyData.Validator<ProductTradeState, ProductTradeState>() {
            @Override
            public ProductTradeState validate(ProductTradeState data) throws FlowException {
                return data;
            }
        });
    }

}
