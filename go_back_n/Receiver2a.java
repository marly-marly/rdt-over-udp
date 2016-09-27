import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;


public class Receiver2a extends Thread{

	private DatagramSocket serverSocket;

	public Receiver2a(int port) throws IOException{

		// Open a datagram socket on the server 
		serverSocket = new DatagramSocket(port);
		serverSocket.setSoTimeout(10000);
	}
	
	public void receiveFile(String receiveFilePath) throws IOException{
		
		// Create the file to be received
		File file = new File(receiveFilePath);
		file.getParentFile().mkdirs();
		file.createNewFile();
		
		FileOutputStream fileWriter = null;
		fileWriter = new FileOutputStream(file);
        
        // Store sequence numbers
        int sequenceNumber = 0;
        int expectedSequenceNumber = -1;
        
		while(true){

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
				System.out.println("Server: sequence number received: " + sequenceNumber);
	            
	            if (sequenceNumber == (expectedSequenceNumber+1)) {
	
	                // Retrieve data from packet
		            for (int i=3; i < packet.length ; i++) {
		                data[i-3] = packet[i];
		            }
	
		            // Write the packet data to the file (keep extending it)
		            fileWriter.write(data);
	
	                // Send acknowledgement
					expectedSequenceNumber++;
	                sendMessage(expectedSequenceNumber, serverSocket, address, port);

	                // Check for last message
		            if ((packet[2] & 0xFF) == 1) {
		            	fileWriter.close();
		                serverSocket.close();
		                break;
		            }
	                
	            } else {
	                
	                //Send acknowledgement
	                sendMessage(expectedSequenceNumber, serverSocket, address, port);
	                System.out.println("Server: expected sequence number: " + (expectedSequenceNumber + 1) + " but received " + sequenceNumber);
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
	}
	
	public static void sendMessage(int sequenceNumber, DatagramSocket socket, InetAddress address, int port) throws IOException {
		
	    // Send confirmation of receiving a packet
	    byte[] packet = new byte[2];
	    packet[0] = (byte)(sequenceNumber >> 8);
	    packet[1] = (byte)(sequenceNumber);
	    
	    DatagramPacket acknowledgement = new  DatagramPacket(packet, packet.length, address, port);
	    socket.send(acknowledgement);
	    System.out.println("Server: sending sequence number = " + sequenceNumber);
	}

	public static void main(final String [] args){

		Thread t = new Thread( new Runnable(){

			int port = Integer.parseInt(args[0]);
			String fileName = args[1];
			
			public void run() {
				Receiver2a server;
				try {
					server = new Receiver2a(port);
					server.receiveFile(fileName);
				} catch (NumberFormatException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
		});
		t.start();
	}
}
