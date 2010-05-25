//xlsParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Tim Riemann
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.document.parser;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Idiom;
import net.yacy.document.ParserException;
import net.yacy.kelondro.logging.Log;

import org.apache.poi.hssf.eventusermodel.HSSFEventFactory;
import org.apache.poi.hssf.eventusermodel.HSSFListener;
import org.apache.poi.hssf.eventusermodel.HSSFRequest;
import org.apache.poi.hssf.record.NumberRecord;
import org.apache.poi.hssf.record.Record;
import org.apache.poi.hssf.record.SSTRecord;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;


public class xlsParser extends AbstractParser implements Idiom {
    
    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Set<String> SUPPORTED_MIME_TYPES = new HashSet<String>();
    public static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<String>();
    static {
        SUPPORTED_EXTENSIONS.add("xls");
        SUPPORTED_EXTENSIONS.add("xla");
        SUPPORTED_MIME_TYPES.add("application/msexcel");
        SUPPORTED_MIME_TYPES.add("application/excel");
        SUPPORTED_MIME_TYPES.add("application/vnd.ms-excel");
        SUPPORTED_MIME_TYPES.add("application/x-excel");
        SUPPORTED_MIME_TYPES.add("application/x-msexcel");
        SUPPORTED_MIME_TYPES.add("application/x-ms-excel");
        SUPPORTED_MIME_TYPES.add("application/x-dos_ms_excel");
        SUPPORTED_MIME_TYPES.add("application/xls");
    }     

    public xlsParser(){
        super("Microsoft Excel Parser");
    }

    /*
     * parses the source documents and returns a plasmaParserDocument containing
     * all extracted information about the parsed document
     */ 
    public Document parse(final MultiProtocolURI location, final String mimeType,
            final String charset, final InputStream source) throws ParserException,
            InterruptedException {
        return new XLSHSSFListener().parse(location, mimeType, charset, source);
    }
    
    public Set<String> supportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

    @Override
    public void reset(){
        //nothing to do
        super.reset();
    }
    
    
    public class XLSHSSFListener implements HSSFListener {

        //StringBuilder for parsed text
        private final StringBuilder sbFoundStrings;
        

        public XLSHSSFListener() {
            this.sbFoundStrings = new StringBuilder(100);
        }

        /*
         * parses the source documents and returns a Document containing
         * all extracted information about the parsed document
         */ 
        public Document parse(final MultiProtocolURI location, final String mimeType,
                final String charset, final InputStream source) throws ParserException,
                InterruptedException {
            try {
                
                //create a new org.apache.poi.poifs.filesystem.Filesystem
                final POIFSFileSystem poifs = new POIFSFileSystem(source);
                //get the Workbook (excel part) stream in a InputStream
                final InputStream din = poifs.createDocumentInputStream("Workbook");
                //construct out HSSFRequest object
                final HSSFRequest req = new HSSFRequest();
                //lazy listen for ALL records with the listener shown above
                req.addListenerForAllRecords(this);
                //create our event factory
                final HSSFEventFactory factory = new HSSFEventFactory();
                //process our events based on the document input stream
                factory.processEvents(req, din);
                //close our document input stream (don't want to leak these!)
                din.close();
                
                //now the parsed strings are in the StringBuilder, now convert them to a String
                final String contents = sbFoundStrings.toString().trim();
                
                /*
                 * create the plasmaParserDocument for the database
                 * and set shortText and bodyText properly
                 */
                final Document theDoc = new Document(
                        location,
                        mimeType,
                        "UTF-8",
                        null,
                        null,
                        location.getFile(),
                        "", // TODO: AUTHOR
                        "", // TODO: publisher
                        null,
                        null,
                        contents.getBytes("UTF-8"),
                        null,
                        null,
                        false);
                return theDoc;
            } catch (final Exception e) { 
                if (e instanceof InterruptedException) throw (InterruptedException) e;

                /*
                 * an unexpected error occurred, log it and throw a ParserException
                 */
                Log.logException(e);
                final String errorMsg = "Unable to parse the xls document '" + location + "':" + e.getMessage();
                throw new ParserException(errorMsg, location);
            }
        }

        public void processRecord(final Record record) {
            SSTRecord sstrec = null;
            switch (record.getSid()){
                case NumberRecord.sid: {
                    final NumberRecord numrec = (NumberRecord) record;
                    sbFoundStrings.append(numrec.getValue());
                    break;
                }
                //unique string records
                case SSTRecord.sid: {
                    sstrec = (SSTRecord) record;
                    for (int k = 0; k < sstrec.getNumUniqueStrings(); k++){
                        sbFoundStrings.append( sstrec.getString(k) );
                        
                        //add line seperator
                        sbFoundStrings.append( "\n" );
                    }
                    break;
                }
                /*
                case LabelSSTRecord.sid: {
                    final LabelSSTRecord lsrec = (LabelSSTRecord)record;
                    sbFoundStrings.append( sstrec.getString(lsrec.getSSTIndex()) );
                    break;
                }
                */
            }
            
            //add line seperator
            sbFoundStrings.append( "\n" );
        }
    }
    
}
