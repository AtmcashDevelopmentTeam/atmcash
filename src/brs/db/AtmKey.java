package brs.db;

import org.jooq.Record;

public interface AtmKey {

  interface Factory<T> {
    AtmKey newKey(T t);

    AtmKey newKey(Record rs);
  }

  long[] getPKValues();

  interface LongKeyFactory<T> extends Factory<T> {
    @Override
    AtmKey newKey(Record rs);

    AtmKey newKey(long id);

  }

  interface LinkKeyFactory<T> extends Factory<T> {
    AtmKey newKey(long idA, long idB);
  }
}
