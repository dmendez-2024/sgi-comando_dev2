package com.cajamarca.sgi.comando.accesscontrol;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/** Hojas imprimibles con códigos QR de autorizaciones vigentes del Punto. */
final class AccessControlQrPdf {
    record Item(String identification,String lastNames,String firstNames,String accreditation,LocalDate validUntil,byte[] qrPng) {}
    private static final PDType1Font REGULAR=new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private AccessControlQrPdf() {}

    static byte[] render(List<Item> items,String pointName,String exportedBy){
        try(PDDocument document=new PDDocument();ByteArrayOutputStream output=new ByteArrayOutputStream()){
            PDImageXObject logo;
            try(InputStream input=AccessControlQrPdf.class.getResourceAsStream("/obsint/sgi-comando-logo.png")){
                logo=input==null?null:PDImageXObject.createFromByteArray(document,input.readAllBytes(),"SGI Comando");
            }
            String exportedAt=ZonedDateTime.now(ZoneId.of("America/Guayaquil"))
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm 'GMT-5'",Locale.forLanguageTag("es-EC")));
            int pageCount=(items.size()+8)/9;
            for(int offset=0;offset<items.size();offset+=9){
                PDPage page=new PDPage(PDRectangle.A4);document.addPage(page);
                try(PDPageContentStream stream=new PDPageContentStream(document,page)){
                    stream.setNonStrokingColor(new Color(31,31,31));stream.addRect(16,758,563,72);stream.fill();
                    if(logo!=null)stream.drawImage(logo,38,764,62,62);
                    stream.setNonStrokingColor(Color.WHITE);
                    text(stream,BOLD,16,112,801,"SGI COMANDO");
                    text(stream,REGULAR,9,112,781,"LISTA BLANCA  |  FICHAS INDIVIDUALES CON QR");
                    stream.setNonStrokingColor(new Color(188,30,46));stream.addRect(16,754,563,4);stream.fill();
                    stream.setNonStrokingColor(new Color(39,49,65));
                    text(stream,REGULAR,9,42,740,"Punto: "+shorten(pointName,64)+"  |  Autorizaciones: "+items.size());
                    for(int index=0;index<9&&offset+index<items.size();index++){
                        Item item=items.get(offset+index);
                        float x=24+(index%3)*187,y=724-(index/3)*216;
                        stream.setStrokingColor(new Color(155,164,176));stream.setLineWidth(.7f);
                        stream.setLineDashPattern(new float[]{3,3},0);stream.addRect(x,y-205,173,205);stream.stroke();stream.setLineDashPattern(new float[]{},0);
                        String fullName=(item.firstNames()+" "+item.lastNames()).trim();
                        String[] nameLines=splitName(fullName,25);
                        text(stream,BOLD,8,x+8,y-14,nameLines[0]);
                        if(!nameLines[1].isBlank())text(stream,BOLD,8,x+8,y-25,nameLines[1]);
                        if(!nameLines[2].isBlank())text(stream,BOLD,8,x+8,y-36,nameLines[2]);
                        text(stream,REGULAR,8,x+8,y-49,"Identificacion: "+item.identification());
                        text(stream,REGULAR,7,x+8,y-61,"Acreditacion: "+shorten(item.accreditation(),27));
                        PDImageXObject qr=PDImageXObject.createFromByteArray(document,item.qrPng(),"QR");
                        stream.drawImage(qr,x+34.5f,y-174,104,104);
                        text(stream,REGULAR,7,x+8,y-193,"Vigente hasta: "+item.validUntil());
                    }
                    stream.setStrokingColor(new Color(190,197,207));stream.moveTo(20,42);stream.lineTo(575,42);stream.stroke();
                    stream.setNonStrokingColor(new Color(103,111,123));
                    text(stream,REGULAR,7,20,29,"SGI COMANDO  |  Exportado por: "+shorten(exportedBy,38)+"  |  "+exportedAt);
                    text(stream,REGULAR,7,512,29,"Pagina "+(offset/9+1)+" de "+pageCount);
                }
            }
            document.save(output);return output.toByteArray();
        }catch(IOException exception){throw new IllegalStateException("No fue posible generar el PDF de codigos QR.",exception);}
    }
    private static void text(PDPageContentStream stream,PDType1Font font,int size,float x,float y,String value)throws IOException{
        String safe=Normalizer.normalize(value==null?"":value,Normalizer.Form.NFD).replaceAll("\\p{M}","").replaceAll("[^\\x20-\\x7E]","?");
        stream.beginText();stream.setFont(font,size);stream.newLineAtOffset(x,y);stream.showText(safe);stream.endText();
    }
    private static String shorten(String value,int max){return value.length()<=max?value:value.substring(0,max-1)+"...";}
    private static String[] splitName(String value,int max){
        String[] lines={"","",""};String remaining=value;
        for(int index=0;index<3&&!remaining.isBlank();index++){
            if(remaining.length()<=max){lines[index]=remaining;break;}
            if(index==2){lines[index]=shorten(remaining,max);break;}
            int breakAt=remaining.lastIndexOf(' ',max);if(breakAt<max/2)breakAt=max;
            lines[index]=remaining.substring(0,breakAt).trim();remaining=remaining.substring(breakAt).trim();
        }
        return lines;
    }
}
