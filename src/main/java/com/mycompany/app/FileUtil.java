package com.mycompany.app; 
import java.io.BufferedReader;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import java.net.URISyntaxException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.spreadsheet.ListEntry;
import com.google.gdata.data.spreadsheet.ListFeed;
import com.google.gdata.data.spreadsheet.SpreadsheetEntry;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.util.ServiceException;


/**
 * This class provides some file utilities for the Magarvey Lab PRISM pipeline.
 * This includes merging files and transfering files.
 * 
 * @author Jin Chao and Nishanth Merwin
 * @email jc.jincao@gmail.com && nishanth.merwin@gmail.com
 */
public class FileUtil {
	public static String RAW_DIRECTORY; // Directory to move converted files
	public static String ASSEMBLED_DIRECTORY;
	public static String LOGS_DIRECTORY;
	public static String INPUT_DIRECTORY;
	public static String TEMP_DIRECTORY;
	public static String PIPE_LOGS_DIRECTORY;
	public static String DB_LOCATION;

	public static String PRISM_DIR;



	/**
	 * Static constructor to instantiate the required information from configuration file.
	 */
	static {
		try {
			File jarPath = new File(FileUtil.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
			FileInputStream configIn = new FileInputStream(new File(jarPath.getParent() + "/config.prop"));
			Properties prop = new Properties();
			prop.load(configIn);
			RAW_DIRECTORY = prop.getProperty("RawDirectory");
			ASSEMBLED_DIRECTORY = prop.getProperty("AssembledDirectory");
			LOGS_DIRECTORY = prop.getProperty("LogDirectory");
			INPUT_DIRECTORY = prop.getProperty("InputDirectory");			
			TEMP_DIRECTORY = prop.getProperty("TempDirectory");

			PIPE_LOGS_DIRECTORY = prop.getProperty("PipeLogDirectory");

			DB_LOCATION = prop.getProperty("DBPath");

			PRISM_DIR = prop.getProperty("PrismDir");

			configIn.close();


		} catch (IOException | URISyntaxException e) {
			Utils.sendNotificationEmail("Error Reading Config File", Utils.getStackTrace(e));
			System.exit(2);
		}
	}
	
	
	
	
	/**
	 * Removes all of the the temporary folders, assembled directories and prism files for a given sample.
	 * 
	 * Changes status to 'in queue'
	 * 
	 */
	public static void cleanStart(String sampleName) {
		// TODO Auto-generated method stub
		
		Path tempPath = new File(FileUtil.TEMP_DIRECTORY + File.separator + sampleName).toPath();
		// Path assembledPath = SQLiteUtils.getPath(sampleName, "assembled");
		// Path prismPath = SQLiteUtils.getPath(sampleName, "prism");
		
		File logDir = new File(FileUtil.LOGS_DIRECTORY + File.separator + sampleName);
		
		try{
			if(tempPath.toFile().exists()){
				FileUtils.deleteDirectory(tempPath.toFile());
			}
			// if(assembledPath.toFile().exists()){
			// 	FileUtils.deleteDirectory(assembledPath.toFile());
			// }
			// if(prismPath.toFile().exists()){
			// 	FileUtils.deleteDirectory(prismPath.toFile());
			// }
			if(logDir.exists()){
				FileUtils.deleteDirectory(logDir);
			}
			
		}
		catch( IOException e) {
			System.out.println("Failed to clear old files for sample: " + sampleName);
			e.printStackTrace();
		}
	}
	
	
	/**
	 * 
	 * Looks in the input directory for directories containing fastq files that match the given sample name.
	 * @param sampleName
	 * @return true or false if exists
	 */
	public static boolean checkExists(String sampleName) {

		List<File> rawDirs = getRawDirectories(INPUT_DIRECTORY);
		boolean exists=false;
		System.out.println("Testing Directories");

		for(File f : rawDirs){
			String dirName = f.getName();
			System.out.println(dirName);
			if(dirName.equals(sampleName)){
				exists=true;
			}
		}
		return exists;
	}


	
	/**
	 * Edits the output prism json file so that its newname is: "sample.fasta.json"
	 * Adds genus, species, strain, researcher, instrument and organization fields while also editing "filename" field
	 * 
	 * @param sample name of the sample
	 */
	public static void editJSON(String sample){
		
		// Get file location of JSON
		File jsonFile = new File(TEMP_DIRECTORY + File.separator + sample + File.separator + sample + File.separator + "PRISM");
		jsonFile = jsonFile.listFiles()[0];
		
		// edit JSON
		String jsonOut = editJSONCore(jsonFile,sample);
		
		// write to PRISM path
		// File outputFile = SQLiteUtils.getPath(sample, "prism").toFile();
		
		// Create directories
		// if(!outputFile.exists()){
		// 	outputFile.getParentFile().mkdirs();
		// }
		
		// try{
		// 	PrintWriter writer = new PrintWriter(outputFile.getAbsolutePath(), "UTF-8");
		// 	writer.print(jsonOut);
		// 	writer.close();
		// }
		// catch(IOException e){
		// 	Utils.sendNotificationEmail("Failed to edit JSON file for sample" + sample,
		// 			"Failed to edit JSON file for sample" + sample + " " + Utils.getStackTrace(e));
		// }
	}


	// 

	/**
	 * Has all the core functionalities of editing the JSON file
	 *
	 *	
	 *	// Need to change 
	 *	// Need to edit field : sample, genus, species, strain 
	 * @param jobName A string containing the name of the job
	 */
	public static String editJSONCore(File jsonFile, String sample){

		String output = null;		
		// Map<String,String> jsonFields = SQLiteUtils.getJsonFields(sample);

		// Read and edit JSON to add phylogenetic and experimental information
		try {
			// Read JSON
			StringBuilder JSON = new StringBuilder();
			BufferedReader reader = new BufferedReader( new FileReader (jsonFile));
			
			String line;
			while((line = reader.readLine()) != null){
				JSON.append(line);
			}
			reader.close();
			

			// Get input object of JSON
			JSONObject prismJSON = new JSONObject(JSON.toString());
			JSONObject inputJSON = prismJSON.getJSONObject("prism_results").getJSONObject("input");

			// inputJSON.put("genus",jsonFields.get("genus"));
			// inputJSON.put("species",jsonFields.get("species"));
			// inputJSON.put("strain",jsonFields.get("strain"));
			// inputJSON.put("researcher",jsonFields.get("researcher"));
			// inputJSON.put("instrument",jsonFields.get("instrument"));
			// inputJSON.put("organization",jsonFields.get("organization"));
			
			inputJSON.put("filename",sample + ".fasta.json");
			
			prismJSON.getJSONObject("prism_results").put("input",inputJSON);			
			output = prismJSON.toString();
		}
		catch (IOException | JSONException e){
			Utils.writeToLog("Failure to edit PRISM JSON" + Utils.getStackTrace(e));
			// SQLiteUtils.updateStatus(sample, "Error editing JSON");
		}
		return output;
	}



	/**
	 * A method that moves completed runs into the assembled directory, alongside their log files and PRISM results
	 * 
	 * @param jobName The name of the completed job
	 */
	public static void moveCompleted(String jobName){
		try{
			// move logs and scripts into assembled
			Path tempDirPath = new File(TEMP_DIRECTORY + File.separator +  jobName + File.separator +  jobName).toPath();
			File sampleLogs = new File(LOGS_DIRECTORY + File.separator + jobName);
			File sampleLogsDest = new File(tempDirPath.toString() + File.separator + "logs");
			
			
			// Path assDirPath = SQLiteUtils.getPath(jobName, "assembled");

			if(sampleLogs.exists()){
				Files.move(sampleLogs.toPath(), sampleLogsDest.toPath() , StandardCopyOption.REPLACE_EXISTING);
			}
			
			// if(!assDirPath.toFile().getParentFile().exists()){
			// 	assDirPath.toFile().getParentFile().mkdirs();
			// }
			
			// Files.move(tempDirPath, assDirPath, StandardCopyOption.REPLACE_EXISTING);

			
			File tempDirOuterPath = new File(TEMP_DIRECTORY + File.separator +  jobName);
			tempDirOuterPath.delete();
		}
		catch (IOException e){
			Utils.writeToLog("Failed to move to assembled Directory" + Utils.getStackTrace(e));
			System.out.println(Utils.getStackTrace(e));
		}
	}

	/**
	 * Cleans the temporary directory of all SLAP temporary files.
	 * 
	 * @param jobName The job name used to identify the job that is completed.
	 */
	public static void cleanTemp(String jobName){

		File tempDirOuterPath = new File(TEMP_DIRECTORY + File.separator +  jobName);
		
		File tempDirInnerPath = new File(TEMP_DIRECTORY + File.separator + jobName + File.separator + jobName);
		
		cleanAssembledDir(tempDirInnerPath, false);
		
		try{
			File[] tempFiles = tempDirOuterPath.listFiles();
			for( File f : tempFiles ) {
				if(f.isFile()){
					f.delete();
				}
			}
		}
		catch (SecurityException e){
			Utils.writeToLog("Unable to clean directory " + tempDirOuterPath + Utils.getStackTrace(e));
			// SQLiteUtils.updateStatus(jobName, "ERROR-cleanup");
		}
	}


	/**
	 * Verify that the directories exist
	 */
	public static void verifyDirectories() {
		File rawDir = new File(RAW_DIRECTORY);
		if (!(rawDir.exists() && rawDir.isDirectory())) {
			Utils.sendNotificationEmail(
					"Raw Directory Doesn't Exist", 
					"The raw directory: " + RAW_DIRECTORY + " isn't a directory or doesn't exist.");
			System.exit(2);
		}

		File assDir = new File(ASSEMBLED_DIRECTORY);
		if (!(assDir.exists() && assDir.isDirectory())) {
			Utils.sendNotificationEmail(
					"Assembled Directory Doesn't Exist", 
					"The assembled directory: " + ASSEMBLED_DIRECTORY + " isn't a directory or doesn't exist.");
			System.exit(2);
		}
		File inDir = new File(INPUT_DIRECTORY);
		if (!(inDir.exists() && inDir.isDirectory())) {
			Utils.sendNotificationEmail(
					"Input Directory Doesn't Exist", 
					"The input directory: " + INPUT_DIRECTORY + " isn't a directory or doesn't exist.");
			System.exit(2);
		}
		File tempDir = new File(TEMP_DIRECTORY);
		if (!(tempDir.exists() && tempDir.isDirectory())) {
			Utils.sendNotificationEmail(
					"Temporary Directory Doesn't Exist", 
					"The temporary directory: " + ASSEMBLED_DIRECTORY + " isn't a directory or doesn't exist.");
			System.exit(2);
		}
		File logDir = new File(LOGS_DIRECTORY);
		if (!(logDir.exists() && logDir.isDirectory())) {
			Utils.sendNotificationEmail(
					"Log Directory Doesn't Exist", 
					"The log directory: " + ASSEMBLED_DIRECTORY + " isn't a directory or doesn't exist.");
			System.exit(2);
		}
	}

	/**
	 * Get a list of all the non-empty directories inside the raw directory. The
	 * directories must contain at least 1 FASTQ file.
	 * 
	 * @param dir String path to the directory.
	 * @return ArrayList of directories with fastq files inside.
	 */
	public static ArrayList<File> getRawDirectories(String dir) {
		ArrayList<File> rawDirs = new ArrayList<File>();
		File rawDir = new File(dir);
		FileFilter filter = new FileFilter() {

			@Override
			public boolean accept(File f) {
				return f.getName().toLowerCase().endsWith(".fastq.gz");
			}
		};

		FileFilter dirFilter = new FileFilter() {

			@Override
			public boolean accept(File f) {
				return f.isDirectory();
			}
		};
		for (File f : rawDir.listFiles()) {
			// if there are fastq files in that directory, add it.
			if (f.isDirectory() && f.listFiles(filter).length > 0) {
				rawDirs.add(f);
			}
			// if there are directories, check those too
			if (f.isDirectory() && f.listFiles(dirFilter).length > 0) {
				rawDirs.addAll(getRawDirectories(f.getAbsolutePath()));
			}
		}
		return rawDirs;
	}

	/**
	 * Get the 2 reads in a directory. Returns null if there is
	 * too many of one read or path isn't valid.
	 * 
	 * @param dir Absolute path to the directory
	 * @return A File[] of two reads or null otherwise.
	 */
	public static File[] getReads(String dir) throws Exception {
		File folder = new File(dir);
		if (!folder.exists()) return null;
		File[] R1 = folder.listFiles(new FileFilter() {
			@Override
			public boolean accept(File f) {
				String name = f.getName();
				return name.toLowerCase().endsWith(".fastq.gz") && name.contains("R1");
			}
		});

		File[] R2 = folder.listFiles(new FileFilter() {
			@Override
			public boolean accept(File f) {
				String name = f.getName();
				return name.toLowerCase().endsWith(".fastq.gz") && name.contains("R2");
			}
		});

		// if there aren't the same number of reads in each folder
		// return null. This folder isn't valid.
		if (R1.length != R2.length) {
			throw new Exception("Error-Inbalanced Reads");
		}

		File firstRead = null;
		if (R1.length == 1) {
			firstRead = R1[0];
		} else {
			for (File f : R1) {
				if (f.getName().matches(".*R1_allcat\\.fastq\\.gz")) {
					firstRead = f;
					break;
				}
			}
		}

		File secondRead = null;
		if (R2.length == 1) {
			secondRead = R2[0];
		} else {
			for (File f : R2) {
				if (f.getName().matches(".*R2_allcat\\.fastq\\.gz")) {
					secondRead = f;
					break;
				}
			}
		}
		if (firstRead != null && secondRead != null) {
			File[] reads = { firstRead, secondRead };
			return reads;
		}

		return null;
	}


	/**
	 * Concatenate files using cat.
	 * @param filesToMerge
	 * @param outputFile
	 * @return True if successfully concatenated. False otherwise.
	 * @throws InterruptedException
	 * @throws IOException
	 */
	static boolean concatenateFile(File[] filesToMerge, File outputFile) throws InterruptedException, IOException {
		String[] catCmd = new String[filesToMerge.length + 1];
		catCmd[0] = "cat";
		int i = 1;
		for (; i <= filesToMerge.length; i++) {
			catCmd[i] = filesToMerge[i-1].getAbsolutePath();
		}

		// don't append to the file if it exists. Overwrite it
		FileOutputStream out = new FileOutputStream(outputFile, false);
		int exitStatus = -1;
		try {
			final Process p = Runtime.getRuntime().exec(catCmd);
			Runnable shutdown = new Runnable() {

				@Override
				public void run() {
					p.destroy();
				}	
			};

			Runtime.getRuntime().addShutdownHook(new Thread(shutdown));
			InputStream stdIn = p.getInputStream();
			byte[] b = new byte[1 << 20];
			int read = 0;
			while ((read = stdIn.read(b)) >= 0) {
				out.write(b, 0, read);
			}
			exitStatus = p.waitFor();
		} catch (IOException e) {
			throw new IOException("Unable to exectute cat command.", e);
		} catch (InterruptedException e) {
			throw new InterruptedException("Unable to concatenate files to " + outputFile.getName());
		} finally {
			out.close();
		}
		if (exitStatus != 0) {
			// delete failed output file.
			outputFile.delete();
			return false;
		}
		return true;
	}

	/**
	 * This method checks whether its the lane numbers or the last numbers that vary within a sample.
	 * If it's just lane variation, then we sort according to that
	 * If it's just final $ variation, then we sort according to that
	 * Else, we convert it to error
	 * 
	 * 
	 * theoretical: SampleName_S1_L001_R1_001.fastq.gz
	 * real: 90_AGGCAGAA-GTAAGGAG_L001_R1_001.fastq.gz
	 * 
	 * 
	 * 
	 * @param directory
	 * @return
	 */
	
	
	/**
	 * This method checks and formats reads according to the format fed into SLAPline
	 * It basically sorts the files (according to either lane number or end number) and then
	 * concatenates them all together, leaving two files:
	 * R1_allcat.fastq.gz
	 * R2_allcat.fastq.gz
	 * @param directory that contains fastq.gz files
	 * @return 
	 * @return 
	 * @throws Exception(Whether it was successful or not!
	 */
	public static void formatReads(File directory) throws Exception{
		// Gets all the fastq files
		List<File> readFiles = new ArrayList<File>();
		for( File f : directory.listFiles()){
			if (f.isDirectory()){
				continue;
			}
			Pattern fastq = Pattern.compile("^.+[.]fastq[.]gz$");
			Matcher fastqMatch = fastq.matcher(f.getName());
			if(! fastqMatch.matches()){
				continue;
			}
			readFiles.add(f);
		}
		
		// Gets all the non concatenated files
		List<File> allCat = new ArrayList<File>();
		List<File> nonCat = new ArrayList<File>();
		for (File f: readFiles){
			Pattern allCatPat = Pattern.compile("^.+_allcat[.]fastq[.]gz$");
			Matcher allCatMatch = allCatPat.matcher(f.getName());
			if(allCatMatch.matches()){
				allCat.add(f);
			}
			else{
				nonCat.add(f);
			}
		}
		
		// Handling folders that have been processed already
		if(allCat.size() == 2){
			return;
		}
		else {
			for(File f : allCat){
				f.delete();
			}
		}
		
		
		HashMap<File,String[]> fileInfoMap = createFileMap(nonCat);
		if(fileInfoMap.size() == 2){
			return;
		}
		else{
			// Figuring out if its sorted according to lane number of end number
			Set<String> laneSet = new HashSet<String>();
			Set<String> endIDSet = new HashSet<String>();
			for(String[] sArray: fileInfoMap.values()){
				laneSet.add(sArray[0]);
				endIDSet.add(sArray[1]);
			}
			
			// Doing the actual sorting
			LinkedHashMap<File,String[]> sortedMap;
			if(laneSet.size() == 1 && endIDSet.size() != 1){
				sortedMap = sortHashMapByValues(fileInfoMap,1);
			}
			else if(laneSet.size() != 1 && endIDSet.size() == 1 ){
				sortedMap = sortHashMapByValues(fileInfoMap,0);
			}
			else {
				throw new Exception("Error-Raw Read nomenclature");
			}
			
			
			// Splitting raw reads files into R1 and R2 lists
			LinkedList<File> forwardReads = new LinkedList<File>();
			LinkedList<File> reverseReads = new LinkedList<File>();
			for(File f : sortedMap.keySet()){
				String[] descriptors = sortedMap.get(f);
				if(descriptors[2].equals("1")){	
					forwardReads.add(f);
				}
				else if(descriptors[2].equals("2")){
					reverseReads.add(f);
				}
			}
			if(forwardReads.size() != reverseReads.size()){
				throw new Exception("Error-Incorrect number of reads");
			}
			
			
			// Converting to array for concatenate to file method
			File[] forwardArray = new File[forwardReads.size()];
			forwardArray = forwardReads.toArray(forwardArray);
			File[] reverseArray = new File[reverseReads.size()];
			reverseArray = reverseReads.toArray(reverseArray);
			File R1Output = new File(directory + File.separator + "R1_allcat.fastq.gz");
			File R2Output = new File(directory + File.separator + "R2_allcat.fastq.gz");
			
			concatenateFile(forwardArray, R1Output);
			concatenateFile(reverseArray, R2Output);
		}
	}
	public static LinkedHashMap<File,String[]> sortHashMapByValues(HashMap<File, String[]> passedMap, final int sortingIndex) {
		
		List<File> mapKeys = new ArrayList<File>(passedMap.keySet());
		List<String[]> mapValues = new ArrayList<String[]>(passedMap.values());
		
		
		Collections.sort(mapValues,new Comparator<String[]>(){
			@Override
			public int compare(String[] arg0, String[] arg1) {
				int arg0Int = Integer.parseInt(arg0[sortingIndex]);
				int arg1Int = Integer.parseInt(arg1[sortingIndex]);
				return Integer.compare( arg0Int , arg1Int);
			}
		});		
		Collections.sort(mapKeys);

		LinkedHashMap<File, String[]> sortedMap = new LinkedHashMap<File, String[]>();
		Iterator<String[]> valueIt = mapValues.iterator();
		while (valueIt.hasNext()) {
			String[] val = valueIt.next();
			Iterator<File> keyIt = mapKeys.iterator();

			while (keyIt.hasNext()) {
				File key = keyIt.next();
				String comp1 = passedMap.get(key).toString();
				String comp2 = val.toString();

				if (comp1.equals(comp2)){
					passedMap.remove(key);
					mapKeys.remove(key);
					sortedMap.put(key,val);
					break;
				}
			}
		}
		return sortedMap;
	}
	
	/**
	 * 
	 *  Getting the lane numbers and ending numbers and read numbers for each file
	 * @param nonCat
	 * @return A hashmap with the keys being the files and the values being attributes about the file
	 * 0 => Lane ID
	 * 1 => Ending ID
	 * 2 => read number
	 */
	private static HashMap<File, String[]> createFileMap(List<File> nonCat) throws Exception{
		HashMap<File,String[]> fileInfoMap = new HashMap<File,String[]>();
		for(File f: nonCat){
			String[] fileInfoArray = new String[3];
			
			Pattern laneID = Pattern.compile("^.+_L(\\d{3})_.+[.]fastq[.]gz$");
			Matcher laneMatch = laneID.matcher(f.getName());
			if(laneMatch.matches()){
				String laneNum = laneMatch.group(1);
				fileInfoArray[0] = laneNum;
			}
			
			Pattern endID = Pattern.compile("^.+_([0-9]{3})[.]fastq[.]gz$");
			Matcher endMatch = endID.matcher(f.getName());
			if(endMatch.matches()){
				String endNum = endMatch.group(1);
				fileInfoArray[1] = endNum;
			}

		
			Pattern readNumPat = Pattern.compile("^.+_R([12])_.+[.]fastq[.]gz$");
			Matcher readMatch = readNumPat.matcher(f.getName());
			if(readMatch.matches()){
				String readNum = readMatch.group(1);
				fileInfoArray[2] = readNum;
			}
			
			
			// If there are non valid fastq files in the pipeline, this just ignores them and continues
			// Non valid as defined as not matching any of the above criteria
			boolean validFile = true;
			for( String s : fileInfoArray){
				if(s == null){
					validFile = false;
				}
			}
			if(validFile){
				fileInfoMap.put(f, fileInfoArray);
			}
			else{
				continue;
			}
		}
		return fileInfoMap;
	}
	
	/**
	 * Get a map of the files to merge with the key representing
	 * the filename of those files.
	 * @param directory
	 * @return
	 */
	public static HashMap<String, File[]> getFilesToMerge(File directory) {
		HashMap<String, File[]> filesToMerge = new HashMap<String, File[]>();
		ArrayList<String> fileNameToCheck = new ArrayList<String>();
		
		
		
		// Check if an allcat.fastq.gz exists. if so, skip all
		
		// Check if 
		
		
		List<File> files = Arrays.asList(directory.listFiles(new FileFilter() {
			@Override
			public boolean accept(File f) {
				String fileName = f.getName();
				if (f.isFile()) {
					if (fileName.toLowerCase().contains("allcat.fastq.gz")) return false;
					if (fileName.toLowerCase().endsWith(".fastq.gz")) return true;

				}
				return false;
			}
		}));

		for (File f : files) {
			String name = f.getName().replaceAll("(.+?)\\..+", ("$1"));
			String noDuplicateName = name.replaceAll("(.+)_[0-9]{3}", "$1");
			if (!fileNameToCheck.contains(noDuplicateName)) {
				fileNameToCheck.add(noDuplicateName);
			}
		}
		
		for (final String fileName : fileNameToCheck) {
			File[] multiples = directory.listFiles(new FileFilter() {
				@Override
				public boolean accept(File f) {
					return f.getName().startsWith(fileName) && !f.getName().contains("allcat.fastq.gz");
				}
			});

			for( File f: multiples){
				System.out.println("It thinks these are mutiples: " + f.getName());
			}
			
			// ensure that the files are sorted in order
			// Arrays.sort(multiples, new NaturalOrderComparator());	
			if (multiples.length > 1) {
				filesToMerge.put(fileName, multiples);
			}
		}
		
		System.out.println(filesToMerge);
		

		return filesToMerge;
	}

	/**
	 * Read the QUAST report TSV in the assembled path and return the best contig file from the assemblies.
	 * Rough implementation only. Return null it can't read the QUAST report.
	 * @param assembledDir Directory to where SLAPline will output to.
	 * @return File representing the best assembly in FASTA format.
	 * @throws IOException 
	 */
	public static File getBestContig(String assembledDir) throws IOException {

		File assembledDirectory = new File(assembledDir);

		File quastReport = new File(assembledDirectory.getAbsolutePath() + "/Output/QUAST_report.tsv");
		if (!quastReport.exists()) {
			return null;
		}

		BufferedReader reader = new BufferedReader(new FileReader(quastReport));
		String[] headers = reader.readLine().split("\t");
		int size = headers.length;
		headers = Arrays.copyOfRange(headers, 1, size);
		String[] contigs0bp = Arrays.copyOfRange(reader.readLine().split("\t"), 1, size);
		String[] contigs1000bp = Arrays.copyOfRange(reader.readLine().split("\t"), 1, size);
		String[] totalLen0bp = Arrays.copyOfRange(reader.readLine().split("\t"), 1, size);
		String[] totalLen1000bp = Arrays.copyOfRange(reader.readLine().split("\t"), 1, size);
		String[] contigs = Arrays.copyOfRange(reader.readLine().split("\t"), 1, size);
		String[] largestContig = Arrays.copyOfRange(reader.readLine().split("\t"), 1, size);
		String[] totalLength = Arrays.copyOfRange(reader.readLine().split("\t"), 1, size);
		String[] gcPerc = Arrays.copyOfRange(reader.readLine().split("\t"), 1, size); // not used for analysis
		String[] N50 = Arrays.copyOfRange(reader.readLine().split("\t"), 1, size);
		String[] N75 = Arrays.copyOfRange(reader.readLine().split("\t"), 1, size);
		String[] Nper100kbp = Arrays.copyOfRange(reader.readLine().split("\t"), 1, size);
		reader.close();
		double[] bestContigIndex = new double[headers.length];

		bestContigIndex[getMin(contigs)] ++;
		bestContigIndex[getMin(contigs0bp)] ++;
		bestContigIndex[getMin(contigs1000bp)] ++;
		bestContigIndex[getMax(largestContig)] ++;
		bestContigIndex[getMax(totalLength)] ++;
		bestContigIndex[getMax(totalLen0bp)] ++;
		bestContigIndex[getMax(totalLen1000bp)] ++;
		//bestContigIndex[getMax(gcPerc)]++;
		bestContigIndex[getMax(N50)] ++;
		bestContigIndex[getMax(N75)] ++;
		bestContigIndex[getMin(Nper100kbp)]++;


		double max = Double.MIN_VALUE;
		int best = 0;
		for (int i = 0; i < bestContigIndex.length; i++) {
			if (bestContigIndex[i] > max) {
				max = bestContigIndex[i];
				best = i;
			}
		}
		File bestFile = new File(assembledDirectory.getAbsolutePath() + "/Output/Contigs/" + headers[best] + ".fasta");
		return bestFile;
	}

	/**
	 * Parse an array of String of numbers and return the index of the largest one.
	 * @param array Array of strings of numbers.
	 * @return Index of largest number.
	 */
	private static int getMax(String[] array) {
		double max = Double.NEGATIVE_INFINITY;
		int best = 0;
		for (int i = 0; i < array.length; i++) {
			double temp = Double.parseDouble(array[i]);
			if (temp > max) {
				max = temp;
				best = i;
			}

		}
		return best;
	}

	/**
	 * Parse an array of String of numbers and return the index of the smallest one.
	 * @param array Array of strings of numbers.
	 * @return Index of the smallest number.
	 */
	private static int getMin(String[] array) {
		double max = Double.POSITIVE_INFINITY;
		int best = 0;
		for (int i = 0; i < array.length; i++) {
			double temp = Double.parseDouble(array[i]);
			if (temp < max) {
				max = temp;
				best = i;
			}

		}
		return best;
	}

	/**
	 * Delete everything inside a folder except for quast_report and contigs.
	 * 
	 * @param assembledDir Directory to clean.
	 * @param cleanSubDirectory True to clean every sub folder under assembledDir.
	 */
	public static void cleanAssembledDir(File assembledDir, boolean cleanSubDirectory) {
		FileFilter filter = new FileFilter() {
			@Override
			public boolean accept(File f) {
				if (f.getName().toLowerCase().contains("contigs.fasta")) return false;
				if (f.getName().toLowerCase().contains("quast_report.tsv")) return false;
				return true;
			}

		};
		for (File f : assembledDir.listFiles(filter)) {
			if (f.isDirectory()) {
				if (cleanSubDirectory) cleanAssembledDir(f, true);
			} else {
				f.delete();
			}

			if (f.isDirectory() && f.listFiles().length == 0) {
				f.delete();
			}
		}
	}



	/**
	 * Moves raw files from the input directory into the sample directory, under the organization name and month according to SQL database
	 * 
	 * @param sampleName
	 */
	public static void MoveRawGenomes(String sampleName, String directory) {

		File sampleDir = new File(INPUT_DIRECTORY + File.separator + directory);
		Path inputPath = sampleDir.toPath();
		// Path rawPath = SQLiteUtils.getPath(sampleName, "raw");

		// try {
		// 	if(!rawPath.toFile().exists()){
		// 		rawPath.toFile().mkdirs();
		// 	}
		// 	Files.move(inputPath, rawPath, StandardCopyOption.REPLACE_EXISTING);

		// } catch (IOException e) {
		// 	Utils.writeToLog("Failed to move data from input to raw directory for: " + sampleName + Utils.getStackTrace(e));
		// 	Utils.sendNotificationEmail("Failed to move data from input to raw directory for: " + sampleName,
		// 			"Failed to move data from input to raw directory for: " + sampleName + Utils.getStackTrace(e));
		// }
	}




}
