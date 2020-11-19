package org.bitcoinj.core.flipstarter;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bouncycastle.util.encoders.Base64;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class FlipstarterDecodeTest {
    @Test
    public void runDecode() {
        String payload = "ewAiAG8AdQB0AHAAdQB0AHMAIgA6AFsAewAiAHYAYQBsAHUAZQAiADoANQAyADAAMAAwADAAMAAwADAAMAAsACIAYQBkAGQAcgBlAHMAcwAiADoAIgBiAGkAdABjAG8AaQBuAGMAYQBzAGgAOgBxAHEAYQBlAGQAdgBjAGMAaAA5AG0AbABjADAAaAA0AGgAYQA1AGoAbAA5ADAAcgAzAHMAMgB0AHcAawA1AGgANgBjADkAYwBnAGYAZAB6ADIAegAiAH0AXQAsACIAZABhAHQAYQAiADoAewAiAGEAbABpAGEAcwAiADoAIgAnBkQGRAY5BkYGKQYiACwAIgBjAG8AbQBtAGUAbgB0ACIAOgAiACcGRAZEBjkGRgYpBicGRAZEBjkGRgYpBicGRAZEBjkGRgYpBicGRAZEBjkGRgYpBicGRAZEBjkGRgYpBicGRAZEBjkGRgYpBiIAfQAsACIAZABvAG4AYQB0AGkAbwBuACIAOgB7ACIAYQBtAG8AdQBuAHQAIgA6ADEAMwA3ADMAOAAxADcAOAB9ACwAIgBlAHgAcABpAHIAZQBzACIAOgAxADYAMAA2ADMANAA4ADgAMAAwAH0A";
        byte[] decodedPayload = Base64.decode(payload);
        String json = new String(decodedPayload, StandardCharsets.UTF_16LE);
        System.out.println(json);
        FlipstarterPledgePayload pledgePayload = new Gson().fromJson(json, FlipstarterPledgePayload.class);
        System.out.println(pledgePayload.outputs[0].address);
    }
}
