package metro.daemon;

import metro.Account;
import metro.Attachment;
import metro.Block;
import metro.Constants;
import metro.Metro;
import metro.MetroException;
import metro.Miner;
import metro.Transaction;
import metro.util.Convert;
import metro.util.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static metro.daemon.DaemonUtils.awareError;
import static metro.daemon.DaemonUtils.awareResult;

public class SendMany implements DaemonRequestHandler {

    static SendMany instance = new SendMany();

    private SendMany() {
    }

    @Override
    public JSONStreamAware process(DaemonRequest dReq) {
        if (dReq.getParams().size() < 2) {
            awareError(-1, "Incorrect request", dReq.getId());
        }
        String secretPhrase = Miner.getPassphrase();
        if (secretPhrase == null) {
            awareError(-1, "There is no passphrase. Can not sign tx.", dReq.getId());
        }
        Map<String, Number> payments = (Map<String, Number>) dReq.getParams().get(1);
        Block ecBlock = Metro.getBlockchain().getECBlock(Metro.getEpochTime());
        byte[] senderPubKey = Convert.parseHexString(Miner.getPublicKey());
        Account sender = Account.getAccount(Convert.parseHexString(Miner.getPublicKey()));
        List<Transaction> txs = new ArrayList<>();
        long totalSpent = 0L;
        for (String pubKey : payments.keySet()) {
            long amountMQT = (new BigDecimal(payments.get(pubKey) instanceof Double ? (Double) payments.get(pubKey) : (Long) payments.get(pubKey)))
                    .multiply(new BigDecimal(Constants.ONE_MTR)).longValueExact();
            long feeMQT = Constants.ONE_MTR;
            Attachment.EmptyAttachment attachment = Attachment.ORDINARY_PAYMENT;
            Account.FullId recipientFullId = Account.FullId.fromPublicKey(Convert.parseHexString(pubKey));
            Transaction.Builder builder = Metro.newTransactionBuilder(senderPubKey, amountMQT, feeMQT,
                    (short)1440, attachment);
            builder.recipientFullId(recipientFullId);
            //builder.appendix(message);
            builder.ecBlockId(ecBlock.getId());
            builder.ecBlockHeight(ecBlock.getHeight());
            Transaction transaction = null;
            try {
                transaction = builder.build(secretPhrase);
                try {
                    totalSpent = Math.addExact(amountMQT, totalSpent);
                    totalSpent = Math.addExact(transaction.getFeeMQT(), totalSpent);

                } catch (ArithmeticException e) {
                    return awareError(6, "Not enough funds", dReq.getId());
                }
                transaction.validate();
                txs.add(transaction);
                Metro.getTransactionProcessor().broadcast(transaction);
            } catch (MetroException.NotValidException e) {
                Logger.logWarningMessage("Send money error:", e);
                return awareError(-1, "NotValidException: " + e.getMessage(), dReq.getId());
            } catch (MetroException.ValidationException e) {
                Logger.logWarningMessage("Send money error:", e);
                return awareError(-1, "NotValidException: " + e.getMessage(), dReq.getId());
            }
        }
        if (totalSpent > sender.getUnconfirmedBalanceMQT()) {
            return awareError(6, "Not enough funds", dReq.getId());
        }
        JSONArray txArray = new JSONArray();
        try {
            for (Transaction tx : txs) {
                Metro.getTransactionProcessor().broadcast(tx);
                txArray.add(tx.getFullHash());
            }
        } catch (MetroException.ValidationException e) {
            return awareError(-1, "NotValidException: " + e.getMessage(), dReq.getId());
        }
        JSONObject result = new JSONObject();
        //result.put("txid", txs.get(0).getFullHash());
        //result.put("txids", txArray);
        return awareResult(txs.get(0).getFullHash(), dReq.getId());
    }

    @Override
    public JSONStreamAware process(DaemonRequest request, Object options) {
        return process(request);
    }
}
