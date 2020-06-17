package brs.db.store;

import brs.Alias;
import brs.db.AtmKey;
import brs.db.VersionedEntityTable;

import java.util.Collection;

public interface AliasStore {
  AtmKey.LongKeyFactory<Alias> getAliasDbKeyFactory();
  AtmKey.LongKeyFactory<Alias.Offer> getOfferDbKeyFactory();

  VersionedEntityTable<Alias> getAliasTable();

  VersionedEntityTable<Alias.Offer> getOfferTable();

  Collection<Alias> getAliasesByOwner(long accountId, int from, int to);

  Alias getAlias(String aliasName);
}
