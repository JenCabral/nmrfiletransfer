package com.mycompany.app;

import java.io.IOException;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import java.io.*;


import java.io.PrintWriter;

import java.io.StringWriter;
import java.io.Writer;
import java.net.URISyntaxException;
import java.net.URL;

import java.security.GeneralSecurityException;
import java.security.Key;

import java.util.*;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import java.sql.Timestamp;
import java.util.Date;
import java.text.*;

import java.io.IOException;
import java.net.URL;
 
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.spreadsheet.ListEntry;
import com.google.gdata.data.spreadsheet.ListFeed;
import com.google.gdata.data.spreadsheet.SpreadsheetEntry;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.util.ServiceException;

public class FTPDownloadDirectoryTest {

private static File jarPath;
private static String FTPPASSWORD;
private static String FTPUSERNAME;
private static String FTPSERVER;
private static int FTPPORT;
private static String[] NMRUSERNAMES;
private static ArrayList<String> ALLUSERNAMES;
private static String TEMPUSERS;
private static String PREPATH;
private static String SAVEPATH;
private static String DATESTRING;
private static Timestamp LASTUPDATE;
private static String NEWTIME;
private static JSONArray RETURNED;
private static JSONArray MOVED;
private static JSONArray MISSINGINFO;
private static ArrayList<String> FILESMOVED;
private static HashMap<String,String> IDTOFILENAMEMAP;
private static JSONArray MOVEDFILESFORGRAPH;


// public static final String GOOGLE_ACCOUNT_USERNAME = "devjencabral@gmail.com"; // Fill in google account username
// public static final String GOOGLE_ACCOUNT_PASSWORD = "timmy123"; // Fill in google account password
// public static final String SPREADSHEET_URL = "https://spreadsheets.google.com/feeds/spreadsheets/1xJLaygdhH3ivSfvJAIiAQYf2rDBch0P98fP6xwJbNwI"; //Fill in google spreadsheet URI


