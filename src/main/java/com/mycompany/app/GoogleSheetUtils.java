package com.mycompany.app;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.gdata.client.spreadsheet.CellQuery;
import com.google.gdata.client.spreadsheet.FeedURLFactory;
import com.google.gdata.client.spreadsheet.SpreadsheetQuery;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.client.spreadsheet.WorksheetQuery;
import com.google.gdata.data.Content;
import com.google.gdata.data.Link;
import com.google.gdata.data.batch.BatchOperationType;
import com.google.gdata.data.batch.BatchStatus;
import com.google.gdata.data.batch.BatchUtils;
import com.google.gdata.data.spreadsheet.CellEntry;
import com.google.gdata.data.spreadsheet.CellFeed;
import com.google.gdata.data.spreadsheet.ListEntry;
import com.google.gdata.data.spreadsheet.ListFeed;
import com.google.gdata.data.spreadsheet.SpreadsheetEntry;
import com.google.gdata.data.spreadsheet.SpreadsheetFeed;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.data.spreadsheet.WorksheetFeed;
import com.google.gdata.util.ServiceException;





public class GoogleSheetUtils {

	private static final String[] SCOPES_ARRAY = {
			"https://spreadsheets.google.com/feeds",
			"http://spreadsheets.google.com/feeds/",
	"https://docs.google.com/feeds/" };
	private static final List<String> SCOPES = Arrays.asList(SCOPES_ARRAY);
	private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	private static final JacksonFactory JSON_FACTORY = new JacksonFactory();
	//private static final byte[] KEY_MAT = Arrays.copyOf("MaGarVKeyEncrypt".getBytes(), 16);
	private static File jarPath;
	private static String SPREADSHEET_TITLE;
	private static String SERVICE_ID;
	private static File P12;
	private static String INPUT_WORKSHEET;
	private static String MISSING_WORKSHEET;
	private static String COMPLETED_WORKSHEET;
	
	public static String OPTIONS_WORKSHEET;



