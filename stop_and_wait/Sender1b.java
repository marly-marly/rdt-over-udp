import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class Sender1b {

	private int port;
	private DatagramSocket clientSocket;
	private InetAddress iPAddress;
	
	public Sender1b(String serverName, int port, int retryTimeout) throws SocketException, UnknownHostException{
		
		this.port = port;
		
		// Open a socket on the client and translate host-name to IP address using DNS	
		clientSocket = new DatagramSocket();
		clientSocket.setSoTimeout(retryTimeout);
		iPAddress = InetAddress.getByName(serverName); 
		
		System.out.println("Client: Connected to " + serverName + " on port " + port);
	}

	public void sendFile(String sendFilePath) throws InterruptedException, IOException{

		// Register the file to be sent
		File file = new File(sendFilePath);
		InputStream inputStream = new FileInputStream(file);
		byte[] fileByteArray = new byte[inputStream.available()];
		inputStream.read(fileByteArray);
		
		// For calculating later the runtime of the transmission
		long startTime = System.currentTimeMillis();
		
        int sequenceNumber = 0; // 16-bit sequence number indicating the order
        boolean isLastPacket = false;
        int lastTransmissionCounter = 0;
     
        int confirmedSequenceNumber = 0; // 16-bit sequence number for acknowledged packets
        int reTransmissionCount = 0; // Counter to count number of retransmissions

		byte[] packet = new byte[1024];
		
		// Breaking the byte-array of the image into pieces
		int startIndex = 0;
		while (startIndex < fileByteArray.length){
			
			packet[0] = (byte)(sequenceNumber >> 8);
			packet[1] = (byte)(sequenceNumber);
			packet[2] = (byte)(0);
			
			int endIndex = (startIndex + 1021);
			
			// Taking care of the left over bytes at the end of the byte-array
			if (endIndex > fileByteArray.length){
				
				endIndex = startIndex + (fileByteArray.length - startIndex);
				packet[2] = (byte)(1); // Flag to indicate last packet
				isLastPacket = true;
				System.out.println("Client: Last packet is about to be sent");
			}
			
			// Fill up the rest of the packet byte-array
			for (int i=0; i < (endIndex - startIndex); i++) {
                packet[i+3] = fileByteArray[startIndex+i];
            }
			
			// Send packet
			DatagramPacket datagramPacket = new DatagramPacket(packet, packet.length, iPAddress, port);
			clientSocket.send(datagramPacket);
			System.out.println("Client: sending sequence number: " + sequenceNumber);
			
			// Packet confirmation from the server
            boolean isCorrectPacket = false;
            boolean hasReceivedPacket = false;

            while (!isCorrectPacket) {

            	// Break the loop if we attempted to resend the last packet many times
            	if (lastTransmissionCounter >= 50){
            		break;
            	}

            	// Must check for acknowledgement
                byte[] confirmation = new byte[2];
                DatagramPacket confirmationPacket = new DatagramPacket(confirmation, confirmation.length);

                try {
                	
                    clientSocket.receive(confirmationPacket);
                    hasReceivedPacket = true;
                    
                    confirmedSequenceNumber = ((confirmation[0] & 0xff) << 8) + (confirmation[1] & 0xff);
                    System.out.println("Client: received confirmation: " + confirmedSequenceNumber);
                    
                } catch (SocketTimeoutException e) {
                	
                	if (isLastPacket){
                		lastTransmissionCounter++;
                		System.out.println("Client: remaining attempts for last re-transmission: " + (50 - lastTransmissionCounter));
                	}
                	hasReceivedPacket = false;
                    System.out.println("Client: timed out while waiting for " + confirmedSequenceNumber + " confirmation");
                }

                // If we received a confirmation and its sequence number matches, move on to sending the next packet
                if ((hasReceivedPacket) && (confirmedSequenceNumber == sequenceNumber)) {
                	
                    isCorrectPacket = true;
                    break;
                } else {
                	
                    clientSocket.send(datagramPacket);

                    reTransmissionCount += 1;
                }
            }
			
			startIndex += 1021;
			sequenceNumber++;
		}
		
		clientSocket.close(); 
		inputStream.close();
		
		System.out.println("Image has been sent!");

		System.out.println("Packet retransmissions: " + reTransmissionCount);

        // Calculate the average throughput
        long totalTime = (System.currentTimeMillis() - startTime) / 1000;
        System.out.println("Transfer time: " + totalTime + " seconds");

        int fileSize = (fileByteArray.length) / 1024;
        System.out.println("File size: " + fileSize + " kilobytes");

        double throughput = (double) fileSize / totalTime;
        System.out.println("Throughput: " + throughput + " kilobytes/seconds");
	}
	
	public static void main(String args[]) throws NumberFormatException, InterruptedException, IOException{

		String serverName = "localhost";
		int port = Integer.parseInt(args[1]);
		String fileName = args[2];
		int retryTimeout = Integer.parseInt(args[3]);
		
		Sender1b client = new Sender1b(serverName, port, retryTimeout);
		client.sendFile(fileName);
	}
}
