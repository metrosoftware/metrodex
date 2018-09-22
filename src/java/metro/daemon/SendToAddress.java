package metro.daemon;

import metro.Account;
import metro.Attachment;
import metro.Block;
import metro.Constants;
import metro.Metro;
import metro.MetroException;
import metro.Miner;
import metro.Transaction;
import metro.TransactionImpl;
import metro.util.Convert;
import metro.util.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import java.math.BigDecimal;

import static metro.daemon.DaemonUtils.awareError;
import static metro.daemon.DaemonUtils.awareResult;

public class SendToAddress implements DaemonRequestHandler {

    static SendToAddress instance = new SendToAddress();

    private SendToAddress() {
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
        String pubKey = (String) dReq.getParams().get(0);
        Number amount = (Number) dReq.getParams().get(1);
        Block ecBlock = Metro.getBlockchain().getECBlock(Metro.getEpochTime());
        byte[] senderPubKey = Convert.parseHexString(Miner.getPublicKey());
        Account sender = Account.getAccount(Convert.parseHexString(Miner.getPublicKey()));
        long amountMQT = -1;
        if (amount instanceof Double) {
            amountMQT = (new BigDecimal((Double) amount)).multiply(new BigDecimal(Constants.ONE_MTR)).longValue();
        } else {
            amountMQT = (new BigDecimal((Long) amount)).multiply(new BigDecimal(Constants.ONE_MTR)).longValue();
        }
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
                if (Math.addExact(amountMQT, transaction.getFeeMQT()) > sender.getUnconfirmedBalanceMQT()) {
                    return awareError(6, "Not enough funds", dReq.getId());
                }
            } catch (ArithmeticException e) {
                return awareError(6, "Not enough funds", dReq.getId());
            }
            Metro.getTransactionProcessor().broadcast(transaction);
        } catch (MetroException.NotValidException e) {
            Logger.logWarningMessage("Send money error:", e);
            return awareError(-1, "NotValidException: " + e.getMessage(), dReq.getId());
        } catch (MetroException.ValidationException e) {
            Logger.logWarningMessage("Send money error:", e);
            return awareError(-1, "NotValidException: " + e.getMessage(), dReq.getId());
        }
        String txhash = Convert.toHexString(((TransactionImpl) transaction).fullHash());
        JSONObject result = new JSONObject();
        result.put("tx", txhash);
        return awareResult(result, dReq.getId());
    }

    @Override
    public JSONStreamAware process(DaemonRequest request, Object options) {
        return process(request);
    }
}
