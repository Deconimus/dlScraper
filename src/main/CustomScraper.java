package main;

import java.io.File;

public interface CustomScraper {

	public int[] getEpisodeInfo(String fileName);
	
	public String getEpisodeTitle(String fileName);
	
	default public void createShowNFO(File dir, String showName) {
		
		XBMCMetadata.createShowNFO(dir, showName);
	}
	
	default public void createEpisodeNFO(int[] info, String title, String fileName, File seasonDir) {
		
		XBMCMetadata.createEpisodeNFO(info, title, fileName, seasonDir);
	}
	
}
