package com.mycompany.app;



import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;

import java.io.PrintWriter;

import java.io.StringWriter;
import java.io.Writer;
import java.net.URISyntaxException;
import java.net.URL;

import java.security.GeneralSecurityException;
import java.security.Key;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;


import org.apache.commons.codec.binary.Base64;



import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.gdata.client.spreadsheet.CellQuery;
import com.google.gdata.client.spreadsheet.SpreadsheetQuery;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.client.spreadsheet.WorksheetQuery;
import com.google.gdata.data.spreadsheet.CellEntry;
import com.google.gdata.data.spreadsheet.CellFeed;
import com.google.gdata.data.spreadsheet.SpreadsheetEntry;
import com.google.gdata.data.spreadsheet.SpreadsheetFeed;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.data.spreadsheet.WorksheetFeed;
import com.google.gdata.util.ServiceException;




public class Utils {
	
	private static final String[] SCOPES_ARRAY = {
			"https://spreadsheets.google.com/feeds",
			"http://spreadsheets.google.com/feeds/",
			"https://docs.google.com/feeds/" };
	private static final List<String> SCOPES = Arrays.asList(SCOPES_ARRAY);
	private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	private static final JacksonFactory JSON_FACTORY = new JacksonFactory();
	private static final byte[] KEY_MAT = Arrays.copyOf("MaGarVKeyEncrypt".getBytes(), 16);
	private static File jarPath;
	private static String NOTIFY_RECIPIANTS;
	private static String SPREADSHEET_TITLE;
	private static String RAW_WORKSHEET;
	private static String ASSEMBLED_WORKSHEET;
	private static String SERVICE_ID;
	private static String ERROR_EMAIL;
	private static String ERROR_EMAIL_PASSWORD;
	private static File P12;
	