	static {
		try {
			jarPath = new File(Utils.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
			Utils.verifyConfigFile();
			FileInputStream configIn = new FileInputStream(new File(jarPath.getParent() + "/config.prop"));
			Properties prop = new Properties();
			prop.load(configIn);
			SPREADSHEET_TITLE = prop.getProperty("SpreadsheetFileName");

			INPUT_WORKSHEET = prop.getProperty("InputWorksheet");

			MISSING_WORKSHEET = prop.getProperty("MissingInfoWorksheet");
			
			OPTIONS_WORKSHEET = prop.getProperty("OptionsWorksheet");

			COMPLETED_WORKSHEET = prop.getProperty("CompletedWorksheet");


			SERVICE_ID = prop.getProperty("ServiceID");
			String p12FileName = prop.getProperty("P12KeyFile");
			configIn.close();
			P12 = new File(jarPath.getParent() + "/" + p12FileName);
		} catch (IOException | URISyntaxException e) {
			Utils.sendNotificationEmail("Error Reading Config File",
					"Unable to read the config file.\n"
							+ Utils.getStackTrace(e));
			System.exit(2);
		}

	}

	private static SpreadsheetService service = getService("MagarveyLab-GenomePipeline-0.1");
	private static SpreadsheetEntry spreadsheet = getSpreadsheet(service);	
	private static WorksheetEntry inputWorksheet= getWorksheet(service,spreadsheet, INPUT_WORKSHEET);
	private static WorksheetEntry missingInfoWorksheet= getWorksheet(service,spreadsheet, MISSING_WORKSHEET);
	private static WorksheetEntry completedWorksheet= getWorksheet(service,spreadsheet, COMPLETED_WORKSHEET);
	
	public static WorksheetEntry optionsWorksheet = getWorksheet(service,spreadsheet,OPTIONS_WORKSHEET);
	
	
	/**
	 * Takes all samples in DB where status isn't complete and displays them on GoogleSheet under Progress worksheet
	 */
	// public static void displayProgressEntries() {

	// 	List<ListEntry> progressRows = SQLiteUtils.getInProgress();

	// 	try{
	// 		while(service.getFeed(ProgressWorksheet.getListFeedUrl(), ListFeed.class).getEntries().size() != 0){
	// 			service.getFeed(ProgressWorksheet.getListFeedUrl(), ListFeed.class).getEntries().get(0).delete();
	// 		}
			
	// 		for(ListEntry e : progressRows){
	// 			service.insert(ProgressWorksheet.getListFeedUrl(), e);
	// 		}
	// 	}
	// 	catch(ServiceException | IOException e){
	// 		// write to log
	// 		Utils.writeToLog("Failed to update progress worksheet" + Utils.getStackTrace(e));
	// 		Utils.sendNotificationEmail("Failed to update progress worksheet", 
	// 				"Failed to update progress worksheet" + Utils.getStackTrace(e));
	// 	}	

	// }
	
	/**
	 * 
	 * Takes all samples in DB where status is complete and displays them on GoogleSheet under Completed worksheet
	 */
	// public static void displayCompletedEntries() {

	// 	List<ListEntry> completedRows = SQLiteUtils.getCompleted();

	// 	try{
			
			
	// 		while(service.getFeed(CompletedWorksheet.getListFeedUrl(), ListFeed.class).getEntries().size() != 0){
	// 			service.getFeed(CompletedWorksheet.getListFeedUrl(), ListFeed.class).getEntries().get(0).delete();
	// 		}
			
	// 		for(ListEntry e : completedRows){
	// 			service.insert(CompletedWorksheet.getListFeedUrl(), e);
	// 		}
	// 	}
	// 	catch(ServiceException | IOException e){
	// 		// write to log
	// 		Utils.writeToLog("Failed to update progress worksheet" + Utils.getStackTrace(e));
	// 		Utils.sendNotificationEmail("Failed to update progress worksheet", 
	// 				"Failed to update progress worksheet" + Utils.getStackTrace(e));
	// 	}	

	// }

	/**
	 * Process the input sheet. Validates each row according to the database and input directory.
	 */
	public static JSONArray processInputSheet() {
		JSONArray newFilesMap = new JSONArray();
		try {
			for (ListEntry e : service.getFeed(inputWorksheet.getListFeedUrl(), ListFeed.class).getEntries()) {
				String peakid = e.getCustomElements().getValue("peakid");
				System.out.println(peakid);
				if(peakid != null){
					String fileName=e.getCustomElements().getValue("nmrFileName");
					String researcher=e.getCustomElements().getValue("researcher");
					String instrument=e.getCustomElements().getValue("instrument");
					String organization=e.getCustomElements().getValue("organization");
					String sample=e.getCustomElements().getValue("sample");
					String username=e.getCustomElements().getValue("username");
					System.out.println(sample+" "+fileName+" "+researcher+" "+username+" "+instrument+" "+organization+" "+peakid);
					JSONObject jo = new JSONObject();
					jo.put("sample",sample);
					jo.put("nmrFileName",fileName);
					jo.put("researcher",researcher);
					jo.put("instrument",instrument);
					jo.put("organization",organization);
					jo.put("peakid",peakid);
					jo.put("username",username);
					newFilesMap.put(jo);

				}
			}
		}
		catch(ServiceException | IOException e){
			// write to log
			Utils.writeToLog("Failed to gather ready samples from Google Sheet" + Utils.getStackTrace(e));
			Utils.sendNotificationEmail("Failed to gather ready samples from Google Sheet", 
					"Failed to gather ready samples from Google Sheet " + Utils.getStackTrace(e));
		}
		// System.out.println(newFilesMap.containsKey("123456"));
		return newFilesMap;	
	}
	
	
	/**
	 * Checks input directory. If new files exist, insert or update rows in the Input worksheet
	 */
	// public static void displayInputDirs(){

	// 	List<File> inputDirs = FileUtil.getRawDirectories(FileUtil.INPUT_DIRECTORY);
	// 	List<String> inputDirNames = new ArrayList<String>();

	// 	for (Iterator<File> iter = inputDirs.listIterator(); iter.hasNext(); ) {
	// 		File f = iter.next();
	// 		inputDirNames.add(f.getName());
	// 	}

	// 	try{
	// 		for (ListEntry e : service.getFeed(inputWorksheet.getListFeedUrl(), ListFeed.class).getEntries()){
	// 			// Check if input dirs matches the sample name in the row
	// 			if( inputDirNames.contains(e.getCustomElements().getValue("directory")) ){
	// 				e.getCustomElements().setValueLocal("comments", "Raw reads found.");
	// 				inputDirNames.remove(e.getCustomElements().getValue("directory"));
	// 			}
	// 		}
	// 		for(String s : inputDirNames){
	// 			ListEntry newEntry = new ListEntry();
	// 			newEntry.getCustomElements().setValueLocal("directory", s);
	// 			newEntry.getCustomElements().setValueLocal("comments", "Raw reads found");
	// 			newEntry.getCustomElements().setValueLocal("ready", "☐");
	// 			service.insert(inputWorksheet.getListFeedUrl(), newEntry);
	// 		}	
	// 	}
	// 	catch(ServiceException | IOException e){
	// 		// write to log
	// 		Utils.writeToLog("Failed to insert input directory reads into input worksheet\n" + Utils.getStackTrace(e));
	// 		Utils.sendNotificationEmail("Failed to insert input directory reads into input worksheet", 
	// 				"Failed to insert input directory reads into input worksheet\n" + Utils.getStackTrace(e));
	// 	}
	// }

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
					Utils.sendNotificationEmail("Unable to find Worksheet", 
							"Failed to find worksheet " + worksheetName
							+ ". Check worksheet name/internet connection.\n"
							+ Utils.getStackTrace(e));
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
		Utils.sendNotificationEmail("No Worksheet Found",
				"Cannot find the worksheet " + worksheetName
				+ " or multiple Found!!");
		System.exit(2);

		return null;
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
			Utils.sendNotificationEmail("Unable to get Service",
					"Failed to get GoogleCredential.\n" + Utils.getStackTrace(e));
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
					Utils.sendNotificationEmail("Unable to find Spreadsheet",
							"Failed to find spreadsheet. Check spreadsheet filename/internet connection.\n" + Utils.getStackTrace(e));
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
		Utils.sendNotificationEmail("No Spreadsheet Found",
				"Cannot find the spreadsheet!! Make sure there is only one file with the specified name.");
		System.exit(2);
		return null;
	}


