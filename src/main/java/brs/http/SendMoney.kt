package brs.http

import brs.Account
import brs.BurstException
import brs.DependencyProvider
import brs.services.ParameterService
import com.google.gson.JsonElement

import javax.servlet.http.HttpServletRequest

import brs.http.common.Parameters.AMOUNT_NQT_PARAMETER
import brs.http.common.Parameters.RECIPIENT_PARAMETER

internal class SendMoney(private val dp: DependencyProvider) : CreateTransaction(dp, arrayOf(APITag.ACCOUNTS, APITag.CREATE_TRANSACTION), RECIPIENT_PARAMETER, AMOUNT_NQT_PARAMETER) {

    @Throws(BurstException::class)
    internal override fun processRequest(req: HttpServletRequest): JsonElement {
        val recipient = ParameterParser.getRecipientId(req)
        val amountNQT = ParameterParser.getAmountNQT(req)
        val account = dp.parameterService.getSenderAccount(req)
        return createTransaction(req, account, recipient, amountNQT)
    }

}
