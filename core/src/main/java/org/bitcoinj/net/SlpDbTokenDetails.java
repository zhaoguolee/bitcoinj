package org.bitcoinj.net;

import org.json.JSONArray;
import org.json.JSONObject;
import org.spongycastle.util.encoders.Base64;

import java.nio.charset.StandardCharsets;

public class SlpDbTokenDetails {
    private String json;

    public SlpDbTokenDetails(String tokenId) {
        JSONObject json = new JSONObject();
        json.put("v", 3);
        JSONObject q = new JSONObject();
        q.put("db", new JSONArray().put("t"));
        JSONObject findJson = new JSONObject();
        JSONObject $queryJson = new JSONObject();
        $queryJson.put("tokenDetails.tokenIdHex", tokenId);
        findJson.put("$query", $queryJson);
        q.put("find", findJson);
        JSONObject project = new JSONObject();
        project.put("tokenDetails", 1);
        project.put("_id", 0);
        q.put("project", project);
        q.put("limit", 1000);
        json.put("q", q);

        this.json = json.toString();
    }

    public String getJson() {
        return this.json;
    }

    public String getEncoded() {
        return new String(Base64.encode(this.json.getBytes()), StandardCharsets.UTF_8);
    }
}


/*

{
  "v": 3,
  "q": {
    "db": ["t"],
    "find":
    {
      "$query":
      {
        "tokenDetails.tokenIdHex": "959a6818cba5af8aba391d3f7649f5f6a5ceb6cdcd2c2a3dcb5d2fbfc4b08e98"
      }
    },
    "project": {"tokenDetails": 1, "tokenStats": 1, "_id": 0 },
    "limit": 1000
  }
}

 */