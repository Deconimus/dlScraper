package visionCore.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.CopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.nio.file.CopyOption;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static java.nio.file.StandardCopyOption.*;

public class Files {
	
	public static void deleteFiles(String directory, boolean recursive) {
		deleteFiles(directory, recursive, null);
	}
	
	public static void deleteFiles(String directory, boolean recursive, Predicate<File> filter) {
		
		File dir = new File(directory);
		
		if (dir != null && dir.exists()) {
		
			File[] files = dir.listFiles();
			
			if (files.length > 0) {
				
				for (File file : files) {
					
					if (file.isDirectory()) {
						
						if (recursive) {
							
							deleteFiles(file.getPath(), true, filter);
							
						}
						
					} else {
						
						if (filter == null || filter.test(file)) {
						
							file.delete();
							
						}
						
					}
					
				}
			}
			
		}
		
	}
	
	public static int getSize(String dir) {
		
		return getSize(new File(dir));
		
	}
	
	public static int getSize(File file) {
		
		int filesize = -1;
		boolean stream = false;
		
		try {
			FileInputStream fis = new FileInputStream(file);
			filesize = fis.available();
			filesize /= 1024;
			fis.close();
		} catch (Exception e) { e.printStackTrace(); }
		
		return filesize;
		
	}
	
	public static void cleanseDir(String directory) {
		
		File dir = new File(directory);
		
		if (dir != null && dir.exists()) {
		
			File[] files = dir.listFiles();
			
			if (files.length > 0) {
				
				for (File file : files) {
					
					if (file.isDirectory()) {
						
						cleanseDir(file.getAbsolutePath());
						
					}
					
					file.delete();
					
				}
			}
			
		}
		
	}
	
	public static List<File> getFilesRecursive(String directory) {
		return getFilesRecursive(directory, null);
	}
	
	public static List<File> getFilesRecursive(String directory, Predicate<File> filter) {
		
		if (directory == null) { return null; }
		
		File dir = new File(directory);
		
		if (!dir.exists()) { return null; }
		
		List<File> files = new ArrayList<File>();
		
		File[] dirFiles = dir.listFiles();
		
		if (dirFiles != null) {
			
			for (File file : dirFiles) {
				
				if (file.isDirectory()) {
					
					files.addAll(getFilesRecursive(file.getPath(), filter));
					
				} else { 
					
					if (filter == null || filter.test(file)) {
					
						files.add(file);
						
					}
					
				}
					
			}
			
		}
		
		return files;
		
	}
	
	public static boolean copyFileUsingStream(File source, File dest) {
		
		InputStream is = null;
		OutputStream os = null;
		
		boolean copied = false;
		
		try {
			
			is = new FileInputStream(source);
			os = new FileOutputStream(dest);
			
			os = new BufferedOutputStream(os);
			
			byte[] buffer = new byte[8192];
			
			for (int length = 0; (length = is.read(buffer)) > 0;) {
				
				os.write(buffer, 0, length);
				
			}
			
			copied = true;
			
		} catch (Exception e) { e.printStackTrace(); }
		
		if (os != null) {
			try { os.flush(); } catch (Exception e) { e.printStackTrace(); }
			try { os.close(); } catch (Exception e) { e.printStackTrace(); }
		}
		
		if (is != null) {
			try { is.close(); } catch (Exception e) { e.printStackTrace(); }
		}
		
		return copied;
		
	}
	
	public static boolean moveFileUsingStream(File source, File dest) {
		
		if (copyFileUsingStream(source, dest)) {
			
			source.delete();
			return true;
			
		}
		
		return false;
		
	}
	
	public static boolean copyFileUsingChannel(File source, File dest) {
		
		FileChannel sourceChannel = null;
	    FileChannel destChannel = null;
	    
	    boolean copied = false;
	    
	    try {
	    	
	    	sourceChannel = new FileInputStream(source).getChannel();
	    	destChannel = new FileOutputStream(dest).getChannel();
	    	destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
	    	
	    	copied = true;
	    	
	    } catch (Exception e) {
	    	
	    	e.printStackTrace();
	    	
	    } finally{
	    	
	    	if (sourceChannel != null) {
	    		try { sourceChannel.close(); } catch (IOException e) { e.printStackTrace(); }
	    	}
	    	
	    	if (destChannel != null) {
	    		try { destChannel.close(); } catch (IOException e) { e.printStackTrace(); }
	    	}
	    	
	    }
	    
	    return copied;
	    
	}
	
	public static boolean moveFileUsingChannel(File source, File dest) {
		
		if (copyFileUsingChannel(source, dest)) {
			
			source.delete();
			return true;
			
		}
		
		return false;
		
	}
	
	public static void copyFileUsingOS(File source, File dest) {
		
		try { java.nio.file.Files.copy(source.toPath(), dest.toPath(), REPLACE_EXISTING); }
		catch (Exception e) { e.printStackTrace(); }
		
	}
	
	public static void moveFileUsingOS(File source, File dest) {
		
		try { java.nio.file.Files.move(source.toPath(), dest.toPath(), REPLACE_EXISTING); }
		catch (Exception e) { e.printStackTrace(); }
		
	}

}
