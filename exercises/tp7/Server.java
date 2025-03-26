import java.net.*;
import java.io.*;
import java.util.*;

public class Server {
    private static Sensor[] sensors;
    private static final int BUFFER_SIZE = 1024;

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

        try (DatagramSocket socket = new DatagramSocket(port)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String received = new String(packet.getData(), 0, packet.getLength());
                handleRequest(received, packet.getAddress(), packet.getPort(), socket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleRequest(String request, InetAddress address, int port, DatagramSocket socket) {
        String[] parts = request.split(" ");
        if (parts.length < 2) return;

        String op = parts[0];
        int sensorId = Integer.parseInt(parts[1]);
        if (sensorId < 0 || sensorId >= sensors.length) return;

        if (op.equals("put") && parts.length == 3) {
            float value = Float.parseFloat(parts[2]);
            sensors[sensorId].addReading(value);
        } else if (op.equals("get")) {
            float avg = sensors[sensorId].getAverage();
            String response = sensorId + " " + avg;
            try {
                byte[] responseData = response.getBytes();
                DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, address, port);
                socket.send(responsePacket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
