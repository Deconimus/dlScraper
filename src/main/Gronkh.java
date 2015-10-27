package main;

import java.io.File;
import java.io.FileWriter;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;

public class Gronkh {

	/** Older pattern: 
	 * "Let's Play {gametitle} #{epnr 3 digits} [Deutsch][HD] - {eptitle}" */
	public static final int PATTERN_0 = 0;
	
	/** Current pattern as of 2015: 
	 * "{gametitle caps} [{epnr}] - {eptitle} [unicode-*] Let's Play {gametitle}" */
	public static final int PATTERN_1 = 1;
	
	public static int[] getEpisodeInfo(String fileName) {

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
	
	public static String getEpisodeTitle(String fileName) {
		
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
	
	public static void showNFO(File dir, String showName) {
		
		if (!dir.exists()) { return; }
		
		File nfo = new File(dir.getPath()+"/tvshow.nfo");
		
		if (!nfo.exists()) {
			
			Document doc =  DocumentHelper.createDocument();
			
			Element root = doc.addElement("tvshow");
			
			root.addElement("title").setText(showName);
			root.addElement("plot").setText("---Let's Play by Gronkh---");
			root.addElement("genre").setText("Lets Play / Gaming");
			root.addElement("studio").setText("Gronkh");
			
			try {
				
				OutputFormat format = OutputFormat.createPrettyPrint();
				
				XMLWriter writer = new XMLWriter(new FileWriter(nfo), format);
				writer.write(doc);
				writer.close();
				
			} catch (Exception | Error e) { e.printStackTrace(); }
			
		}
		
	}
	
	public static void episodeNFO(int[] info, String title, String fileName, File seasonDir) {
		
		if (!seasonDir.exists()) { return; }
		
		File nfo = new File(seasonDir.getPath()+"/"+fileName+".nfo");
		
		if (!nfo.exists()) {
			
			Document doc =  DocumentHelper.createDocument();
			
			Element root = doc.addElement("episodedetails");
			
			root.addElement("title").setText(title);
			root.addElement("season").setText(info[0]+"");
			root.addElement("episode").setText(info[1]+"");
			root.addElement("director").setText("Gronkh");
			
			try {
				
				OutputFormat format = OutputFormat.createPrettyPrint();
				
				XMLWriter writer = new XMLWriter(new FileWriter(nfo), format);
				writer.write(doc);
				writer.close();
				
			} catch (Exception | Error e) { e.printStackTrace(); }
			
		}
		
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
