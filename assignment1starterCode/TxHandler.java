import java.util.ArrayList;
import java.util.HashSet;

public class TxHandler {

    private final UTXOPool uPool;
    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        uPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        ArrayList<Transaction.Input> inputs = tx.getInputs();
        ArrayList<Transaction.Output> outputs = tx.getOutputs();

        for(Transaction.Input input: inputs){
            UTXO u = new UTXO(input.prevTxHash, input.outputIndex);
            if(!uPool.contains(u)) return false;
        }

        double sumIn = 0;
        double sumOut = 0;

        HashSet<UTXO> usedUTXOs = new HashSet<>();

        for(int i = 0; i < tx.numInputs(); i++){
            Transaction.Input input = tx.getInput(i);

            UTXO u = new UTXO(input.prevTxHash, input.outputIndex);

            if(usedUTXOs.contains(u)) return false;
            usedUTXOs.add(u);

            Transaction.Output out = uPool.getTxOutput(u);
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

    private void updatePool(Transaction tx){
        ArrayList<Transaction.Input> inputs = tx.getInputs();
        for(Transaction.Input input: inputs){
            UTXO u = new UTXO(input.prevTxHash, input.outputIndex);
            uPool.removeUTXO(u);
        }

        for(int i = 0; i < tx.numOutputs(); i++){
            uPool.addUTXO(new UTXO(tx.getHash(), i), tx.getOutput(i));
        }
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        ArrayList<Transaction> confirmedTxs = new ArrayList<>();

        for (Transaction tx : possibleTxs) {
            if (isValidTx(tx)) {
                updatePool(tx);
                confirmedTxs.add(tx);
            }
        }

        Transaction[] arr = new Transaction[confirmedTxs.size()];
        arr = confirmedTxs.toArray(arr);

        return arr;
    }

    public UTXOPool getUTXOPool() {
        return uPool;
    }
}
