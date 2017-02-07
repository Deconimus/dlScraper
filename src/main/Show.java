package main;

import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

import visionCore.util.Files;
import visionCore.util.Web;

import static main.Main.parseBoolean;
import static main.Main.parseId;

public class Show {
	
	
	public String name, seriesPath;
	
	/** absolute episode numbering */
	public boolean absNr;
	
	public String lang;
	public int id;
	public boolean local;
	
	public String url;
	
	
	private Document tmpDoc;
	
	
	public Show(String name, String seriesPath, boolean absNr, int id, String lang, boolean local) {
		
		this.name = name;
		this.seriesPath = seriesPath;
		
		this.absNr = absNr;
		
		this.id = id;
		this.lang = lang;
		this.local = local;
		
		this.url = null;
	}
	
	
	public void load() {
		
		loadCfg();
		
		if (id < 0) { fetchId(); }
		
		url = "http://thetvdb.com/api/"+Main.API_KEY+"/series/"+id+"/all/"+lang+".zip";
		
		updateCfg();
		updateNFOId();
	}
	
	
	private void fetchId() {
		
		SAXReader r = new SAXReader();
		Document document = null;
		try { document = r.read(new URL("http://thetvdb.com/api/GetSeries.php?seriesname="+name.toLowerCase().replace(' ', '+'))); }
		catch (Exception e) { return; }
	
		try { id = Integer.parseInt(document.getRootElement().element("Series").element("seriesid").getText()); }
		catch (Exception | Error e) { return; }
	}
	
	
	private void loadCfg() {
		
		File dir = new File(seriesPath.replace('\\', '/')+name);
		File cfgFile = null;
		
		if (dir == null || !dir.exists()) { return; }
		
		for (File f : dir.listFiles()) {
			
			String nm = f.getName().toLowerCase().replace(".xml", "").replace(".", "").trim();
			
			if (nm.equals("scrapecfg") || nm.equals("scrapercfg")) {
				
				cfgFile = f;
				break;
			}
		}
		
		if (cfgFile != null) {
			
			SAXReader r = new SAXReader();
			Document document = null;
			try { document = r.read(cfgFile); }
			catch (Exception e) { return; }
			
			Element root = document.getRootElement();
			
			for (Iterator<Element> it = root.elementIterator(); it.hasNext();) {
				Element elem = it.next();
				
				String nm = elem.getName().toLowerCase().trim();
				
				if (nm.equals("local")) {
					
					try { this.local = parseBoolean(elem.getText()); } catch (Exception ex) {}
					
				} else if (nm.equals("lang")) {
					
					String v = elem.getTextTrim();
					this.lang = (v != null && !v.isEmpty()) ? v : this.lang;
					
				} else if (nm.equals("id")) {
					
					try { this.id = parseId(elem.getText()); } catch (Exception ex) {}
				}
				
			}
			
			this.tmpDoc = document;
		}
		
	}
	
	private void updateCfg() {
		
		if (this.id < 0) { return; }
		
		Document document = null;
		
		if (tmpDoc != null) {
			
			document = tmpDoc;
			
		} else {
			
			document = DocumentHelper.createDocument();
			document.addElement("cfg");
		}
		
		Element idElem = getOrAddElement(document.getRootElement(), "id");
		
		if (!idElem.getTextTrim().equals(id+"")) {
			
			idElem.setText(id+"");
			
			File cfgFile = new File(seriesPath.replace('\\', '/')+name+"/"+"scrapecfg.xml");
			
			try {
				
				OutputFormat format = OutputFormat.createPrettyPrint();
				
				XMLWriter writer = new XMLWriter(new FileWriter(cfgFile), format);
				writer.write(document);
				writer.flush();
				writer.close();
				
			} catch (Exception e) {}
			
		}
		
		tmpDoc = null;
	}
	
	
	private void updateNFOId() {
		
		if (this.id < 0) { return; }
		
		File file = new File(seriesPath.replace('\\', '/')+name+"/"+"tvshow.nfo");
		
		boolean notXML = false;
		
		if (file.exists()) {
			
			SAXReader r = new SAXReader();
			Document document = null;
			try { document = r.read(file); }
			catch (Exception e) { notXML = true; }
			
			if (document != null) {
				
				Element idElem = getOrAddElement(document.getRootElement(), "id");
				
				if (!idElem.getTextTrim().equals(id+"")) {
					
					idElem.setText(url);
					
					try {
						
						OutputFormat format = OutputFormat.createPrettyPrint();
						
						XMLWriter writer = new XMLWriter(new FileWriter(file), format);
						writer.write(document);
						writer.flush();
						writer.close();
						
					} catch (Exception e) {}
				}
				
			}
		}
		
		if (notXML || !file.exists()) {
			
			String str = "http://thetvdb.com/?tab=series&id="+id;
			
			if (lang.equals("en")) { str += "&lid=7"; }
			
			Files.writeText(file, str);
		}
		
	}
	
	
	public void saveEpisodeMetadata(File episodeFile, Element episodeElem) {
		
		String thumbUrl = null;
		
		for (Element elem : episodeElem.elements()) {
			
			String nm = elem.getName().toLowerCase();
			
			if (nm.equals("filename")) {
				
				thumbUrl = "http://thetvdb.com/banners/"+elem.getTextTrim();
				break;
			}
		}
		
		File thumbFile = new File(episodeFile.getAbsolutePath().substring(0, episodeFile.getAbsolutePath().lastIndexOf('.'))+".jpg");
		Web.downloadFile(thumbUrl, thumbFile);
		
		File nfoFile = new File(episodeFile.getAbsolutePath().substring(0, episodeFile.getAbsolutePath().lastIndexOf('.'))+".nfo");
		XBMCMetadata.createEpisodeNFO(episodeElem, nfoFile, thumbFile.getName(), absNr);
		
	}
	
