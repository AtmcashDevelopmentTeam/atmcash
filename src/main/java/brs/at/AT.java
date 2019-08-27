/*
 * Some portion .. Copyright (c) 2014 CIYAM Developers

 Distributed under the MIT/X11 software license, please refer to the file LICENSE.txt
*/

package brs.at;

import brs.*;
import brs.db.BurstKey;
import brs.db.TransactionDb;
import brs.db.VersionedEntityTable;
import brs.services.AccountService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class AT extends AtMachineState {
    private static final LinkedHashMap<Long, Long> pendingFees = new LinkedHashMap<>();
    private static final List<AtTransaction> pendingTransactions = new ArrayList<>();
    public final BurstKey dbKey;
    private final String name;
    private final String description;
    private final int nextHeight;

    private AT(DependencyProvider dp, byte[] atId, byte[] creator, String name, String description, byte[] creationBytes, int height) {
        super(dp, atId, creator, creationBytes, height);
        this.name = name;
        this.description = description;
        dbKey = atDbKeyFactory(dp).newKey(AtApiHelper.getLong(atId));
        this.nextHeight = dp.blockchain.getHeight();
    }

    public AT(DependencyProvider dp, byte[] atId, byte[] creator, String name, String description, short version,
              byte[] stateBytes, int csize, int dsize, int cUserStackBytes, int cCallStackBytes,
              int creationBlockHeight, int sleepBetween, int nextHeight,
              boolean freezeWhenSameBalance, long minActivationAmount, byte[] apCode) {
        super(dp, atId, creator, version,
                stateBytes, csize, dsize, cUserStackBytes, cCallStackBytes,
                creationBlockHeight, sleepBetween,
                freezeWhenSameBalance, minActivationAmount, apCode);
        this.name = name;
        this.description = description;
        dbKey = atDbKeyFactory(dp).newKey(AtApiHelper.getLong(atId));
        this.nextHeight = nextHeight;
    }

    public static void clearPendingFees() {
        pendingFees.clear();
    }

    public static void clearPendingTransactions() {
        pendingTransactions.clear();
    }

    public static void addPendingFee(long id, long fee) {
        pendingFees.put(id, fee);
    }

    public static void addPendingFee(byte[] id, long fee) {
        addPendingFee(AtApiHelper.getLong(id), fee);
    }

    public static void addPendingTransaction(AtTransaction atTransaction) {
        pendingTransactions.add(atTransaction);
    }

    public static boolean findPendingTransaction(byte[] recipientId) {
        for (AtTransaction tx : pendingTransactions) {
            if (Arrays.equals(recipientId, tx.getRecipientId())) {
                return true;
            }
        }
        return false;
    }

    // TODO stop passing dp around, fix this code to be organized properly!!!

    private static BurstKey.LongKeyFactory<AT> atDbKeyFactory(DependencyProvider dp) {
        return dp.atStore.getAtDbKeyFactory();
    }

    private static VersionedEntityTable<AT> atTable(DependencyProvider dp) {
        return dp.atStore.getAtTable();
    }

    private static BurstKey.LongKeyFactory<ATState> atStateDbKeyFactory(DependencyProvider dp) {
        return dp.atStore.getAtStateDbKeyFactory();
    }

    private VersionedEntityTable<ATState> atStateTable() {
        return dp.atStore.getAtStateTable();
    }

    public static AT getAT(DependencyProvider dp, byte[] id) {
        return getAT(dp, AtApiHelper.getLong(id));
    }

    public static AT getAT(DependencyProvider dp, Long id) {
        return dp.atStore.getAT(id);
    }

    public static void addAT(DependencyProvider dp, Long atId, Long senderAccountId, String name, String description, byte[] creationBytes, int height) {
        ByteBuffer bf = ByteBuffer.allocate(8 + 8);
        bf.order(ByteOrder.LITTLE_ENDIAN);

        bf.putLong(atId);

        byte[] id = new byte[8];

        bf.putLong(8, senderAccountId);

        byte[] creator = new byte[8];
        bf.clear();
        bf.get(id, 0, 8);
        bf.get(creator, 0, 8);

        AT at = new AT(dp, id, creator, name, description, creationBytes, height);

        AtController.INSTANCE.resetMachine(at);

        atTable(dp).insert(at);

        at.saveState();

        Account account = Account.getOrAddAccount(dp, atId);
        account.apply(dp, new byte[32], height);
    }

    // TODO just do it yourself! or add a utils class or something... same goes for all of the methods around here doing this
    public static List<Long> getOrderedATs(DependencyProvider dp) {
        return dp.atStore.getOrderedATs();
    }

    public static byte[] compressState(byte[] stateBytes) {
        if (stateBytes == null || stateBytes.length == 0) {
            return null;
        }

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
                gzip.write(stateBytes);
                gzip.flush();
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static byte[] decompressState(byte[] stateBytes) {
        if (stateBytes == null || stateBytes.length == 0) {
            return null;
        }

        try (ByteArrayInputStream bis = new ByteArrayInputStream(stateBytes);
             GZIPInputStream gzip = new GZIPInputStream(bis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[256];
            int read;
            while ((read = gzip.read(buffer, 0, buffer.length)) > 0) {
                bos.write(buffer, 0, read);
            }
            bos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public void saveState() {
        ATState state = atStateTable().get(atStateDbKeyFactory(dp).newKey(AtApiHelper.getLong(this.getId())));
        int prevHeight = dp.blockchain.getHeight();
        int newNextHeight = prevHeight + getWaitForNumberOfBlocks();
        if (state != null) {
            state.setState(getState());
            state.setPrevHeight(prevHeight);
            state.setNextHeight(newNextHeight);
            state.setSleepBetween(getSleepBetween());
            state.setPrevBalance(getpBalance());
            state.setFreezeWhenSameBalance(freezeOnSameBalance());
            state.setMinActivationAmount(minActivationAmount());
        } else {
            state = new ATState(dp, AtApiHelper.getLong(this.getId()),
                    getState(), newNextHeight, getSleepBetween(),
                    getpBalance(), freezeOnSameBalance(), minActivationAmount());
        }
        atStateTable().insert(state);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int nextHeight() {
        return nextHeight;
    }

    public static class HandleATBlockTransactionsListener implements Consumer<Block> {
        private final DependencyProvider dp;

        public HandleATBlockTransactionsListener(DependencyProvider dp) {
            this.dp = dp;
        }

        @Override
        public void accept(Block block) {
            pendingFees.forEach((key, value) -> {
                Account atAccount = dp.accountService.getAccount(key);
                dp.accountService.addToBalanceAndUnconfirmedBalanceNQT(atAccount, -value);
            });

            List<Transaction> transactions = new ArrayList<>();
            for (AtTransaction atTransaction : pendingTransactions) {
                dp.accountService.addToBalanceAndUnconfirmedBalanceNQT(dp.accountService.getAccount(AtApiHelper.getLong(atTransaction.getSenderId())), -atTransaction.getAmount());
                dp.accountService.addToBalanceAndUnconfirmedBalanceNQT(dp.accountService.getOrAddAccount(AtApiHelper.getLong(atTransaction.getRecipientId())), atTransaction.getAmount());

                Transaction.Builder builder = new Transaction.Builder(dp, (byte) 1, Genesis.getCreatorPublicKey(),
                        atTransaction.getAmount(), 0L, block.getTimestamp(), (short) 1440, Attachment.Companion.getAT_PAYMENT());

                builder.senderId(AtApiHelper.getLong(atTransaction.getSenderId()))
                        .recipientId(AtApiHelper.getLong(atTransaction.getRecipientId()))
                        .blockId(block.getId())
                        .height(block.getHeight())
                        .blockTimestamp(block.getTimestamp())
                        .ecBlockHeight(0)
                        .ecBlockId(0L);

                byte[] message = atTransaction.getMessage();
                if (message != null) {
                    builder.message(new Appendix.Message(message, dp.blockchain.getHeight()));
                }

                try {
                    Transaction transaction = builder.build();
                    if (!dp.dbs.getTransactionDb().hasTransaction(transaction.getId())) {
                        transactions.add(transaction);
                    }
                } catch (BurstException.NotValidException e) {
                    throw new RuntimeException("Failed to construct AT payment transaction", e);
                }
            }

            if (!transactions.isEmpty()) {
                // WATCH: Replace after transactions are converted!
                dp.dbs.getTransactionDb().saveTransactions(transactions);
            }
        }
    }

    public static class ATState {

        public final BurstKey dbKey;
        private final long atId;
        private byte[] state;
        private int prevHeight;
        private int nextHeight;
        private int sleepBetween;
        private long prevBalance;
        private boolean freezeWhenSameBalance;
        private long minActivationAmount;

        protected ATState(DependencyProvider dp, long atId, byte[] state,
                          int nextHeight, int sleepBetween, long prevBalance, boolean freezeWhenSameBalance, long minActivationAmount) {
            this.atId = atId;
            this.dbKey = atStateDbKeyFactory(dp).newKey(this.atId);
            this.state = state;
            this.nextHeight = nextHeight;
            this.sleepBetween = sleepBetween;
            this.prevBalance = prevBalance;
            this.freezeWhenSameBalance = freezeWhenSameBalance;
            this.minActivationAmount = minActivationAmount;
        }


        public long getATId() {
            return atId;
        }

        public byte[] getState() {
            return state;
        }

        void setState(byte[] newState) {
            state = newState;
        }

        public int getPrevHeight() {
            return prevHeight;
        }

        void setPrevHeight(int prevHeight) {
            this.prevHeight = prevHeight;
        }

        public int getNextHeight() {
            return nextHeight;
        }

        void setNextHeight(int newNextHeight) {
            nextHeight = newNextHeight;
        }

        public int getSleepBetween() {
            return sleepBetween;
        }

        void setSleepBetween(int newSleepBetween) {
            this.sleepBetween = newSleepBetween;
        }

        public long getPrevBalance() {
            return prevBalance;
        }

        void setPrevBalance(long newPrevBalance) {
            this.prevBalance = newPrevBalance;
        }

        public boolean getFreezeWhenSameBalance() {
            return freezeWhenSameBalance;
        }

        void setFreezeWhenSameBalance(boolean newFreezeWhenSameBalance) {
            this.freezeWhenSameBalance = newFreezeWhenSameBalance;
        }

        public long getMinActivationAmount() {
            return minActivationAmount;
        }

        void setMinActivationAmount(long newMinActivationAmount) {
            this.minActivationAmount = newMinActivationAmount;
        }
    }
}
