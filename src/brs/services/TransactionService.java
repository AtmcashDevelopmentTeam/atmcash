package brs.services;

import brs.AtmException;
import brs.Transaction;

public interface TransactionService {

  boolean verifyPublicKey(Transaction transaction);

  void validate(Transaction transaction) throws AtmException.ValidationException;

  boolean applyUnconfirmed(Transaction transaction);

  void apply(Transaction transaction);

  void undoUnconfirmed(Transaction transaction);
}
