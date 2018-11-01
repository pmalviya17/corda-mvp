package com.cts.corda.ing.flows;


import net.corda.core.identity.Party;
import net.corda.core.serialization.CordaSerializable;
import net.corda.core.utilities.OpaqueBytes;
import com.cts.corda.ing.obligation.ProductAsset;

@CordaSerializable
class ProductIssueRequest {

    ProductAsset productAsset;
    OpaqueBytes issueRef;
    Party notary;

    public ProductIssueRequest(ProductAsset productAsset,
                               OpaqueBytes issueRef,
                               Party notary) {
        super();
        this.productAsset = productAsset;
        this.issueRef = issueRef;
        this.notary = notary;

    }
}