	public static void main(String[] args) throws IOException, ServiceException{
		// GET INFO FROM THE SPREADSHEET
		ALLUSERNAMES = new ArrayList<String>();
		
		IDTOFILENAMEMAP = new HashMap();
		RETURNED = GoogleSheetUtils.processInputSheet();
		// System.out.println(returned.containsKey("123456"));
		for(int i=0; i<RETURNED.length(); i++){
			JSONObject jo = RETURNED.getJSONObject(i);
			System.out.println(jo.getString("peakid")+" "+jo.getString("nmrFileName"));
			IDTOFILENAMEMAP.put(jo.getString("peakid"),jo.getString("nmrFileName").toLowerCase());
			if (!ALLUSERNAMES.contains(jo.getString("username").toLowerCase())){
				ALLUSERNAMES.add(jo.getString("username").toLowerCase());
			}
			// Arrays.asList(yourArray).contains(yourValue)
			
		}
		
		// GET ALL THE PARAMETERS FOR THE FTP SERVER
		try{
			jarPath = new File(FTPDownloadDirectoryTest.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
			verifyConfigFile();
			FileInputStream configIn = new FileInputStream(new File(jarPath.getParent() + "/config.prop"));
			Properties prop = new Properties();
			prop.load(configIn);
			FTPPASSWORD = prop.getProperty("FTPPassword");
			FTPUSERNAME = prop.getProperty("FTPUser");
			FTPSERVER = prop.getProperty("FTPServer");
			String portTemp = prop.getProperty("FTPPort");
			FTPPORT = Integer.parseInt(portTemp);
			TEMPUSERS = prop.getProperty("NMRUsers");
			System.out.println(TEMPUSERS);
			NMRUSERNAMES = TEMPUSERS.split(",");

			// CHECK IF OTHER USERNAMES
			for(int i=0; i<NMRUSERNAMES.length; i++){
				System.out.println(NMRUSERNAMES[i]);
				if (!ALLUSERNAMES.contains(NMRUSERNAMES[i].toLowerCase())){
					ALLUSERNAMES.add(NMRUSERNAMES[i].toLowerCase());
				}
			}

			PREPATH = prop.getProperty("Prepath");
			SAVEPATH = prop.getProperty("Savepath");

		 	DATESTRING = prop.getProperty("LastUpdate");
			try{
			    SimpleDateFormat dateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy");
			    Date parsedDate = dateFormat.parse(DATESTRING);
			    LASTUPDATE = new java.sql.Timestamp(parsedDate.getTime());
			    System.out.println(LASTUPDATE);
			}catch(Exception e){
				e.printStackTrace();
			}
			
			

			configIn.close();

			// GET INFO FROM THE SERVER	
			transferFiles();
			Timestamp currentTime = new Timestamp(System.currentTimeMillis());
			SimpleDateFormat dateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy");
			String string = dateFormat.format(new Date());
			System.out.println("DATE: "+string);
			NEWTIME = string;

		} catch (IOException | URISyntaxException e) {
			System.exit(2);
		} finally {
			verifyRequiredFiles();
		}

		// UPDATE THE CONFIG FILE
		String listofUsers = "";
		for(String name : ALLUSERNAMES){
			listofUsers += name+",";
		}

		try{
			File configFile = new File(jarPath.getParent() + "/config.prop");
		    FileReader fr = new FileReader(configFile);
		    String searchDate = DATESTRING;
		    String searchNames = TEMPUSERS;

		    String replaceDate = NEWTIME;
		    String replaceNames = listofUsers;
		    String s;
		    String totalStr = "";
		    try  {
		    	BufferedReader br = new BufferedReader(fr);
		        while ((s = br.readLine()) != null) {
		            totalStr += s;
		            totalStr += "\n";
		            System.out.println(s);
		        }
		        totalStr = totalStr.replaceAll(searchDate, replaceDate);
		        totalStr = totalStr.replaceAll(searchNames, replaceNames);
		        FileWriter fw = new FileWriter(configFile);
		        System.out.println(totalStr);
		    	fw.write(totalStr);
		    	fw.close();
	    	}catch(Exception e){
	    		e.printStackTrace();
	    	}
		}catch(Exception e){
		    e.printStackTrace();
		}
		// Update the google sheets
		GoogleSheetUtils.updateMoved(MOVED);
		GoogleSheetUtils.updateMissingInfo(MISSINGINFO);
	}

private static void transferFiles(){
	FTPClient ftpClient = new FTPClient();
	FILESMOVED = new ArrayList<String>();
	try {
		// connect and login to the server
		ftpClient.connect(FTPSERVER, FTPPORT);
		System.out.println("Connected to " + FTPSERVER + ".");
  		System.out.print(ftpClient.getReplyString());
		ftpClient.login(FTPUSERNAME, FTPPASSWORD);
		System.out.println(ftpClient.getReplyString());
		// use local passive mode to pass firewall
		ftpClient.enterLocalPassiveMode();
		System.out.println(ftpClient.printWorkingDirectory());

		System.out.println("Connected");
		for(String user : ALLUSERNAMES){
			String prePath = PREPATH+"/"+user+"/data/";

			String remoteDirPath = user+"/nmr";
			String fullPath = prePath+user+"/nmr";
			FTPFile[] subFiles = ftpClient.listFiles(fullPath);
			// System.out.println(subFiles.length);
			for(int j = 0; j <subFiles.length; j++){
				// System.out.println(subFiles[j].getTimestamp().getTime().toString());
				if(subFiles[j].getTimestamp().getTime().compareTo(LASTUPDATE)>=0){
					// System.out.println("NEW FILE");
					String currentFileName = subFiles[j].getName();
					System.out.println(currentFileName);
					
					String fullPath2 = fullPath +"/"+ currentFileName;
					FILESMOVED.add(currentFileName.toLowerCase());
					// if(IDTOFILENAMEMAP.containsValue(currentFileName.toLowerCase())){
					// 	String key=IDTOFILENAMEMAP.getKey(currentFileName.toLowerCase());
					// 	IDTOFILENAMEMAP.put(key, fullPath2);
					// }
					String remoteDirPath2 = remoteDirPath+"/"+currentFileName;
					FTPUtil.downloadDirectory(ftpClient, fullPath2, "", SAVEPATH,remoteDirPath2);
				}

			}
		}

		// log out and disconnect from the server
		ftpClient.logout();
		ftpClient.disconnect();

		System.out.println("Disconnected");
	} catch (IOException ex) {
		ex.printStackTrace();
	}

}

	private static void verifyConfigFile() {
		File config = new File(jarPath.getParent() + "/config.prop");
		if (!config.isFile()) {
			System.out.println("Missing config file: config.prop");
			// JOptionPane.showMessageDialog(null, new JTextArea(
			// 		"Could not find the config file: config.prop.\n"
			// 				+ "Please make sure the config file is in the same directory as the jar."), 
			// 		"MISSING Configuration File!",
			// 		JOptionPane.ERROR_MESSAGE);
			System.exit(2);
		}
	}
	public static void verifyRequiredFiles() {
		
			// if (!P12.isFile()) {
			// 	System.out.println("Missing p12 key file: " + P12.getName());
			// 	String errorMsg = "Could not find the p12 file: " + P12.getName() + "\n"
			// 			+ "Please make sure the p12 key file is in the same directory as the jar.";
			// 	writeToLog(errorMsg);
			// 	sendNotificationEmail("Missing P12 Key", errorMsg);
			// 	System.exit(2);
			// }
	}
	
}