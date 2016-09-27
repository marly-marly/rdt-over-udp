
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class Sender1a {
	
	private int port;
	
	private DatagramSocket clientSocket;
	private InetAddress iPAddress;
	
	public Sender1a(String serverName, int port) throws SocketException, UnknownHostException{
		
		this.port = port;
		
		// Open a socket on the client and translate host-name to IP address using DNS	
		clientSocket = new DatagramSocket();
		iPAddress = InetAddress.getByName(serverName); 
		
		System.out.println("Client: Connected to " + serverName + " on port " + port);
	}
	
	public void sendFile(String sendFilePath) throws InterruptedException, IOException{
	
		// Register the file to be sent
		File file = new File(sendFilePath);
		InputStream inputStream = new FileInputStream(file);
		byte[] fileByteArray = new byte[inputStream.available()];
		inputStream.read(fileByteArray);
		
		// Create a 16-bit sequence number indicating the order
        int sequenceNumber = 0; 

		byte[] packet = new byte[1024];
		
		// Breaking the byte-array of the image into pieces
		int startIndex = 0;
		while (startIndex < fileByteArray.length){
			
			sequenceNumber++;
			packet[0] = (byte)(sequenceNumber >> 8);
			packet[1] = (byte)(sequenceNumber);
			packet[2] = (byte)(0);
			
			int endIndex = (startIndex + 1021);
			
			// Taking care of the left over bytes at the end of the byte-array
			if (endIndex > fileByteArray.length){
				endIndex = startIndex + (fileByteArray.length - startIndex);
				// Flag to indicate last packet
				packet[2] = (byte)(1);
			}
			
			// Fill up the rest of the packet byte-array
			for (int i=0; i < (endIndex - startIndex); i++) {
                packet[i+3] = fileByteArray[startIndex+i];
            }
			
			System.out.println("Client: sending sequence number: " + sequenceNumber);
			
			// Send packet
			DatagramPacket sendPacket = new DatagramPacket(packet, packet.length, iPAddress, port);
			clientSocket.send(sendPacket);
			
			startIndex += 1021;
		}
	
		clientSocket.close(); 
		inputStream.close();
	}

	public static void main(String args[]) throws InterruptedException, NumberFormatException, IOException{

		String serverName = args[0];
		int port = Integer.parseInt(args[1]);
		String fileName = args[2];
		
		Sender1a client = new Sender1a(serverName, port);
		client.sendFile(fileName);
	}
}