	static {
		try {
			jarPath = new File(Utils.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
			verifyConfigFile();
			FileInputStream configIn = new FileInputStream(new File(jarPath.getParent() + "/config.prop"));
			Properties prop = new Properties();
			prop.load(configIn);
			NOTIFY_RECIPIANTS = prop.getProperty("ErrorRecipientsEmails");
			SPREADSHEET_TITLE = prop.getProperty("SpreadsheetFileName");
			RAW_WORKSHEET = prop.getProperty("RawWorksheet");
			ASSEMBLED_WORKSHEET = prop.getProperty("AssembledWorksheet");
			SERVICE_ID = prop.getProperty("ServiceID");
			ERROR_EMAIL = prop.getProperty("ErrorEMail");
			String p12FileName = prop.getProperty("P12KeyFile");
			ERROR_EMAIL_PASSWORD = decryptPass(prop.getProperty("ErrorEMailEncPassword"));
			configIn.close();
			
			P12 = new File(jarPath.getParent() + "/" + p12FileName);
		} catch (IOException | URISyntaxException e) {
			Utils.sendNotificationEmail("Error Reading Config File",
					"Unable to read the config file.\n"
							+ Utils.getStackTrace(e));
			System.exit(2);
		} finally {
			verifyRequiredFiles();
		}
	}
	
	
	
	/*
	 * Return status of 
	 */
	
	
	/**
	 * Get the stack trace as a string.
	 * 
	 * @param throwable
	 *            A thrown exception object.
	 * @return A string of the stack trace.
	 */
	public static String getStackTrace(Throwable throwable) {
		final Writer result = new StringWriter();
		final PrintWriter printWriter = new PrintWriter(result);
		throwable.printStackTrace(printWriter);
		return result.toString();
	}
	
	/**
	 * Get the OAuth2 login credentials.
	 * 
	 * @return Google OAuth2 login credentials
	 * @throws GeneralSecurityException
	 * @throws IOException
	 */
	private static GoogleCredential getCredentials()
			throws GeneralSecurityException, IOException {
		GoogleCredential credential = new GoogleCredential.Builder()
				.setTransport(HTTP_TRANSPORT)
				.setJsonFactory(JSON_FACTORY)
				.setServiceAccountId(SERVICE_ID)
				.setServiceAccountScopes(SCOPES)
				.setServiceAccountPrivateKeyFromP12File(P12).build();
		return credential;
	}

	/**
	 * Get the SpreadsheetService.
	 * 
	 * @param serviceName
	 *            The name for the spreadsheet service.
	 * @return a SpreadsheetService
	 * @throws GeneralSecurityException
	 * @throws IOException
	 */
	public static SpreadsheetService getService(String serviceName) {
		GoogleCredential credential = null;
		try {
			credential = getCredentials();
		} catch (GeneralSecurityException | IOException e) {
			sendNotificationEmail("Unable to get Service",
					"Failed to get GoogleCredential.\n" + getStackTrace(e));
			System.exit(2);
		}
		SpreadsheetService service = new SpreadsheetService(serviceName);
		service.setProtocolVersion(SpreadsheetService.Versions.V3);
		service.setOAuth2Credentials(credential);

		return service;
	}
	

	/**
	 * Get the SpreadsheetEntry with a specific file name.
	 * 
	 * @param service
	 *            Google SpreadsheetService. Can be obtained with
	 *            Utilities.getService()
	 * @param sheetTitleName
	 *            Spreadsheet file name
	 * @return The first SpreadsheetEntry with specified file name. Null if none
	 *         found.
	 * @throws IOException
	 * @throws ServiceException
	 */
	public static SpreadsheetEntry getSpreadsheet(SpreadsheetService service) {
		int attempts = 0;
		while (attempts < 6) {
			try {
				URL SPREADSHEET_FEED_URL = new URL(
						"https://spreadsheets.google.com/feeds/spreadsheets/private/full");
				SpreadsheetQuery query = new SpreadsheetQuery(SPREADSHEET_FEED_URL);
				query.setTitleQuery(SPREADSHEET_TITLE);
				query.setTitleExact(true);
				SpreadsheetFeed feed = service.getFeed(query, SpreadsheetFeed.class);
	
				if (feed.getEntries().size() == 1) {
					return feed.getEntries().get(0);
				} else {
					break;
				}
			} catch (IOException | ServiceException e) {
				if (++attempts == 5) {
					sendNotificationEmail("Unable to find Spreadsheet",
							"Failed to find spreadsheet. Check spreadsheet filename/internet connection.\n" + getStackTrace(e));
					System.exit(2);
				}
				System.out.println("Failed to get Spreadsheet. Reattempting...");
				try {
					TimeUnit.SECONDS.sleep(5);
				} catch (InterruptedException e1) {
					System.out.println("Sleep interrupted while getting spreadsheet.");
				}
			}
		}
		sendNotificationEmail("No Spreadsheet Found",
				"Cannot find the spreadsheet!! Make sure there is only one file with the specified name.");
		System.exit(2);
		return null;
	}
	

	/**
	 * Get a worksheet within a specified SpreadsheetEntry given a file name.
	 * Default worksheet if worksheetName is null.
	 * 
	 * @param service
	 *            Google SpreadsheetService. Can be obtained with
	 *            Utilities.getService()
	 * @param spreadsheet
	 *            - SpreadsheetEntry to search for the worksheet in.
	 * @param worksheetName
	 *            - The string representing the name of the worksheet. Default
	 *            worksheet if value is null.
	 * @return Google WorksheetEntry representing that worksheet in the
	 *         specified spreadsheet file
	 * @throws IOException
	 * @throws ServiceException
	 */
	public static WorksheetEntry getWorksheet(SpreadsheetService service,
			SpreadsheetEntry spreadsheet, String worksheetName) {

		WorksheetQuery query = new WorksheetQuery(
				spreadsheet.getWorksheetFeedUrl());
		query.setTitleQuery(worksheetName);
		query.setTitleExact(true);
		
		int attempts = 0;
		while (attempts < 6) {
			try {
				WorksheetFeed feed = service.getFeed(query, WorksheetFeed.class);
				if (feed.getEntries().size() == 1) {
					return feed.getEntries().get(0);
				} else {
					break;
				}
			} catch (IOException | ServiceException e) {
				if (++attempts == 5) {
					sendNotificationEmail("Unable to find Worksheet", 
							"Failed to find worksheet " + worksheetName
									+ ". Check worksheet name/internet connection.\n"
									+ getStackTrace(e));
					System.exit(2);
				}
				
				System.out.println("Unable to get worksheet. Retrying...");
				try {
					TimeUnit.SECONDS.sleep(5);
				} catch (InterruptedException e1) {
					System.out.println("Sleep interrupted while getting spreadsheet.");
				}
			}
		}
		System.out.println("No worksheet found" + worksheetName);
		sendNotificationEmail("No Worksheet Found",
				"Cannot find the worksheet " + worksheetName
						+ " or multiple Found!!");
		System.exit(2);
		
		return null;
	}


	public static WorksheetEntry getRawWorksheet(SpreadsheetService service,
			SpreadsheetEntry spreadsheet) {
		return getWorksheet(service, spreadsheet, RAW_WORKSHEET);
	}

	public static WorksheetEntry getAssembledWorksheet(SpreadsheetService service, SpreadsheetEntry spreadsheet) {
		return getWorksheet(service, spreadsheet, ASSEMBLED_WORKSHEET);
	}
	
	
	/*
	 * Decrypts password for service account
	 */
	private static String decryptPass(String encPass) {
		Key key = new SecretKeySpec(KEY_MAT, "AES");
		Cipher cipher;
		String decryptedPassword = null;
		try {
			cipher = Cipher.getInstance("AES");
			cipher.init(Cipher.DECRYPT_MODE, key);
			byte[] decodedValues = Base64.decodeBase64(encPass.getBytes());
			byte[] decValue = cipher.doFinal(decodedValues);
			decryptedPassword = new String(decValue);
		} catch (Exception e) {
			writeToLog("Failed to decrypt password. " + e.getMessage());
			System.exit(2);
		}
		
		return decryptedPassword;
	}
	
	/*
	 * Verify that the config file exists
	 */
	public static void verifyConfigFile() {
		File config = new File(jarPath.getParent() + "/config.prop");
		if (!config.isFile()) {
			System.out.println("Missing config file: config.prop");
			
			writeToLog("Could not find the config file: config.prop.\n"
					+ "Please make sure the config file is in the same directory as the jar.");
			System.exit(2);
		}
	}
	
	/**
	 * Write to the log file.
	 * @param errorMsg
	 */
	public static void writeToLog(String errorMsg) {
		String sep = System.getProperty("line.separator");
		errorMsg = errorMsg.replaceAll("\\n", sep);
		try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(FileUtil.PIPE_LOGS_DIRECTORY + "/log.txt", true)))) {
			Date date = new Date();
			out.println(date.toString() + " : " + errorMsg + sep + sep);
		} catch (IOException e1) {
			System.out.println("Failed to write to log.");
		}
	}
	
	/*
	 * Verify P12 file exists
	 */
	public static void verifyRequiredFiles() {
		if (!P12.isFile()) {
			System.out.println("Missing p12 key file: " + P12.getName());
			String errorMsg = "Could not find the p12 file: " + P12.getName() + "\n"
					+ "Please make sure the p12 key file is in the same directory as the jar.";
			writeToLog(errorMsg);
			sendNotificationEmail("Missing P12 Key", errorMsg);
			System.exit(2);
		}
	}
	
	/*
	 * Sends an mail
	 * @param: subject, message
	 */
	public static void sendNotificationEmail(String subject, String msg) {
		// E-mail user name and password
		final String username = ERROR_EMAIL;
		final String password = ERROR_EMAIL_PASSWORD;

		// SMTP authentication properties
		Properties props = new Properties();
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.host", "smtp.gmail.com");
		props.put("mail.smtp.port", "587");

		Session session = Session.getInstance(props,
				new javax.mail.Authenticator() {
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(username, password);
					}
				});
		
		try {
			Message message = new MimeMessage(session);
			message.setRecipients(Message.RecipientType.TO,
					InternetAddress.parse(NOTIFY_RECIPIANTS));

			message.setSubject("PRISM Pipeline Failure - " + subject);
			message.setText("There was a failure in the PRISM pipeline with the following error message:\n\n"
					+ msg
					+ "\n\n"
					+ "This e-mail was generated automatically. Please do not reply.");

			Transport.send(message);
			System.out.println("E-mail Notification Sent - " + subject);
		} catch (Exception e) {
			System.out.println("Couldn't send e-mail. Check log for error message.");
			writeToLog("Failed to send error e-mail notification with the following message:\n\n"
							+ msg + "\n\nError Message:\n" + getStackTrace(e));
			System.exit(2);
		}
	}
	
	/**
	 * Get a list of the headers (as they appear) in a Google worksheet.
	 * 
	 * @param service Google SpreadsheetService
	 * @param worksheet Google WorksheetEntry
	 * @return A list of string representation of the headers, as they appear in the sheet.
	 */
	public static LinkedList<String> getHeaders(SpreadsheetService service, WorksheetEntry worksheet) {
		LinkedList<String> headers = new LinkedList<String>();		
		CellQuery query = new CellQuery(worksheet.getCellFeedUrl());
		query.setMaximumRow(1); // query only first row = header row
		
		CellFeed cellFeed = null;
		int attempts = 0;
		while (cellFeed == null && attempts < 6) {
			try {
				cellFeed = service.getFeed(query, CellFeed.class);
				
				for (CellEntry cell : cellFeed.getEntries()) {
					String cellValue = cell.getCell().getValue();
					if (cellValue != null) {
						headers.add(cellValue);
					}
				}
				
			} catch (IOException | ServiceException e) {
				if (++attempts == 5) {
					sendNotificationEmail(
							"Failed to get Headers", 
							"Failed to get worksheet headers.\n" + getStackTrace(e));
					System.exit(2);
				}
				try {
					TimeUnit.SECONDS.sleep(5);
				} catch (InterruptedException e1) {
					System.out.println("Sleep interrupeted when getting headers...");
				}
			}
		}
		
		return headers;
	}
	
	


	
	
}
