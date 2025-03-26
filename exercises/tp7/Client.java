import java.net.*;
import java.io.*;
import java.util.*;

public class Client {
    private static final int BUFFER_SIZE = 1024;

    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println("Usage: java Client <addr> <port> <op> <id> [<val>]");
            return;
        }
        String serverAddress = args[0];
        int port = Integer.parseInt(args[1]);
        String operation = args[2];
        int sensorId = Integer.parseInt(args[3]);

        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress serverIP = InetAddress.getByName(serverAddress);
            if (operation.equals("put") && args.length == 5) {
                float value = Float.parseFloat(args[4]);
                String message = "put " + sensorId + " " + value;
                sendMessage(socket, serverIP, port, message);
                Thread.sleep(1000);
            } else if (operation.equals("get")) {
                String message = "get " + sensorId;
                sendMessage(socket, serverIP, port, message);
                socket.setSoTimeout(2000);
                receiveResponse(socket);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void sendMessage(DatagramSocket socket, InetAddress address, int port, String message) throws IOException {
        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        socket.send(packet);
    }

    private static void receiveResponse(DatagramSocket socket) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        try {
            socket.receive(packet);
            String response = new String(packet.getData(), 0, packet.getLength());
            System.out.println("Response: " + response);
        } catch (SocketTimeoutException e) {
            System.out.println("No response received from server.");
        }
    }
}
