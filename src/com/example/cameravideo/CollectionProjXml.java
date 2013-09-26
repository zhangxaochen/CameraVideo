package com.example.cameravideo;

import java.util.ArrayList;
import java.util.List;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

@Root(name="collection-seq")
public class CollectionProjXml {
	@Element(name="collection-proj-name")
	private String projName;
	
	@Element(name="collection-proj-discription")
	private String projDiscription;
	
	@Element(name="collection-count")
	private int collectionCnt=0;
	
	@Element(name="collections")
	private CollectionsNode collectionsNode;
}

class CollectionsNode{
	@ElementList(entry="collection")
	List<CollectionNode> collectionList=new ArrayList<CollectionNode>();
}

class CollectionNode{
	@Element(name="pic-count")
	int picCount=0;

	@Element(name="pics")
	PicsNode picsNode;
	
//	@ElementList(entry="pic")
//	List<PicNode> picList=new ArrayList<PicNode>();
	
	@Element(name="sensor-name")
	String sensorName;	
}

class PicsNode{
	@ElementList(entry="pic")
	List<PicNode> picList=new ArrayList<PicNode>();
}

class PicNode{
	@Element(name="name")
	String picName;
	
	@Element(name="timestamp")
	float timeStamp;
}