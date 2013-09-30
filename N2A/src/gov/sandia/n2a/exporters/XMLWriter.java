/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.exporters;

// Taken from libNeuroML; modified

public class XMLWriter  
{
    int indentCount = 0;
    public static final String INDENT = "    ";

    String commPre = "<!--";
    String commPost = "-->";

    protected void addComment(StringBuilder sb, String comment) {
        addComment(sb, comment, false);
    }

    protected void addComment(StringBuilder sb, String comment, boolean extraReturns) 
    {
        if (extraReturns) sb.append("\n");
        if (comment.indexOf("\n")<0)
            sb.append(getIndent()+commPre+comment+commPost+"\n");
        else
            sb.append(commPre+"\n"+comment+"\n"+commPost+"\n");

        if (extraReturns) sb.append("\n");
    }

    protected void startElement(StringBuilder main, String name){
            main.append(getIndent()+"<"+name+">\n");
            indentCount++;
    }

    protected void startEndElement(StringBuilder main, String name){
        main.append(getIndent()+"<"+name+"/>\n");
    }

        protected void startEndTextElement(StringBuilder main, String name, String contents){
                main.append(getIndent()+"<"+name+">"+contents+"</"+name+">\n");
        }

        protected String getIndent(){
                StringBuilder sb = new StringBuilder();
                for(int i=0;i<indentCount;i++)
                        sb.append(INDENT);
                return sb.toString();
        }

        protected void startElement(StringBuilder main, String name, String a1){
                startElement(main, name, a1, false);
        }
        
        protected void startEndElement(StringBuilder main, String name, String a1){
                startElement(main, name, a1, true);
        }
        protected void startElement(StringBuilder main, String name, String a1, boolean endToo){

                main.append(getIndent());
                String[] aa = a1.split("=");
                String end = endToo?"/":"";
                main.append("<"+name+" "+aa[0].trim()+"=\""+aa[1].trim()+"\""+end+">\n");
                if (!endToo) indentCount++;
        }

        protected void addTextElement(StringBuilder main, String name, String text){

                main.append(getIndent());
                main.append("<"+name+">"+text+"</"+name+">\n");
        }

        protected void startElement(StringBuilder main, String name, String a1, String a2){
                startElement(main, name, a1, a2, false);
        }
        protected void startEndElement(StringBuilder main, String name, String a1, String a2){
                startElement(main, name, a1, a2, true);
        }
        protected void startElement(StringBuilder main, String name, String a1, String a2, boolean endToo){

                main.append(getIndent());
                String[] aa = a1.split("=");
                String[] aaa = a2.split("=");
                String end = endToo?"/":"";
                main.append("<"+name+" "+aa[0].trim()+"=\""+aa[1].trim()+"\" "+aaa[0].trim()+"=\""+aaa[1].trim()+"\""+end+">\n");
                if (!endToo) indentCount++;
        }

        protected void startElement(StringBuilder main, String name, String a1, String a2, String a3){
                startElement(main, name, a1, a2, a3, false);
        }
        protected void startEndElement(StringBuilder main, String name, String a1, String a2, String a3){
                startElement(main, name, a1, a2, a3, true);
        }
        protected void startElement(StringBuilder main, String name, String a1, String a2, String a3, boolean endToo){

                main.append(getIndent());
                String[] aa = a1.split("=");
                String[] aaa = a2.split("=");
                String[] aaaa = a3.split("=");
                String end = endToo?"/":"";
                main.append("<"+name+" "+aa[0].trim()+"=\""+aa[1].trim()+"\" "+aaa[0].trim()+"=\""+aaa[1].trim()+"\" "+aaaa[0].trim()+"=\""+aaaa[1].trim()+"\""+end+">\n");
                if (!endToo) indentCount++;
        }

        protected void startElement(StringBuilder main, String name, String a1, String a2, String a3, String a4){
                startElement(main, name, a1, a2, a3, a4, false);
        }
        protected void startEndElement(StringBuilder main, String name, String a1, String a2, String a3, String a4){
                startElement(main, name, a1, a2, a3, a4, true);
        }
        protected void startElement(StringBuilder main, String name, String a1, String a2, String a3, String a4, boolean endToo){

                main.append(getIndent());
                String[] aa = a1.split("=");
                String[] aaa = a2.split("=");
                String[] aaaa = a3.split("=");
                String[] aaaaa = a4.split("=");
                String end = endToo?"/":"";
                main.append("<"+name+" "+aa[0].trim()+"=\""+aa[1].trim()+"\" "+aaa[0].trim()+"=\""+aaa[1].trim()+"\" "+aaaa[0].trim()+"=\""+aaaa[1].trim()+"\" "+aaaaa[0].trim()+"=\""+aaaaa[1].trim()+"\""+end+">\n");
                if (!endToo) indentCount++;
        }

        protected void startElement(StringBuilder main, String name, String a1, String a2, String a3, String a4, String a5){
                startElement(main, name, a1, a2, a3, a4, a5, false);
        }
        protected void startEndElement(StringBuilder main, String name, String a1, String a2, String a3, String a4, String a5){
                startElement(main, name, a1, a2, a3, a4, a5, true);
        }
        protected void startElement(StringBuilder main, String name, String a1, String a2, String a3, String a4, String a5, boolean endToo){

                main.append(getIndent());
                String[] aa = a1.split("=");
                String[] aaa = a2.split("=");
                String[] aaaa = a3.split("=");
                String[] aaaaa = a4.split("=");
                String[] aaaaaa = a5.split("=");
                String end = endToo?"/":"";
                main.append("<"+name+" "+aa[0].trim()+"=\""+aa[1].trim()+"\" "+aaa[0].trim()+"=\""+aaa[1].trim()+"\" "+aaaa[0].trim()+"=\""+aaaa[1].trim()+"\" "+aaaaa[0].trim()+"=\""+aaaaa[1].trim()+"\" "+aaaaaa[0].trim()+"=\""+aaaaaa[1].trim()+"\""+end+">\n");
                if (!endToo) indentCount++;
        }


        protected void startElement(StringBuilder main, String name, String[] attrs){
                startElement(main, name, attrs, false);
        }
        protected void startEndElement(StringBuilder main, String name, String[] attrs){
                startElement(main, name, attrs, true);
        }
        protected void startElement(StringBuilder main, String name, String[] attrs, boolean endToo){

                main.append(getIndent());
                main.append("<"+name);
                for(String attr:attrs)
                {
                        String[] aa = attr.split("=");
                        main.append(" "+aa[0].trim()+"=\""+aa[1].trim()+"\"");
                }
                String end = endToo?"/":"";
                main.append(end+">\n");
                if (!endToo) indentCount++;
        }



        protected void endElement(StringBuilder main, String name){
                indentCount--;
                main.append(getIndent()+"</"+name+">\n");
        }

        public void processMathML(StringBuilder main, String expression){
            startElement(main,"math", "xmlns=http://www.w3.org/1998/Math/MathML");

            addComment(main,"Complete export to MathML not yet implemented!");
            addTextElement(main,"ci", expression.toString());
            endElement(main,"math");
    }
}


















