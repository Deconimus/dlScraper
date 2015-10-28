package main;

import java.io.File;
import java.io.FileWriter;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;

public class Gronkh implements CustomScraper {

	/** Older pattern: 
	 * "Let's Play {gametitle} #{epnr 3 digits} [Deutsch][HD] - {eptitle}" */
	public static final int PATTERN_0 = 0;
	
	/** Current pattern as of 2015: 
	 * "{gametitle caps} [{epnr}] - {eptitle} [unicode-*] Let's Play {gametitle}" */
	public static final int PATTERN_1 = 1;
	
	public static void load() {
		
		Main.customScrapers.put("gronkh", new Gronkh());
	}
	
	@Override
	public int[] getEpisodeInfo(String fileName) {

		int pattern = getPattern(fileName);
		
		fileName = fileName.substring(0, fileName.lastIndexOf('.')).trim().toLowerCase();
		
		String epNum = "";
		
		if (pattern == PATTERN_1) {
			
			epNum = fileName.substring(fileName.indexOf('[')+1, fileName.indexOf(']'));
			
		} else if (pattern == PATTERN_0) {
			
			epNum = fileName.substring(fileName.indexOf('#')+1);
			epNum = epNum.substring(0, epNum.indexOf(' '));
			
		}
		
		int season, episode;
		
		if (epNum.contains("s") && epNum.contains("e")) {
			
			season = Integer.parseInt(epNum.substring(epNum.indexOf('s')+1, epNum.indexOf('e')));
			episode = Integer.parseInt(epNum.substring(epNum.indexOf('e')+1));
			
		} else {
			
			season = 1;
			episode = Integer.parseInt(epNum);
			
		}
		
		return new int[]{season, episode};
	}
	
	@Override
	public String getEpisodeTitle(String fileName) {
		
		int pattern = getPattern(fileName);
		
		fileName = fileName.substring(0, fileName.lastIndexOf('.'));
		fileName = fileName.trim();
		
		String title = "";
		
		if (pattern == PATTERN_1) {
		
			title = fileName.substring(fileName.indexOf("] -")+3, fileName.indexOf("Let's Play")-2).trim();
			
		} else if (pattern == PATTERN_0) {
			
			title = fileName.substring(fileName.indexOf('#'));
			title = title.substring(title.indexOf('-')+2);
			title = title.substring(0, title.indexOf('(')).trim();
			
		}
		
		return title;
	}
	
	@Override
	public void createShowNFO(File dir, String showName) {
		
		XBMCMetadata.createShowNFO(dir, showName, "---Let's Play by Gronkh---");
	}
	
	@Override
	public void createEpisodeNFO(int[] info, String title, String fileName, File seasonDir) {
		
		XBMCMetadata.createEpisodeNFO(info, title, fileName, seasonDir);
	}
	
	private static int getPattern(String fileName) {
		
		fileName = fileName.toLowerCase();
		fileName = fileName.substring(0, fileName.lastIndexOf('.'));
		fileName = fileName.trim();
		
		if (fileName.contains("[") && fileName.substring(fileName.indexOf('[')).contains("let's play")) {
			
			return PATTERN_1;
			
		} else {
			
			return PATTERN_0;
			
		}
		
	}
	
}
