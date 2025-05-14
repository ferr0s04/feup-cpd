package ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Prompter {
    String modelName= "llama3";
    URL url;
    HttpURLConnection conn;

    public Prompter(){}

    private void openConnection() throws IOException {
        url = new URL("http://localhost:11434/api/generate");
        conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
    }

    private void closeConnection(){
        conn.disconnect();
    }

    public PromptOut prompt(String promptText, JSONArray context) throws IOException {
        String jsonIputString;

        openConnection();

        if (context.isEmpty()) {
            jsonIputString = String.format(
                    "{\"model\": \"%s\", \"prompt\": \"%s\", \"stream\": false }", modelName, promptText
            );
        } else{
            jsonIputString = String.format(
                    "{\"model\": \"%s\", \"prompt\": \"%s\", \"stream\": false, \"context\": %s }", modelName, promptText, context
            );
        }

        try(OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonIputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int code = conn.getResponseCode();

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            response.append(line);
        }
        in.close();

        JSONObject jsonResponse = new JSONObject(response.toString());
        String responseText = jsonResponse.getString("response");
        context = jsonResponse.getJSONArray("context");

        closeConnection();

        return new PromptOut(responseText, context);
    }
}