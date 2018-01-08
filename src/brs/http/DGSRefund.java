package brs.http;

import brs.*;
import brs.services.ParameterService;
import brs.util.Convert;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static brs.http.JSONResponses.*;
import static brs.http.common.Parameters.PURCHASE_PARAMETER;
import static brs.http.common.Parameters.REFUND_NQT_PARAMETER;

public final class DGSRefund extends CreateTransaction {

  private final ParameterService parameterService;

  DGSRefund(ParameterService parameterService, TransactionProcessor transactionProcessor) {
    super(new APITag[] {APITag.DGS, APITag.CREATE_TRANSACTION}, parameterService, transactionProcessor, PURCHASE_PARAMETER, REFUND_NQT_PARAMETER);
    this.parameterService = parameterService;
  }

  @Override
  JSONStreamAware processRequest(HttpServletRequest req) throws BurstException {

    Account sellerAccount = parameterService.getSenderAccount(req);
    DigitalGoodsStore.Purchase purchase = ParameterParser.getPurchase(req);
    if (sellerAccount.getId() != purchase.getSellerId()) {
      return INCORRECT_PURCHASE;
    }
    if (purchase.getRefundNote() != null) {
      return DUPLICATE_REFUND;
    }
    if (purchase.getEncryptedGoods() == null) {
      return GOODS_NOT_DELIVERED;
    }

    String refundValueNQT = Convert.emptyToNull(req.getParameter(REFUND_NQT_PARAMETER));
    long refundNQT = 0;
    try {
      if (refundValueNQT != null) {
        refundNQT = Long.parseLong(refundValueNQT);
      }
    } catch (RuntimeException e) {
      return INCORRECT_DGS_REFUND;
    }
    if (refundNQT < 0 || refundNQT > Constants.MAX_BALANCE_NQT) {
      return INCORRECT_DGS_REFUND;
    }

    Account buyerAccount = Account.getAccount(purchase.getBuyerId());

    Attachment attachment = new Attachment.DigitalGoodsRefund(purchase.getId(), refundNQT);
    return createTransaction(req, sellerAccount, buyerAccount.getId(), 0, attachment);

  }

}
