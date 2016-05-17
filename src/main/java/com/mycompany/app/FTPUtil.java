package com.mycompany.app;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

/**
 * This utility class implements a method that downloads a directory completely
 * from a FTP server, using Apache Commons Net API.
 *
 * @author www.codejava.net
 *
 */
public class FTPUtil {

	/**
	 * Download a whole directory from a FTP server.
	 * @param ftpClient an instance of org.apache.commons.net.ftp.FTPClient class.
	 * @param parentDir Path of the parent directory of the current directory being
	 * downloaded.
	 * @param currentDir Path of the current directory being downloaded.
	 * @param saveDir path of directory where the whole remote directory will be
	 * downloaded and saved.
	 * @throws IOException if any network or IO error occurred.
	 */
	public static void downloadDirectory(FTPClient ftpClient, String parentDir,String currentDir, String saveDir, String pathToUse) throws IOException {
		String dirToList = parentDir;
		if (!currentDir.equals("")) {
			dirToList += "/" + currentDir;
		}
		// System.out.println(dirToList);
		FTPFile[] subFiles = ftpClient.listFiles(dirToList);
		// System.out.println(dirToList);
		// System.out.println(subFiles);
		// System.out.println(subFiles.length);
		if (subFiles != null && subFiles.length > 0) {
			for (FTPFile aFile : subFiles) {
				String currentFileName = aFile.getName();
				// System.out.println(currentFileName);
				if (currentFileName.equals(".") || currentFileName.equals("..")) {
					// skip parent directory and the directory itself
					continue;
				}
				String filePath = parentDir + "/" + currentDir + "/"
						+ currentFileName;
				if (currentDir.equals("")) {
					filePath = parentDir + "/" + currentFileName;
				}

				String newDirPath = pathToUse + File.separator + currentFileName;
				if (currentDir.equals("")) {
					newDirPath = saveDir + pathToUse + File.separator
							  + currentFileName;
				}
				// System.out.println("NEW DIRECTORY PATH: "+newDirPath);
				if (aFile.isDirectory()) {
					// create the directory in saveDir
					File newDir = new File(newDirPath);
					boolean created = newDir.mkdirs();
					if (created) {
						// System.out.println("CREATED the directory: " + newDirPath);
					} else {
						// System.out.println("COULD NOT create the directory: " + newDirPath);
					}

					// download the sub directory
					downloadDirectory(ftpClient, dirToList, currentFileName,
							saveDir, newDirPath);
				} else {
					// download the file
					boolean success = downloadSingleFile(ftpClient, filePath,
							newDirPath);
					if (success) {
						// System.out.println("DOWNLOADED the file: " + filePath);
						// System.out.println("To directory: "+newDirPath);
					} else {
						// System.out.println("COULD NOT download the file: "+ filePath);
					}
				}
			}
		}
	}

	/**
	 * Download a single file from the FTP server
	 * @param ftpClient an instance of org.apache.commons.net.ftp.FTPClient class.
	 * @param remoteFilePath path of the file on the server
	 * @param savePath path of directory where the file will be stored
	 * @return true if the file was downloaded successfully, false otherwise
	 * @throws IOException if any network or IO error occurred.
	 */
	public static boolean downloadSingleFile(FTPClient ftpClient,
			String remoteFilePath, String savePath) throws IOException {
		// System.out.println(savePath);
		File downloadFile = new File(savePath);

		File parentDir = downloadFile.getParentFile();
		if (!parentDir.exists()) {
			parentDir.mkdir();
		}

		OutputStream outputStream = new BufferedOutputStream(
				new FileOutputStream(downloadFile));
		try {
			ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
			return ftpClient.retrieveFile(remoteFilePath, outputStream);
		} catch (IOException ex) {
			throw ex;
		} finally {
			if (outputStream != null) {
				outputStream.close();
			}
		}
	}
}