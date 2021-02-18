package main;

import java.io.File;

public class GigaTop100 implements CustomScraper {

	public static void load() {
		
		Main.customScrapers.put("giga", new GigaTop100());
	}
	
	public String getAlias() {
		
		return "giga";
	}
	
	@Override
	public int[] getEpisodeInfo(String fileName) {
		
		fileName = fileName.toLowerCase();
		
		if (fileName.contains("finale")) { return new int[]{1, 20}; }
		
		String f = "aller zeiten - teil ";
		fileName = fileName.substring(fileName.indexOf(f) + f.length());
		fileName = fileName.substring(0, fileName.indexOf(" "));
		fileName = fileName.replace(":", "");
		
		return new int[]{1, Integer.parseInt(fileName.trim())};
	}

	@Override
	public String getEpisodeTitle(String fileName) {
		
		fileName = fileName.toLowerCase();
		fileName = fileName.replace("ä", "ae");
		
		if (fileName.contains("finale")) { return "Das Finale"; }
		
		String f = "aller zeiten ";
		
		if (fileName.contains("plaetze")) {
			
			f += "- teil";
		}
		
		fileName = fileName.substring(fileName.indexOf(f) + f.length());
		fileName = fileName.replace(":", "");
		fileName = fileName.substring(fileName.indexOf("- ")+2);
		
		fileName = fileName.replace(" (720p)", "");
		fileName = fileName.replace(".mp4", "");
		fileName = fileName.replace("teil", "Teil");
		fileName = fileName.replace("plaetze", "Plaetze");
		
		return fileName;
	}
	
	@Override
	public void createShowNFO(File dir, String showName) {
		
		XBMCMetadata.createShowNFO(dir, showName);
	}
	
	@Override
	public void createEpisodeNFO(int[] info, String title, String fileName, File seasonDir) {
		
		XBMCMetadata.createEpisodeNFO(info, title, fileName, seasonDir);
	}

}
