import java.io.*;
import java.net.*;
import java.lang.Thread;
import java.util.concurrent.locks.ReentrantLock;

public class Server {
    private static Sensor[] sensors;

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java Server <port> <no_sensors>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        int numSensors = Integer.parseInt(args[1]);
        sensors = new Sensor[numSensors];
        for (int i = 0; i < numSensors; i++) {
            sensors[i] = new Sensor(i);
        }

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server listening on port " + port);

            while (true) {
                Socket socket = serverSocket.accept();
                Thread.startVirtualThread(() -> handleClient(socket));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket socket) {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String request;
            while ((request = reader.readLine()) != null) {
                try {
                    Thread.sleep(100); // 100ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                String response = processRequest(request);
                System.out.println("Request received: " + request + " -> " + response);
                writer.println(response);
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + e.getMessage());
        }
    }

    private static String processRequest(String request) {
        String[] parts = request.split(" ");
        if (parts.length < 2) return "Invalid request";

        String op = parts[0];

        int sensorId;
        try {
            sensorId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return "Invalid request";
        }

        if (sensorId < 0 || sensorId >= sensors.length) return "Invalid request";

        if (op.equals("put") && parts.length == 3) {
            try {
                float value = Float.parseFloat(parts[2]);
                sensors[sensorId].addReading(value);
                return "Value stored";
            } catch (NumberFormatException e) {
                return "Invalid request";
            }
        } else if (op.equals("get")) {
            float avg = sensors[sensorId].getAverage();
            return sensorId + " " + avg;
        }

        return "Invalid request";
    }
}
