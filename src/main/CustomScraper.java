package main;

import java.io.File;

public interface CustomScraper {

	public String getAlias();
	
	public int[] getEpisodeInfo(String fileName);
	
	public String getEpisodeTitle(String fileName);
	
	public void createShowNFO(File dir, String showName);
	
	public void createEpisodeNFO(int[] info, String title, String fileName, File seasonDir);
	
}
