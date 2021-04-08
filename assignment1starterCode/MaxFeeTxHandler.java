import java.util.*;

public class MaxFeeTxHandler {

    private final UTXOPool uPool;
    private ArrayList<Transaction> currBestSet;
    private double currBestSum;

    public MaxFeeTxHandler(UTXOPool utxoPool) {
        uPool = new UTXOPool(utxoPool);
    }

    public boolean isValidTx(Transaction tx) {
        return isValidTx(tx, uPool);
    }

    private boolean isValidTx(Transaction tx, UTXOPool pool) {
        ArrayList<Transaction.Input> inputs = tx.getInputs();
        ArrayList<Transaction.Output> outputs = tx.getOutputs();

        for(Transaction.Input input: inputs){
            UTXO u = new UTXO(input.prevTxHash, input.outputIndex);
            if(!pool.contains(u)) return false;
        }

        double sumIn = 0;
        double sumOut = 0;

        HashSet<UTXO> usedUTXOs = new HashSet<>();

        for(int i = 0; i < tx.numInputs(); i++){
            Transaction.Input input = tx.getInput(i);

            UTXO u = new UTXO(input.prevTxHash, input.outputIndex);

            if(usedUTXOs.contains(u)) return false;
            usedUTXOs.add(u);

            Transaction.Output out = pool.getTxOutput(u);
            sumIn += out.value;
            if(!Crypto.verifySignature(out.address, tx.getRawDataToSign(i), input.signature))
                return false;
        }

        for(Transaction.Output output: outputs){
            if(output.value < 0) return false;
            sumOut += output.value;
        }

        return (sumIn >= sumOut);
    }

    private void updatePool(Transaction tx, UTXOPool pool){
        ArrayList<Transaction.Input> inputs = tx.getInputs();
        for(Transaction.Input input: inputs){
            UTXO u = new UTXO(input.prevTxHash, input.outputIndex);
            pool.removeUTXO(u);
        }

        for(int i = 0; i < tx.numOutputs(); i++){
            pool.addUTXO(new UTXO(tx.getHash(), i), tx.getOutput(i));
        }
    }

    private double getFees(Transaction tx, UTXOPool pool) {
        double fees = 0;
        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);

            UTXO u = new UTXO(input.prevTxHash, input.outputIndex);
            Transaction.Output out = pool.getTxOutput(u);

            fees += out.value;
        }

        for (int i = 0; i < tx.numOutputs(); i++) {
            fees -= tx.getOutput(i).value;
        }

        return fees;
    }

    private void dfs(int n, List<Transaction> allTxs, List<Transaction> taken, UTXOPool pool, double feeSum) {
        if (n == allTxs.size()) {
            if (feeSum > currBestSum) {
                currBestSum = feeSum;
                currBestSet = new ArrayList<>(taken);
            }
            return;
        }

        // do not take the current transaction
        dfs(n + 1, allTxs, taken, pool, feeSum);

        // take current transaction if valid
        if (isValidTx(allTxs.get(n), pool)) {
            Transaction tx = allTxs.get(n);

            List<Transaction> newTaken = new ArrayList<>(taken);
            newTaken.add(tx);

            double newFees = feeSum + getFees(tx, pool);
            UTXOPool newPool = new UTXOPool(pool);
            updatePool(tx, newPool);

            dfs(n + 1, allTxs, newTaken, newPool, newFees);
        }
    }

    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        List<Transaction> allTxs = Arrays.asList(possibleTxs);

        currBestSum = -5.0;
        currBestSet = new ArrayList<>();

        UTXOPool pool = new UTXOPool(uPool);

        ArrayList<Transaction> temp = new ArrayList<>();

        dfs(0, allTxs, temp, pool, 0.0);

        Transaction[] arr = new Transaction[currBestSet.size()];
        arr = currBestSet.toArray(arr);

        for (Transaction tx : arr) {
            updatePool(tx, uPool);
        }
        return arr;
    }

}
