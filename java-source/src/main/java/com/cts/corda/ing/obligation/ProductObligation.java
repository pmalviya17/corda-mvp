package com.cts.corda.ing.obligation;

import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.serialization.CordaSerializable;

import java.security.PublicKey;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static net.corda.core.utilities.EncodingUtils.toBase58String;

@CordaSerializable
public class ProductObligation implements LinearState {

    private final ProductAsset productAsset;
    private final AbstractParty lender;
    private final AbstractParty borrower;

    private final Amount<Currency> amount;

    private final UniqueIdentifier linearId;

    public ProductObligation(ProductAsset productAsset, AbstractParty lender, AbstractParty borrower, UniqueIdentifier linearId) {
        this.productAsset = productAsset;
        this.amount = null;
        this.lender = lender;
        this.borrower = borrower;
        this.linearId = linearId;
    }

    public ProductObligation(Amount<Currency> amount, AbstractParty lender, AbstractParty borrower, UniqueIdentifier linearId) {
        this.amount = amount;
        this.productAsset = null;
        this.lender = lender;
        this.borrower = borrower;
        this.linearId = linearId;
    }


    public ProductObligation(ProductAsset eftAsset, AbstractParty lender, AbstractParty borrower, Amount<Currency> amount) {
        this.productAsset = eftAsset;
        this.amount = amount;
        this.lender = lender;
        this.borrower = borrower;
        this.linearId = new UniqueIdentifier();
    }

    public ProductAsset getProductAsset() {
        return productAsset;
    }

    public Amount<Currency> getAmount() {
        return amount;
    }

    public AbstractParty getLender() {
        return lender;
    }

    public AbstractParty getBorrower() {
        return borrower;
    }


    @Override
    public UniqueIdentifier getLinearId() {
        return linearId;
    }

    @Override
    public List<AbstractParty> getParticipants() {
        return ImmutableList.of(lender, borrower);
    }

    public ProductObligation pay(Amount<Currency> amountToPay) {
        return new ProductObligation(
                this.productAsset,
                this.lender,
                this.borrower,
                this.linearId
        );
    }

    public ProductObligation pay(ProductAsset productAsset) {
        return new ProductObligation(
                this.amount,
                this.lender,
                this.borrower,
                this.linearId
        );
    }

    public List<PublicKey> getParticipantKeys() {
        return getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        String lenderString;
        if (this.lender instanceof Party) {
            lenderString = ((Party) lender).getName().getOrganisation();
        } else {
            PublicKey lenderKey = this.lender.getOwningKey();
            lenderString = toBase58String(lenderKey);
        }

        String borrowerString;
        if (this.borrower instanceof Party) {
            borrowerString = ((Party) borrower).getName().getOrganisation();
        } else {
            PublicKey borrowerKey = this.borrower.getOwningKey();
            borrowerString = toBase58String(borrowerKey);
        }

        return String.format("ProductObligation(%s): %s owes %s %s and has returned stock %s.",
                this.linearId, borrowerString, lenderString, this.amount, this.productAsset);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ProductObligation)) {
            return false;
        }
        ProductObligation other = (ProductObligation) obj;

        if (amount == null && productAsset == null) return false;

        boolean amountEq = (amount != null) ? (amount.equals(other.getAmount())) : true;
        boolean productAssetEq = (productAsset != null) ? productAsset.equals(other.getProductAsset()) : true;

        return amountEq
                && lender.equals(other.getLender())
                && borrower.equals(other.getBorrower())
                && productAssetEq
                && linearId.equals(other.getLinearId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, lender, borrower, productAsset, linearId);
    }
}