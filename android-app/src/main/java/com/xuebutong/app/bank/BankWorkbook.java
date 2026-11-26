package com.xuebutong.app.bank;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 读取模板的“题库”工作表；不执行公式，限制解压大小。 */
public final class BankWorkbook {
    private static final int LIMIT=16*1024*1024;
    private static XmlPullParser parser(byte[] bytes)throws Exception{XmlPullParser p=Xml.newPullParser();p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);p.setInput(new ByteArrayInputStream(bytes),"UTF-8");return p;}
    private static String attr(XmlPullParser p,String name){for(int i=0;i<p.getAttributeCount();i++)if(name.equals(p.getAttributeName(i)))return p.getAttributeValue(i);return null;}
    public static List<String[]> read(InputStream input)throws Exception{
        HashMap<String,byte[]> parts=new HashMap<>();int total=0,count=0;
        try(ZipInputStream zip=new ZipInputStream(input)){ZipEntry e;byte[] buf=new byte[8192];while((e=zip.getNextEntry())!=null){if(++count>300)throw new IOException("文件内容过多");ByteArrayOutputStream out=new ByteArrayOutputStream();int n;while((n=zip.read(buf))!=-1){total+=n;if(total>LIMIT)throw new IOException("表格解压后超过 16 MB");out.write(buf,0,n);}parts.put(e.getName(),out.toByteArray());}}
        byte[] workbook=parts.get("xl/workbook.xml"),rels=parts.get("xl/_rels/workbook.xml.rels");if(workbook==null||rels==null)throw new IOException("不是有效的 XLSX 文件");
        String id=null;XmlPullParser p=parser(workbook);for(int t=p.next();t!=XmlPullParser.END_DOCUMENT;t=p.next())if(t==XmlPullParser.START_TAG&&p.getName().equals("sheet")&&"题库".equals(attr(p,"name")))id=attr(p,"id");
        if(id==null)throw new IOException("未找到名为“题库”的工作表");
        String path=null;p=parser(rels);for(int t=p.next();t!=XmlPullParser.END_DOCUMENT;t=p.next())if(t==XmlPullParser.START_TAG&&p.getName().equals("Relationship")&&id.equals(attr(p,"Id")))path=attr(p,"Target");
        if(path==null||path.contains(".."))throw new IOException("工作表路径无效");path=path.startsWith("/")?path.substring(1):"xl/"+path;
        byte[] sheet=parts.get(path);if(sheet==null)throw new IOException("无法读取题库工作表");
        ArrayList<String> shared=new ArrayList<>();byte[] strings=parts.get("xl/sharedStrings.xml");
        if(strings!=null){p=parser(strings);StringBuilder value=null;for(int t=p.next();t!=XmlPullParser.END_DOCUMENT;t=p.next()){if(t==XmlPullParser.START_TAG&&p.getName().equals("si"))value=new StringBuilder();else if(t==XmlPullParser.START_TAG&&p.getName().equals("t")&&value!=null)value.append(p.nextText());else if(t==XmlPullParser.END_TAG&&p.getName().equals("si")){shared.add(value.toString());value=null;}}}
        ArrayList<String[]> rows=new ArrayList<>();p=parser(sheet);String[] row=null;int column=-1;String kind="",value="";boolean formula=false;StringBuilder inline=new StringBuilder();
        for(int t=p.next();t!=XmlPullParser.END_DOCUMENT;t=p.next()){
            if(t==XmlPullParser.START_TAG){String name=p.getName();if(name.equals("row")){String number=attr(p,"r");int index=number==null?rows.size()+1:Integer.parseInt(number);if(index<1||index>10001||index<=rows.size())throw new IOException("表格行号无效或超过 10000 行");while(rows.size()<index-1){String[] blank=new String[8];Arrays.fill(blank,"");rows.add(blank);}row=new String[8];Arrays.fill(row,"");}else if(name.equals("c")&&row!=null){String ref=attr(p,"r");column=0;if(ref==null)throw new IOException("单元格缺少坐标");for(char ch:ref.toCharArray()){if(ch>='A'&&ch<='Z')column=column*26+ch-'A'+1;else break;}column--;kind=attr(p,"t");value="";formula=false;inline.setLength(0);}else if(name.equals("f"))formula=true;else if(name.equals("v"))value=p.nextText();else if(name.equals("t")&&row!=null)inline.append(p.nextText());
            }else if(t==XmlPullParser.END_TAG){String name=p.getName();if(name.equals("c")&&row!=null&&column>=0&&column<8){if(formula)throw new IOException("题库不支持公式单元格，请粘贴为纯文本");if("s".equals(kind)){int i=Integer.parseInt(value);if(i<0||i>=shared.size())throw new IOException("表格文字索引无效");value=shared.get(i);}else if("inlineStr".equals(kind))value=inline.toString();row[column]=value;}else if(name.equals("row")&&row!=null){rows.add(row);row=null;}}
        }
        String[] header={"题型","题目","选项A","选项B","选项C","选项D","正确答案","解析"};if(rows.isEmpty()||!Arrays.equals(rows.remove(0),header))throw new IOException("表头与内置模板不一致，请使用模板填写");
        return rows;
    }
}
