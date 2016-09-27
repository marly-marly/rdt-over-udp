import java.io.IOException;

public class Main {

	public static void main(String [] args) throws IOException, InterruptedException{
		
		String serverName = "localhost";
		int port = 8000;
		
		String sendFilePath = "C:/Users/Marci/Desktop/JavaProject/comn2b/src/test.jpg";
		String receiveFilePath = "C:/Users/Marci/Desktop/JavaProject/comn2b/src/test_receive.jpg";
		
		int retryTimeOut = 50;
		int windowSize = 800;
		
		Receiver2b.main(new String[]{Integer.toString(port), receiveFilePath, Integer.toString(880)});
		Sender2b.main(new String[]{serverName, Integer.toString(port), sendFilePath, Integer.toString(retryTimeOut), Integer.toString(windowSize)});
	}
}
