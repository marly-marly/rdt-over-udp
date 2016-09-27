
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;


public class Receiver1a extends Thread{

	private DatagramSocket serverSocket;

	public Receiver1a(int port) throws IOException{

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

		// Flag to indicate the last message
        boolean isLastMessage = false;
        
		while(!isLastMessage){

			try{
				
				// Byte array to store the whole received packet
				byte[] packet = new byte[1024]; 
				// Byte array to store the actual data (without flags)
				byte[] data = new byte[1021];

				// Receive the whole packet
				DatagramPacket receivedPacket = new DatagramPacket(packet, packet.length); 
				serverSocket.receive(receivedPacket);
				packet = receivedPacket.getData();
	            
	            int sequenceNumber = ((packet[0] & 0xFF) << 8) + (packet[1] & 0xFF);
				System.out.println("Server: sequence number received: " + sequenceNumber);

				// Retrieve data from packet
	            for (int i=3; i < packet.length ; i++) {
	                data[i-3] = packet[i];
	            }
	            
				// Write the packet data to the file (keep extending it)
	            fileWriter.write(data);
	            
	            // Check if it's the last packet that we received
	            if ((packet[2] & 0xFF) == 1) {
	            	System.out.println("Server: file received!");
	                fileWriter.close();
	                serverSocket.close();
	                isLastMessage = false;
	                break;
	            }

			}catch(SocketTimeoutException s){
				System.out.println("Server: Socket timed out!");
				serverSocket.close();
				break;
			}
		}
	}

	public static void main(final String[] args) throws IOException{

		Thread t = new Thread( new Runnable(){

			int port = Integer.parseInt(args[0]);
			String fileName = args[1];
			
				public void run() {
					Receiver1a server;
					try {
						server = new Receiver1a(port);
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
