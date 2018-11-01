package com.cts.corda.ing.api;

import com.cts.corda.ing.contract.SecurityContract;
import com.cts.corda.ing.contract.SecurityStock;
import com.cts.corda.ing.flows.APBuyProductFLow;
import com.cts.corda.ing.flows.APSellProductFLow;
import com.cts.corda.ing.flows.CashIssueFlow;
import com.cts.corda.ing.flows.ProductIssueFlow;
import com.cts.corda.ing.model.ProductAsset;
import com.cts.corda.ing.model.ProductTradeRequest;
import com.cts.corda.ing.model.TradeType;
import com.cts.corda.ing.state.ProductTradeState;
import com.cts.corda.ing.state.SecurityBuyState;
import com.cts.corda.ing.state.SecuritySellState;
import com.cts.corda.ing.util.RequestHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.ImmutableMap;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.FlowHandle;
import net.corda.core.transactions.SignedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CREATED;

@Path("issue")
public class INGRestApi {

    private static final Logger logger = LoggerFactory.getLogger(INGRestApi.class);
    private final CordaRPCOps rpcOps;
    private final Party myIdentity;

    public INGRestApi(CordaRPCOps rpcOps) {
        this.rpcOps = rpcOps;
        this.myIdentity = rpcOps.nodeInfo().getLegalIdentities().get(0);
    }

    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Party> me() {
        return ImmutableMap.of("me", myIdentity);
    }

    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, List<String>> peers() {
        return ImmutableMap.of("peers", rpcOps.networkMapSnapshot()
                .stream()
                .filter(nodeInfo -> nodeInfo.getLegalIdentities().get(0) != myIdentity)
                .map(it -> it.getLegalIdentities().get(0).getName().getOrganisation())
                .collect(toList()));
    }
    @GET
    @Path("SellRequests")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<SecuritySellState>> getSellRequests() {
        List<StateAndRef<SecuritySellState>> ref =  rpcOps.vaultQuery(SecuritySellState.class).getStates();
        return ref;
    }

    @GET
    @Path("UnMatchedSellRequests")
    @Produces(MediaType.APPLICATION_JSON)
    public List<SecuritySellState> getUnMatchedSellRequests() {
        List<StateAndRef<SecuritySellState>> ref =  rpcOps.vaultQuery(SecuritySellState.class).getStates();
        return RequestHelper.getUnmatchedSecuritySellState(ref);
    }

    @GET
    @Path("UnMatchedBuyRequests")
    @Produces(MediaType.APPLICATION_JSON)
    public List<SecurityBuyState> getUnMatchedBuyRequests() {
        List<StateAndRef<SecurityBuyState>> ref =  rpcOps.vaultQuery(SecurityBuyState.class).getStates();
        return RequestHelper.getUnmatchedSecurityBuyState(ref);
    }

