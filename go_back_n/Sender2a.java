import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;

public class Sender2a {

	private int port;
	private DatagramSocket clientSocket;
	private InetAddress iPAddress;
	private int windowSize;
	private ArrayList<byte[]> packetsToBeSent = new ArrayList<byte[]>();
	
	public Sender2a(String serverName, int port, int retryTimeout, int windowSize) throws SocketException, UnknownHostException{
		
		this.port = port;
		
		// Open a socket on the client and translate host-name to IP address using DNS	
		clientSocket = new DatagramSocket();
		clientSocket.setSoTimeout(retryTimeout);
		iPAddress = InetAddress.getByName(serverName);
		this.windowSize = windowSize;
		
		System.out.println("Client: Connected to " + serverName + " on port " + port);
	}

	public void sendFile(String sendFilePath) throws InterruptedException, IOException{

		// Register the file to be sent
		File file = new File(sendFilePath);
		InputStream inputStream = new FileInputStream(file);
		byte[] fileByteArray = new byte[inputStream.available()];
		inputStream.read(fileByteArray);
		
		// Preparing the packets to be sent
		int startIndex = 0;
		while (startIndex * 1021 < fileByteArray.length){
			byte[] packet = preparePacket(startIndex, fileByteArray, startIndex * 1021);
			packetsToBeSent.add(packet);
			startIndex++;
		}
		
		// Marking the last packet with its flag set to true
		int lastIndex = packetsToBeSent.size()-1;
		byte[] lastPacket = packetsToBeSent.get(lastIndex);
		lastPacket[2] = (byte)(1);
		packetsToBeSent.set(lastIndex, lastPacket);
		
		// For calculating later the runtime of the transmission
		long startTime = System.currentTimeMillis();
		
		// 16-bit sequence number indicating the order
        int sequenceNumber = 0;
        // 16-bit sequence number for acknowledged packets
        int confirmedSequenceNumber = 0; 	
        
        int windowStart = 0;
        
        // Counter to count number of retransmissions
        int reTransmissionCount = 0;
		
        // Store packets that have already been sent once
		ArrayList<byte[]> sentPackets = new ArrayList<byte[]>();	

		while (sequenceNumber < packetsToBeSent.size()){
			
			// If the pipe is full, check for  acknowledgement else prepare and send next packet.
			if ((sequenceNumber - windowStart) > windowSize){
				
				// CHECK FOR ACKNOWLEDGEMENT
	            while (true) {

	                byte[] acknowledgement = new byte[2];
	                DatagramPacket acknowledgementPack = new DatagramPacket(acknowledgement, acknowledgement.length);

	                try {
	                    clientSocket.receive(acknowledgementPack);
	                    confirmedSequenceNumber = ((acknowledgement[0] & 0xff) << 8) + (acknowledgement[1] & 0xff);
	                    System.out.println("Client: received confirmation: " + confirmedSequenceNumber);
	                    
	                } catch (SocketTimeoutException e) {
	                	
	                    System.out.println("Client: timed out while waiting for " + confirmedSequenceNumber + " confirmation");
	                    
	                	// RESEND PACKETS
	                	int j = windowStart;
	                	while(j < sentPackets.size()){
	                		
	                		byte[] packet = new byte[1024];
	                		packet = sentPackets.get(j);
	        				DatagramPacket datagramPacket = new DatagramPacket(packet, packet.length, iPAddress, port);
	        				clientSocket.send(datagramPacket);
	        				
	        				j++;
	        				reTransmissionCount++;
	                	}
	                }

	                // If we received an acknowledgement and its sequence number is any of the ones in the pipe, shift the window
	                if ((confirmedSequenceNumber >= windowStart)) {
	                	                  
	                	windowStart = confirmedSequenceNumber;
	                    break; 
	                }
	            }
			}else{
				
				// SEND THE PACKET
				System.out.println("Client: sending sequence number: " + sequenceNumber);
				byte[] packet = packetsToBeSent.get(sequenceNumber);
				DatagramPacket datagramPacket = new DatagramPacket(packet, packet.length, iPAddress, port);
				clientSocket.send(datagramPacket);
				
				sentPackets.add(packet);
				
				sequenceNumber++;
			}
		}

		// Receive last acknowledgements
		int resendCounter = 0;
		boolean hasLastAckArrived = false;
		while (!hasLastAckArrived){
			
			// CHECK FOR ACKNOWLEDGEMENT
            while (true) {

                byte[] acknowledgement = new byte[2];
                DatagramPacket acknowledgementPack = new DatagramPacket(acknowledgement, acknowledgement.length);

                try {
                    clientSocket.receive(acknowledgementPack);
                    confirmedSequenceNumber = ((acknowledgement[0] & 0xff) << 8) + (acknowledgement[1] & 0xff);
                    System.out.println("Client: received confirmation: " + confirmedSequenceNumber);
                    resendCounter = 0;

                } catch (SocketTimeoutException e) {
                    System.out.println("Client: timed out while waiting for " + confirmedSequenceNumber + " confirmation");

                	// RESEND PACKETS
                	int i = windowStart;
                	while(i < sentPackets.size()){
                		
                		byte[] packet = new byte[1024];
                		packet = sentPackets.get(i);
                		
                		int resendSequenceNumber = ((packet[0] & 0xff) << 8) + (packet[1] & 0xff);
                		
        				DatagramPacket datagramPacket = new DatagramPacket(packet, packet.length, iPAddress, port);
        				clientSocket.send(datagramPacket);
        				System.out.println("Client: resending sequence number: " + resendSequenceNumber);
        				
        				i++;
        				reTransmissionCount++;
                	}

                	resendCounter++;

                	// If the last acknowledgement has arrived or we already resent the last packets many times
                	if ((confirmedSequenceNumber > sequenceNumber-1) || resendCounter > 20){
                		hasLastAckArrived = true;
                		break;
                	}
                }

                // If the received acknowledgement's sequence number is within the window
                if (confirmedSequenceNumber >= windowStart) {
                	
                	windowStart = confirmedSequenceNumber;

	                // If it was the last acknowledgement, exit
	            	if (confirmedSequenceNumber >= sequenceNumber-1){
	            		hasLastAckArrived = true;
	            	}

                    break;  
                }
            }
		}
		
		clientSocket.close(); 
		inputStream.close();
		System.out.println("--------------------");
		System.out.println("Image has been sent!");
		System.out.println("Packet retransmissions: " + reTransmissionCount);

        // Calculate statistics
        double totalTime = (System.currentTimeMillis() - startTime) / 1000.0;
        System.out.println("Transfer time: " + totalTime + " seconds");

        int fileSize = (fileByteArray.length) / 1024;
        System.out.println("File size: " + fileSize + " kilobytes");

        double throughput = (double) fileSize / totalTime;
        System.out.println("Throughput: " + throughput + " kilobytes/seconds");
	}
	
	public byte[] preparePacket(int sequenceNumber, byte[] fileByteArray, int startIndex){
		
		byte[] packet = new byte[1024];
		
		packet[0] = (byte)(sequenceNumber >> 8);
		packet[1] = (byte)(sequenceNumber);
		packet[2] = (byte)(0);
		
		int endIndex = (startIndex + 1021);
		
		// Taking care of the left over bytes at the end of the byte-array
		if (endIndex > fileByteArray.length){
			endIndex = startIndex + (fileByteArray.length - startIndex);
		}
		
		// Fill up the rest of the packet byte-array
		for (int i=0; i < (endIndex - startIndex); i++) {
            packet[i+3] = fileByteArray[startIndex+i];
        }
		
		return packet;
	}
	
	public static void main(String args[]) throws NumberFormatException, InterruptedException, IOException{

		String serverName = "localhost";
		int port = Integer.parseInt(args[1]);
		String fileName = args[2];
		int retryTimeout = Integer.parseInt(args[3]);
		int windowSize = Integer.parseInt(args[4]);
		
		Sender2a client = new Sender2a(serverName, port, retryTimeout, windowSize);
		client.sendFile(fileName);
	}
}