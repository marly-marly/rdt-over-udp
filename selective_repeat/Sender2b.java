import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;

public class Sender2b {

	private boolean DEBUG = false;
	
	private int port;
	private InetAddress iPAddress;
	private DatagramSocket clientSocket;
	
	private int windowStart = 0;
	private int windowSize;
	private int retryTimeout;
	
	private ArrayList<byte[]> packetsToBeSent = new ArrayList<byte[]>();
	private Long[] packetTimers;

    // 16-bit sequence number for acknowledged packets
    private int confirmedSequenceNumber = -1;
    private int largestAckedPack = -1;
    
    // Counter to count number of retransmissions
    private int reTransmissionCount = 0;
    
    // Flag to indicate when to finish our program
    private boolean hasLastAckArrived = false;
	
	public Sender2b(String serverName, int port, int retryTimeout, int windowSize) throws SocketException, UnknownHostException{
		
		// Open a socket on the client and translate host-name to IP address using DNS	
		this.port = port;
		clientSocket = new DatagramSocket();
		clientSocket.setSoTimeout(3000);
		iPAddress = InetAddress.getByName(serverName);
		
		this.retryTimeout = retryTimeout;
		this.windowSize = windowSize;
		
		System.out.println("Client: Connected to " + serverName + " on port " + port);
	}

	public void sendFile(String sendFilePath) throws InterruptedException, IOException{

		System.out.println("Client: Sending the file...");
		
		// Register the file to be sent
		File file = new File(sendFilePath);
		InputStream inputStream = new FileInputStream(file);
		byte[] fileByteArray = new byte[inputStream.available()];
		inputStream.read(fileByteArray);
		inputStream.close();
		
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
		
		// Initialise a timer for each packet, set them to -1 by default
		packetTimers = new Long[packetsToBeSent.size()];
		for (int i = 0; i < packetTimers.length; i++){
			packetTimers[i] = (long) -1;
		}
		
		// For calculating later the runtime of the transmission
		long startTime = System.currentTimeMillis();

		// Create sender thread
		(new Thread(new TimerRunnable())).start();
		
		// 16-bit sequence number indicating the order
        int sequenceNumber = 0;
        
		// Receive packets and shift the window and send new packets
		while (!hasLastAckArrived){
			
			// If the pipe is full, check for  acknowledgement else send next packet.
			if (((sequenceNumber - windowStart) > windowSize) || (sequenceNumber == packetsToBeSent.size())){
				
				// CHECK FOR ACKNOWLEDGEMENT
				boolean hasReceivedAck = false;
	            while (!hasReceivedAck) {
			
		            byte[] acknowledgement = new byte[2];
		            DatagramPacket acknowledgementPack = new DatagramPacket(acknowledgement, acknowledgement.length);
		
		            try {
		                clientSocket.receive(acknowledgementPack);
		                confirmedSequenceNumber = ((acknowledgement[0] & 0xff) << 8) + (acknowledgement[1] & 0xff);
		                
		                if (DEBUG) System.out.println("Client: received confirmation: " + confirmedSequenceNumber);
		                if (confirmedSequenceNumber > largestAckedPack) largestAckedPack = confirmedSequenceNumber;
		                
		            } catch (SocketTimeoutException e) {
		                if (DEBUG) System.out.println("Client: timed out using socket-timeout...");
		                hasLastAckArrived = true;
		            }
		            
		            // If we received an acknowledgement and its sequence number is any of the ones in the pipe, 
		            // cancel its timer and attempt to shift the window
		            if ((confirmedSequenceNumber >= windowStart)) {
		            	
		            	hasReceivedAck = true;
		            	
		            	synchronized(packetTimers){
		            		
		            		// Set timer to null for the acknowledged packet
		                	packetTimers[confirmedSequenceNumber] = null;
		                	
		                	// Shift the window - but only until the first unacknowledged packet
		                	for (int i = windowStart; i < packetsToBeSent.size(); i++){
		                		
		                		// If it is an acknowledged packet, shift the window
		                		if (packetTimers[i] == null){
		                			// Don't shift any further than the last packet
		                			if (i != packetsToBeSent.size()-1){
		                				windowStart = i+1;
		                			}
		                		}else{
		                			break;
		                		}
		                	}
		                	
			            	// Check if we received the acknowledgement for every packet
			            	if ((windowStart == packetsToBeSent.size()-1) && (largestAckedPack == packetsToBeSent.size()-1)){
			            		hasLastAckArrived = true;
			            	}
		            	}
		            }
	            }
            }else{
            	
            	// SEND THE PACKET
            	if (DEBUG) System.out.println("Client: sending sequence number: " + sequenceNumber);
				
				try {
					sendPacket(packetsToBeSent.get(sequenceNumber));
					// Reset the timer
					long currTime = System.currentTimeMillis();
					packetTimers[sequenceNumber] = currTime;
					// Move on
					sequenceNumber++;
				} catch (IOException e) {
					e.printStackTrace();
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
	
	private class TimerRunnable implements Runnable{

		// Loop constantly to check if a packet times out and re-send accordingly
		public void run(){

			while (!hasLastAckArrived){
				
				int i = windowStart;
				while(i < packetsToBeSent.size()){
					
					// Check if the pipe is full
					if (i == windowStart + windowSize){
						//if (DEBUG) System.out.println("Client: Pipe is full!");
						break;
					}
					
					synchronized(packetTimers){

						// Get the current time in milliseconds
						long currTime = System.currentTimeMillis();

						// If the packet has already been acknowledged, move on
						if (packetTimers[i] == null){
							i++;
							continue; // Break would be faster with great window size?
						// If the packet has been sent but hasn't timed out yet, move on
						}else if (packetTimers[i] == -1){
							i++;
							continue; // Break would be faster with great window size?
						// If the packet has timed out
						}else if (currTime - packetTimers[i] >= retryTimeout){
							if (DEBUG) System.out.println("Client: REsending sequence number: " + i);
							reTransmissionCount++;
						}
						
						try {
							sendPacket(packetsToBeSent.get(i));
							// Reset the timer
							currTime = System.currentTimeMillis();
							packetTimers[i] = currTime;
							// Move on
							i++;
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}	
			}
			
			if (DEBUG) System.out.println("Client: TimerRunnable finished! ");
		}
	}
	
	private void sendPacket(byte[] packet) throws IOException{
		DatagramPacket datagramPacket = new DatagramPacket(packet, packet.length, iPAddress, port);
		clientSocket.send(datagramPacket);
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
		
		Sender2b client = new Sender2b(serverName, port, retryTimeout, windowSize);
		client.sendFile(fileName);
	}
}