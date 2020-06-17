package brs.db.store;

import brs.Asset;
import brs.db.AtmKey;
import brs.db.sql.EntitySqlTable;

import java.util.Collection;

public interface AssetStore {
  AtmKey.LongKeyFactory<Asset> getAssetDbKeyFactory();

  EntitySqlTable<Asset> getAssetTable();

  Collection<Asset> getAssetsIssuedBy(long accountId, int from, int to);
}