	public void saveNFO(Element showElement) {
		
		File nfoFile = new File(seriesPath.replace('\\', '/')+name+"/"+"tvshow.nfo");
		XBMCMetadata.createShowNFO(showElement, nfoFile);
	}
	
	
	public void updateMedia(Element showElem, ZipFile zipFile) {
		
		File showdir = new File(seriesPath.replace('\\', '/')+name);
		
		File posterdir = new File(showdir.getAbsolutePath()+"/posters");
		if (!posterdir.exists()) { posterdir.mkdir(); }
		
		File fanartdir = new File(showdir.getAbsolutePath()+"/fanart");
		if (!fanartdir.exists()) { fanartdir.mkdir(); }
		
		
		ZipEntry bannersEntry = zipFile.getEntry("banners.xml");
		
		if (bannersEntry != null) {
		
			SAXReader r = new SAXReader();
			Document document = null;
			try { document = r.read(zipFile.getInputStream(bannersEntry)); }
			catch (Exception | Error e) { }
			
			if (document != null) {
			
				Element root = document.getRootElement();
				
				for (Element bannerElem : root.elements()) {
					
					try { 
						
						String bannerUrl = "http://thetvdb.com/banners/"+bannerElem.elementTextTrim("BannerPath");
						String type = bannerElem.elementTextTrim("BannerType").toLowerCase();
						
						String imgName = bannerUrl.substring(bannerUrl.lastIndexOf('/')+1);
						
						File d = null;
						
						if (type.equals("fanart")) { d = fanartdir; } 
						else if (type.equals("poster")) { d = posterdir; }
						else { continue; }
						
						File f = new File(d.getAbsolutePath()+"/"+imgName);
						if (!f.exists()) { Web.downloadFile(bannerUrl, f); }
						
					} catch (Exception e) {}
				}
				
			}
		}
		
		
		File fanart = new File(showdir.getAbsolutePath()+"/fanart.jpg");
		if (!fanart.exists()) { fetchPic("fanart", fanartdir, showElem, fanart); }
		
		File folderJPG = new File(showdir.getAbsolutePath()+"/folder.jpg");
		if (!folderJPG.exists()) { fetchPic("poster", posterdir, showElem, folderJPG); }
		
		
		XBMCMetadata.checkPosters(showdir);
		
	}
	
	
	private void fetchPic(String type, File dir, Element showElem, File out) {
		
		try {
			
			String picUrl = null;
			
			for (Element elem : showElem.elements()) {
				
				if (elem.getName().equalsIgnoreCase(type)) {
					
					picUrl = "http://thetvdb.com/banners/"+elem.getTextTrim();
					break;
				}
			}
			
			if (picUrl != null) {
				
				File cpy = null;
				
				if (dir != null) {
					
					String picName = picUrl.substring(picUrl.lastIndexOf('/')+1);
					
					for (File f : dir.listFiles()) { if (f.getName().equals(picName)) { cpy = f; break; } }
				}
				
				if (cpy == null) { Web.downloadFile(picUrl, out); } 
				else { Files.copy(cpy, out); }
			}
			
		} catch (Exception e) {}
	}
	
	
	private Element getOrAddElement(Element node, String elementName) {
		
		elementName = elementName.trim();
		String elementNameLC = elementName.toLowerCase();
		
		Element element = null;
		
		for (Iterator<Element> it = node.elementIterator(); it.hasNext();) {
			Element elem = it.next();
			
			if (elem.getName().toLowerCase().trim().equals(elementNameLC)) { element = elem; }
		}
		
		if (element == null) { element = node.addElement(elementName); }
		
		return element;
	}
	
	private void setElementText(Element node, String elementName, String text) {
		
		getOrAddElement(node, elementName).setText(text);
	}
	
	
}