    @GET
    @Path("security-balance")
    @Produces(MediaType.APPLICATION_JSON)
    public Response checkSecurityStockBalance() {
        final List<Party> notaries = rpcOps.notaryIdentities();
        if (notaries.isEmpty()) {
            throw new IllegalStateException("Could not find a notary.");
        }

        try {
            List<StateAndRef<SecurityStock.State>> productTradeStatesQueryResp = rpcOps.vaultQuery(SecurityStock.State.class).getStates();
            Map<String, Long> securityBalanceMap = new HashMap<>();

            for (StateAndRef<SecurityStock.State> stateAndRef : productTradeStatesQueryResp) {
                SecurityStock.State productTradeState = stateAndRef.getState().getData();
                Long quantity = productTradeState.getQuantity();
                if (securityBalanceMap.containsKey(productTradeState.getSecurityName())) {
                    quantity = quantity + securityBalanceMap.get(productTradeState.getSecurityName());
                }
                securityBalanceMap.put(productTradeState.getSecurityName(), quantity);
            }


            for (String key : securityBalanceMap.keySet()) {
                Long val = securityBalanceMap.get(key);
                if (val == 0) {
                    securityBalanceMap.remove(key);
                }
            }

            logger.info("productTradeStates for checkProductBalance size " + securityBalanceMap.size());
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final ObjectMapper mapper = new ObjectMapper();
            mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
            mapper.writeValue(out, securityBalanceMap);
            String json = new String(out.toByteArray());
            logger.info("SecurityTradeStates  json " + json);
            return Response.status(CREATED).entity(json).build();
        } catch (Exception e) {
            return Response.status(BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
    @GET
    @Path("cash-balances")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<Currency, Amount<Currency>> cashBalances() {
        return net.corda.finance.contracts.GetBalances.getCashBalances(rpcOps);
        //return getCashBalances(rpcOps);
    }



    @GET
    @Path("checkProductBalance")
    public Response checkProductBalance() {
        final List<Party> notaries = rpcOps.notaryIdentities();
        if (notaries.isEmpty()) {
            throw new IllegalStateException("Could not find a notary.");
        }
        // 2. Start flow and wait for response.
        try {
            logger.info("query productTradeStates for checkProductBalance");
            List<StateAndRef<ProductTradeState>> productStatesQueryResp = rpcOps.vaultQuery(ProductTradeState.class).getStates();
            List<ProductTradeState> productTradeStates = new ArrayList<>();
            for (StateAndRef<ProductTradeState> stateAndRef : productStatesQueryResp) {
                ProductTradeState productTradeState = stateAndRef.getState().getData();

                if (productTradeState.getTradeType().equals("ISSUEPRODUCT")) {
                    productTradeState.setToParty(null);
                    productTradeState.setFromParty(null);
                    productTradeStates.add(productTradeState);
                }
            }

            logger.info("productTradeStates for checkProductBalance size " + productTradeStates.size());
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final ObjectMapper mapper = new ObjectMapper();
            mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
            mapper.writeValue(out, productTradeStates);
            String json = new String(out.toByteArray());
            logger.info("productTradeStates  json " + json);
            return Response.status(CREATED).entity(json).build();
        } catch (Exception e) {
            return Response.status(BAD_REQUEST).entity(e.getMessage()).build();
        }
    }


    @GET
    @Path("checkCashBalance")
    public Response checkCashBalance() {
        final List<Party> notaries = rpcOps.notaryIdentities();
        if (notaries.isEmpty()) {
            throw new IllegalStateException("Could not find a notary.");
        }
        // 2. Start flow and wait for response.
        try {
            logger.info("query productTradeStates for checkProductBalance");
            List<StateAndRef<ProductTradeState>> productStatesQueryResp = rpcOps.vaultQuery(ProductTradeState.class).getStates();
            List<ProductTradeState> productTradeStates = new ArrayList<>();
            for (StateAndRef<ProductTradeState> stateAndRef : productStatesQueryResp
                    ) {
                ProductTradeState productTradeState = stateAndRef.getState().getData();
                if (productTradeState.getTradeType().equals("ISSUECASH")) {
                    productTradeState.setToParty(null);
                    productTradeState.setFromParty(null);
                    productTradeStates.add(productTradeState);
                }
            }
            logger.info("productTradeStates for checkCashBalance size " + productTradeStates.size());
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final ObjectMapper mapper = new ObjectMapper();
            mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
            mapper.writeValue(out, productTradeStates);
            String json = new String(out.toByteArray());
            logger.info("productTradeStates  json " + json);
            return Response.status(CREATED).entity(json).build();
        } catch (Exception e) {
            return Response.status(BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("self-issue-product")
    public Response selfIssueProduct(
            @QueryParam(value = "quantity") int quantity,
            @QueryParam(value = "productName") String productName) {

        logger.info("quantity::" + quantity);
        logger.info("productName::" + productName);

        final List<Party> notaries = rpcOps.notaryIdentities();
        if (notaries.isEmpty()) {
            throw new IllegalStateException("Could not find a notary.");
        }
        // 2. Start flow and wait for response.
        try {

            rpcOps.startFlowDynamic(ProductIssueFlow.class, new ProductAsset(productName, new Long(quantity)));
            final String msg = rpcOps.vaultQuery(ProductTradeState.class).getStates().get(0).getState().getData().toString();
            return Response.status(CREATED).entity("SUCCESS").build();
        } catch (Exception e) {
            return Response.status(BAD_REQUEST).entity("SUCCESS").build();
        }
    }


    @GET
    @Path("self-issue-cash")
    public Response selfIssueCash(
            @QueryParam(value = "amount") int amount,
            @QueryParam(value = "currency") String currency) {
        final List<Party> notaries = rpcOps.notaryIdentities();
        if (notaries.isEmpty()) {
            throw new IllegalStateException("Could not find a notary.");
        }
        // 2. Start flow and wait for response.
        try {
            rpcOps.startFlowDynamic(CashIssueFlow.class, new Amount<Currency>(amount, Currency.getInstance(currency)));
            final String msg = rpcOps.vaultQuery(ProductTradeState.class).getStates().get(0).getState().getData().toString();
            System.out.println("self issue completed");
            return Response.status(CREATED).entity("SUCCESS").build();
        } catch (Exception e) {
            return Response.status(BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("issue-product-buy-sell")
    public Response issueProductBuySell(
            @QueryParam(value = "buysell") String buysell,
            @QueryParam(value = "counterparty") String counterparty,
            @QueryParam(value = "productName") String productName,
            @QueryParam(value = "currency") String currency,
            @QueryParam(value = "quantity") int quantity,
            @QueryParam(value = "amount") int amount) {

        logger.info("buysell::" + buysell);
        logger.info("counterparty::" + counterparty);
        logger.info("productName::" + productName);
        logger.info("currency::" + currency);
        logger.info("quantity::" + quantity);
        logger.info("amount::" + amount);

        if("BUY".equalsIgnoreCase(buysell)){

            Runnable th = new Runnable() {
                @Override
                public void run() {
                    ProductTradeRequest productTradeRequest = new ProductTradeRequest(counterparty, productName, Long.valueOf(quantity + ""), Long.valueOf(amount + ""), TradeType.BUY);
                    try {
                        logger.info("calling flow-->");
                        final FlowHandle<SignedTransaction> flowHandle = rpcOps.startFlowDynamic(APBuyProductFLow.class, productTradeRequest, "CUSTODIAN1");
                        logger.info("received resp from flow-->");
                        final SignedTransaction result = flowHandle.getReturnValue().get();
                        selfIssueProduct(productTradeRequest.getQuantity().intValue(), productTradeRequest.getProductName());

                        ProductTradeState productRequest1 = (ProductTradeState) result.getTx().getOutput(0);
                        selfIssueCash(- Integer.parseInt(productRequest1.getAmount().getQuantity()+""), productRequest1.getAmount().getToken().getCurrencyCode());
                        System.out.println("transaction to buy/selll completed");
                    } catch (Exception e) {
                        logger.info("",e);
                    }
                }
            };

            new Thread(th).start();
        }else{
            Runnable th = new Runnable() {
                @Override
                public void run() {
                    ProductTradeRequest productTradeRequest = new ProductTradeRequest(counterparty, productName, Long.valueOf(quantity + ""), Long.valueOf(amount + ""), TradeType.SELL);
                    try {
                        final FlowHandle<SignedTransaction> flowHandle = rpcOps.startFlowDynamic(
                                APSellProductFLow.class, productTradeRequest, "CUSTODIAN2");
                        final SignedTransaction result = flowHandle.getReturnValue().get();
                        selfIssueProduct(- productTradeRequest.getQuantity().intValue(), productTradeRequest.getProductName());


                        ProductTradeState productRequest1 = (ProductTradeState) result.getTx().getOutput(0);

                        selfIssueCash(Integer.parseInt(productRequest1.getAmount().getQuantity()+""), productRequest1.getAmount().getToken().getCurrencyCode());
                        System.out.println("transaction to buy/selll completed");

                    } catch (Exception e) {
                        logger.info("",e);
                    }
                }
            };

            new Thread(th).start();
        }

        return Response.status(CREATED).entity("Request placed Successfully").build();
    }



    @GET
    @Path("sell-product-to-party")
    public Response initiateSellProduct(
            @QueryParam(value = "toPartyName") String toPartyName,
            @QueryParam(value = "productName") String productName,
            @QueryParam(value = "quantity") int quantity, @QueryParam(value = "sellamount") int sellAmount) {

        logger.info("initiateSellProduct -->");

        Runnable th = new Runnable() {
            @Override
            public void run() {
                ProductTradeRequest productTradeRequest = new ProductTradeRequest(toPartyName, productName, Long.valueOf(quantity + ""), Long.valueOf(sellAmount + ""), TradeType.SELL);
                try {
                    final FlowHandle<SignedTransaction> flowHandle = rpcOps.startFlowDynamic(
                            APSellProductFLow.class, productTradeRequest, "CUSTODIAN2");
                    final SignedTransaction result = flowHandle.getReturnValue().get();
                    selfIssueProduct(- productTradeRequest.getQuantity().intValue(), productTradeRequest.getProductName());


                    ProductTradeState productRequest1 = (ProductTradeState) result.getTx().getOutput(0);

                    selfIssueCash(Integer.parseInt(productRequest1.getAmount().getQuantity()+""), productRequest1.getAmount().getToken().getCurrencyCode());


                } catch (Exception e) {
                    logger.info("",e);
                }
            }
        };

        new Thread(th).start();

        return Response.status(CREATED).entity("Request sent. Please check later").build();
    }
    @GET
    @Path("buy-product-from-party")
    public Response initiateBuyProduct(
            @QueryParam(value = "toPartyName") String toPartyName,
            @QueryParam(value = "productName") String productName,
            @QueryParam(value = "quantity") int quantity,
            @QueryParam(value = "buyamount") int buyAmount) {
        logger.info("initiateBuyProduct -->");

        Runnable th = new Runnable() {
            @Override
            public void run() {
                ProductTradeRequest productTradeRequest = new ProductTradeRequest(toPartyName, productName, Long.valueOf(quantity + ""), Long.valueOf(buyAmount + ""), TradeType.BUY);
                try {
                    logger.info("calling flow-->");
                    final FlowHandle<SignedTransaction> flowHandle = rpcOps.startFlowDynamic(APBuyProductFLow.class, productTradeRequest, "CUSTODIAN1");
                    logger.info("received resp from flow-->");
                    final SignedTransaction result = flowHandle.getReturnValue().get();
                    selfIssueProduct(productTradeRequest.getQuantity().intValue(), productTradeRequest.getProductName());

                    ProductTradeState productRequest1 = (ProductTradeState) result.getTx().getOutput(0);
                    selfIssueCash(- Integer.parseInt(productRequest1.getAmount().getQuantity()+""), productRequest1.getAmount().getToken().getCurrencyCode());
                } catch (Exception e) {
                    logger.info("",e);
                }
            }
        };

        new Thread(th).start();
        return Response.status(CREATED).entity("Request sent. Please check later").build();
    }


    @GET
    @Path("commodity-balance")
    @Produces(MediaType.APPLICATION_JSON)
    public List checkCommodityBalance() {
        List<StateAndRef<SecurityContract.State>> etfTradeStatesQueryResp = rpcOps.vaultQuery(SecurityContract.State.class).getStates();
        List<SecurityContract.State> securityBalanceMap = new ArrayList<>();
        for (StateAndRef<SecurityContract.State> stateAndRef : etfTradeStatesQueryResp) {
            securityBalanceMap.add(stateAndRef.getState().getData());
        }
        return securityBalanceMap;
    }

}