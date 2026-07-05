package main;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Minimal RCON client for the Minecraft RCON protocol (Source RCON, RFC-like).
 * Packet layout (all ints little-endian):
 *   [length:4][requestId:4][type:4][payload:variable][null:1][null:1]
 * where length = 4 + 4 + payload.length + 2
 */
public class RconClient {

    private static final int TYPE_AUTH     = 3;
    private static final int TYPE_COMMAND  = 2;

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream  in;
    private int nextId = 1;

    public void connect(String host, int port, String password) throws IOException {
        socket = new Socket(host, port);
        socket.setSoTimeout(5000);
        out = new DataOutputStream(socket.getOutputStream());
        in  = new DataInputStream(socket.getInputStream());

        int id = nextId++;
        sendPacket(id, TYPE_AUTH, password);
        Packet response = readPacket();
        if (response.requestId == -1) {
            disconnect();
            throw new IOException("RCON authentication failed — check rcon.password in server.properties.");
        }
    }

    public String sendCommand(String command) throws IOException {
        if (!isConnected()) throw new IOException("Not connected to RCON.");
        int id = nextId++;
        sendPacket(id, TYPE_COMMAND, command);
        Packet response = readPacket();
        return response.payload;
    }

    public void disconnect() {
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
        socket = null;
        out    = null;
        in     = null;
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }

    // ─── Protocol helpers ───────────────────────────────────────────

    private void sendPacket(int requestId, int type, String payload) throws IOException {
        byte[] payloadBytes = payload.getBytes("UTF-8");
        int length = 4 + 4 + payloadBytes.length + 2; // id + type + payload + 2 nulls
        ByteBuffer buf = ByteBuffer.allocate(4 + length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(length);
        buf.putInt(requestId);
        buf.putInt(type);
        buf.put(payloadBytes);
        buf.put((byte) 0);
        buf.put((byte) 0);
        out.write(buf.array());
        out.flush();
    }

    private Packet readPacket() throws IOException {
        byte[] header = new byte[4];
        in.readFully(header);
        int length = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (length < 10 || length > 4110)
            throw new IOException("Invalid RCON packet length: " + length);
        byte[] data = new byte[length];
        in.readFully(data);
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int requestId = buf.getInt();
        int type      = buf.getInt();
        byte[] payloadBytes = new byte[length - 10]; // minus id(4) + type(4) + nulls(2)
        buf.get(payloadBytes);
        return new Packet(requestId, type, new String(payloadBytes, "UTF-8").trim());
    }

    private static class Packet {
        final int requestId, type;
        final String payload;
        Packet(int requestId, int type, String payload) {
            this.requestId = requestId;
            this.type      = type;
            this.payload   = payload;
        }
    }
}
