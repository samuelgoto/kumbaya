package com.kumbaya.router;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import junit.framework.TestCase;
import org.junit.Test;

public class UdpTest extends TestCase {
  private class UdpServer implements Runnable {
    private final int port;
    
    UdpServer(int port) {
      this.port = port;
    }

    @Override
    public void run() {
      try {
        DatagramSocket serverSocket = new DatagramSocket(port);
        byte[] receiveData = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        serverSocket.receive(receivePacket);
        Packet message = Packet.of(receivePacket);
        
        if (message.getType() == Packet.Type.INTEREST) {
          InterestPacket interest = (InterestPacket) message;
          String capitalizedSentence = new String(interest.getName()).toUpperCase();
          DataPacket response = new DataPacket(
              interest.getName(),
              capitalizedSentence,
              message.getIPAddress(), 
              message.getPort());
          serverSocket.send(response.datagram());
        } else if (message.getType() == Packet.Type.DATA) {
          // TODO(goto): implement this;
        }
        
        serverSocket.close();
      } catch (SocketException e) {
      } catch (IOException e) {
      }
    }
  }
  
  private static class Packet {
    enum Type {
      INTEREST, DATA
    }
    private final Type type;
    private final byte[] data;
    private final InetAddress IPAddress;
    private final int port;
    
    Packet(Type type, byte[] data, InetAddress IPAddress, int port) {
      this.type = type;
      this.data = data;
      this.IPAddress = IPAddress;
      this.port = port;
    }
    
    DatagramPacket datagram() throws IOException {
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      stream.write(type == Type.INTEREST ? 0 : 1);
      stream.write(data);
      DatagramPacket sendPacket = new DatagramPacket(stream.toByteArray(), stream.size(), IPAddress, port);
      return sendPacket;
    }
    
    static Packet of(DatagramPacket receivePacket) {
      // The first byte is reserved for the type of the packet.
      ByteArrayInputStream stream = new ByteArrayInputStream(
          receivePacket.getData(), 0, receivePacket.getLength());
      int type = stream.read();
      
      
      InetAddress IPAddress = receivePacket.getAddress();
      int port = receivePacket.getPort();

      if (type == 0) {
        // interest packet
        return InterestPacket.of(stream, IPAddress, port);
      } else {
        // data packet
        return DataPacket.of(stream, IPAddress, port);
      }
    }
    
    Type getType() {
      return type;
    }
    
    @SuppressWarnings("unused")
    byte[] getData() {
      return data;
    }
    
    InetAddress getIPAddress() {
      return IPAddress;
    }
    
    int getPort() {
      return port;
    }
  }
  
  private static class InterestPacket extends Packet {
    private final String name;
    
    InterestPacket(String name, InetAddress IPAddress, int port) {
      super(Type.INTEREST, name.getBytes(), IPAddress, port);
      
      this.name = name;
    }
    
    String getName() {
      return name;
    }
    
    static InterestPacket of(ByteArrayInputStream stream, InetAddress IPAddress, int port) {
      // From the second byte forward, you get the type-specific payload.
      int n = stream.available();
      byte[] bytes = new byte[n];
      stream.read(bytes, 0, n);
      String name = new String(bytes, 0, bytes.length);
      
      return new InterestPacket(name, IPAddress, port);      
    }
  }
  
  private static class DataPacket extends Packet {
    private final String name;
    private final String content;
    
    DataPacket(String name, String content, InetAddress IPAddress, int port) {
      super(Type.DATA, name.getBytes(), IPAddress, port);
      
      this.name = name;
      this.content = content;
    }    

    static DataPacket of(ByteArrayInputStream stream, InetAddress IPAddress, int port) {
      // From the second byte forward, you get the type-specific payload.
      int n = stream.available();
      byte[] bytes = new byte[n];
      stream.read(bytes, 0, n);
      String name = new String(bytes, 0, bytes.length);
      
      String content = "fake";
      
      return new DataPacket(name, content, IPAddress, port);
    }
    
    String getName() {
      return name;
    }
    
    String getContent() {
      return content;
    }
  }

  private class UdpClient {    
    private final InetAddress IPAddress;
    private final int port;
    
    UdpClient(InetAddress IPAddress, int port) {
      this.IPAddress = IPAddress;
      this.port = port;
    }
    
    public DataPacket send(String name) throws IOException {
      DatagramSocket clientSocket = new DatagramSocket();
      clientSocket.send(new InterestPacket(name, IPAddress, port).datagram());
      byte[] receiveData = new byte[1024];
      DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
      clientSocket.receive(receivePacket);
      Packet received = Packet.of(receivePacket);
      clientSocket.close();
      return (DataPacket) received;
    }
  }

  @Test
  public void testServer() throws IOException {
    Thread server = new Thread(new UdpServer(9876));
    server.start();

    UdpClient client = new UdpClient(InetAddress.getByName("localhost"), 9876);
    DataPacket result = client.send("hello world");
    assertEquals("hello world", result.getName());
    // TODO(goto): use type length value encoding to insert two strings into a byte array.
    assertEquals("fake", result.getContent());
  }
}