	/////////// BATCH UPDATE STUFF
	
	/**
	 * A basic struct to store cell row/column information and the associated RnCn
	 * identifier.
	 */
	private static class CellAddress {
		public final int row;
		public final int col;
		public final String content;
		public final String idString;

		/**
		 * Constructs a CellAddress representing the specified {@code row} and
		 * {@code col}.  The idString will be set in 'RnCn' notation.
		 */
		public CellAddress(int row, int col, String content) {
			this.row = row;
			this.col = col;
			this.idString = String.format("R%sC%s", row, col);

			this.content = content;
		}
	}


	public static void updateMoved(JSONArray itemsMoved){
		updateWorksheet(itemsMoved, completedWorksheet);
	}

	public static void updateMissingInfo(JSONArray itemsMissingInfo){
		updateWorksheet(itemsMissingInfo, missingInfoWorksheet);
	}


	public static void updateWorksheet(JSONArray items, WorksheetEntry worksheetToUpdate){
		long startTime = System.currentTimeMillis();
		
		// Prepare Spreadsheet Service
		SpreadsheetService ssSvc = service;
		
		
		List<String> completedHeaders = Arrays.asList("nmrFileName","peakid","sample","researcher","username","instrument","organization","comments");
		
		
		
		// TODO: Authorize the service object for a specific user (see other sections)
		boolean isSuccess = true;
		try{
		
			URL cellFeedUrl = worksheetToUpdate.getCellFeedUrl();
			CellFeed cellFeed = ssSvc.getFeed(cellFeedUrl, CellFeed.class);
			
			// Build list of cell addresses to be filled in
			List<CellAddress> cellAddrs = new ArrayList<CellAddress>();
			
			for(int header=0; header<6; header++){
				System.out.println("Getting values for header: " + completedHeaders.get(header));
				if(items!=null){
					for(int row=2; row < items.length()+2; row++){
						JSONObject temp = items.getJSONObject(row-2);
						cellAddrs.add(new CellAddress(row,header + 1,temp.getString(completedHeaders.get(header))));
						System.out.println(temp.getString(completedHeaders.get(header)));
					}
				}
			}
			Map<String, CellEntry> cellEntries = getCellEntryMap(ssSvc, cellFeedUrl, cellAddrs);
			
			
			
			CellFeed batchRequest = new CellFeed();
			for (CellAddress cellAddr : cellAddrs) {
				CellEntry batchEntry = new CellEntry(cellEntries.get(cellAddr.idString));
				batchEntry.changeInputValueLocal(cellAddr.content);
				BatchUtils.setBatchId(batchEntry, cellAddr.idString);
				BatchUtils.setBatchOperationType(batchEntry, BatchOperationType.UPDATE);
				batchRequest.getEntries().add(batchEntry);
			}
			
			System.out.println("Sending to google sheets...");
			
			// Submit the update
			Link batchLink = cellFeed.getLink(Link.Rel.FEED_BATCH, Link.Type.ATOM);

			CellFeed batchResponse = ssSvc.batch(new URL(batchLink.getHref()), batchRequest);
			
			// Check the results

			System.out.println("Checking for success...");
			
			for (CellEntry entry : batchResponse.getEntries()) {
				String batchId = BatchUtils.getBatchId(entry);
				if (!BatchUtils.isSuccess(entry)) {
					isSuccess = false;
					BatchStatus status = BatchUtils.getBatchStatus(entry);
					System.out.printf("%s failed (%s) %s\n", batchId, status.getReason(), status.getContent());
				}
			}
		}
		// }
		catch(Exception e){
			System.out.println("May not have completed...");
			e.printStackTrace();
		}
		
		
		System.out.println(isSuccess ? "\nBatch operations successful." : "\nBatch operations failed");
		System.out.printf("\n%s ms elapsed\n", System.currentTimeMillis() - startTime);
		
	}
	
	
	/**
	 * Connects to the specified {@link SpreadsheetService} and uses a batch
	 * request to retrieve a {@link CellEntry} for each cell enumerated in {@code
	 * cellAddrs}. Each cell entry is placed into a map keyed by its RnCn
	 * identifier.
	 *
	 * @param ssSvc the spreadsheet service to use.
	 * @param cellFeedUrl url of the cell feed.
	 * @param cellAddrs list of cell addresses to be retrieved.
	 * @return a map consisting of one {@link CellEntry} for each address in {@code
	 *         cellAddrs}
	 */
	public static Map<String, CellEntry> getCellEntryMap(
			SpreadsheetService ssSvc, URL cellFeedUrl, List<CellAddress> cellAddrs)
					throws IOException, ServiceException {
		CellFeed batchRequest = new CellFeed();
		for (CellAddress cellId : cellAddrs) {
			CellEntry batchEntry = new CellEntry(cellId.row, cellId.col, cellId.idString);
			batchEntry.setId(String.format("%s/%s", cellFeedUrl.toString(), cellId.idString));
			BatchUtils.setBatchId(batchEntry, cellId.idString);
			BatchUtils.setBatchOperationType(batchEntry, BatchOperationType.QUERY);
			batchRequest.getEntries().add(batchEntry);
		}

		CellFeed cellFeed = ssSvc.getFeed(cellFeedUrl, CellFeed.class);
		CellFeed queryBatchResponse =
				ssSvc.batch(new URL(cellFeed.getLink(Link.Rel.FEED_BATCH, Link.Type.ATOM).getHref()),
						batchRequest);

		Map<String, CellEntry> cellEntryMap = new HashMap<String, CellEntry>(cellAddrs.size());
		for (CellEntry entry : queryBatchResponse.getEntries()) {
			cellEntryMap.put(BatchUtils.getBatchId(entry), entry);
		}

		return cellEntryMap;
	}



}
