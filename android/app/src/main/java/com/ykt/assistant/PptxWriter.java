package com.ykt.assistant;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Minimal OOXML picture deck, assembled locally without an export service. */
final class PptxWriter {
    private static final String A="http://schemas.openxmlformats.org/drawingml/2006/main", P="http://schemas.openxmlformats.org/presentationml/2006/main", R="http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    static String xml(String value) { return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;"); }
    static String rels(String... pairs) { StringBuilder s=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"); for(int i=0;i<pairs.length;i+=3) s.append("<Relationship Id=\"").append(pairs[i]).append("\" Type=\"").append(R).append('/').append(pairs[i+1]).append("\" Target=\"").append(pairs[i+2]).append("\"/>"); return s.append("</Relationships>").toString(); }
    static String tree() { return "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>"; }
    static String ns() { return " xmlns:p=\""+P+"\" xmlns:a=\""+A+"\" xmlns:r=\""+R+"\""; }
    static void entry(ZipOutputStream z,String name,String text) throws Exception { z.putNextEntry(new ZipEntry(name)); z.write(text.getBytes(StandardCharsets.UTF_8)); z.closeEntry(); }
    static void write(OutputStream out,String title,double ratio,List<File> images) throws Exception {
        long height=6858000, width=Math.round(height*Math.max(.25,Math.min(4,ratio)));
        try(ZipOutputStream zip=new ZipOutputStream(out)) {
            StringBuilder types=new StringBuilder("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Default Extension=\"jpg\" ContentType=\"image/jpeg\"/>");
            for(String[] item:new String[][]{{"/ppt/presentation.xml","application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"},{"/ppt/slideMasters/slideMaster1.xml","application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"},{"/ppt/slideLayouts/slideLayout1.xml","application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"},{"/ppt/theme/theme1.xml","application/vnd.openxmlformats-officedocument.theme+xml"},{"/docProps/core.xml","application/vnd.openxmlformats-package.core-properties+xml"}}) types.append("<Override PartName=\"").append(item[0]).append("\" ContentType=\"").append(item[1]).append("\"/>");
            for(int i=1;i<=images.size();i++) types.append("<Override PartName=\"/ppt/slides/slide").append(i).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>");
            entry(zip,"[Content_Types].xml",types.append("</Types>").toString());
            entry(zip,"_rels/.rels",rels("rId1","officeDocument","ppt/presentation.xml") .replace("</Relationships>","<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/></Relationships>"));
            entry(zip,"docProps/core.xml","<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>"+xml(title)+"</dc:title><dc:creator>雨课堂助手</dc:creator></cp:coreProperties>");
            StringBuilder slides=new StringBuilder(), links=new StringBuilder(rels("rIdM","slideMaster","slideMasters/slideMaster1.xml").replace("</Relationships>",""));
            for(int i=1;i<=images.size();i++) { slides.append("<p:sldId id=\"").append(255+i).append("\" r:id=\"rId").append(i).append("\"/>"); links.append("<Relationship Id=\"rId").append(i).append("\" Type=\"").append(R).append("/slide\" Target=\"slides/slide").append(i).append(".xml\"/>"); }
            entry(zip,"ppt/presentation.xml","<p:presentation"+ns()+"><p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rIdM\"/></p:sldMasterIdLst><p:sldIdLst>"+slides+"</p:sldIdLst><p:sldSz cx=\""+width+"\" cy=\""+height+"\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/></p:presentation>");
            entry(zip,"ppt/_rels/presentation.xml.rels",links.append("</Relationships>").toString());
            entry(zip,"ppt/slideMasters/slideMaster1.xml","<p:sldMaster"+ns()+"><p:cSld><p:spTree>"+tree()+"</p:spTree></p:cSld><p:clrMap accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" bg1=\"lt1\" bg2=\"lt2\" folHlink=\"folHlink\" hlink=\"hlink\" tx1=\"dk1\" tx2=\"dk2\"/><p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rId1\"/></p:sldLayoutIdLst><p:txStyles><p:titleStyle/><p:bodyStyle/><p:otherStyle/></p:txStyles></p:sldMaster>");
            entry(zip,"ppt/slideMasters/_rels/slideMaster1.xml.rels",rels("rId1","slideLayout","../slideLayouts/slideLayout1.xml","rId2","theme","../theme/theme1.xml"));
            entry(zip,"ppt/slideLayouts/slideLayout1.xml","<p:sldLayout"+ns()+" type=\"blank\" preserve=\"1\"><p:cSld name=\"Blank\"><p:spTree>"+tree()+"</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>");
            entry(zip,"ppt/slideLayouts/_rels/slideLayout1.xml.rels",rels("rId1","slideMaster","../slideMasters/slideMaster1.xml"));
            entry(zip,"ppt/theme/theme1.xml",theme());
            for(int i=1;i<=images.size();i++) {
                String pic="<p:pic><p:nvPicPr><p:cNvPr id=\"2\" name=\"Page "+i+"\"/><p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed=\"rId1\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\""+width+"\" cy=\""+height+"\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic>";
                entry(zip,"ppt/slides/slide"+i+".xml","<p:sld"+ns()+"><p:cSld><p:spTree>"+tree()+pic+"</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>");
                entry(zip,"ppt/slides/_rels/slide"+i+".xml.rels",rels("rId1","image","../media/image"+i+".jpg","rId2","slideLayout","../slideLayouts/slideLayout1.xml"));
                zip.putNextEntry(new ZipEntry("ppt/media/image"+i+".jpg"));
                try(FileInputStream image=new FileInputStream(images.get(i-1))) { byte[] buffer=new byte[8192]; int n; while((n=image.read(buffer))!=-1) zip.write(buffer,0,n); } zip.closeEntry();
            }
        }
    }
    private static String theme() {
        String[] names={"dk1","lt1","dk2","lt2","accent1","accent2","accent3","accent4","accent5","accent6","hlink","folHlink"};
        String[] colors={"243D30","FFFFFF","52715E","EFF4EE","2C5846","839D77","C6D6BB","B99769","7896A1","B7BEA2","2C5846","52715E"};
        StringBuilder s=new StringBuilder("<a:theme xmlns:a=\""+A+"\" name=\"Rain\"><a:themeElements><a:clrScheme name=\"Rain\">");
        for(int i=0;i<names.length;i++) s.append('<').append("a:").append(names[i]).append("><a:srgbClr val=\"").append(colors[i]).append("\"/></a:").append(names[i]).append('>');
        s.append("</a:clrScheme><a:fontScheme name=\"Rain\"><a:majorFont><a:latin typeface=\"Arial\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:majorFont><a:minorFont><a:latin typeface=\"Arial\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:minorFont></a:fontScheme><a:fmtScheme name=\"Rain\"><a:fillStyleLst>");
        for(int i=0;i<3;i++) s.append("<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>");
        s.append("</a:fillStyleLst><a:lnStyleLst>"); for(int i=0;i<3;i++) s.append("<a:ln w=\"9525\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:prstDash val=\"solid\"/></a:ln>");
        s.append("</a:lnStyleLst><a:effectStyleLst>"); for(int i=0;i<3;i++) s.append("<a:effectStyle><a:effectLst/></a:effectStyle>");
        s.append("</a:effectStyleLst><a:bgFillStyleLst>"); for(int i=0;i<3;i++) s.append("<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>");
        return s.append("</a:bgFillStyleLst></a:fmtScheme></a:themeElements><a:objectDefaults/><a:extraClrSchemeLst/></a:theme>").toString();
    }
}
