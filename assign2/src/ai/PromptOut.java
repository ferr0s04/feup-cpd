package ai;

import org.json.JSONArray;

public class PromptOut {
    String response;
    JSONArray context;
    public PromptOut(String responseIn, JSONArray contextIn){
        response = responseIn;
        context = contextIn;
    }

    public String getResponse(){
        return response;
    }

    public JSONArray getContext() {
        return context;
    }
}
