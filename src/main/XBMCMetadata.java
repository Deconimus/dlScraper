package main;

import java.io.File;
import java.io.FileWriter;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;

public class XBMCMetadata {

	public static void createShowNFO(File dir, String showName) {
		createShowNFO(dir, showName, "");
	}
	
	public static void createShowNFO(File dir, String showName, String plot) {
		
		if (!dir.exists()) { return; }
		
		File nfo = new File(dir.getPath()+"/tvshow.nfo");
		
		if (!nfo.exists()) {
			
			Document doc =  DocumentHelper.createDocument();
			
			Element root = doc.addElement("tvshow");
			
			if (showName.length() > 0) { root.addElement("title").setText(showName); }
			if (plot.length() > 0) { root.addElement("plot").setText(plot); }
			
			try {
				
				OutputFormat format = OutputFormat.createPrettyPrint();
				
				XMLWriter writer = new XMLWriter(new FileWriter(nfo), format);
				writer.write(doc);
				writer.close();
				
			} catch (Exception | Error e) { e.printStackTrace(); }
			
		}
		
	}
	
	public static void createEpisodeNFO(int[] info, String title, String fileName, File seasonDir) {
		
		if (!seasonDir.exists()) { return; }
		
		File nfo = new File(seasonDir.getPath()+"/"+fileName+".nfo");
		
		if (!nfo.exists()) {
			
			Document doc =  DocumentHelper.createDocument();
			
			Element root = doc.addElement("episodedetails");
			
			root.addElement("title").setText(title);
			root.addElement("season").setText(info[0]+"");
			root.addElement("episode").setText(info[1]+"");
			
			try {
				
				OutputFormat format = OutputFormat.createPrettyPrint();
				
				XMLWriter writer = new XMLWriter(new FileWriter(nfo), format);
				writer.write(doc);
				writer.close();
				
			} catch (Exception | Error e) { e.printStackTrace(); }
			
		}
		
	}
	
}
