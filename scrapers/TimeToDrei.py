from main import CustomScraper
from main import Main
from main import XBMCMetadata

import unicodedata

class TimeToDrei(CustomScraper):
	
	
	def getAlias(self):
		
		return "t23"

		
	def getEpisodeInfo(self, fileName):
		
		#fileName = unicodedata.normalize('NFKD', fileName).encode('ascii','ignore')
		
		fileName = fileName[fileName.index("#")+1:]
		
		int2parse = ""
		
		for i in range(0, len(fileName)):
			
			s = int2parse + fileName[i]
			
			if (s.isdigit()):
				
				int2parse = s
				
			else:
				break
				
		episode = 0
		
		if (int2parse.isdigit()):
			
			episode = int(int2parse)
		
		return [1, episode]

		
	def getEpisodeTitle(self, fileName):
		
		title = fileName[:fileName.rindex("-")]
		title = title[:title.rindex("-")]
		title = title[:title.rindex("-")]
		
		title = title.replace("\"", "").replace("_", "")
		title = title.strip()
		title = Main.cleanseFileName(title)
		
		return title
		
		
	def createShowNFO(self, dir, showName):
		
		XBMCMetadata.createShowNFO(dir, showName, "----Let's Play by TimeToDrei----")
		
		
	def createEpisodeNFO(self, info, title, fileName, seasonDir):
		
		XBMCMetadata.createEpisodeNFO(info, title, fileName, seasonDir)