package main;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;


public class QosProgram {

	
	static String ip="203.142.105.18";
	static String filename="C:/Users/ranger.kao/Dropbox/workspace/addonQos/src/Qosfile.txt";
	static int waitTime=7000;

	public static void main(String args[]) throws InterruptedException{
		
		if(args.length<1){
			System.out.println("沒有ip參數");
			return;
		}
		if(args.length<2){
			System.out.println("沒有檔案名稱");
			return;
		}
		ip=args[0];
		filename=args[1];
		
		
		if(args.length>=3){
			waitTime=Integer.parseInt(args[2])*1000;
		}
		
		readtxt(filename);
		
	}
	
	public static void readtxt(String filePath) throws InterruptedException {
			
		BufferedReader reader = null;
		String url="";
		String param="";
		String result="";
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), "UTF-8")); 
			// 指定讀取文件的編碼格式，以免出現中文亂碼
			if(reader==null){
				System.out.println("檔案不存在");
				return;
			}
			String str = null;
			SimpleDateFormat sdf = new SimpleDateFormat("dd---yyyy.HH:mm:ss");
			String dString=sdf.format(new Date());
			int dm=Calendar.getInstance().get(Calendar.MONTH)+1;
			switch(dm){
				case 1:
					dString=dString.replaceAll("---", "-JAN-");//Jan, Feb, Mar, Apr, May, Jun, Jul, Aug, Sep, Oct, Nov, Dec
					break;
				case 2:
					dString=dString.replaceAll("---", "-FEB-");
					break;
				case 3:
					dString=dString.replaceAll("---", "-MAR-");
					break;
				case 4:
					dString=dString.replaceAll("---", "-APR-");
					break;
				case 5:
					dString=dString.replaceAll("---", "-MAY-");
					break;
				case 6:
					dString=dString.replaceAll("---", "-JUN-");
					break;
				case 7:
					dString=dString.replaceAll("---", "-JUL-");
					break;
				case 8:
					dString=dString.replaceAll("---", "-AUG-");
					break;
				case 9:
					dString=dString.replaceAll("---", "-SEP-");
					break;
				case 10:
					dString=dString.replaceAll("---", "-OCT-");
					break;
				case 11:
					dString=dString.replaceAll("---", "-NOV-");
					break;
				case 12:
					dString=dString.replaceAll("---", "-DEC-");
					break;
				default:
			}
			
			
			while ((str = reader.readLine()) != null) {
				
				System.out.println(str);
				String s=str.trim();

				String[] ims=s.split(",");
				
				if(s.length()<2)
					continue;
				
				url=	"http://"+ip+"/mvno_api/MVNO_UPDATE_QOS";
				param="VERSION="+"1"+"&MSISDN="+ims[1]+"&IMSI="+ims[0]+"&DATE_TIME="+dString+"&VENDOR="+"S"+"&ACTION="+"A"+"&PLAN="+"2"+"";
				result =HttpPost(url,param,"");
				System.out.println(url+"?"+param+"   \nresult:"+result);

				Thread.sleep(waitTime);
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.out.println("send url:"+url+"?"+param+"<br>"+"result:"+result+"<br>"+e.getMessage());
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("send url:"+url+"?"+param+"<br>"+"result:"+result+"<br>"+e.getMessage());
		} finally {
			try {
				if(reader!=null){
					reader.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	
	public static String HttpPost(String url,String param,String charset) throws IOException{
		URL obj = new URL(url);
		
		if(charset!=null && !"".equals(charset))
			param=URLEncoder.encode(param, charset);
		
		
		HttpURLConnection con =  (HttpURLConnection) obj.openConnection();
 
		//add reuqest header
		/*con.setRequestMethod("POST");
		con.setRequestProperty("User-Agent", USER_AGENT);
		con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");*/
 
		// Send post request
		con.setDoOutput(true);
		DataOutputStream wr = new DataOutputStream(con.getOutputStream());
		wr.writeBytes(param);
		wr.flush();
		wr.close();
 
		int responseCode = con.getResponseCode();
		System.out.println("\nSending 'POST' request to URL : " + url);
		System.out.println("Post parameters : " + param);
		System.out.println("Response Code : " + responseCode);
 
		BufferedReader in = new BufferedReader(
		        new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();
 
		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
 
		//print result
		return(response.toString());
	}
}
