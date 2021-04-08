// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockChain {
	public static final int CUT_OFF_AGE = 10;

	private final List<List<Block>> blockchain;
	private final List<Integer> blockCnt; // block count in ith chain
	private int maxHeight; // max height modulo (CUT_OFF_AGE + 1)
	private final Map<ByteArrayWrapper, UTXOPool> blockToUTXOPool;
	private final TransactionPool transactionPool;
	private final Map<ByteArrayWrapper, Integer> blockToHeight;
	private Block maxHeightBlock;

	/**
	 * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
	 * block
	 */
	public BlockChain(Block genesisBlock) {
		blockchain = new ArrayList<>();
		blockCnt = new ArrayList<>();
		transactionPool = new TransactionPool();
		blockToUTXOPool = new HashMap<>();
		blockToHeight = new HashMap<>();
		maxHeight = 0;

		addChain(maxHeight, genesisBlock);

		// add coinbase manually
		UTXO ut = new UTXO(genesisBlock.getCoinbase().getHash(), 0);
		UTXOPool uPool = new UTXOPool();
		uPool.addUTXO(ut, genesisBlock.getCoinbase().getOutput(0));

		blockToUTXOPool.put(new ByteArrayWrapper(genesisBlock.getHash()), uPool);
		blockToHeight.put(new ByteArrayWrapper(genesisBlock.getHash()), maxHeight);
		maxHeightBlock = genesisBlock;
	}

	/**
	 * Get the maximum height block
	 */
	public Block getMaxHeightBlock() {
		return maxHeightBlock;
	}

	/**
	 * Get the UTXOPool for mining a new block on top of max height block
	 */
	public UTXOPool getMaxHeightUTXOPool() {
		return blockToUTXOPool.get(new ByteArrayWrapper(maxHeightBlock.getHash()));
	}

	/**
	 * Get the transaction pool to mine a new block
	 */
	public TransactionPool getTransactionPool() {
		return transactionPool;
	}

	private void updateTransactionPool(Block newBlock) {
		UTXOPool uPool = blockToUTXOPool.get(new ByteArrayWrapper(newBlock.getHash()));
		TxHandler handler = new TxHandler(uPool);

		List<Transaction> txs = transactionPool.getTransactions();
		for (Transaction tx : txs) {
			if (!handler.isValidTx(tx))
				transactionPool.removeTransaction(tx.getHash());
		}
	}

	private void removeIthElement(int n) {
		for (int i = 0; i < blockchain.size(); i++) {
			Block b = blockchain.get(i).get(n);
			if (b == null) continue;
			blockToUTXOPool.remove(new ByteArrayWrapper(b.getHash()));
			blockToHeight.remove(new ByteArrayWrapper(b.getHash()));

			blockchain.get(i).set(n, null);
			blockCnt.set(i, blockCnt.get(i) - 1);

			if (blockCnt.get(i) == 0) {
				blockchain.remove(i);
				blockCnt.remove(i);
				i--;
			}
		}
	}

	private void addChain(int idx, Block block) {
		blockchain.add(new ArrayList<>());
		for (int i = 0; i < CUT_OFF_AGE + 1; i++)
			blockchain.get(blockchain.size() - 1).add(null);

		blockchain.get(blockchain.size() - 1).set(idx, block);
		blockCnt.add(1);
	}

	private int findChain(int height, ByteArrayWrapper blockHash) {
		for (int i = 0; i < blockchain.size(); i++) {
			Block b = blockchain.get(i).get(height);
			if (b == null) continue;
			ByteArrayWrapper wrapper = new ByteArrayWrapper(b.getHash());
			if (wrapper.equals(blockHash)) return i;
		}

		return -1;
	}

	/**
	 * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
	 * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
	 *
	 * <p>
	 * For example, you can try creating a new block over the genesis block (block height 2) if the
	 * block chain height is {@code <=
	 * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block
	 * at height 2.
	 *
	 * @return true if block is successfully added
	 */
	public boolean addBlock(Block block) {
		if (block.getPrevBlockHash() == null) return false; // genesis block

		ByteArrayWrapper hash = new ByteArrayWrapper(block.getHash());
		ByteArrayWrapper prevHash = new ByteArrayWrapper(block.getPrevBlockHash());

		if (!blockToUTXOPool.containsKey(prevHash)) return false;

		TxHandler handler = new TxHandler(blockToUTXOPool.get(prevHash));

		Transaction[] txs = block.getTransactions().toArray(new Transaction[0]);
		Transaction[] rTxs = handler.handleTxs(txs);

		if (txs.length != rTxs.length) return false;

		UTXO coin = new UTXO(block.getCoinbase().getHash(), 0);
		handler.getUTXOPool().addUTXO(coin, block.getCoinbase().getOutput(0));

		int prevHeight = blockToHeight.get(prevHash);
		int nxt = (prevHeight + 1) % (CUT_OFF_AGE + 1);

		blockToUTXOPool.put(hash, handler.getUTXOPool());
		blockToHeight.put(hash, nxt);

		if (prevHeight == maxHeight) { // extending the chain
			removeIthElement(nxt);
			maxHeightBlock = block;
			maxHeight = nxt;
			updateTransactionPool(block);
		}

		int id = findChain(prevHeight, prevHash);

		if (blockchain.get(id).get(nxt) == null) {
			blockchain.get(id).set(nxt, block);
			blockCnt.set(id, blockCnt.get(id) + 1);
		} else {
			addChain(nxt, block);
		}

		return true;
	}

	/**
	 * Add a transaction to the transaction pool
	 */
	public void addTransaction(Transaction tx) {
		transactionPool.addTransaction(tx);
	}
}