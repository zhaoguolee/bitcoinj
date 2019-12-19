package org.bitcoinj.net;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class SlpDbProcessor {
    private String[] slpDbInstances = new String[] {"slpdb.fountainhead.cash", "slpdb.bitcoin.com"};
    private String slpDbEndpoint = "/q/";

    public SlpDbProcessor() {

    }

    private String readData(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    public JSONObject getTokenData(String base64Query) throws IOException, JSONException {
        try (InputStream is = new URL("https://" + this.slpDbInstances[0] + slpDbEndpoint + base64Query).openStream()) {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String jsonText = readData(rd);
            return new JSONObject(jsonText).getJSONArray("t").getJSONObject(0);
        }
    }
}
