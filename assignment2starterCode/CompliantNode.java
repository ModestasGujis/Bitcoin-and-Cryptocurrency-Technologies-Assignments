import javax.swing.plaf.ColorUIResource;
import java.util.*;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
public class CompliantNode implements Node {
    private Set<Transaction> currSet;

    public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) { }

    public void setFollowees(boolean[] followees) { }

    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        currSet = pendingTransactions;
    }

    public Set<Transaction> sendToFollowers() {
        return currSet;
    }

    public void receiveFromFollowees(Set<Candidate> candidates) {
        for (Candidate c : candidates) {
            Transaction tx = c.tx;
            currSet.add(tx);
        }
    }
}
