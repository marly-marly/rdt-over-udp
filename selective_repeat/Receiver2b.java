import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;


public class Receiver2b extends Thread{

	private boolean DEBUG = false;
	
	private DatagramSocket serverSocket;
	private HashMap<Integer,byte[]> packetsReceived = new HashMap<Integer,byte[]>();
	private HashMap<Integer,Boolean> packetCounter = new HashMap<Integer,Boolean>();
	private int finalSize;
	private boolean hasReceivedLastPack = false;
	private int writeCounter = 0;
	private int windowSize;
	private int windowStart;
	
	public Receiver2b(int port, int windowSize) throws IOException{

		// Open a datagram socket on the server 
		serverSocket = new DatagramSocket(port);
		serverSocket.setSoTimeout(10000);
		this.windowSize = windowSize;
	}
	
	public void receiveFile(String receiveFilePath) throws IOException{
		
		// Create the file to be received
		File file = new File(receiveFilePath);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		file.getParentFile().mkdirs();
		file.createNewFile();
		
		FileOutputStream fileWriter =  new FileOutputStream(file);
        
        // Store sequence numbers
        int sequenceNumber = 0;
        
		while(!hasReceivedLastPack){
			
			try{
				// Byte array to store the whole received packet
				byte[] packet = new byte[1024];
				// Byte array to store the actual data (without flags)
				byte[] data = new byte[1021];

				// Receive the whole packet
				DatagramPacket receivedPacket = new DatagramPacket(packet, packet.length);
				serverSocket.receive(receivedPacket);
				
				// Get port and address for sending confirmation
				InetAddress address = receivedPacket.getAddress();
	            int port = receivedPacket.getPort();
				
				sequenceNumber = ((packet[0] & 0xFF) << 8) + (packet[1] & 0xFF);
				if (DEBUG) System.out.println("Server: sequence number received: " + sequenceNumber);
				
				// Check if received packet is within the window
				if ((sequenceNumber >= windowStart) && (sequenceNumber < windowStart + windowSize)){
					
					if ((sequenceNumber <= windowStart + windowSize)){
						// Retrieve data from packet
			            for (int i = 3; i < packet.length ; i++) {
			                data[i-3] = packet[i];
			            }
			            
		            	packetsReceived.put(sequenceNumber,data);
		            	packetCounter.put(sequenceNumber, true);
		            	
		            	int i = windowStart;
		            	// Shift the window
		            	while (true){
		            		if (packetCounter.get(i) != null){
		            			windowStart++;
		            			i++;
		            		}else{
		            			break;
		            		}
		            	}
		            	
		            	// Send acknowledgement
		                sendMessage(sequenceNumber, serverSocket, address, port);

		                // Check for last message
			            if ((packet[2] & 0xFF) == 1) {
			            	finalSize = sequenceNumber+1;
			            }
			            
			            // Check if all packets have been received
			            if (packetsReceived.size() == finalSize){
			            	hasReceivedLastPack = true;
			            }
					}
				}
	        }catch(SocketTimeoutException s){
				System.out.println("Server: Socket timed out!");
				serverSocket.close();
				break;
			}catch(IOException e){
				e.printStackTrace();
				break;
			}
		}
		
		// Write the final image
		ArrayList<Integer> keys = new ArrayList<Integer>(packetsReceived.keySet());
		Collections.sort(keys);
		if (DEBUG) System.out.println(Arrays.toString(keys.toArray()));
		for (Integer key : keys){
			baos.write(packetsReceived.get(key));
			writeCounter++;
    	}
		
		fileWriter.write(baos.toByteArray());
		
    	fileWriter.close();
        serverSocket.close();
        if (DEBUG) System.out.println("Server: Number of packets written into the file: " + writeCounter);
        if (DEBUG) System.out.println("Server: Receiver socket closed!");
	}
	
	public void sendMessage(int sequenceNumber, DatagramSocket socket, InetAddress address, int port) throws IOException {
		
	    // Send confirmation of receiving a packet
	    byte[] packet = new byte[2];
	    packet[0] = (byte)(sequenceNumber >> 8);
	    packet[1] = (byte)(sequenceNumber);
	    
	    DatagramPacket acknowledgement = new  DatagramPacket(packet, packet.length, address, port);
	    socket.send(acknowledgement);
	}

	public static void main(final String [] args){

		Thread t = new Thread( new Runnable(){

			int port = Integer.parseInt(args[0]);
			String fileName = args[1];
			int windowSize = Integer.parseInt(args[2]);
			
			public void run() {
				Receiver2b server;
				try {
					server = new Receiver2b(port, windowSize);
					server.receiveFile(fileName);
				}catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		t.start();
	}
}
