package com.cts.corda.ing.contract;

import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.contracts.TypeOnlyCommandData;
import net.corda.core.transactions.LedgerTransaction;

public class CashIssueContract implements Contract {

    public static final String SELF_ISSUE_CASH_CONTRACT_ID = "com.cts.corda.ing.contract.CashIssueContract";

    @Override
    public void verify(LedgerTransaction tx) {
        System.out.println("Inside contract. inputs " + tx.getInputs());
        System.out.println("Inside contract. outputs " + tx.getOutputs());
    }

    public interface Commands extends CommandData {

        class SelfIssueCash extends TypeOnlyCommandData implements CashIssueContract.Commands {

        }


    }


}