package metro.http;

import metro.BlockchainTest;
import metro.Metro;
import metro.util.Logger;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public class GetWorkTest extends BlockchainTest {

    @Test
    public void testGetWorkSubmitSolutionSuccess() {
        JSONObject response = new APICall.Builder("getWork").
                body("{\"params\":[\"01803a6b0560060000000000000000000000e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855d2b2f262b742308ad54465acf12b9fee64f4e574443fdf531cfd2c8275721f2c00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ffff001e0000000000155c7c\"]}").
                build().invoke();
        Logger.logDebugMessage("getWork: " + response);
        generateBlock();
        Assert.assertNotNull(Metro.getBlockchain().getLastKeyBlock());

    }

    @Test
    public void testGetWorkSubmitSolutionFailed() {
        JSONObject response = new APICall.Builder("getWork").
                body("{\"params\":[\"0180debfe35f060000000000000000000000e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855d2b2f262b742308ad54465acf12b9fee64f4e574443fdf531cfd2c8275721f2c00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ffff001d00000000035b8604\"]}").
                build().invoke();
        Logger.logDebugMessage("getWork: " + response);
        generateBlock();
        Assert.assertNull(Metro.getBlockchain().getLastKeyBlock());
    }
}
