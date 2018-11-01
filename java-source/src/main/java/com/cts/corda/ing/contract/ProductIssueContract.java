package com.cts.corda.ing.contract;

import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.contracts.TypeOnlyCommandData;
import net.corda.core.transactions.LedgerTransaction;

public class ProductIssueContract implements Contract {

    public static final String SELF_ISSUE_PRODUCT_CONTRACT_ID = "com.cts.corda.ing.contract.ProductIssueContract";

    @Override
    public void verify(LedgerTransaction tx) {
        System.out.println("Inside contract. inputs " + tx.getInputs());
        System.out.println("Inside contract. outputs " + tx.getOutputs());
    }

    public interface Commands extends CommandData {

        class SelfIssueProduct extends TypeOnlyCommandData implements ProductIssueContract.Commands {

        }

        class ProductBuyCommand extends TypeOnlyCommandData implements ProductIssueContract.Commands {

        }


    }


}