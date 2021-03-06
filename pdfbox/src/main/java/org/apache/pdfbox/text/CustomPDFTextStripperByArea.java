package org.apache.pdfbox.text;

import org.apache.pdfbox.pdmodel.PDPage;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.Map.Entry;

/**
 * Created by goforit on 2017/9/22.
 */
public class CustomPDFTextStripperByArea extends PDFTextStripper {
	private List<String> regions;
	private Map<String, Rectangle2D> regionArea;
	private Map<String, ArrayList<List<TextPosition>>> regionCharacterList;
	private Map<String, StringWriter> regionText = null;

	private Map<TextPositionKey, List<TextPosition>> textPositionMap = null;
	private int flagPicTitle;// 0为过滤.1位不过滤
	private int flagTable;// 0为过滤.1位不过滤
	private boolean beginDelete=true;

	/**
	 * Constructor.
	 *
	 * @throws IOException
	 *             If there is an error loading properties.
	 */
	public CustomPDFTextStripperByArea(int flagPicTitle, int flagTable) throws IOException {
		this.flagPicTitle = flagPicTitle;
		this.flagTable = flagTable;
		regions = new ArrayList<String>();
		regionArea = new HashMap<String, Rectangle2D>();
		regionCharacterList = new HashMap<String, ArrayList<List<TextPosition>>>();
		regionText = new HashMap<String, StringWriter>();
		textPositionMap = new HashMap<TextPositionKey, List<TextPosition>>();
		super.setShouldSeparateByBeads(false);
	}

	/**
	 * This method does nothing in this derived class, because beads and regions
	 * are incompatible. Beads are ignored when stripping by area.
	 *
	 * @param aShouldSeparateByBeads
	 *            The new grouping of beads.
	 */
	@Override
	public final void setShouldSeparateByBeads(boolean aShouldSeparateByBeads) {
	}

	/**
	 * Add a new region to group text by.
	 *
	 * @param regionName
	 *            The name of the region.
	 * @param rect
	 *            The rectangle area to retrieve the text from.
	 */
	public void addRegion(String regionName, Rectangle2D rect) {
		regions.add(regionName);
		regionArea.put(regionName, rect);
	}

	/**
	 * Delete a region to group text by. If the region does not exist, this
	 * method does nothing.
	 *
	 * @param regionName
	 *            The name of the region to delete.
	 */
	public void removeRegion(String regionName) {
		regions.remove(regionName);
		regionArea.remove(regionName);
	}

	/**
	 * Get the list of regions that have been setup.
	 *
	 * @return A list of java.lang.String objects to identify the region names.
	 */
	public List<String> getRegions() {
		return regions;
	}

	/**
	 * Get the text for the region, this should be called after
	 * extractRegions().
	 *
	 * @param regionName
	 *            The name of the region to get the text from.
	 * @return The text that was identified in that region.
	 */
	public String getTextForRegion(String regionName) {
		StringWriter text = regionText.get(regionName);
		return text.toString();
	}

	/**
	 * Process the page to extract the region text.
	 *
	 * @param page
	 *            The page to extract the regions from.
	 * @throws IOException
	 *             If there is an error while extracting text.
	 */
	public void extractRegions(PDPage page) throws IOException {
		for (String region : regions) {
			setStartPage(getCurrentPageNo());
			setEndPage(getCurrentPageNo());
			// reset the stored text for the region so this class
			// can be reused.
			String regionName = region;
			ArrayList<List<TextPosition>> regionCharactersByArticle = new ArrayList<List<TextPosition>>();
			regionCharactersByArticle.add(new ArrayList<TextPosition>());
			regionCharacterList.put(regionName, regionCharactersByArticle);
			regionText.put(regionName, new StringWriter());
		}

		if (page.hasContents()) {
			processPage(page);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void processTextPosition(TextPosition text) {
		for (String region : regionArea.keySet()) {
			Rectangle2D rect = regionArea.get(region);
			if (rect.contains(text.getX(), text.getY())) {
				charactersByArticle = regionCharacterList.get(region);
				super.processTextPosition(text);
			}
		}
	}

	/**
	 * This will print the processed page text to the output stream.
	 *
	 * @throws IOException
	 *             If there is an error writing the text.
	 */
	@Override
	protected void writePage() throws IOException {
		List<TextPosition> removeList = new LinkedList<TextPosition>();
		for (String region : regionArea.keySet()) {
			charactersByArticle = regionCharacterList.get(region);

			textPositionMap.clear();
			for (int i = 0; i < charactersByArticle.size(); i++) {
				for (int j = 0; j < charactersByArticle.get(i).size(); j++) {
					TextPosition w = charactersByArticle.get(i).get(j);
					
					if(w.getX()<70f&w.getHeight()>7.0f){
						try{
							if(Integer.parseInt(w.getUnicode())<6){
								beginDelete = true;
							}
							if(Integer.parseInt(w.getUnicode())>=6){
								beginDelete = false;
							}
						}catch(Exception e){
							
						}
					}
					if(beginDelete){
						removeList.add(w);
					}
					TextPositionKey key = new TextPositionKey((int) w.getY(), (int) w.getHeight());

					List<TextPosition> textPositionsList = textPositionMap.get(key);
					if (textPositionsList == null) {
						textPositionsList = new LinkedList<TextPosition>();
					}
					textPositionsList.add(w);
					textPositionMap.put(key, textPositionsList);
				}
			}
			//just keep6~9
			for(TextPosition w:removeList){
//				System.out.print(w.getUnicode());
				charactersByArticle.get(0).remove(w);
			}
			
			for (Entry<TextPositionKey, List<TextPosition>> entry : textPositionMap.entrySet()) {
				TextPositionKey k = entry.getKey();
				List<TextPosition> v = entry.getValue();
				if (v.get(0).getX() > 150f) {
					for (TextPosition w : v) {
						if (w.getHeight() > 3f&&flagPicTitle == 0) {
								charactersByArticle.get(0).remove(w);
								// System.out.println( "x:" + w.getX() + ",y:" +
								// w.getY() + ",height:" + w.getHeight() + ",
								// content:" + w.getUnicode());
						}
					}
				}else {
					float maxWidthForText = v.get(0).getWidth();
					float maxSplitTable = maxWidthForText * 3;
					boolean isDelete = false;
					TextPosition last = null;
					//delete Table
					int splitSize = 0;
					for (int i = 0; i < v.size(); i++) {
						if (last != null) {
							float textInterval = v.get(i).getX() - last.getX();
							if ( textInterval > maxSplitTable&& textInterval > v.get(i).getWidth()*3) {
								 System.out.println(last.getUnicode()
								 +"|||||||||" + v.get(i).getUnicode() +",textInterval:" + textInterval + ",v.get(i).getWidth():" + v.get(i).getWidth());
								splitSize++;
								
//								break;
							}
						}
						last = v.get(i);
					}
					if(splitSize>1){
						isDelete=true;
					}
					if (isDelete&&flagTable==0) {
						for (TextPosition w : v) {
							if (w.getHeight() > 3f) {
								charactersByArticle.get(0).remove(w);
								 System.out.println("x:" + w.getX() + ",y:" + w.getY() + ",height:" + w.getHeight() + ",width:"+ w.getWidth()+", content:" + w.getUnicode());
							}
						}
					}
				}
			}

			output = regionText.get(region);
			super.writePage();
		}
	}

}
