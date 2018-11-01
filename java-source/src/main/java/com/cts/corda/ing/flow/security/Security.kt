package com.cts.corda.ing.flow.security

import net.corda.core.contracts.TokenizableAssetInfo
import net.corda.core.serialization.CordaSerializable
import java.math.BigDecimal

@CordaSerializable
data class Security(val commodityCode: String,
                    val displayName: String,
                    val defaultFractionDigits: Int = 0) : TokenizableAssetInfo {
    override val displayTokenSize: BigDecimal
        get() = BigDecimal.ONE.scaleByPowerOfTen(-defaultFractionDigits)

    companion object {
        private val registry = mapOf(
                // Simple example commodity, as in http://www.investopedia.com/university/commodities/commodities14.asp
                Pair("FCOJ", Security("FCOJ", "Frozen concentrated orange juice"))
        )

        fun getInstance(commodityCode: String): Security? = registry[commodityCode]
    }
}